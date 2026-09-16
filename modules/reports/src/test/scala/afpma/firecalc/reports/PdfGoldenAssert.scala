/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.DataFormatException
import java.util.zip.Inflater

import scala.collection.mutable.ArrayBuffer

/**
 * Golden-file assertion for generated PDF reports.
 *
 * === Why not a byte compare? ===
 *
 * Typst renders a generation timestamp into every page footer
 * (`CALC_ID : 26/07/2026 13:04:14` / `Date : …`). Because those glyphs sit
 * *inside* the FlateDecode'd page content streams, a one-second difference
 * reflows the compressed bytes, shifts every xref offset, and changes the
 * document `/ID`. Two consecutive runs of the same report therefore differ in
 * ~25 of 46 streams while being semantically identical, so a raw byte (or
 * whole-file hash) comparison would fail on literally every run.
 *
 * === What is compared instead ===
 *
 *   - the number of page objects, and
 *   - the text extracted from each page, with rendered timestamps normalised.
 *
 * That is stable across runs yet still catches any real change to what the
 * report says. Extraction is done with the JDK only (`Inflater` + the fonts'
 * `/ToUnicode` CMaps); the reports module has no PDF-reading library on its
 * test classpath (PDFBox is a *test-scope* dependency of `java-typst`, so it
 * is not propagated to us) and this comparison is not worth adding one for.
 */
