/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.standard

import afpma.firecalc.dto.FireboxAvailabilityExtensions.localizedTypeName
import afpma.firecalc.engine.models.FireboxPipeT
import afpma.firecalc.engine.models.PipeType

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import cats.data.ValidatedNel
import cats.syntax.all.*

/**
 * A validated, non-negative slot index in the post-firebox pipe chain.
 *
 * Absence of a slot (air intake, firebox, standalone pipe modules) is
 * represented as `Option[SlotIndex]`, never as a sentinel value.
 */
opaque type SlotIndex = Int

object SlotIndex:
    /** Safe constructor. Returns `None` for negative inputs. */
    def from(n: Int): Option[SlotIndex] =
        if n >= 0 then Some(n) else None

    /**
     * Unsafe constructor for internal use where `n >= 0` is guaranteed
     * (e.g. `zipWithIndex` in the chain compute loop).
     */
    def unsafe(n: Int): SlotIndex = n

    extension (s: SlotIndex)
        def value  : Int     = s
        def isFirst: Boolean = s == 0

/**
 * Captures the slot context for error targeting during incremental validation.
 * Replaces explicit `Option[SlotIndex]` threading through error constructors.
 */
final class SlotContext private (private val _slotIndex: Option[SlotIndex]):
    def slotIndex: Option[SlotIndex] = _slotIndex

object SlotContext:
    def forSlot(i: SlotIndex): SlotContext = new SlotContext(Some(i))
    val unslotted: SlotContext = new SlotContext(None)
    def fromOption(o: Option[SlotIndex]): SlotContext = o.fold(unslotted)(forSlot(_))

    /** Convenience: slot context for a raw non-negative index. */
    def forSlotUnsafe(n: Int): SlotContext = forSlot(SlotIndex.unsafe(n))
extension (opt: Option[SlotIndex])
    /**
     * Resolve an error target from an optional slot index.
     *  - `Some(idx)` → `SlotTarget(idx)` (visible in one slot panel)
     *  - `None`      → `TypeTarget(pt)` (visible in all panels of that pipe type)
     */
    def targetFor(pt: PipeType): ErrorTarget =
        opt.fold[ErrorTarget](ErrorTarget.TypeTarget(pt))(ErrorTarget.SlotTarget(_))

type VNelMcalcErr[+X] = ValidatedNel[MCalc_Error, X]

trait MCalc_Error

/**
 * Which scope an error belongs to, for panel-level error filtering.
 * A panel only shows errors whose target it "sees" (scope containment).
 */
enum ErrorTarget:
    /** Belongs to one specific slot. */
    case SlotTarget(slotIndex: SlotIndex)

    /** Belongs to all panels of a given pipe type. */
    case TypeTarget(pipeType: PipeType)

    /** Visible to all panels. */
    case GlobalTarget

/** Marker trait for errors that participate in the new scope-containment model. */
trait TargetedError:
    def target: ErrorTarget

given ShowUsingLocale[MCalc_Error] = showUsingLocale:
    case e: UnexpectedDevError          => s"DEV_ERROR: ${e.msg}"
    case e: NotYetSupportedInFlueRegion =>
        s"Not yet supported in flue region: ${e.reason}"
    case e: Inputs_Error                => e.show // Uses ShowUsingLocale[Inputs_Error]
    case e: EN15544_Error               => e.show // Uses ShowUsingLocale[EN15544_Error]
    case e: EN13384_Error               => e.show // Uses ShowUsingLocale[EN13384_Error]
    case e: MecaFlu_Error               => e.show // Uses ShowUsingLocale[MecaFlu_Error]
    case e: IncrementalValidation_Error => e.show // Uses ShowUsingLocale[IncrementalValidation_Error]
    case ErrorsInOtherSectionType => I18N.builder_errors.errors_in_other_section_type
    case e: FireboxTypeDisabledError     => e.show // Uses ShowUsingLocale[FireboxTypeDisabledError]
    case e: FireboxGeometryRejectedError => e.show // Uses ShowUsingLocale[FireboxGeometryRejectedError]
    case e: RemoteFireboxUnavailable     => e.show // Uses ShowUsingLocale[RemoteFireboxUnavailable]
    case RemoteFireboxPending => I18N.builder_errors.remote_firebox_pending
    case ResultsNotComputed   => I18N.builder_errors.results_not_computed
    case e: FireboxCalculatorRefused => e.show // Uses ShowUsingLocale[FireboxCalculatorRefused]

// Unexpected Error
case class UnexpectedDevError(msg: String) extends MCalc_Error

