/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.TermConstraint
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.λ
import afpma.firecalc.engine.standard.FireboxError
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.NonEmptyList
import cats.data.ValidatedNel

import scala.reflect.TypeTest

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locale

/**
 * The closed taxonomy of firebox families.
 *
 * Distinct from a firebox *implementation*: the kind is a presentational and
 * registry key, whereas implementations are open and may live outside the
 * engine modules.
 *
 * `BlackBox` is parameterised, so `enum` gets no synthetic `values` array —
 * see [[FireboxKind.closed]].
 */
enum FireboxKind:
    case Traditional
    case Ecolabeled
    case AFPMA_PRSE
    case SingleTested
    case Door15aCatalog
    case CustomForLab
    case BlackBox(name: String)

object FireboxKind:

    /**
     * The non-parameterised cases, hand-listed.
     *
     * Registry totality is checked against this set. `BlackBox` is excluded
     * deliberately: an open family cannot be checked for coverage.
     */
    val closed: Set[FireboxKind] = Set(
        Traditional,
        Ecolabeled,
        AFPMA_PRSE,
        SingleTested,
        Door15aCatalog,
        CustomForLab
    )

end FireboxKind

/**
 * What a firebox contributes for one gas path.
 *
 * `D` is the *full description* type selected by the standard variant — strict
 * binds the flow-only `_15544` descriptors, MCE the thermal `_13384` ones.
 *
 * `Without` is expressed HERE and nowhere else. The pipe modules' `PipeCanBe`
 * is itself a union that already includes `Without`
 * (`engine/models/pipe_modules.scala:16`); binding `D` to `FullDescr` and
 * letting this enum carry absence keeps exactly one representation of "no pipe"
 * at the seam. The application algebra maps `Without` to `Module.without` when
 * it materialises a `PipeCanBe`.
 *
 * `AsDescr` plus a `PressureDiff` element is the PREFERRED black-box route —
 * Door15a catalog already does precisely this
 * (`Door15aCatalogFireboxToCombustionAirPipe_15544_Strict.scala:41-55`).
 * `AsResult` is the narrow escape hatch for an implementation that must dictate
 * `ph`; it owes the full continuation state at EVERY operating point (see the
 * case's scaladoc).
 */
enum FireboxContribution[+D]:
    /** No pipe for this path (e.g. SingleTested combustion air). */
    case Without

    /** A pipe description for the solver to compute. */
    case AsDescr(descr: D)

    /**
     * Fully-determined results, supplied INSTEAD of a description — keyed by
     * operating point.
     *
     * '''Physics contract.''' An [[AsDescr]] is operating-point-independent by
     * construction: the client's solver evaluates it at any operating point.
     * An `AsResult` entry is valid at EXACTLY ONE operating point — the one it
     * is keyed by — and the grid MUST cover every operating point the standard
     * evaluates for that firebox: both draft conditions, and the reduced load
     * exactly when the firebox declares a reduced heat output with a
     * determinable minimum load. A bare, unkeyed result is unrepresentable:
     * the grid is non-empty and every entry names the point it is valid at.
     *
     * The grid-completeness obligation sits with the PRODUCER. `OpPoint.axesFor`
     * (`alg/en15544/FireboxCalculation.scala`) is the sanctioned axis
     * derivation: a grid built over `OpPoint.axesFor(firebox)` covers exactly
     * the points the standard will consume. A covered-point miss at consumption
     * time is a producer bug and fails loudly with the operating point named —
     * it is never a fallback.
     *
     * Note the case is independent of `D`: a `FireboxContribution[?]` can still
     * be matched for `AsResult` and yields fully-typed `PipeResult`s, which is
     * what lets the plumbing live in this shared module rather than being
     * duplicated per standard.
     *
     * Carrying a `PipeResult` means owing its CONTINUATION STATE
     * (`gas_temp_end`, `last_density_mean`/`last_density_middle`,
     * `last_velocity_mean`/`last_velocity_middle`). Those fields seed the next
     * pipe downstream via `UpstreamState.fromPipeResult`
     * (`engine-13384-common/.../ops/generic/UpstreamState.scala:45-60`).
     * Leaving them `None` makes a downstream flue pipe throw
     * `MecaFlu_Error.MissingUpstreamSeedValues`
     * (`ThermalMecaFlu_13384.scala:530`). Build each entry with
     * `PipeResult.fromValues` (`results.scala:251-293`), not
     * `PipeResult.useless`.
     */
    case AsResult(grid: NonEmptyList[(OpPoint, PipeResult)])
