/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.io.File
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit

import afpma.firecalc.payments.TestDatabaseSetup
import afpma.firecalc.payments.db.Migrations
import afpma.firecalc.payments.domain.{Currency, OrderStatus}
import afpma.firecalc.payments.repository.impl.{
    MoleculeCustomerRepository,
    MoleculeDatabaseInspection,
    MoleculeInvoiceCounterRepository,
    MoleculeOrderRepository
}
import afpma.firecalc.payments.shared.api.{BackendCompatibleLanguage, CustomerInfo, CustomerType}

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*

import molecule.db.common.spi.Conn
import utest.*

/**
 * Boot acceptance: the real backend JVM, started against a rewound database, must
 * refuse to start — non-zero exit, the fatal block naming the file, the counter, the
 * highest issued number and the next-mint collision, and no HTTP port ever bound.
 * Forked via ProcessBuilder on the test JVM's own classpath. Everything the boot reads
 * is synthesized, so the test depends on no untracked machine file: the fixture DB is
 * built through the repository layer in a temp directory — six paid-out orders numbered
 * 1..6 under the staging prefix, the counter then rewound to 1, the shape of the actual
 * staging incident — and the configuration is written into the temp tree from the
 * tracked template under configs/templates/, with the db path, prefix and counter-start
 * substituted. The template's one `${JWT_SECRET}` substitution is given a literal
 * test value — the whole input tree of the forked boot comes from tracked files.
 */
