/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.Roughness
import afpma.firecalc.units.others.sunit.SUnit
import afpma.firecalc.units.others.sunits.SUnits.given

import afpma.firecalc.i18n.ShowUsingLocale

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.gtypedefs.CoefficientOfFlowResistance
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.standard.*

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*

import coulomb.*
import coulomb.syntax.*

import afpma.firecalc.domain.*
import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.alg.en15544.FireboxOperatingEnvelope
import afpma.firecalc.engine.alg.en15544.OpPoint
import afpma.firecalc.engine.models.en13384.typedefs.{DraftCondition, T_L_override}
import afpma.firecalc.engine.standard.PressureLossMustBeDefined
import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Locale

/**
 * Bidirectional conversions between wire protocol types ([[firebox_calc_api]]
 * package) and engine domain types.
 *
 * This module lives in `engine-15544-common` because it bridges the wire
 * contract (owned by `firebox_calc_api`) to the EN 15544 engine model
 * (owned by `engine-kernel` / `engine-13384-strict`).
 *
 * Key design decisions:
 *   - `PipeResult.fromValues` is used (not a new `FromWire` case class) to
 *     reconstruct `PipeResult` from wire fields.
 *   - The `pd`, `ζ`, `pu` parameter order in `fromValues` differs from the
 *     wire field order (`zeta`, `pu`, `pd`); the conversion reorders.
 *   - `gas_temp_mean` is NOT passed to `fromValues` — it is derived internally.
 *   - All conversions are explicit functions (no implicit `given Conversion`
 *     instances) to avoid ambiguity.
 */