end FireboxContribution

/**
 * Bundled typeclass for all firebox behaviour.
 *
 * Replaces the separately-summoned per-concern typeclasses. At this migration
 * step it carries the constraint surface previously provided by
 * `FireboxConstraints[-F]`; the pipe contributions, formulas and thermal
 * outputs join it in step 2 (see `.omp/firebox-implementation-plan.md`, DEC-1).
 *
 * `F` is contravariant: a `FireboxEngine[Firebox_15544]` satisfies
 * `FireboxEngine[Ecolabeled]`, which lets one general instance cover subtypes
 * that override nothing. The converse does NOT hold — see
 * [[FireboxEngineAny]] for why that matters at dispatch time.
 *
 * The trait is deliberately **not** bounded by `F <: Firebox_15544`: black-box
 * models arriving from outside the engine modules need not extend the current
 * hierarchy. The bound lives on [[FireboxEngineAny.from]], where an engine is
 * registered against the models that exist today.
 */
trait FireboxEngine[-F]:

    /** The [[FireboxKind]] this instance handles. */
    def kind: FireboxKind

    // ── Standard variant type members ──────────────────────────────────────

    /**
     * Full description type for the firebox pipe, selected by the standard.
     *
     * Replaces the abstract member of `FireboxToFireboxPipe`
     * (`alg/FireboxToFireboxPipe.scala:15`). Bound once per standard by
     * `FireboxEngine_15544_Strict` / `_MCE`, never by a per-kind instance.
     */
    type FireboxPipe_FullDescr

    /** Full description type for the combustion-air pipe, selected by the standard. */
    type CombustionAirPipe_FullDescr

    // ── Pipe descriptions ──────────────────────────────────────────────────

    /**
     * The firebox gas-path contribution.
     *
     * No standard-construction subtype overrides this: the firebox pipe reads
     * only `dimensions.base` and `dimensions.height`, so one generic builder
     * serves every kind (facts §8.3). Kept per-engine anyway because a black-box
     * implementation is exactly the case that must override it.
     *
     * Returns a `ValidatedNel` because building a description validates geometry
     * incrementally — same contract as the `toFireboxPipe_FullDescr` extension
     * method it replaces.
     */
    def fireboxContribution(
        firebox: F
    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]]

    /**
     * The combustion-air gas-path contribution — the path that genuinely varies
     * per kind (Ecolabeled ~15 sections, SingleTested `Without`, Door15a a
     * catalog `PressureDiff`).
     */
    def combustionAirContribution(
        firebox: F
    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]]

    // ── Constraints folding (§4.4) ─────────────────────────────────────

    /**
     * Folds the per-term constraint methods into a flat list of wire verdicts.
     *
     * `eta_constraints` is deliberately excluded (§4.6).
     *
     * When `ctx` is `None` (m_B unavailable) every m_B-dependent term
     * yields `NotEvaluated(MissingMB)`.
     */
    def constraints(
        firebox: F,
        ctx    : Option[ConstraintContext],
        fctx   : FireboxConstraintContext
    )              (using Locale): List[ConstraintVerdict]

end FireboxEngine

/**
 * Existential wrapper erasing the type parameter of [[FireboxEngine]].
 *
 * Mirrors `CatalogCategoryAny` (`CatalogCategory.scala:52-56`), including its
 * key discipline: dispatch is by a key the value carries ([[FireboxKind]]),
 * never by a runtime type test on the erased parameter.
 *
 * ⚠ Do NOT add `def accepts(fb: Any) = fb.isInstanceOf[FB]`. `FB` is erased, so
 * that test is vacuous — it succeeds for every value, and the first list entry
 * would answer every lookup. Runtime evidence comes from the `TypeTest` that
 * [[FireboxEngineAny.from]] demands at registration; unlike the `ClassTag` it
 * replaces, `TypeTest` makes the erased-type test real and sound.
 */