/**
 * An error state reporting that the calculation pipeline holds no result: the
 * computation is still pending, or the remote service that owns it could not
 * answer. Both ride the error channel because they travel with the computation
 * result, but neither is an input problem the user can fix.
 *
 * Sealed: the family is closed here, so a consumer of the error channel can
 * classify all of it with one case, and a new member still needs a render case
 * in the `ShowUsingLocale[MCalc_Error]` dispatch — open over `MCalc_Error`,
 * with no catch-all.
 */
sealed trait NoResultError extends MCalc_Error

/**
 * A no-result state carrying no user-actionable information: a result is
 * expected (a fetch in flight, a debounce window, a project switch) and
 * consumers must treat it as "no data yet", never as an error to display.
 */
sealed trait PendingStateError extends NoResultError

/**
 * The remote firebox calculation has not concluded: a fetch for the kind is in
 * flight, so its data is not available yet. Transient — the fetch ends either
 * with data, with [[RemoteFireboxUnavailable]], or with a geometry verdict.
 */
case object RemoteFireboxPending extends PendingStateError

/**
 * Results are not available for the current computation: the engine is inside
 * its debounce window, or the result on hand was computed for a project that is
 * no longer the active one.
 */
case object ResultsNotComputed extends PendingStateError

/**
 * The remote firebox calculation service could not provide the kind's data and
 * nothing usable was cached, so the kind cannot be computed at all. Terminal,
 * unlike [[RemoteFireboxPending]]: the fetch is over, and the reason is the
 * user-facing verdict rather than an internal invariant violation.
 *
 * `reason` is already localized — it is built where the project locale is in
 * scope, by the producer that knows which service and which kind failed.
 */
case class RemoteFireboxUnavailable(reason: String) extends NoResultError

object RemoteFireboxUnavailable:
    given ShowUsingLocale[RemoteFireboxUnavailable] = showUsingLocale: e =>
        e.reason

/**
 * The deployment has NO calculator for this firebox kind: an Austrian kind
 * resolved to the in-process engine, which the public product does not carry
 * (ADR-0010). Terminal, like [[RemoteFireboxUnavailable]] — but it is a
 * deploy condition, not a service verdict: the browser refuses BEFORE any
 * computation, and the reason is the remote-only carriers' own wording.
 *
 * Deliberately NOT a [[PendingStateError]]: a refusal must never read as the
 * neutral "no data yet" state. It is also NOT a standards finding — nothing
 * about the heater was checked or implicated.
 *
 * `reason` is already localized — built by `RemoteFireboxAvailability` with
 * the project locale, naming the refused kind.
 */
case class FireboxCalculatorRefused(reason: String) extends NoResultError

object FireboxCalculatorRefused:
    given ShowUsingLocale[FireboxCalculatorRefused] = showUsingLocale: e =>
        e.reason

/**
 * Restriction — certain slot topologies are not yet supported in the flue region
 * (up to and including the last FluePipeT slot) because their computation would
 * require HA-power givens that are not yet resolved at the time the flue region
 * is computed.
 */
case class NotYetSupportedInFlueRegion(reason: String) extends MCalc_Error

// Re-exports for FlowAreaConservation placeholder types (deprecated)
export afpma.firecalc.engine.{
    ExpectedDimension,
    ExpectedDimRectangle,
    ExpectedDimSquare,
    ExpectedDimCircle,
    FlowAreaTransition,
    PendingFlowAreaCheck
}
// FireboxTypeDisabledError — circuit-breaker for UI-disabled firebox types
case class FireboxTypeDisabledError(typeName: String) extends MCalc_Error

object FireboxTypeDisabledError:
    given ShowUsingLocale[FireboxTypeDisabledError] = showUsingLocale: e =>
        I18N.errors.firebox_type_disabled(e.typeName.localizedTypeName)

// FireboxGeometryRejectedError — remote calc-service geometry pre-check verdict (firebox-scoped)
case class FireboxGeometryRejectedError(reason: String) extends MCalc_Error, TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(FireboxPipeT)

object FireboxGeometryRejectedError:
    given ShowUsingLocale[FireboxGeometryRejectedError] = showUsingLocale: e =>
        e.reason

// ErrorsInOtherSectionType
case object ErrorsInOtherSectionType extends MCalc_Error
type ErrorsInOtherSectionType = ErrorsInOtherSectionType.type

given ShowUsingLocale[ErrorsInOtherSectionType] = showUsingLocale:
    case ErrorsInOtherSectionType => I18N.builder_errors.errors_in_other_section_type
