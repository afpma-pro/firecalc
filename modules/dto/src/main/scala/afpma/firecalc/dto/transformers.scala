/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.dto.v2.*
import afpma.firecalc.dto.v3.*
import afpma.firecalc.dto.v4.*
import afpma.firecalc.dto.v5
import afpma.firecalc.dto.v5.FireCalcYAML_V5

import cats.syntax.all.*

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.dto.common.AirSpaceDetailed_V1.WithoutAirSpace
import afpma.firecalc.dto.common.AirSpaceDetailed_V1.WithAirSpace

object transformers:

    // Firebox_V1 -> Firebox_V2

    // V1 → V2 Migration
    // The new `height_of_first_row_of_air_injectors` field has a default value (5.cm)
    // in the Firebox case classes, so Chimney can derive the transformer automatically
    // since the Circe decoder will use the default when deserializing V1 JSON.

    given Transformer[FireCalcYAML_V1, FireCalcYAML_V2] =
        Transformer
            .define[FireCalcYAML_V1, FireCalcYAML_V2]
            .withFieldConst(_.version, FireCalcYAML_V2.VERSION)
            .withFieldComputed(_.firebox, _.firebox.transformInto[v2.Firebox_V2])
            .buildTransformer

    given Transformer[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional] =
        Transformer
            .define[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer

    given Transformer[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled] =
        Transformer
            .define[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer

    given thermalV1ToFlowOnlyV1: Transformer[Seq[ThermalPipeDescr_13384_V1], Seq[FlowOnlyPipeDescr_13384_V1]] =
        (xs: Seq[ThermalPipeDescr_13384_V1]) =>
            xs.mapFilter[FlowOnlyPipeDescr_13384_V1]: x =>
                x match
                    case y: AddThermalPipeElement_13384_V1 => y.into[AddFlowOnlyPipeElement_13384_V1].transform.some
                    case y @ SetThermalPipeProp_13384_V1.SetInnerShape(shape)                       =>
                        SetFlowOnlyPipeProp_13384_V1.SetInnerShape(shape).some
                    case y @ SetThermalPipeProp_13384_V1.SetOuterShape(shape)                       => None
                    case y @ SetThermalPipeProp_13384_V1.SetThickness(thickness)                    => None
                    case y @ SetThermalPipeProp_13384_V1.SetRoughness(roughness)                    =>
                        SetFlowOnlyPipeProp_13384_V1.SetRoughness(roughness).some
                    case y @ SetThermalPipeProp_13384_V1.SetMaterial(material)                      =>
                        SetFlowOnlyPipeProp_13384_V1.SetMaterial(material).some
                    case y @ SetThermalPipeProp_13384_V1.SetLayer(thickness, thermal_conductivity)  => None
                    case y @ SetThermalPipeProp_13384_V1.SetLayers(layers)                          => None
                    case y @ SetThermalPipeProp_13384_V1.SetAirSpaceAfterLayers(air_space_detailed) => None
                    case y @ SetThermalPipeProp_13384_V1.SetPipeLocation(pipe_location)             => None
                    case y @ SetThermalPipeProp_13384_V1.SetDuctType(duct)                          => None
                    case y @ SetThermalPipeProp_13384_V1.SetNumberOfFlows(n_flows)                  =>
                        SetFlowOnlyPipeProp_13384_V1.SetNumberOfFlows(n_flows).some

    given Transformer[AddThermalPipeElement_13384_V1, AddFlowOnlyPipeElement_13384_V1] =
        Transformer
            .define[AddThermalPipeElement_13384_V1, AddFlowOnlyPipeElement_13384_V1]
            .enableOptionDefaultsToNone
            .buildTransformer

    // V2 to V3 Migration: FlowOnlyPipeDescr seq transformers
    // These are needed because SetInitialDirection is new in V1 (current) and doesn't
    // exist in V2 (historical snapshot), and the roll field was added.
    // SetInitialDirection is silently dropped (old V2 files never contained it).

    given flowOnly13384V1ToV2: Transformer[Seq[FlowOnlyPipeDescr_13384_V1], Seq[FlowOnlyPipeDescr_13384_V2]] =
        (xs: Seq[FlowOnlyPipeDescr_13384_V1]) =>
            import v3.AddFlowOnlyPipeElement_13384_V2 as El
            import v3.SetFlowOnlyPipeProp_13384_V2 as Prop
            xs.flatMap:
                case SetFlowOnlyPipeProp_13384_V1.SetInnerShape(shape)              => Some(Prop.SetInnerShape(shape))
                case SetFlowOnlyPipeProp_13384_V1.SetRoughness(roughness)           => Some(Prop.SetRoughness(roughness))
                case SetFlowOnlyPipeProp_13384_V1.SetMaterial(material)             => Some(Prop.SetMaterial(material.transformInto[Material_13384_V2]))
                case SetFlowOnlyPipeProp_13384_V1.SetNumberOfFlows(n)               => Some(Prop.SetNumberOfFlows(n))
                case _: SetFlowOnlyPipeProp_13384_V1.SetInitialDirection            => None // new field, not in V2 - drop on migration
                case AddFlowOnlyPipeElement_13384_V1.AddSectionSlopped(n, l, e)         => Some(El.AddSectionSlopped(n, l, e))
                case AddFlowOnlyPipeElement_13384_V1.AddSectionHorizontal(n, l)         => Some(El.AddSectionHorizontal(n, l))
                case AddFlowOnlyPipeElement_13384_V1.AddSectionVertical(n, e)           => Some(El.AddSectionVertical(n, e))
                case AddFlowOnlyPipeElement_13384_V1.AddAngleAdjustable(n, a, z, _)    => Some(El.AddAngleAdjustable(n, a, z))
                case AddFlowOnlyPipeElement_13384_V1.AddSharpeAngle_0_to_90(n, a, _)   => Some(El.AddSharpeAngle_0_to_90(n, a))
                case AddFlowOnlyPipeElement_13384_V1.AddSharpeAngle_0_to_90_Unsafe(n, a, _) => Some(El.AddSharpeAngle_0_to_90_Unsafe(n, a))
                case AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_90(n, r, _)        => Some(El.AddSmoothCurve_90(n, r))
                case AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_90_Unsafe(n, r, _) => Some(El.AddSmoothCurve_90_Unsafe(n, r))
                case AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_60(n, r, _)        => Some(El.AddSmoothCurve_60(n, r))
                case AddFlowOnlyPipeElement_13384_V1.AddSmoothCurve_60_Unsafe(n, r, _) => Some(El.AddSmoothCurve_60_Unsafe(n, r))
                case AddFlowOnlyPipeElement_13384_V1.AddElbows_2x45(n, r, _)           => Some(El.AddElbows_2x45(n, r))
                case AddFlowOnlyPipeElement_13384_V1.AddElbows_3x30(n, r, _)           => Some(El.AddElbows_3x30(n, r))
                case AddFlowOnlyPipeElement_13384_V1.AddElbows_4x22p5(n, r, _)         => Some(El.AddElbows_4x22p5(n, r))
                case AddFlowOnlyPipeElement_13384_V1.AddSectionDecrease(n, d)           => Some(El.AddSectionDecrease(n, d))
                case AddFlowOnlyPipeElement_13384_V1.AddSectionIncrease(n, d)           => Some(El.AddSectionIncrease(n, d))
                case AddFlowOnlyPipeElement_13384_V1.AddFlowResistance(n, z, cs)        => Some(El.AddFlowResistance(n, z, cs))
                case AddFlowOnlyPipeElement_13384_V1.AddPressureDiff(n, p)              => Some(El.AddPressureDiff(n, p))

    given flowOnly15544V1ToV2: Transformer[Seq[FlowOnlyPipeDescr_15544_V1], Seq[FlowOnlyPipeDescr_15544_V2]] =
        (xs: Seq[FlowOnlyPipeDescr_15544_V1]) =>
            import v3.AddFlowOnlyPipeElement_15544_V2 as El
            import v3.SetFlowOnlyPipeProp_15544_V2 as Prop
            xs.flatMap:
                case SetFlowOnlyPipeProp_15544_V1.SetInnerShape(shape)              => Some(Prop.SetInnerShape(shape))
                case SetFlowOnlyPipeProp_15544_V1.SetRoughness(roughness)           => Some(Prop.SetRoughness(roughness))
                case SetFlowOnlyPipeProp_15544_V1.SetMaterial(material)             => Some(Prop.SetMaterial(material.transformInto[Material_15544_V2]))
                case SetFlowOnlyPipeProp_15544_V1.SetNumberOfFlows(n)               => Some(Prop.SetNumberOfFlows(n))
                case _: SetFlowOnlyPipeProp_15544_V1.SetInitialDirection            => None // new field, not in V2 - drop on migration
                case AddFlowOnlyPipeElement_15544_V1.AddSectionSlopped(n, l, e)         => Some(El.AddSectionSlopped(n, l, e))
                case AddFlowOnlyPipeElement_15544_V1.AddSectionHorizontal(n, l)         => Some(El.AddSectionHorizontal(n, l))
                case AddFlowOnlyPipeElement_15544_V1.AddSectionVertical(n, e)           => Some(El.AddSectionVertical(n, e))
                case AddFlowOnlyPipeElement_15544_V1.AddSharpeAngle_0_to_180(n, a, _)   => Some(El.AddSharpeAngle_0_to_180(n, a, None))
                case AddFlowOnlyPipeElement_15544_V1.AddCircularArc_60(n, _)            => Some(El.AddCircularArc_60(n))
                case AddFlowOnlyPipeElement_15544_V1.AddSectionShapeChange(n, s)         => Some(El.AddSectionShapeChange(n, s))
                case AddFlowOnlyPipeElement_15544_V1.AddFlowResistance(n, z, cs)         => Some(El.AddFlowResistance(n, z, cs))
                case AddFlowOnlyPipeElement_15544_V1.AddPressureDiff(n, p)               => Some(El.AddPressureDiff(n, p))

    // V2 to V3 Migration: Material transformers

    given Transformer[Material_13384_V1, Material_13384_V2] = (v1: Material_13384_V1) =>
        v1 match
            case Material_13384_V1.WeldedSteel     => Material_13384_V2.WeldedSteel()
            case Material_13384_V1.Glass           => Material_13384_V2.Glass()
            case Material_13384_V1.Plastic         => Material_13384_V2.Plastic()
            case Material_13384_V1.Aluminium       => Material_13384_V2.Aluminium()
            case Material_13384_V1.ClayFlueLiners  => Material_13384_V2.ClayFlueLiners()
            case Material_13384_V1.Bricks          => Material_13384_V2.Bricks()
            case Material_13384_V1.SolderedMetal   => Material_13384_V2.SolderedMetal()
            case Material_13384_V1.Concrete        => Material_13384_V2.Concrete()
            case Material_13384_V1.Fibrociment     => Material_13384_V2.Fibrociment()
            case Material_13384_V1.Masonry         => Material_13384_V2.Masonry()
            case Material_13384_V1.CorrugatedMetal => Material_13384_V2.CorrugatedMetal()

    given Transformer[Material_15544_V1, Material_15544_V2] = (v1: Material_15544_V1) =>
        v1 match
            case Material_15544_V1.TuyauxEnChamotte => Material_15544_V2.TuyauxEnChamotte()
            case Material_15544_V1.BlocsDeChamotte  => Material_15544_V2.BlocsDeChamotte()

    // Firebox_V2 -> Firebox_V3

    given firebox_v2_traditional_to_v3: Transformer[v2.Firebox_V2.Traditional, v4.Firebox_V3.Traditional] =
        Transformer
            .define[v2.Firebox_V2.Traditional, v4.Firebox_V3.Traditional]
            .withFieldRenamed(_.height_of_first_row_of_air_injectors, _.height_of_lowest_opening)
            .buildTransformer

    given firebox_v2_ecolabeled_to_ecolabeled_v3: Transformer[v2.Firebox_V2.EcoLabeled, v4.Firebox_V3.Ecolabeled] =
        Transformer.define[v2.Firebox_V2.EcoLabeled, v4.Firebox_V3.Ecolabeled].buildTransformer
    
    given firebox_v2_ecolabeled_to_firebox_v3: Transformer[v2.Firebox_V2.EcoLabeled, v4.Firebox_V3] =
        firebox_v2_ecolabeled_to_ecolabeled_v3.transform(_): v4.Firebox_V3

    given firebox_v2_afpma_prse_to_v3: Transformer[v2.Firebox_V2.AFPMA_PRSE, v4.Firebox_V3.AFPMA_PRSE] =
        Transformer.define[v2.Firebox_V2.AFPMA_PRSE, v4.Firebox_V3.AFPMA_PRSE].buildTransformer

    given firebox_v2_to_v3: Transformer[v2.Firebox_V2, v4.Firebox_V3] =
        Transformer.define[v2.Firebox_V2, v4.Firebox_V3].buildTransformer

    // V3 to V4 Migration

    given Transformer[AirSpaceDetailed_V1, AirSpaceDetailed_V2] = (v1: AirSpaceDetailed_V1) =>
        v1 match
            case WithoutAirSpace => AirSpaceDetailed_V2.WithoutAirSpace_V2
            case WithAirSpace(width, direction, ventil_openings) => AirSpaceDetailed_V2.WithAirSpace_V2(width, direction, ventil_openings)

    // V3 to V4 Migration: ThermalPipeDescr_13384_V2 → V3
    // Explicit transformer because:
    // - AddSectionSlopped drops elevation_gain (V2 has 3 fields, V3 has 2)
    // - AddDirectionChange subtypes get absDir = None (new in V3)
    // - AirSpaceDetailed_V1 → V2 conversion needed
    // - New V3 coproduct variants (SetInitialDirection, LinedFlue, SetPropertiesInBatch)
    //   can't appear in V2 data.

    given thermalV2ToV3: Transformer[v3.ThermalPipeDescr_13384_V2, v4.ThermalPipeDescr_13384_V3] =
        import v3.SetThermalPipeProp_13384_V2 as Prop2
        import v3.AddThermalPipeElement_13384_V2 as El2
        import v4.SetThermalPipeProp_13384_V3 as Prop3
        import v4.AddThermalPipeElement_13384_V3 as El3
        (x: v3.ThermalPipeDescr_13384_V2) => x match
            case Prop2.SetInnerShape(shape)       => Prop3.SetInnerShape(shape)
            case Prop2.SetOuterShape(shape)       => Prop3.SetOuterShape(shape)
            case Prop2.SetThickness(thickness)    => Prop3.SetThickness(thickness)
            case Prop2.SetRoughness(roughness)    => Prop3.SetRoughness(roughness)
            case Prop2.SetMaterial(material)      => Prop3.SetMaterial(material)
            case Prop2.SetLayer(t, lambda)        => Prop3.SetLayer(t, lambda)
            case Prop2.SetLayers(layers)          => Prop3.SetLayers(layers)
            case Prop2.SetAirSpaceAfterLayers(as) => Prop3.SetAirSpaceAfterLayers(as.transformInto[AirSpaceDetailed_V2])
            case Prop2.SetPipeLocation(loc)       => Prop3.SetPipeLocation(loc)
            case Prop2.SetDuctType(duct)          => Prop3.SetDuctType(duct)
            case Prop2.SetNumberOfFlows(n)        => Prop3.SetNumberOfFlows(n)
            case El2.AddSectionSlopped(n, l, _)   => El3.AddSectionSlopped(n, l)
            case El2.AddSectionHorizontal(n, l)   => El3.AddSectionHorizontal(n, l)
            case El2.AddSectionVertical(n, e)     => El3.AddSectionVertical(n, e)
            case El2.AddAngleAdjustable(n, a, z)  => El3.AddAngleAdjustable(n, a, z, None)
            case El2.AddSharpeAngle_0_to_90(n, a) => El3.AddSharpeAngle_0_to_90(n, a, None)
            case El2.AddSharpeAngle_0_to_90_Unsafe(n, a) => El3.AddSharpeAngle_0_to_90_Unsafe(n, a, None)
            case El2.AddSmoothCurve_90(n, r)      => El3.AddSmoothCurve_90(n, r, None)
            case El2.AddSmoothCurve_90_Unsafe(n, r) => El3.AddSmoothCurve_90_Unsafe(n, r, None)
            case El2.AddSmoothCurve_60(n, r)      => El3.AddSmoothCurve_60(n, r, None)
            case El2.AddSmoothCurve_60_Unsafe(n, r) => El3.AddSmoothCurve_60_Unsafe(n, r, None)
            case El2.AddElbows_2x45(n, r)         => El3.AddElbows_2x45(n, r, None)
            case El2.AddElbows_3x30(n, r)         => El3.AddElbows_3x30(n, r, None)
            case El2.AddElbows_4x22p5(n, r)       => El3.AddElbows_4x22p5(n, r, None)
            case El2.AddSectionDecrease(n, d)     => El3.AddSectionDecrease(n, d)
            case El2.AddSectionIncrease(n, d)     => El3.AddSectionIncrease(n, d)
            case El2.AddFlowResistance(n, z, cs)  => El3.AddFlowResistance(n, z, cs)
            case El2.AddPressureDiff(n, p)        => El3.AddPressureDiff(n, p)

    // V3 to V4 Migration: FlowOnlyPipeDescr_13384_V2 → V3
    // - AddSectionSlopped drops elevation_gain (V2 has 3 fields, V3 has 2)
    // - AddDirectionChange subtypes get absDir = None (new in V3)
    // - SetInitialDirection never present in V2 data.

    given flowOnly13384V2ToV3: Transformer[v3.FlowOnlyPipeDescr_13384_V2, v4.FlowOnlyPipeDescr_13384_V3] =
        import v3.SetFlowOnlyPipeProp_13384_V2 as Prop2
        import v3.AddFlowOnlyPipeElement_13384_V2 as El2
        import v4.SetFlowOnlyPipeProp_13384_V3 as Prop3
        import v4.AddFlowOnlyPipeElement_13384_V3 as El3
        (x: v3.FlowOnlyPipeDescr_13384_V2) => x match
            case Prop2.SetInnerShape(shape)       => Prop3.SetInnerShape(shape)
            case Prop2.SetRoughness(roughness)    => Prop3.SetRoughness(roughness)
            case Prop2.SetMaterial(material)      => Prop3.SetMaterial(material)
            case Prop2.SetNumberOfFlows(n)        => Prop3.SetNumberOfFlows(n)
            case El2.AddSectionSlopped(n, l, _)   => El3.AddSectionSlopped(n, l)
            case El2.AddSectionHorizontal(n, l)   => El3.AddSectionHorizontal(n, l)
            case El2.AddSectionVertical(n, e)     => El3.AddSectionVertical(n, e)
            case El2.AddAngleAdjustable(n, a, z)  => El3.AddAngleAdjustable(n, a, z, None)
            case El2.AddSharpeAngle_0_to_90(n, a) => El3.AddSharpeAngle_0_to_90(n, a, None)
            case El2.AddSharpeAngle_0_to_90_Unsafe(n, a) => El3.AddSharpeAngle_0_to_90_Unsafe(n, a, None)
            case El2.AddSmoothCurve_90(n, r)      => El3.AddSmoothCurve_90(n, r, None)
            case El2.AddSmoothCurve_90_Unsafe(n, r) => El3.AddSmoothCurve_90_Unsafe(n, r, None)
            case El2.AddSmoothCurve_60(n, r)      => El3.AddSmoothCurve_60(n, r, None)
            case El2.AddSmoothCurve_60_Unsafe(n, r) => El3.AddSmoothCurve_60_Unsafe(n, r, None)
            case El2.AddElbows_2x45(n, r)         => El3.AddElbows_2x45(n, r, None)
            case El2.AddElbows_3x30(n, r)         => El3.AddElbows_3x30(n, r, None)
            case El2.AddElbows_4x22p5(n, r)       => El3.AddElbows_4x22p5(n, r, None)
            case El2.AddSectionDecrease(n, d)     => El3.AddSectionDecrease(n, d)
            case El2.AddSectionIncrease(n, d)     => El3.AddSectionIncrease(n, d)
            case El2.AddFlowResistance(n, z, cs)  => El3.AddFlowResistance(n, z, cs)
            case El2.AddPressureDiff(n, p)        => El3.AddPressureDiff(n, p)

    // V3 to V4 Migration: FlowOnlyPipeDescr_15544_V2 → V3
    // - AddSectionSlopped drops elevation_gain (V2 has 3 fields, V3 has 2)
    // - AddDirectionChange subtypes get absDir = None (new in V3)
    // - SetInitialDirection never present in V2 data.

    given flowOnly15544V2ToV3: Transformer[v3.FlowOnlyPipeDescr_15544_V2, v4.FlowOnlyPipeDescr_15544_V3] =
        import v3.SetFlowOnlyPipeProp_15544_V2 as Prop2
        import v3.AddFlowOnlyPipeElement_15544_V2 as El2
        import v4.SetFlowOnlyPipeProp_15544_V3 as Prop3
        import v4.AddFlowOnlyPipeElement_15544_V3 as El3
        (x: v3.FlowOnlyPipeDescr_15544_V2) => x match
            case Prop2.SetInnerShape(shape)    => Prop3.SetInnerShape(shape)
            case Prop2.SetRoughness(roughness) => Prop3.SetRoughness(roughness)
            case Prop2.SetMaterial(material)   => Prop3.SetMaterial(material)
            case Prop2.SetNumberOfFlows(n)     => Prop3.SetNumberOfFlows(n)
            case El2.AddSectionSlopped(n, l, _)  => El3.AddSectionSlopped(n, l)
            case El2.AddSectionHorizontal(n, l)  => El3.AddSectionHorizontal(n, l)
            case El2.AddSectionVertical(n, e)    => El3.AddSectionVertical(n, e)
            case El2.AddSharpeAngle_0_to_180(n, a, angleN2) => El3.AddSharpeAngle_0_to_180(n, a, None)
            case El2.AddCircularArc_60(n)        => El3.AddCircularArc_60(n, None)
            case El2.AddSectionShapeChange(n, s) => El3.AddSectionShapeChange(n, s)
            case El2.AddFlowResistance(n, z, cs) => El3.AddFlowResistance(n, z, cs)
            case El2.AddPressureDiff(n, p)       => El3.AddPressureDiff(n, p)

    // V4 to V5 Migration

    given Transformer[v4.Firebox_V3.Ecolabeled, v5.Firebox_V4.Ecolabeled] =
    Transformer
        .define[v4.Firebox_V3.Ecolabeled, v5.Firebox_V4.Ecolabeled]
        .withFieldRenamed(_.reinforcement_bars_offset_in_corners, _.reinforcement_bars_offset_in_corners_R1)
        .withFieldRenamed(_.reinforcement_bars_offset_in_corners, _.reinforcement_bars_offset_in_corners_R2)
        .withFieldRenamed(_.reinforcement_bars_offset_in_corners, _.reinforcement_bars_offset_in_corners_R3)
        .buildTransformer

    // ─── Top-level FireCalcYAML transformers (version bumping) ───────────────────
    //
    // IMPORTANT: Every cross-version FireCalcYAML transformer MUST include:
    //   .withFieldConst(_.version, FireCalcYAML_VN.VERSION)
    //
    // Without this, Chimney would fail to compile thanks to the FireCalc_Version.V[N]
    // literal types — V[4] and V[5] are incompatible types, so Chimney cannot auto-copy
    // the version field.
    //
    // Before March 2026, V2→V3, V3→V4, and V4→V5 lacked explicit transformers.
    // Chimney auto-derived them, silently copying the source version number. This produced
    // corrupted files in the wild (e.g. version:4 with V5 data). See FireCalcYAMLMigrations
    // for fallback recovery logic.
    //
    // V1→V2 (above) was the only transformer that correctly used .withFieldConst from the start.

    given Transformer[FireCalcYAML_V2, FireCalcYAML_V3] =
        Transformer
            .define[FireCalcYAML_V2, FireCalcYAML_V3]
            .withFieldConst(_.version, FireCalcYAML_V3.VERSION)
            .buildTransformer

    given Transformer[FireCalcYAML_V3, FireCalcYAML_V4] =
        Transformer
            .define[FireCalcYAML_V3, FireCalcYAML_V4]
            .withFieldConst(_.version, FireCalcYAML_V4.VERSION)
            .buildTransformer

    given Transformer[FireCalcYAML_V4, FireCalcYAML_V5] =
        Transformer
            .define[FireCalcYAML_V4, FireCalcYAML_V5]
            .withFieldConst(_.version, FireCalcYAML_V5.VERSION)
            .withFieldComputed(_.firebox, _.firebox.transformInto[v5.Firebox_V4])
            .buildTransformer