object InvoiceNumberingBootProcessTest extends TestSuite with TestDatabaseSetup:

    private def templatePath(root: Path): Path =
        root.resolve("configs/templates/payments/payments-config.conf.template")

    private def emailTemplatePath(root: Path): Path =
        root.resolve("configs/templates/payments/email-config.conf.template")

    /** The repo root carrying the tracked config templates. */
    private def repoRoot(): Path =
        var dir = new File(System.getProperty("user.dir")).toPath.toAbsolutePath
        while !Files.exists(templatePath(dir)) do
            dir = dir.getParent
            if dir == null then
                throw new java.lang.AssertionError("no repo root carrying the config templates above user.dir")
        dir

    /**
     * The staging-rewind shape, synthesized the way a fresh production file is made:
     * the schema by the Flyway chain (which also leaves the migration history a later
     * boot validates), then — through the repository layer — six paid-out orders
     * holding the numbers 1..6, and the counter rewound to 1. Numbers 1..6 are issued
     * under FCALC-STG-[YYYY]- with the CURRENT year, which is what the boot of the
     * same day re-mints against.
     */
    private def buildRewoundFixture(fixture: Path): Unit =
        Migrations.migrate[IO](s"jdbc:sqlite:${fixture.toAbsolutePath}").unsafeRunSync()
        testFileConnectionResource(fixture)
            .use { conn =>
                given Conn       = conn
                val counterRepo  = new MoleculeInvoiceCounterRepository[IO]()
                val orderRepo    = new MoleculeOrderRepository[IO]()
                val customerRepo = new MoleculeCustomerRepository[IO]()
                for
                    _    <- counterRepo.initializeCounter(1L)
                    cust <- customerRepo.create(
                        CustomerInfo       (
                            email        = "boot-guard@example.com",
                            customerType = CustomerType.Individual,
                            language     = BackendCompatibleLanguage.English,
                            givenName    = Some("Jean"),
                            familyName   = Some("Dupont"),
                            companyName  = None,
                            addressLine1 = None,
                            addressLine2 = None,
                            addressLine3 = None,
                            city         = None,
                            region       = None,
                            postalCode   = None,
                            countryCode  = None,
                            phoneNumber  = None
                        )
                    )
                    _    <- (1 to 6).toList.traverse_ { _ =>
                        for
                            o <- orderRepo
                                .create(
                                    cust.id,
                                    afpma.firecalc.payments.shared.api.ProductId(java.util.UUID.randomUUID()),
                                    BigDecimal(59.0),
                                    Currency.EUR,
                                    BackendCompatibleLanguage.English,
                                    None
                                )
                            n <- counterRepo.getNextInvoiceNumber()
                            _ <- orderRepo.updateInvoiceNumber(
                                o.id,
                                InvoiceNumberValidator.formatInvoiceNumber("FCALC-STG-[YYYY]-", n, 4)
                            )
                            _ <- orderRepo.updateStatus(o.id, OrderStatus.PaidOut)
                        yield ()
                    }
                    // Raw DML for the one state the repository layer cannot create: the rewind.
                    _    <- IO(molecule.db.sqlite.sync.rawTransact("UPDATE InvoiceCounter SET currentNumber = 1")).void
                yield ()
            }
            .unsafeRunSync                                                            ()

    /**
     * Read the built fixture back through the same [[InvoiceNumberingSource]] the boot
     * uses and assert the precondition it is supposed to carry: counter Some(1L), highest
     * issued 6, six numbered orders. Checked before the fork so a broken fixture fails
     * here, attributed to the fixture — not later, as unexplained missing stdout.
     */
    private def verifyRewoundFixture(fixture: Path): Unit =
        testFileConnectionResource(fixture)
            .use { conn =>
                given Conn = conn
                val source = InvoiceNumberingSource[IO](
                    new MoleculeInvoiceCounterRepository[IO](),
                    new MoleculeOrderRepository[IO](),
                    new MoleculeDatabaseInspection[IO](),
                    fixture.toString
                )
                source.read.flatMap { state =>
                    IO {
                        assert(state.counterCurrent == Some(1L)             ) // the rewind is on disk
                        assert(state.highestIssuedSequenceNumber == Some(6L)) // the six issued numbers
                        assert(state.numberedCount == 6                     ) // all six orders hold one
                    }
                }
            }
            .unsafeRunSync()

    /**
     * A classpath for the forked JVM. Under an sbt server the test code lives on a
     * child URLClassLoader, so `java.class.path` alone names only sbt's launcher;
     * collect the real entries from the classloader chain, and keep the JVM property
     * as a fallback for a plain forked test JVM where the chain is the system loader.
     */
    private def childClasspath: String =
        val seen = scala.collection.mutable.LinkedHashSet.empty[String]
        var loader: ClassLoader = getClass.getClassLoader
        while loader != null do
            loader match
                case url: java.net.URLClassLoader =>
                    url.getURLs.foreach(u => seen += Path.of(u.toURI).toString)
                case _ =>
            loader = loader.getParent
        // The classes directory of this module, wherever the class was loaded from.
        Option(
            classOf[afpma.firecalc.payments.invoice.InvoiceNumberingState].getProtectionDomain.getCodeSource.getLocation
        )
            .foreach(loc => seen += Path.of(loc.toURI).toString)
        // Only when the loader chain exposed nothing (plain forked JVM): the system
        // property is the real classpath there. Mixing it in next to an sbt server's
        // chain would put sbt's own scala-library on the child's boot path.
        if seen.isEmpty then
            sys.props.get("java.class.path").foreach(_.split(':').filter(_.nonEmpty).foreach(seen += _))
        seen.mkString(":")

    val tests = Tests {

        test("the real backend process refuses a synthesized rewound fixture and never binds the HTTP port") {
            val root = repoRoot()

            val tmp = Files.createTempDirectory("firecalc-boot-guard")
            try
                // The rewound fixture: numbers 1..6 issued, counter rewound to 1.
                val fixture = tmp.resolve("boot-guard-fixture.db")
                buildRewoundFixture (fixture)
                verifyRewoundFixture(fixture)

                // The config is written from the TRACKED template into the temp tree the
                // forked process resolves FIRECALC_ENV=development against. The fixture's
                // own prefix is used so the contradiction is purely the counter rewind.
                val paymentsConf = tmp.resolve("configs/development/payments/payments-config.conf")
                Files.createDirectories(paymentsConf.getParent)
                val rewritten = Files
                    .readString  (templatePath(root)     )
                    .replace(
                        """path = "./databases/dev/firecalc-payments-dev.db"""",
                        s"""path = "${fixture.toAbsolutePath}""""
                    )
                    .replace("""number-prefix = "FCALC-DEV-[YYYY]-"""", """number-prefix = "FCALC-STG-[YYYY]-"""")
                    // Synthesize the one secret the template takes from the environment:
                    // the loader resolves substitutions against system properties only, so
                    // this boot needs a literal here. The JWT is never used — the process
                    // refuses before serving anything.
                    .replace("""secret = ${JWT_SECRET}""", """secret = "boot-guard-test-secret-0123456789abcdef"""")
                    // Pin the counter-start explicitly (any key generation): the config
                    // carries the template start while the file was rewound — the b25
                    // condition. Inheriting a local dev config's value would make the
                    // fixture depend on an untracked machine file.
                    .replaceAll("(?m)^\\s*counter-start(ing-number)?\\s*=\\s*[0-9]+", "counter-start = 1")
                Files.writeString(paymentsConf, rewritten)

                // The boot loads the email config before the guard runs; the tracked
                // template is complete on its own — no substitutions needed.
                Files.copy(emailTemplatePath(root), tmp.resolve("configs/development/payments/email-config.conf"))

                // Fork the actual backend main class with the test JVM's own classpath.
                val javaBin = Path.of(System.getProperty("java.home"), "bin", "java")
                val pb      = new java.lang.ProcessBuilder(
                    javaBin.toString,
                    "-cp",
                    childClasspath,
                    "afpma.firecalc.payments.Main"
                )
                pb.directory          (tmp.toFile                   )
                pb.environment().put  ("FIRECALC_ENV", "development")
                pb.redirectErrorStream(true                         )
                val out = tmp.resolve("process-output.txt")
                pb.redirectOutput(out.toFile)

                val process  = pb.start()
                // 300 s, not 180: the first forked run under an sbt --client pays a cold
                // JIT plus first-touch class loading, and a wait-bound flake without the
                // fork's own output was unattributable. The bound itself stays — a boot
                // that never exits is exactly what this test rejects.
                val finished = process.waitFor(300, TimeUnit.SECONDS)
                if !finished then
                    process.destroyForcibly(                    )
                    process.waitFor        (10, TimeUnit.SECONDS)
                val text     = if Files.exists(out) then Files.readString(out) else ""

                // The fixture was numbered under the current year — the same year the
                // forked boot runs in (see buildRewoundFixture).
                val thisYear = java.time.LocalDate.now().getYear

                try
                    assert(finished                                      ) // it must exit, not sit on a bound port
                    assert(process.exitValue() != 0                      ) // ExitCode.Error
                    assert(text.contains(fixture.toAbsolutePath.toString)) // names the database file
                    assert(text.contains("refusing to start")            ) // names itself as the guard
                    assert(text.contains("counter current")              )
                    assert(text.contains("highest issued")               )
                    assert(text.contains(s"FCALC-STG-$thisYear-0006")    ) // names the highest issued number
                    assert(
                        text.contains(s"FCALC-STG-$thisYear-0002")
                    ) // names the string the next mint would have produced
                    assert(
                        text.contains(s"FCALC-STG-${thisYear + 1}-0002")
                    ) // the adjacent calendar year was considered
                    assert(!text.contains("Server started")              ) // no HTTP port was bound
                catch
                    // The fork's captured output is the evidence for ANY failure here —
                    // surface it before the finally below deletes the temp tree (and the
                    // output file with it), so a wait-bound flake is never silent.
                    case failed: Throwable =>
                        val exitDesc = if finished then process.exitValue().toString else "n/a (never exited)"
                        println(s"forked boot evidence — finishedWithinBound: $finished, exit: $exitDesc")
                        println(s"captured fork output (${text.length} chars):"                          )
                        println(text                                                                     )
                        throw failed
            finally
                Files
                    .walk(tmp)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(Files.deleteIfExists(_))
        }
    }
