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
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.gtypedefs.{KindOfWood, λ}
import afpma.firecalc.engine.models.gtypedefs
import afpma.firecalc.engine.standard.*
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
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.impl.en15544.mce.HasTypeMembers_15544_MCE
import afpma.firecalc.units.coulombutils.VolumeFlow

object std:

    import afpma.firecalc.engine.models.en13384.std.*

    export Firebox_15544.*
    export Firebox_15544.WhenOneOff.wrapWhenOneOff
    export Firebox_15544.WhenTested.wrapWhenTested

    object PressureLossCoeff:

        type Err = afpma.firecalc.engine.standard.PressureLossCoeff_Error
        type GErr = Err // TODO: remove alias

    end PressureLossCoeff

    sealed trait Inputs_15544_Alg extends HasPipeModules_15544Only_Alg:
        self =>

        type Pipes_15544 <: Pipes_15544_Alg {
            type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
            type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
            type FluePipe_Module_T          = self.FluePipe_Module_T
        }
                
        val localConditions: LocalConditions
        val en13384NationalAcceptedData: NationalAcceptedData
        val stoveParams: StoveParams
        val design: Design
        val pipes: Pipes_15544
        final val flueGasCondition: FlueGasCondition.Dry_NonCondensing.type = FlueGasCondition.Dry_NonCondensing // force dry conditions for 15544

    case class Inputs_15544_Strict(
        localConditions: LocalConditions,
        en13384NationalAcceptedData: NationalAcceptedData,
        stoveParams: StoveParams,
        design: Design,
        pipes: Pipes_15544_Strict,
        // wood: Wood,
    ) extends Inputs_15544_Alg with HasPipeModules_15544Only_Strict:
        override type Pipes_15544                = Pipes_15544_Strict

    case class Inputs_15544_MCE(
        localConditions: LocalConditions,
        en13384NationalAcceptedData: NationalAcceptedData,
        stoveParams: StoveParams,
        design: Design,
        pipes: Pipes_15544_MCE,
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
    ) extends Inputs_15544_Alg with HasPipeModules_15544Only_MCE:
        override type Pipes_15544              = Pipes_15544_MCE

    case class Design(
        firebox: Firebox_15544
    )

    sealed trait Firebox_15544:
        def reference: LocalizedString
        def type_of_appliance: TypeOfAppliance
        def emissions_values: EmissionsAndEfficiencyValues

        def firebox_glass_surface_ratio_below_one_fifth_constraint: Option[TermConstraint[Unit]]

        def t_n_constraintSlots: ConstraintSlots.T_n = ConstraintSlots.T_n()
        def m_B_constraintSlots: ConstraintSlots.M_B = ConstraintSlots.M_B()
        def m_B_min_constraintSlots: ConstraintSlots.M_B_Min = ConstraintSlots.M_B_Min()
        def glassArea_constraintSlots: ConstraintSlots.GlassAreaSlots = ConstraintSlots.GlassAreaSlots()
        def fireboxDimensions_Base_constraintSlots: ConstraintSlots.FireboxDimensionsBase = ConstraintSlots.FireboxDimensionsBase()
        def h_br_constraintSlots: ConstraintSlots.H_BR = ConstraintSlots.H_BR()
        def λ_constraintSlots: ConstraintSlots.Lambda = ConstraintSlots.Lambda()
        def η_constraintSlots: ConstraintSlots.Eta = ConstraintSlots.Eta()
        def height_of_lowest_opening_constraintSlots: ConstraintSlots.HeightOfLowestOpening = ConstraintSlots.HeightOfLowestOpening()

        def t_n_constraints: Seq[Option[TermConstraint[t_n]]] = t_n_constraintSlots.toSeq
        def m_B_constraints: Seq[Option[TermConstraint[m_B]]] = m_B_constraintSlots.toSeq
        def m_B_min_constraints: Seq[Option[TermConstraint[m_B_min]]] = m_B_min_constraintSlots.toSeq
        def glassArea_constraints: Seq[Option[TermConstraint[GlassArea]]] = glassArea_constraintSlots.toSeq
        def h_br_constraints: Seq[Option[TermConstraint[H_BR]]] = h_br_constraintSlots.toSeq
        def λ_constraints: Seq[Option[TermConstraint[λ]]] = λ_constraintSlots.toSeq
        def η_constraints: Seq[Option[TermConstraint[η]]] = η_constraintSlots.toSeq
        def height_of_lowest_opening_constraints: Seq[Option[TermConstraint[height_of_lowest_opening]]] = height_of_lowest_opening_constraintSlots.toSeq
        def fireboxDimensions_Base_constraints: Seq[Option[TermConstraint[Dimensions.Base]]] = fireboxDimensions_Base_constraintSlots.toSeq

        /**
          * Validate constraints NOT related to EN 15544.
          * Use this method for specific contraints on some family of firebox (e.g: Ecolabeled firebox, 15a doors, or other certified custom designs)
          *
          * @param mB
          * @param flow_rate
          * @return
          */
        def validateSpecificConstraints(mB: m_B, flow_rate: Option[VolumeFlow]): Locale ?=> ValidatedNel[FireboxError, Unit]

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
            tBurnout: TCelsius,
            height_of_first_row_of_air_injectors: Option[Length] = None,
            is_glass_surface_ratio_below_one_fifth: Boolean = true,
        ) extends Firebox_15544:
            override def firebox_glass_surface_ratio_below_one_fifth_constraint: Option[TermConstraint[Unit]] =
                import afpma.firecalc.engine.models.en15544.typedefs.{given_TermDef_Unit, given_TermDefDetails_Unit}
                Some(TermConstraint.GenericTyped[Unit, GlassSurfaceRatioNotConfirmed](
                    value = (),
                    isValid = _ =>
                        if is_glass_surface_ratio_below_one_fifth then Right(())
                        else Left(GlassSurfaceRatioNotConfirmed())
                ))

            override def m_B_constraintSlots: ConstraintSlots.M_B = ConstraintSlots.M_B(
                min = minimumFuelMass.map(TermConstraint.Min.apply),
                max = TermConstraint.Max[m_B](maximumFuelMass).some
            )

            override def validateSpecificConstraints(m_B: m_B, flow_rate: Option[VolumeFlow]) = ().validNel

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
            def height_of_first_row_of_air_injectors: Length

        object OneOff:
            final case class CustomForLab(
                override val reference: LocalizedString,
                override val type_of_appliance: TypeOfAppliance,
                emissions_values: EmissionsAndEfficiencyValues,
                pn_reduced: HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal,
                dimensions: Dimensions,
                glass_area: GlassArea,
                height_of_first_row_of_air_injectors: Length = 5.cm,
            ) extends OneOff {
                override def validateSpecificConstraints(m_B: m_B, flow_rate: Option[VolumeFlow]): Locale ?=> ValidatedNel[FireboxError, Unit] = ().validNel

                override def firebox_glass_surface_ratio_below_one_fifth_constraint: Option[TermConstraint[Unit]] = None
            }

            object CustomForLab:
                given showAsTable: Locale => ShowAsTable[CustomForLab] = 
                    ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
                        import x.*
                        
                        (I18N.firebox.typ                                          :: ""  :: I18N.firebox_names.custom_lab_tested                    :: Nil) ::
                        (I18N.firebox.traditional.firebox_floor_shape              :: ""  :: dimensions.base.showP                                   :: Nil) ::
                        (I18N.firebox.traditional.height                           :: ""  :: dimensions.height.to_cm.showP                           :: Nil) ::
                        (I18N.en15544.terms_xtra.height_of_the_lowest_opening.name :: ""  :: height_of_first_row_of_air_injectors.to_cm.showP        :: Nil) ::
                        (I18N.firebox.traditional.glass_surface_area               :: ""  :: glass_area.showP                                        :: Nil) ::
                        Nil
    end Firebox_15544
    
    case class Outputs(
        technicalSpecs: TechnicalSpecficiations,
        pipesResult_15544: VNelMcalcErr[PipesResult_15544],
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