trait FireboxEngineAny[FD, CD]:
    type FB <: Firebox_15544
    val kind: FireboxKind

    /**
     * The engine, refined so its two descriptor members are known to be `FD` and
     * `CD`.
     *
     * The refinement is what keeps the seam cast-free. Without it,
     * `instance.FireboxPipe_FullDescr` is an opaque path-dependent type with no
     * known relation to the `_15544`/`_13384` descriptors the application algebra
     * must hand to MecaFlu — and recovering it would need exactly the cast this
     * design exists to remove. Each registry is per-standard, so `FD`/`CD` are
     * known constants where the registry is declared.
     */
    val instance: FireboxEngine[FB] {
        type FireboxPipe_FullDescr       = FD
        type CombustionAirPipe_FullDescr = CD
    }

    /**
     * The per-term constraint algebra, when this entry's engine evaluates
     * constraints locally.
     *
     * `None` means the engine has no per-term algebra to offer: it serves
     * already-folded verdicts through [[FireboxEngine.constraints]] instead.
     * [[RemoteBackedFireboxEngine]] is the only such engine today — the algebra
     * is not transmitted over the wire.
     *
     * Consumers of the per-term reporting path MUST render `None` as "detail
     * not available", never as an empty — and therefore vacuously satisfied —
     * constraint set.
     */
    def constraintsAlg: Option[FireboxConstraints[FB]]

    /** Narrow an erased firebox to this entry's model type. `None` if not ours. */
    def narrow(firebox: Firebox_15544): Option[FB]

    /**
     * This entry with its local engine swapped for a [[RemoteBackedFireboxEngine]]
     * serving `data`, keeping the SAME `FB`, `kind`, and `narrow` evidence.
     *
     * The remote engine is registered through [[FireboxEngineAny.foldedOnly]] —
     * it owns no per-term constraint algebra (that is not transmitted over the
     * wire), so `constraintsAlg` becomes `None`. Because the `FB` and the
     * `TypeTest`-backed `narrow` are carried over verbatim, the entry still
     * dispatches on exactly the model type it did before; only the engine that
     * answers changes. This is what lets the remote-backed registry be built
     * generically from the local one — the caller never names the model type,
     * so the browser can assemble a remote registry for kinds whose engine
     * models it does not link (ticket 11).
     */
    def toRemoteBacked(
        data: RemoteFireboxData[FD, CD]
    ): FireboxEngineAny[FD, CD] =
        val self = this
        new FireboxEngineAny[FD, CD]:
            type FB = self.FB
            val kind          : FireboxKind                    = self.kind
            val instance      : FireboxEngine[FB] {
                type FireboxPipe_FullDescr       = FD
                type CombustionAirPipe_FullDescr = CD
            } = RemoteBackedFireboxEngine[FB, FD, CD](kind, data)
            def constraintsAlg: Option[FireboxConstraints[FB]] = None
            def narrow(firebox: Firebox_15544): Option[FB] = self.narrow(firebox)

object FireboxEngineAny:

    /**
     * `TypeTest` supplies the runtime evidence erasure destroys — the same role
     * the exhaustive `match` played in the retired `FireboxConstraintsResolver`,
     * but declared once per entry instead of re-derived at each dispatch site.
     * It is the designated sound replacement for `ClassTag.unapply`, which could
     * test only the class component of a type.
     *
     * The descriptor types are *projected out of the summoned engine* rather than
     * passed explicitly, so a registry entry stays `from[TraditionalFirebox]` —
     * one type argument, the model. The engine is a `using` parameter in its own
     * clause so its path can be named in the result type.
     */
    def from[F <: Firebox_15544](using
        tt: TypeTest[Firebox_15544, F]
    )(using
        engine: FoldsConstraints[F]
    ): FireboxEngineAny[engine.FireboxPipe_FullDescr, engine.CombustionAirPipe_FullDescr] =
        new FireboxEngineAny[engine.FireboxPipe_FullDescr, engine.CombustionAirPipe_FullDescr]:
            type FB = F
            val kind          : FireboxKind                   = engine.kind
            val instance      : FireboxEngine[F] {
                type FireboxPipe_FullDescr       = engine.FireboxPipe_FullDescr
                type CombustionAirPipe_FullDescr = engine.CombustionAirPipe_FullDescr
            } = engine
            def constraintsAlg: Option[FireboxConstraints[F]] = Some(engine.constraintsAlg)
            def narrow(firebox: Firebox_15544): Option[F] = tt.unapply(firebox)

    /**
     * Register an engine that serves already-folded verdicts and owns no
     * per-term algebra — [[RemoteBackedFireboxEngine]] is the only one today.
     *
     * Deliberately a second constructor rather than a throwing stub behind
     * [[FoldsConstraints.constraintsAlg]]: the absence is carried in the type
     * ([[FireboxEngineAny.constraintsAlg]] is `None`), so no consumer can reach
     * a per-term method that cannot answer, and none can mistake the absence
     * for an empty — hence vacuously satisfied — constraint set. The engine's
     * folded verdicts remain available through [[FireboxEngine.constraints]],
     * which is what such an engine overrides.
     */
    def foldedOnly[F <: Firebox_15544](using
        tt: TypeTest[Firebox_15544, F]
    )(using
        engine: FireboxEngine[F]
    ): FireboxEngineAny[engine.FireboxPipe_FullDescr, engine.CombustionAirPipe_FullDescr] =
        new FireboxEngineAny[engine.FireboxPipe_FullDescr, engine.CombustionAirPipe_FullDescr]:
            type FB = F
            val kind          : FireboxKind                   = engine.kind
            val instance      : FireboxEngine[F] {
                type FireboxPipe_FullDescr       = engine.FireboxPipe_FullDescr
                type CombustionAirPipe_FullDescr = engine.CombustionAirPipe_FullDescr
            } = engine
            def constraintsAlg: Option[FireboxConstraints[F]] = None
            def narrow(firebox: Firebox_15544): Option[F] = tt.unapply(firebox)