object WireConversions:

    // =========================================================================
    //  WireQty <-> QtyD[U] / TempD[U]
    // =========================================================================

    /** Convert a wire quantity to a Coulomb typed quantity. Validates unit string. */
    def wireQtyToQtyD[U](w: WireQty)(using su: SUnit[U]): QtyD[U] =
        require    (
            w.unit == su.showUnitFull,
            s"WireQty unit mismatch: expected '${su.showUnitFull}', got '${w.unit}'"
        )
        su.makeQtyD(w.value.toDouble)

    /** Convert a Coulomb typed quantity to its wire representation. */
    def qtyDToWireQty[U](q: QtyD[U])(using su: SUnit[U]): WireQty =
        WireQty(q.value.toString, su.showUnitFull)

    /** Convert a wire quantity to a Coulomb typed temperature. */
    def wireQtyToTempD[U](w: WireQty)(using su: SUnit[U]): TempD[U] =
        require(
            w.unit == su.showUnitFull,
            s"WireQty unit mismatch: expected '${su.showUnitFull}', got '${w.unit}'"
        )
        w.value.toDouble.withTemperature[U]

    /** Convert a Coulomb typed temperature to its wire representation. */
    def tempDToWireQty[U](t: TempD[U])(using su: SUnit[U]): WireQty =
        WireQty(t.value.toString, su.showUnitFull)

    // =========================================================================
    //  PipeShape <-> WirePipeShape
    // =========================================================================

    def toWireShape(s: PipeShape): WirePipeShape = s match
        case PipeShape.Circle(diameter) =>
            WirePipeShape.Circle(qtyDToWireQty[Meter](diameter))
        case PipeShape.Square(side)     =>
            WirePipeShape.Square(qtyDToWireQty[Meter](side))
        case PipeShape.Rectangle(a, b)  =>
            WirePipeShape.Rectangle(qtyDToWireQty[Meter](a), qtyDToWireQty[Meter](b))

    def fromWireShape(w: WirePipeShape): PipeShape = w match
        case WirePipeShape.Circle(diameter) =>
            PipeShape.Circle(wireQtyToQtyD[Meter](diameter))
        case WirePipeShape.Square(side)     =>
            PipeShape.Square(wireQtyToQtyD[Meter](side))
        case WirePipeShape.Rectangle(a, b)  =>
            PipeShape.Rectangle(wireQtyToQtyD[Meter](a), wireQtyToQtyD[Meter](b))

    // =========================================================================
    //  PipeType <-> WirePipeType (positional mapping)
    // =========================================================================

    def toWireType(t: PipeType): WirePipeType = t match
        case AirIntakePipeT     => WirePipeType.AirIntake
        case CombustionAirPipeT => WirePipeType.CombustionAir
        case FireboxPipeT       => WirePipeType.Firebox
        case FluePipeT          => WirePipeType.Flue
        case ConnectorPipeT     => WirePipeType.Connector
        case ChimneyPipeT       => WirePipeType.Chimney
        case NoFluePipeT        => WirePipeType.NoFlue

    def fromWireType(w: WirePipeType): PipeType = w match
        case WirePipeType.AirIntake     => AirIntakePipeT
        case WirePipeType.CombustionAir => CombustionAirPipeT
        case WirePipeType.Firebox       => FireboxPipeT
        case WirePipeType.Flue          => FluePipeT
        case WirePipeType.Connector     => ConnectorPipeT
        case WirePipeType.Chimney       => ChimneyPipeT
        case WirePipeType.NoFlue        => NoFluePipeT

    // =========================================================================
    //  PipeElDescr <-> WirePipeElDescr (7 cases)
    // =========================================================================

    def toWireElDescr(e: PipeElDescr): WirePipeElDescr = e match
        case StraightSection(length, geometry, roughness, elevationGain)     =>
            WirePipeElDescr.StraightSection(
                qtyDToWireQty[Meter](length       ),
                toWireShape         (geometry     ),
                qtyDToWireQty[Meter](roughness    ),
                qtyDToWireQty[Meter](elevationGain)
            )
        case DirectionChange.AngleVifDe0A180(alpha, angleN2, effectiveShape) =>
            WirePipeElDescr.AngleVifDe0A180(
                qtyDToWireQty[Degree](alpha                ),
                angleN2.map          (qtyDToWireQty[Degree]),
                toWireShape          (effectiveShape       )
            )
        case DirectionChange.CircularArc60(effectiveShape)                   =>
            WirePipeElDescr.CircularArc60(toWireShape(effectiveShape))
        case SplitMerge90(nFlows, angleN2, effectiveShape)                   =>
            WirePipeElDescr.SplitMerge90(
                nFlows,
                angleN2.map(qtyDToWireQty[Degree]),
                toWireShape(effectiveShape       )
            )
        case SectionGeometryChange(from, to)                                 =>
            WirePipeElDescr.SectionGeometryChange(toWireShape(from), toWireShape(to))
        case SingularFlowResistance(zeta, crossSectionO)                     =>
            WirePipeElDescr.SingularFlowResistance(
                zeta.value,
                crossSectionO.map(qtyDToWireQty[(Meter ^ 2)])
            )
        case PressureDiff(pa, crossSectionO)                                 =>
            WirePipeElDescr.PressureDiff(
                qtyDToWireQty[Pascal](pa                        ),
                crossSectionO.map    (qtyDToWireQty[(Meter ^ 2)])
            )

    def fromWireElDescr(w: WirePipeElDescr): PipeElDescr = w match
        case WirePipeElDescr.StraightSection(length, geometry, roughness, elevationGain) =>
            StraightSection(
                wireQtyToQtyD[Meter](length       ),
                fromWireShape       (geometry     ),
                wireQtyToQtyD[Meter](roughness    ),
                wireQtyToQtyD[Meter](elevationGain)
            )
        case WirePipeElDescr.AngleVifDe0A180(alpha, angleN2, effectiveShape)             =>
            DirectionChange.AngleVifDe0A180(
                wireQtyToQtyD[Degree](alpha                ),
                angleN2.map          (wireQtyToQtyD[Degree]),
                fromWireShape        (effectiveShape       )
            )
        case WirePipeElDescr.CircularArc60(effectiveShape)                               =>
            DirectionChange.CircularArc60(fromWireShape(effectiveShape))
        case WirePipeElDescr.SplitMerge90(nFlows, angleN2, effectiveShape)               =>
            SplitMerge90(
                nFlows,
                angleN2.map  (wireQtyToQtyD[Degree]),
                fromWireShape(effectiveShape       )
            )
        case WirePipeElDescr.SectionGeometryChange(from, to)                             =>
            SectionGeometryChange(fromWireShape(from), fromWireShape(to))
        case WirePipeElDescr.SingularFlowResistance(zeta, crossSection)                  =>
            SingularFlowResistance(
                wireDoubleToZeta(zeta                      ),
                crossSection.map(wireQtyToQtyD[(Meter ^ 2)])
            )
        case WirePipeElDescr.PressureDiff(pa, crossSection)                              =>
            PressureDiff(
                wireQtyToQtyD[Pascal](pa                        ),
                crossSection.map     (wireQtyToQtyD[(Meter ^ 2)])
            )

    // =========================================================================
    //  NamedPipeElDescrG <-> WireNamedPipeEl
    //  (Wire omits idx/typ/nf; engine uses defaults for wire-to-engine)
    // =========================================================================

    def toWireNamedPipeEl(nel: NamedPipeElDescrG[PipeElDescr]): WireNamedPipeEl =
        WireNamedPipeEl(nel.name, toWireElDescr(nel.el))

    /**
     * Convert wire named pipe element to engine side.
     *
     * The wire format omits `idx`, `typ`, and `nf`. These are restored from
     * context: `typ` comes from the enclosing [[PipeFullDescrG]], `idx` is
     * derived from position in the element list, and `nf` defaults to 1 flow.
     */
    def fromWireNamedPipeEl(
        w  : WireNamedPipeEl,
        idx: PipeIdx,
        typ: PipeType,
        nf : NbOfFlows
    ): NamedPipeElDescrG[PipeElDescr] =
        NamedPipeElDescrG(idx, typ, w.name, fromWireElDescr(w.el), nf)

    // =========================================================================
    //  PipeFullDescrG <-> WirePipeFullDescr
    // =========================================================================

    def toWirePipeFullDescr(pf: PipeFullDescrG[PipeElDescr]): WirePipeFullDescr =
        WirePipeFullDescr(
            toWireType(pf.pipeType),
            pf.elements.map(toWireNamedPipeEl).toList
        )

    def fromWirePipeFullDescr(w: WirePipeFullDescr): PipeFullDescrG[PipeElDescr] =
        val pipeType = fromWireType(w.pipeType)
        val elements = w.elements.zipWithIndex.map: (wne, i) =>
            fromWireNamedPipeEl(wne, PipeIdx(i), pipeType, NbOfFlows(1))
        PipeFullDescrG(elements.toVector, pipeType)

    def fromWireFireboxPipeFullDescr(w: WirePipeFullDescr): FireboxPipe_Module_15544.FullDescr =
        FireboxPipe_Module_15544.PipeFullDescr(fromWirePipeFullDescr(w))

    def fromWireCombustionAirPipeFullDescr(
        w: WirePipeFullDescr
    ): CombustionAirPipe_Module_15544.FullDescr =
        CombustionAirPipe_Module_15544.PipeFullDescr(fromWirePipeFullDescr(w))

    // =========================================================================
    //  ζ (coefficient of flow resistance) <-> Double
    // =========================================================================

    /** Convert a dimensionless Double to the typed ζ (coefficient of flow resistance). */
    def wireDoubleToZeta(d: Double): ζ =
        d.withUnit[1]

    /** Extract the dimensionless Double value from a ζ. */
    def zetaToWireDouble(z: ζ): Double =
        z.value

    // =========================================================================
    //  PipeResult <-> WirePipeResult — THE B.1-B ROUND-TRIP
    //
    //  CRITICAL: fromValues param order is (..., pd, ζ, pu, ...)
    //  Wire field order is (..., zeta, pu, pd, ...)
    //  The conversion REORDERS: pd before ζ before pu.
    // =========================================================================

    def toWireResult(r: PipeResult)(using Locale): WirePipeResult =
        WirePipeResult               (
            typ                = toWireType(r.typ),
            lengthSum          = qtyDToWireQty[Meter](r.lengthSum),
            heightSum          = qtyDToWireQty[Meter](r.heightSum),
            zeta               = r.ζ.map(zetaToWireDouble),
            pu                 = validatedNelToWireValidated(r.pu),
            pd                 = r.pd.map(qtyDToWireQty[Pascal]),
            pRs                = qtyDToWireQty[Pascal](r.pRs),
            pRg                = qtyDToWireQty[Pascal](r.pRg),
            ph                 = qtyDToWireQty[Pascal](r.ph),
            gasTempStart       = tempDToWireQty[Celsius](r.gas_temp_start),
            gasTempMiddle      = tempDToWireQty[Celsius](r.gas_temp_middle),
            gasTempEnd         = tempDToWireQty[Celsius](r.gas_temp_end),
            densityMiddle      = r.density_middle.map(qtyDToWireQty[Kilogram / (Meter ^ 3)]),
            densityMean        = r.density_mean.map(qtyDToWireQty[Kilogram / (Meter ^ 3)]),
            vStart             = r.v_start.map(qtyDToWireQty[Meter / Second]),
            vEnd               = r.v_end.map(qtyDToWireQty[Meter / Second]),
            lastDensityMean    = r.last_density_mean.map(qtyDToWireQty[Kilogram / (Meter ^ 3)]),
            lastDensityMiddle  = r.last_density_middle.map(qtyDToWireQty[Kilogram / (Meter ^ 3)]),
            lastVelocityMean   = r.last_velocity_mean.map(qtyDToWireQty[Meter / Second]),
            lastVelocityMiddle = r.last_velocity_middle.map(qtyDToWireQty[Meter / Second])
        )

    /**
     * Reconstruct a PipeResult from wire fields via `PipeResult.fromValues`.
     *
     * CRITICAL: the `fromValues` parameter order is
     *   (..., pd, ζ, pu, ...)
     * but the wire field order is
     *   (..., zeta, pu, pd, ...)
     * This function reorders to match the engine API.
     *
     * `gas_temp_mean` is NOT passed — it is derived internally by `fromValues`.
     */
    def fromWireResult(w: WirePipeResult)(using Locale): PipeResult =
        PipeResult.fromValues                  (
            pt                   = fromWireType(w.typ),
            lengthSum            = wireQtyToQtyD[Meter](w.lengthSum),
            heightSum            = wireQtyToQtyD[Meter](w.heightSum),
            pd                   = w.pd.map(wireQtyToQtyD[Pascal]),
            ζ                    = w.zeta.map(wireDoubleToZeta),
            pu                   = wireValidatedToValidatedNel(w.pu),
            pRs                  = wireQtyToQtyD[Pascal](w.pRs),
            pRg                  = wireQtyToQtyD[Pascal](w.pRg),
            ph                   = wireQtyToQtyD[Pascal](w.ph),
            gas_temp_start       = wireQtyToTempD[Celsius](w.gasTempStart),
            gas_temp_middle      = wireQtyToTempD[Celsius](w.gasTempMiddle),
            gas_temp_end         = wireQtyToTempD[Celsius](w.gasTempEnd),
            density_middle       = w.densityMiddle.map(wireQtyToQtyD[Kilogram / (Meter ^ 3)]),
            density_mean         = w.densityMean.map(wireQtyToQtyD[Kilogram / (Meter ^ 3)]),
            v_start              = w.vStart.map(wireQtyToQtyD[Meter / Second]),
            v_end                = w.vEnd.map(wireQtyToQtyD[Meter / Second]),
            last_density_mean    = w.lastDensityMean.map(wireQtyToQtyD[Kilogram / (Meter ^ 3)]),
            last_density_middle  = w.lastDensityMiddle.map(wireQtyToQtyD[Kilogram / (Meter ^ 3)]),
            last_velocity_mean   = w.lastVelocityMean.map(wireQtyToQtyD[Meter / Second]),
            last_velocity_middle = w.lastVelocityMiddle.map(wireQtyToQtyD[Meter / Second])
        )

    // =========================================================================
    //  WireValidated[WireQty] <-> ValidatedNel[MecaFlu_Error, Pressure]
    // =========================================================================

    /**
     * Convert wire validated pressure to engine ValidatedNel.
     *
     * Wire errors are wrapped as `MecaFlu_Error.UnexpectedPipeType` since
     * `FireboxErrorCustom` extends `FireboxError` (not `MecaFlu_Error`).
     * The `UnexpectedPipeType` constructor is the most general `MecaFlu_Error`
     * that accepts an arbitrary reason string.
     */
    def wireValidatedToValidatedNel(
        wv: WireValidated[WireQty]
    )(using Locale): ValidatedNel[MecaFlu_Error, Pressure] =
        wv match
            case WireValidated.Valid(value)    =>
                Validated.valid(wireQtyToQtyD[Pascal](value))
            case WireValidated.Invalid(errors) =>
                val nel = NonEmptyList.fromListUnsafe(
                    errors.map(wireErrorItemToMecaFluError)
                )
                Validated.invalid(nel)

    /** Convert engine ValidatedNel pressure to wire validated. */
    def validatedNelToWireValidated(
        v: ValidatedNel[MecaFlu_Error, Pressure]
    )(using Locale): WireValidated[WireQty] =
        v match
            case Validated.Valid(p)     =>
                WireValidated.Valid(qtyDToWireQty[Pascal](p))
            case Validated.Invalid(nel) =>
                WireValidated.Invalid(nel.toList.map(mecaFluErrorToWireErrorItem))

    // =========================================================================
    //  WireErrorItem <-> MecaFlu_Error
    // =========================================================================

    /**
     * Convert a wire error item to a MecaFlu_Error.
     *
     * The wire error originates from an external source (client-side).
     * We wrap it as `UnexpectedPipeType` with the original code and message.
     * `FireboxPipeT` is used as the default section type since wire errors
     * typically originate from firebox computation contexts.
     */
    def wireErrorItemToMecaFluError(wi: WireErrorItem): MecaFlu_Error =
        MecaFlu_Error.UnexpectedPipeType(s"[wire] ${wi.code}: ${wi.message}", FireboxPipeT)

    /**
     * Convert a MecaFlu_Error to a wire error item.
     *
     * Uses `ShowUsingLocale[MecaFlu_Error]` to resolve the localized message,
     * and the error's simple class name as the code.
     */
    def mecaFluErrorToWireErrorItem(e: MecaFlu_Error)(using loc: Locale): WireErrorItem =
        val code    = e.getClass.getSimpleName
        val message = ShowUsingLocale[MecaFlu_Error].show(e)
        WireErrorItem(code, message)

    // =========================================================================
    //  OpPoint keys: WireDraftCondition / WireLoadQty <-> engine enums
    //  (total mappings — explicit match on every case, no catch-all)
    // =========================================================================

    def toWireDraftCondition(dc: DraftCondition): WireDraftCondition =
        dc match
            case DraftCondition.DraftMinOrPositivePressureMax => WireDraftCondition.DraftMinOrPositivePressureMax
            case DraftCondition.DraftMaxOrPositivePressureMin => WireDraftCondition.DraftMaxOrPositivePressureMin

    def fromWireDraftCondition(w: WireDraftCondition): DraftCondition =
        w match
            case WireDraftCondition.DraftMinOrPositivePressureMax => DraftCondition.DraftMinOrPositivePressureMax
            case WireDraftCondition.DraftMaxOrPositivePressureMin => DraftCondition.DraftMaxOrPositivePressureMin

    def toWireLoadQty(lq: LoadQty): WireLoadQty =
        lq match
            case LoadQty.Nominal => WireLoadQty.Nominal
            case LoadQty.Reduced => WireLoadQty.Reduced

    def fromWireLoadQty(w: WireLoadQty): LoadQty =
        w match
            case WireLoadQty.Nominal => LoadQty.Nominal
            case WireLoadQty.Reduced => LoadQty.Reduced

    // =========================================================================
    //  FireboxContribution[D] -> WireFireboxContribution
    //  (engine-to-wire direction; used by FireboxCalcService / testkit server)
    //
    //  AsResult emits the operating-point-keyed grid: one WireOpPointResult per
    //  (OpPoint, PipeResult) entry (round-3 decision D3). The wire list mirrors
    //  the engine grid bijectively; non-emptiness is carried by the engine's
    //  NonEmptyList and preserved by construction.
    // =========================================================================

    def toWireFireboxContribution[D](
        c: FireboxContribution[D]
    )(using
        toWirePipeFullDescr: D => WirePipeFullDescr,
        loc                : Locale
    ): WireFireboxContribution =
        c match
            case FireboxContribution.Without        =>
                WireFireboxContribution.Without
            case FireboxContribution.AsDescr(descr) =>
                WireFireboxContribution.AsDescr(toWirePipeFullDescr(descr))
            case FireboxContribution.AsResult(grid) =>
                WireFireboxContribution.AsResult(
                    grid.toList.map { case (op, result) =>
                        WireOpPointResult(
                            draftCondition = toWireDraftCondition(op.draftCondition),
                            loadQty        = toWireLoadQty(op.loadQty),
                            result         = toWireResult(result)
                        )
                    }
                )

    // =========================================================================
    //  WireFireboxContribution -> FireboxContribution[D]
    //  (wire-to-engine direction; used by RemoteBackedFireboxEngine builders)
    // =========================================================================

    /**
     * Convert a wire firebox contribution to its engine-side counterpart.
     *
     * The caller provides `fromWirePipeFullDescr` to convert the wire pipe
     * description into the engine's full-description type `D` (e.g.,
     * `FireboxPipe_Module_15544.FullDescr`).
     *
     * The `asResult` arm rebuilds the engine grid from the keyed wire list —
     * bijective with [[toWireFireboxContribution]]: the wire decoder guarantees
     * non-emptiness (`minItems: 1`) and key uniqueness, so the engine's
     * `NonEmptyList` is recovered entry-for-entry.
     *
     * Conversion failures (e.g., bad unit strings in wire quantities)
     * are caught and wrapped as `PressureLossMustBeDefined` errors, since
     * it is the simplest `IncrementalValidation_Error` constructor that
     * does not require a `SlotContext`.
     */
    def fromWireFireboxContribution[D](
        w: WireFireboxContribution
    )(using
        fromWirePipeFullDescr: WirePipeFullDescr => D,
        loc                  : Locale
    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[D]] =
        try
            w match
                case WireFireboxContribution.Without =>
                    FireboxContribution.Without.validNel[IncrementalValidation_Error]

                case WireFireboxContribution.AsDescr(wireDescr) =>
                    FireboxContribution.AsDescr(fromWirePipeFullDescr(wireDescr)).validNel[IncrementalValidation_Error]

                case WireFireboxContribution.AsResult(results) =>
                    // The wire decoder has proven non-emptiness (minItems: 1) and
                    // key uniqueness; the engine grid is rebuilt entry-for-entry.
                    val grid = NonEmptyList.fromListUnsafe(
                        results.map { entry =>
                            OpPoint(
                                fromWireDraftCondition(entry.draftCondition),
                                fromWireLoadQty       (entry.loadQty       )
                            ) -> fromWireResult(entry.result)
                        }
                    )
                    FireboxContribution.AsResult(grid).validNel[IncrementalValidation_Error]
        catch
            case e: Exception =>
                val sectionTyp =
                    w match
                        case WireFireboxContribution.Without          => FireboxPipeT
                        case WireFireboxContribution.AsDescr(wd)      => fromWireType(wd.pipeType)
                        case WireFireboxContribution.AsResult(Nil)    => FireboxPipeT
                        case WireFireboxContribution.AsResult(h :: _) => fromWireType(h.result.typ)
                PressureLossMustBeDefined(sectionTyp).invalidNel[FireboxContribution[D]]

    // =========================================================================
    //  WireValidated[WireFireboxContribution] -> ValidatedNel[..., FireboxContribution[D]]
    //
    //  The response's validated wrapper (round-3 decision D5): the `valid` arm
    //  converts through [[fromWireFireboxContribution]]; the `invalid` arm lands
    //  in the remote-data record's existing ValidatedNel channel as
    //  [[RemoteContributionInvalid]] errors carrying the stable machine code
    //  and pre-localised message verbatim.
    // =========================================================================

    def fromWireValidatedFireboxContribution[D](
        wv: WireValidated[WireFireboxContribution]
    )(using
        fromWirePipeFullDescr: WirePipeFullDescr => D,
        loc                  : Locale
    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[D]] =
        wv match
            case WireValidated.Valid(contribution) =>
                fromWireFireboxContribution[D](contribution)
            case WireValidated.Invalid(errors)     =>
                // The WireValidated decoder guarantees at least one error.
                Validated.invalid(
                    NonEmptyList.fromListUnsafe(
                        errors.map(e => RemoteContributionInvalid(e.code, e.message))
                    )
                )

    // =========================================================================
    //  WireOperatingEnvelope -> FireboxOperatingEnvelope
    //
    //  Raw standard inputs only (round-3 decision D4): altitude in metres plus
    //  optional per-draft-condition outside-air temperature overrides in °C,
    //  mapped onto the engine's `T_L_override` vocabulary. Absent overrides
    //  mean "standard defaults", exactly as locally.
    // =========================================================================

    def fromWireOperatingEnvelope(w: WireOperatingEnvelope): FireboxOperatingEnvelope =
        val altitude = wireQtyToQtyD[Meter](w.altitude)
        val tL       =
            w.temperatureOverrides match
                case None            => T_L_override.none
                case Some(overrides) =>
                    T_L_override.fromOptions(
                        whenDraftMin = overrides.draftMinOrPositivePressureMax
                            .map(t => wireQtyToTempD[Celsius](t).to_degK),
                        whenDraftMax = overrides.draftMaxOrPositivePressureMin
                            .map(t => wireQtyToTempD[Celsius](t).to_degK)
                    )
        FireboxOperatingEnvelope(altitude, tL)

    // =========================================================================
    //  WireFireboxKind <-> FireboxKind (total bijection over 6 closed cases)
    //
    //  `BlackBox` is excluded: it is an open family with no wire representation.
    //  Adding a new WireFireboxKind case forces a compile-time decision here.
    // =========================================================================

    def toWireFireboxKind(k: FireboxKind): WireFireboxKind =
        k match
            case FireboxKind.Traditional    => WireFireboxKind.Traditional
            case FireboxKind.Ecolabeled     => WireFireboxKind.Ecolabeled
            case FireboxKind.AFPMA_PRSE     => WireFireboxKind.AfpmaPrse
            case FireboxKind.SingleTested   => WireFireboxKind.SingleTested
            case FireboxKind.Door15aCatalog => WireFireboxKind.Door15aCatalog
            case FireboxKind.CustomForLab   => WireFireboxKind.CustomForLab
            case FireboxKind.BlackBox(_)    =>
                throw new IllegalArgumentException("BlackBox has no wire representation")

    def fromWireFireboxKind(w: WireFireboxKind): FireboxKind =
        w match
            case WireFireboxKind.Traditional    => FireboxKind.Traditional
            case WireFireboxKind.Ecolabeled     => FireboxKind.Ecolabeled
            case WireFireboxKind.AfpmaPrse      => FireboxKind.AFPMA_PRSE
            case WireFireboxKind.SingleTested   => FireboxKind.SingleTested
            case WireFireboxKind.Door15aCatalog => FireboxKind.Door15aCatalog
            case WireFireboxKind.CustomForLab   => FireboxKind.CustomForLab
