/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import cats.*
import cats.data.*
import cats.derived.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en15544.firebox.FireboxHelper_15544
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.engine.standard.FireboxError
import afpma.firecalc.engine.utils.ShowAsTable
import afpma.firecalc.engine.utils.VNelString

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import io.taig.babel.Locale
import afpma.firecalc.engine.standard.MecaFlu_Error

object std:

    import afpma.firecalc.engine.models.en13384.std.*

    export Firebox_15544.*
    export Firebox_15544.WhenOneOff.wrapWhenOneOff
    export Firebox_15544.WhenTested.wrapWhenTested

    object PressureLossCoeff:

        type Err = afpma.firecalc.engine.standard.PressureLossCoeff_Error
        type GErr = Err // TODO: remove alias

    end PressureLossCoeff

    sealed trait Inputs[Pipes <: Pipes_EN15544_Strict | Pipes_EN15544_MCE]:
        val localConditions: LocalConditions
        val en13384NationalAcceptedData: NationalAcceptedData
        val stoveParams: StoveParams
        val design: Design
        val pipes: Pipes
        final val flueGasCondition: FlueGasCondition.Dry_NonCondensing.type = FlueGasCondition.Dry_NonCondensing // force dry conditions for 15544

    case class Inputs_EN15544_Strict(
        localConditions: LocalConditions,
        en13384NationalAcceptedData: NationalAcceptedData,
        stoveParams: StoveParams,
        design: Design,
        pipes: Pipes_EN15544_Strict,
        // wood: Wood,
    ) extends Inputs[Pipes_EN15544_Strict]

    case class Inputs_EN15544_MCE(
        localConditions: LocalConditions,
        en13384NationalAcceptedData: NationalAcceptedData,
        stoveParams: StoveParams,
        design: Design,
        pipes: Pipes_EN15544_MCE,
        wood: Wood,
        kindOfWood: KindOfWood,
        computeWoodCalorificValueUsingComposition: "Yes" | "No",
        combustionDuration: Duration,
        fluegas_co2_dry_nominal: Percentage,
        fluegas_co2_dry_lowest: Option[Percentage],
        fluegas_h2o_perc_vol_nominal: Option[Percentage],
        fluegas_h2o_perc_vol_lowest: Option[Percentage],
        massFlows_override: HeatingAppliance.MassFlows,
        ext_air_rel_hum_default: Percentage,
    ) extends Inputs[Pipes_EN15544_MCE]

    case class Design(
        firebox: Firebox_15544
    )

    sealed trait Firebox_15544:
        def reference: LocalizedString
        def type_of_appliance: TypeOfAppliance
        def emissions_values: EmissionsAndEfficiencyValues

    object Firebox_15544 extends FireboxHelper_15544:

        case class Dimensions(
            base: Dimensions.Base,
            height: typedefs.H_BR
        )

        object Dimensions:

            enum Base(
                val perimeter: QtyD[Meter],
                val area: QtyD[(Meter ^ 2)]
            ) derives Show:

                case Squared(
                    width: QtyD[Meter],
                    depth: QtyD[Meter]
                ) extends Base(
                    perimeter = (width + depth) * 2,
                    area = width * depth
                )

            object Base:
                given Show[Squared] = Show.show: b =>
                    val x = b.width.toUnit[Centimeter].showP
                    val y = b.depth.toUnit[Centimeter].showP
                    s"□ $x x $y"

                given TermDef[Base] = TermDef("A_BR")
                given TermDefDetails[Base] = TermDefDetails(
                    I18N.en15544.terms.A_BR.name,
                    I18N.en15544.terms.A_BR.descr,
                )
        end Dimensions

        enum AreaCalcMethod:
            case AutoIfCubic
            case Manual(value: Area)

        final case class Tested(
            override val reference: LocalizedString,
            override val type_of_appliance: TypeOfAppliance,
            efficiency_nominal: Percentage,
            efficiency_reduced: Option[Percentage],
            pn_reduced: HeatOutputReduced.NotDefined_Or_Tested,
            minimumFuelMass: Option[Mass],
            maximumFuelMass: Mass,
            airFuelRatio_nominal: Dimensionless,
            airFuelRatio_lowest: Option[Dimensionless],
            co2_dry_nominal: Percentage,
            co2_dry_lowest: Option[Percentage],
            emissions_values: EmissionsAndEfficiencyValues,
            meanFireboxTemperature: Option[TCelsius],
            tBurnout: TCelsius
        ) extends Firebox_15544

        object Tested:
            given showAsTable: Locale => ShowAsTable[Tested] = 
                ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
                    import x.*
                    val I = I18N.firebox.tested
                    (I18N.firebox.typ               :: "" :: I18N.firebox_names.certified :: Nil) ::
                    (I18N.firebox.ref               :: "" :: reference.show               :: Nil) ::
                    (I18N.type_of_appliance.descr   :: "" :: type_of_appliance.show       :: Nil) ::
                    (I.efficiency_nominal           :: "" :: efficiency_nominal.showP     :: Nil) ::
                    (I.efficiency_reduced           :: "" :: efficiency_reduced.showP     :: Nil) ::
                    (I.heat_output_nominal          :: "" :: "TODO"                       :: Nil) ::
                    (I.heat_output_reduced          :: "" :: pn_reduced.showP             :: Nil) ::
                    (I.load_size_nominal            :: "" :: maximumFuelMass.showP        :: Nil) ::
                    (I.load_size_reduced            :: "" :: minimumFuelMass.showP        :: Nil) ::
                    (I.air_to_fuel_ratio_nominal    :: "" :: airFuelRatio_nominal.showP   :: Nil) ::
                    (I.air_to_fuel_ratio_reduced    :: "" :: airFuelRatio_lowest.showP    :: Nil) ::
                    (I.co2_perc_by_vol_dry_nominal  :: "" :: co2_dry_nominal.showP        :: Nil) ::
                    (I.co2_perc_by_vol_dry_reduced  :: "" :: co2_dry_lowest.showP         :: Nil) ::
                    (I.average_firebox_temperature  :: "" :: meanFireboxTemperature.showP :: Nil) ::
                    (I.firebox_exit_temperature     :: "" :: tBurnout.showP               :: Nil) ::
                    Nil

        trait OneOff extends Firebox_15544:
            def pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal
            def dimensions: Dimensions
            def glass_area: GlassArea
            def air_injector_surface_area: Area
            def validate(mB: m_B): Locale ?=> ValidatedNel[FireboxError, Unit]

        object OneOff:
            final case class CustomForLab(
                override val reference: LocalizedString,
                override val type_of_appliance: TypeOfAppliance,
                emissions_values: EmissionsAndEfficiencyValues,
                pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
                dimensions: Dimensions,
                glass_area: GlassArea,
                air_injector_surface_area: Area,
            ) extends OneOff {
                def validate(m_B: m_B): Locale ?=> ValidatedNel[FireboxError, Unit] = ().validNel
            }

            object CustomForLab:
                given showAsTable: Locale => ShowAsTable[CustomForLab] = 
                    ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
                        import x.*
                        
                        (I18N.firebox.typ                       :: ""  :: I18N.firebox_names.custom_lab_tested :: Nil) ::
                        (I18N.firebox.traditional.firebox_floor_shape :: ""  :: dimensions.base.showP                        :: Nil) ::
                        (I18N.firebox.traditional.height        :: ""  :: dimensions.height.to_cm.showP                :: Nil) ::
                        (I18N.firebox.traditional.glass_surface_area    :: ""  :: glass_area.showP                             :: Nil) ::
                        (I18N.firebox.traditional.air_injector_surface_area:: ""  :: air_injector_surface_area.showP                         :: Nil) ::
                        Nil
    end Firebox_15544
    
    case class Outputs(
        technicalSpecs: TechnicalSpecficiations,
        pipesResult_15544: ValidatedNel[MecaFlu_Error, PipesResult_15544],
        reference_temperatures: ReferenceTemperatures,
        efficiencies_values: EfficienciesValues,
        // pressureRequirement_EN15544: VNelString[PressureRequirement],
        // estimated_output_temperatures: EstimatedOutputTemperatures,
        // flue_gas_triple_of_variates: VNelString[FlueGasTripleOfVariates],
    )

    object Outputs:
        case class TechnicalSpecficiations(
            P_n: P_n,
            t_n: t_n,
            m_B: m_B,
            m_B_min: Option[m_B_min],
            n_min: n_min,
            facing_type: FacingType,
            innerConstructionMaterial: InnerConstructionMaterial
        )

end std