object PdfGoldenAssert:

    /**
     * Compare `freshPath` against `goldenPath`.
     *
     * When the golden is absent the comparison is skipped via `note` rather
     * than failed, so the suites still work for a checkout without goldens and
     * a brand-new report can be added without a chicken-and-egg failure.
     */
    def assertMatchesGolden(freshPath: Path, goldenPath: Path, note: String => Unit): Unit =
        if !Files.exists(goldenPath) then note(s"golden PDF absent, comparison skipped: $goldenPath")
        else
            val golden = Pdf.read(goldenPath)
            val fresh  = Pdf.read(freshPath)

            // Guard against the comparison silently degrading into a no-op if a
            // future Typst release changes how text is encoded.
            if golden.pageTexts.forall(_.isBlank) then
                fail                  (
                    goldenPath,
                    freshPath,
                    s"no text could be extracted from the golden (${golden.pageCount} pages). " +
                        "PdfGoldenAssert can no longer read these PDFs; fix the extractor rather than the report."
                )

            if golden.pageCount != fresh.pageCount then
                fail(
                    goldenPath,
                    freshPath,
                    s"page count differs: golden has ${golden.pageCount}, fresh output has ${fresh.pageCount}"
                )

            golden.pageTexts.zip(fresh.pageTexts).zipWithIndex.foreach { case ((g, f), idx) =>
                if g != f then fail(goldenPath, freshPath, s"text differs on page ${idx + 1}: ${describe(g, f)}")
            }

    private def fail(goldenPath: Path, freshPath: Path, what: String): Nothing =
        throw new AssertionError(
            s"""PDF golden mismatch
               |  golden: $goldenPath
               |  fresh : $freshPath
               |  cause : $what
               |Open both files to compare. If the new output is correct, copy the fresh
               |file over the golden and commit it.""".stripMargin
        )

    /** Locate and quote the first divergence so a human knows where to look. */
    private def describe(golden: String, fresh: String): String =
        val common = golden.lazyZip(fresh).takeWhile(_ == _).size
        val window = 60
        val from   = math.max(0, common - window / 2)
        s"""at character $common
           |    golden: ...${excerpt(golden, from, window)}...
           |    fresh : ...${excerpt(fresh, from, window)}...""".stripMargin

    private def excerpt(s: String, from: Int, len: Int): String =
        s.substring(math.min(from, s.length), math.min(from + len, s.length))

    // ── Minimal PDF text extraction ──────────────────────────────────────

    /**
     * Timestamps Typst renders into the report (`26/07/2026 13:04:14`).
     *
     * The time part is required, so the fixtures' own dates (e.g. the project
     * `Date 13/01/2025`) keep taking part in the comparison.
     */
    private val RenderedTimestamp = """\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2}""".r

    private final case class Pdf(pageCount: Int, pageTexts: Vector[String])

    private object Pdf:

        /**
         * PDF syntax is byte-oriented with binary stream payloads, so the file
         * is carried as ISO-8859-1 text: that charset is the identity map
         * between bytes and chars, which lets us use regexes without losing a
         * single byte.
         */
        private val Latin1 = StandardCharsets.ISO_8859_1

        // Anchored on a line start so the pattern cannot match inside a binary stream.
        private val ObjHeader  = """(?m)^(\d+)\s+0\s+obj\b""".r
        private val StreamHead = """stream\r?\n""".r

        private val PageType  = """/Type\s*/Page\b(?!s)""".r
        private val Contents  = """/Contents\s+(\d+)\s+0\s+R""".r
        private val Resources = """/Resources\s+(\d+)\s+0\s+R""".r
        private val ToUnicode = """/ToUnicode\s+(\d+)\s+0\s+R""".r
        private val FontDict  = """(?s)/Font\s*<<(.*?)>>""".r
        private val FontEntry = """/(\w+)\s+(\d+)\s+0\s+R""".r

        private val BfCharBlock  = """(?s)beginbfchar(.*?)endbfchar""".r
        private val BfRangeBlock = """(?s)beginbfrange(.*?)endbfrange""".r
        private val HexPair      = """<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""".r
        private val HexTriple    = """<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""".r

        /** `/F3 9 Tf` selects a font; `[...] TJ` and `(...) Tj` show text. */
        private val TextToken =
            """(?s)/(\w+)\s+[\d.]+\s+Tf|\[((?:[^\[\]\\]|\\.)*)\]\s*TJ|\(((?:[^()\\]|\\.)*)\)\s*Tj""".r

        private val PdfString = """(?s)\(((?:[^()\\]|\\.)*)\)""".r

        /** An indirect object: its dictionary, plus its decoded stream if any. */
        private final case class Obj(dict: String, stream: Option[String])

        def read(path: Path): Pdf =
            val raw  = new String(Files.readAllBytes(path), Latin1)
            val objs = parseObjects(raw)

            // Font object -> CID/unicode mapping taken from its /ToUnicode CMap.
            val cmaps: Map[Int, Map[Int, String]] =
                objs.flatMap { case (num, obj) =>
                    for
                        m    <- ToUnicode.findFirstMatchIn(obj.dict)
                        cmap <- objs.get(m.group(1).toInt).flatMap(_.stream)
                    yield num -> parseCMap(cmap)
                }

            val texts =
                objs.toVector
                    .sortBy(_._1)
                    .collect { case (_, obj) if PageType.findFirstMatchIn(obj.dict).isDefined => obj }
                    .flatMap(pageText(_, objs, cmaps))

            Pdf(texts.size, texts)

        private def parseObjects(raw: String): Map[Int, Obj] =
            val out = Map.newBuilder[Int, Obj]
            ObjHeader.findAllMatchIn(raw).foreach { m =>
                val num   = m.group(1).toInt
                val start = m.end

                // Resolve the stream first: a binary payload may well contain
                // the bytes "endobj", so it must never be searched for one.
                val streamAt =
                    StreamHead.findFirstMatchIn(raw.substring(start)).map(s => (start + s.start, start + s.end))
                val plainEnd = raw.indexOf("endobj", start)

                streamAt match
                    case Some((headAt, bodyAt)) if plainEnd < 0 || headAt < plainEnd =>
                        val endStream = raw.indexOf("endstream", bodyAt)
                        val stop      = if endStream < 0 then raw.length else endStream
                        val dict      = raw.substring(start, headAt)
                        val payload   = raw.substring(bodyAt, stop)
                        val decoded   = if dict.contains("/FlateDecode") then inflate(payload) else Some(payload)
                        out += num -> Obj(dict, decoded)
                    case _                                                           =>
                        val stop = if plainEnd < 0 then raw.length else plainEnd
                        out += num -> Obj(raw.substring(start, stop), None)
            }
            out.result()

        private def inflate(payload: String): Option[String] =
            val inflater = new Inflater()
            try
                inflater.setInput(payload.getBytes(Latin1))
                val out = new ByteArrayOutputStream(payload.length * 4)
                val buf = new Array[Byte](16384)
                var ok  = true
                while ok && !inflater.finished() do
                    val n = inflater.inflate(buf)
                    if n > 0 then out.write(buf, 0, n)
                    else if inflater.needsInput() || inflater.needsDictionary() then ok = false
                Option.when(ok)(new String(out.toByteArray, Latin1))
            catch case _: DataFormatException => None
            finally inflater.end()

        private def parseCMap(cmap: String): Map[Int, String] =
            val out = Map.newBuilder[Int, String]

            BfCharBlock.findAllMatchIn(cmap).foreach { blk =>
                HexPair.findAllMatchIn(blk.group(1)).foreach { p =>
                    out += Integer.parseInt(p.group(1), 16) -> hexToText(p.group(2))
                }
            }

            BfRangeBlock.findAllMatchIn(cmap).foreach { blk =>
                HexTriple.findAllMatchIn(blk.group(1)).foreach { r =>
                    val lo   = Integer.parseInt(r.group(1), 16)
                    val hi   = Integer.parseInt(r.group(2), 16)
                    val base = Integer.parseInt(r.group(3), 16)
                    (lo to hi).foreach(cid => out += cid -> (base + cid - lo).toChar.toString)
                }
            }

            out.result()

        private def hexToText(hex: String): String =
            new String(hex.grouped(2).map(h => Integer.parseInt(h, 16).toByte).toArray, StandardCharsets.UTF_16BE)

        private def pageText(page: Obj, objs: Map[Int, Obj], cmaps: Map[Int, Map[Int, String]]): Option[String] =
            // /Resources is usually an indirect reference shared by every page.
            val resources = Resources
                .findFirstMatchIn(page.dict)
                .flatMap(m => objs.get(m.group(1).toInt))
                .map(_.dict)
                .getOrElse(page.dict)

            val fonts: Map[String, Map[Int, String]] =
                FontDict
                    .findFirstMatchIn(resources)
                    .toList
                    .flatMap(fd => FontEntry.findAllMatchIn(fd.group(1)))
                    .map(e => e.group(1) -> cmaps.getOrElse(e.group(2).toInt, Map.empty))
                    .toMap

            for
                m       <- Contents.findFirstMatchIn(page.dict)
                content <- objs.get(m.group(1).toInt).flatMap(_.stream)
            yield
                val text    = new StringBuilder
                var current = Map.empty[Int, String]

                TextToken.findAllMatchIn(content).foreach { t =>
                    if t.group(1) != null then current = fonts.getOrElse(t.group(1), Map.empty)
                    else
                        val shown = if t.group(2) != null then t.group(2) else t.group(3)
                        PdfString.findAllMatchIn(shown).foreach { s =>
                            val bytes = unescape(s.group(1))
                            // Identity-H: two bytes per CID.
                            bytes.grouped(2).foreach { pair =>
                                if pair.length == 2 then text ++= current.getOrElse((pair(0) << 8) | pair(1), "")
                            }
                        }
                }

                RenderedTimestamp.replaceAllIn(text.result(), "<TIMESTAMP>")

        /** Resolve PDF string escapes (`\056`, `\n`, `\(`, …) to raw byte values. */
        private def unescape(s: String): Array[Int] =
            val out = ArrayBuffer.empty[Int]
            var i   = 0
            while i < s.length do
                if s.charAt(i) == '\\' && i + 1 < s.length then
                    val next = s.charAt(i + 1)
                    if next >= '0' && next <= '7' then
                        var len = 0
                        while len < 3 && i + 1 + len < s.length
                            && s.charAt(i + 1 + len) >= '0' && s.charAt(i + 1 + len) <= '7'
                        do len += 1
                        out += Integer.parseInt(s.substring(i + 1, i + 1 + len), 8)
                        i += 1 + len
                    else
                        out += (next match
                            case 'n' => '\n'.toInt
                            case 'r' => '\r'.toInt
                            case 't' => '\t'.toInt
                            case 'b' => '\b'.toInt
                            case 'f' => '\f'.toInt
                            case other => other.toInt)
                        i += 2
                else
                    out += s.charAt(i).toInt
                    i += 1
            out.toArray

    end Pdf

end PdfGoldenAssert