end FireboxEngineAny

/**
 * An engine paired with the firebox it was resolved for.
 *
 * Built by a registry's `withEngine`, where the entry's `FB` still connects the
 * two. Materialising the pair — rather than a single result — lets the many
 * constraint callsites each compute their own result without threading a
 * polymorphic function through every one of them. `FB` never escapes, so no
 * cast is needed anywhere.
 */
trait FireboxEngineFor[FD, CD]:
    type FB <: Firebox_15544
    val engine: FireboxEngine[FB] {
        type FireboxPipe_FullDescr       = FD
        type CombustionAirPipe_FullDescr = CD
    }

    /**
     * The per-term constraint algebra of the resolved engine, with [[firebox]]
     * already applied — the local reporting path: the `citedConstraints`
     * builder in `en15544_common_constraints` and
     * `validateFireboxSpecificConstraints` in `en15544_common_application`
     * reach per-term `TermConstraint`s through it. The folded wire verdicts
     * come from `engine.constraints` instead.
     *
     * `None` when the resolved engine has no per-term algebra — see
     * [[FireboxEngineAny.constraintsAlg]]. Both readers then fall back to the
     * engine's folded verdicts rather than reporting an empty constraint set.
     *
     * Pre-applied so `FB` never escapes the pair: callers get plain
     * `TermConstraint`s and never name the model type, which is what lets this
     * seam stay cast-free.
     */
    def appliedConstraints: Option[AppliedFireboxConstraints]

    val firebox: FB

/**
 * A [[FireboxConstraints]] instance with its firebox already applied.
 *
 * Exists so [[FireboxEngineFor]] can publish the per-term algebra without
 * leaking `FB`: every method here returns plain `TermConstraint`s /
 * `FireboxError`s, so consumers never name the model type and the seam stays
 * cast-free. The eleven members mirror [[FireboxConstraints]] exactly, minus
 * the `firebox` argument.
 */
trait AppliedFireboxConstraints:
    def m_B_constraints                     (ctx: ConstraintContext): Seq[Option[TermConstraint[m_B]]]
    def m_B_min_constraints                 (ctx: ConstraintContext): Seq[Option[TermConstraint[m_B_min]]]
    def glassArea_constraints               (ctx: ConstraintContext): Seq[Option[TermConstraint[GlassArea]]]
    def fireboxDimensions_Base_constraints  (ctx: ConstraintContext): Seq[Option[TermConstraint[Dimensions.Base]]]
    def h_br_constraints                    (ctx: ConstraintContext): Seq[Option[TermConstraint[H_BR]]]
    def lambda_constraints                  (ctx: ConstraintContext): Seq[Option[TermConstraint[λ]]]
    def eta_constraints                     (ctx: ConstraintContext): Seq[Option[TermConstraint[η]]]
    def height_of_lowest_opening_constraints(
        ctx: ConstraintContext
    ): Seq[Option[TermConstraint[height_of_lowest_opening]]]
    def firebox_glass_surface_ratio_constraint: Option[TermConstraint[Unit]]
    def firebox_custom_constraints(ctx: FireboxConstraintContext): List[FireboxError]
    def internal_firebox_custom_constraints: List[FireboxError]

