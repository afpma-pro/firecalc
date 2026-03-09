/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v2.*
import afpma.firecalc.dto.v3.*
import afpma.firecalc.dto.v4.*

import cats.syntax.all.*

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.dto.common.AirSpaceDetailed_V1.WithoutAirSpace
import afpma.firecalc.dto.common.AirSpaceDetailed_V1.WithAirSpace

object transformers:

    // Firebox_V1 -> Firebox_V2

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
    // Explicit seq transformer to handle new `roll` field (defaults to None) on
    // AddDirectionChange subtypes, and new coproduct variants in V3 that can't
    // appear in old V3 data (SetInitialDirection, LinedFlue, SetPropertiesInBatch).

    given thermalV2ToV3: Transformer[v3.ThermalPipeDescr_13384_V2, v4.ThermalPipeDescr_13384_V3] =
        Transformer
            .define[v3.ThermalPipeDescr_13384_V2, v4.ThermalPipeDescr_13384_V3]
            .enableOptionDefaultsToNone
            .buildTransformer
        
        