object AppliedFireboxConstraints:

    def apply[F <: Firebox_15544](alg: FireboxConstraints[F], fb: F): AppliedFireboxConstraints =
        new AppliedFireboxConstraints:
            def m_B_constraints      (ctx: ConstraintContext) = alg.m_B_constraints(fb, ctx)
            def m_B_min_constraints  (ctx: ConstraintContext) = alg.m_B_min_constraints(fb, ctx)
            def glassArea_constraints(ctx: ConstraintContext) = alg.glassArea_constraints(fb, ctx)
            def fireboxDimensions_Base_constraints(ctx: ConstraintContext) =
                alg.fireboxDimensions_Base_constraints(fb, ctx)
            def h_br_constraints(ctx: ConstraintContext) = alg.h_br_constraints(fb, ctx)
            def lambda_constraints(ctx: ConstraintContext) = alg.lambda_constraints(fb, ctx)
            def eta_constraints   (ctx: ConstraintContext) = alg.eta_constraints(fb, ctx)
            def height_of_lowest_opening_constraints(ctx: ConstraintContext) =
                alg.height_of_lowest_opening_constraints(fb, ctx)
            def firebox_glass_surface_ratio_constraint                       =
                alg.firebox_glass_surface_ratio_constraint(fb)
            def firebox_custom_constraints(ctx: FireboxConstraintContext)    =
                alg.firebox_custom_constraints(fb, ctx)
            def internal_firebox_custom_constraints                          =
                alg.internal_firebox_custom_constraints(fb)

end AppliedFireboxConstraints

object FireboxEngineFor:
    def apply[F <: Firebox_15544, FD, CD](
        e  : FireboxEngine[F] { type FireboxPipe_FullDescr = FD; type CombustionAirPipe_FullDescr = CD },
        alg: Option[FireboxConstraints[F]],
        fb : F
    ): FireboxEngineFor[FD, CD] =
        new FireboxEngineFor[FD, CD]:
            type FB = F
            val engine            : FireboxEngine[F] {
                type FireboxPipe_FullDescr       = FD
                type CombustionAirPipe_FullDescr = CD
            } = e
            val firebox           : F                                 = fb
            def appliedConstraints: Option[AppliedFireboxConstraints] =
                alg.map(AppliedFireboxConstraints(_, fb))

end FireboxEngineFor

/**
 * Common shape of a per-standard firebox engine registry.
 *
 * Concrete registries are static `List`s assembled in each standard's top
 * module — deliberately not a mutable map populated from module initialisers,
 * because Scala.js dead-code-eliminates unreferenced initialisers and such
 * registrations would silently vanish in the browser build while passing on
 * the JVM.
 */
trait FireboxEngineRegistry[FD, CD]:

    /** Human-readable registry name, used in error messages. */
    def name: String

    def all: List[FireboxEngineAny[FD, CD]]

    /**
     * Resolve and APPLY in one step. The engine never escapes the wrapper, so
     * no cast is needed: `entry.narrow` proves the value matches `entry.FB`,
     * and `entry.instance` accepts exactly that type.
     *
     * Entries are tried in list order, so overlapping `TypeTest`s resolve by
     * position — the same discipline as the ordered match arms this replaces.
     */
    def withEngine[A](firebox: Firebox_15544)(
        f: [F <: Firebox_15544] => (
            FireboxEngine[F] { type FireboxPipe_FullDescr = FD; type CombustionAirPipe_FullDescr = CD },
            Option[FireboxConstraints[F]],
            F
        ) => A
    ): Either[String, A] =
        all
            .collectFirst:
                Function.unlift: entry =>
                    entry.narrow(firebox).map(fb => f[entry.FB](entry.instance, entry.constraintsAlg, fb))
            .toRight(s"No $name FireboxEngine registered for ${firebox.getClass.getSimpleName}")

    /** Resolve the engine and keep it paired with its firebox. */
    def engineFor(firebox: Firebox_15544): Either[String, FireboxEngineFor[FD, CD]] =
        withEngine(firebox): [F <: Firebox_15544] =>
            (
                e  : FireboxEngine[F] { type FireboxPipe_FullDescr = FD; type CombustionAirPipe_FullDescr = CD },
                alg: Option[FireboxConstraints[F]],
                fb : F
            ) => FireboxEngineFor(e, alg, fb)

    /**
     * Fail fast on duplicate kinds and on closed kinds left uncovered.
     * Call from a `locally` block in the concrete registry object.
     */
    protected def checkTotality(): Unit =
        val kinds = all.map(_.kind)
        val dupes = kinds.diff(kinds.distinct)
        require(dupes.isEmpty, s"Duplicate kind(s) in $name: ${dupes.mkString(", ")}")
        val missing = FireboxKind.closed -- kinds.toSet
        require(missing.isEmpty, s"Uncovered kind(s) in $name: ${missing.mkString(", ")}")

end FireboxEngineRegistry

/** No registry entry matched the firebox presented for calculation. */
final class NoFireboxEngineRegistered(msg: String) extends RuntimeException(msg)
