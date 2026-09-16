/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.ShowAsTable

import cats.*
import cats.derived.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

object std:

    import afpma.firecalc.engine.models.en13384.std.*

    export Firebox_15544.Dimensions
    export Firebox_15544.CertifiedDesign
    export Firebox_15544.Traditional
    export Firebox_15544.AreaCalcMethod
    export Firebox_15544.SingleTested

    object PressureLossCoeff:

        type Err  = afpma.firecalc.engine.standard.PressureLossCoeff_Error
        type GErr = Err // TODO: remove alias

    end PressureLossCoeff

    trait Inputs_15544_Alg extends HasPipeModules_15544Only_Alg:
        self =>

        type Pipes_15544 <: Pipes_15544_Alg {
            type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
            type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
            type FluePipe_Module_T          = self.FluePipe_Module_T
        }

        val localConditions            : LocalConditions
        val en13384NationalAcceptedData: NationalAcceptedData
        val stoveParams                : StoveParams
        val design                     : Design
        val pipes                      : Pipes_15544
        final val flueGasCondition: FlueGasCondition.Dry_NonCondensing.type =
            FlueGasCondition.Dry_NonCondensing // force dry conditions for 15544

    case class Design(
        firebox: Firebox_15544
    )

    trait Firebox_15544:

        val FLOOR_DEPTH_TO_WIDTH_MIN_RATIO: Double = 1.0
        val FLOOR_DEPTH_TO_WIDTH_MAX_RATIO: Double = 2.0

        def firebox_type     : Locale ?=> String // Ecolabeled, 15a, etc...
        def reference        : LocalizedString
        def type_of_appliance: TypeOfAppliance

        def dimensions: Dimensions

        def glass_area: GlassArea

        def height_of_lowest_opening: Length

        def emissions_values: EmissionsAndEfficiencyValues

        def min_load    : MinLoad
        def nominal_load: Option[Mass]
        def max_load    : Option[Mass]

        def pn_reduced     : HeatOutputReduced
        def co2_dry_nominal: σ_CO2
        def co2_dry_lowest : Option[σ_CO2]

        /**
         * The air-intake pipe end shapes this firebox's inlet accepts, or
         * `None` when the firebox imposes no shape restriction.
         *
         * Virtual on purpose, for the same reason as `showAsTableRows` below:
         * the constraint-folding seam needs the *expected* shapes to emit a
         * declarative `ConstraintDescriptor.ShapeOneOf`, and reading them via a
         * type test would force this module to name kinds that live downstream
         * (the cycle invariant). `PipeShape` is a shared domain type, so the
         * signature couples nothing.
         *
         * Defaults to `None` so kinds without an inlet restriction are
         * unaffected and the trait stays unsealed.
         */
        def expectedAirIntakePipeShapes: Option[List[PipeShape]] = None

        /**
         * Rows for this firebox's description table.
         *
         * Virtual on purpose: the shared rendering seam
         * (`ShowAsTableInstances_15544`) used to `match` on the eight concrete
         * kinds and call `showOnlyRows` in every arm — a virtual call written as
         * a type test, which forced this module to name kinds that are being
         * extracted into their own modules. Overriding this member keeps the
         * dispatch but removes the coupling.
         *
         * The default renders only the members every `Firebox_15544` declares.
         */
        def showAsTableRows: Locale ?=> Seq[Seq[String]] =
            val I = I18N.firebox
            (I18N.firebox.typ                                              :: "" :: firebox_type                   :: Nil) ::
                (I18N.firebox.ref                                          :: "" :: reference.show                 :: Nil) ::
                (I18N.type_of_appliance.descr                              :: "" :: type_of_appliance.show         :: Nil) ::
                (I.base_geometry                                           :: "" :: dimensions.base.showP          :: Nil) ::
                (I18N.en15544.terms.H_BR.name                              :: "" :: dimensions.height.showP        :: Nil) ::
                (I18N.en15544.terms_xtra.height_of_the_lowest_opening.name :: "" :: height_of_lowest_opening.showP :: Nil) ::
                (I.glass_area                                              :: "" :: glass_area.showP               :: Nil) ::
                (I18N.en15544.terms.m_B_min.name                           :: "" :: min_load.show                  :: Nil) ::
                (I18N.en15544.terms.P_n_reduced.name                       :: "" :: pn_reduced.show                :: Nil) ::
                (I18N.firebox.tested.co2_perc_by_vol_dry_nominal           :: "" :: co2_dry_nominal.showP          :: Nil) ::
                (I18N.firebox.tested.co2_perc_by_vol_dry_reduced           :: "" :: co2_dry_lowest.showP           :: Nil) ::
                Nil

    object Firebox_15544:

        /**
         * Default dry CO₂ concentration for fireboxes.
         * See [[https://github.com/afpma-pro/firecalc/blob/main/modules/engine/CO2_DRY_NOMINAL_IMPACT.md]]
         * for impact analysis.
         */
        val co2_dry_nominal_default: σ_CO2 = 7.05.percent

        given showAsTable: Locale => ShowAsTable[Firebox_15544] =
            ShowAsTable.mkLightFor(I18N.headers.firebox_description)(_.showAsTableRows)

        case class Dimensions(
            base  : Dimensions.Base,
            height: typedefs.H_BR
        )

        object Dimensions:

            enum Base(
                val perimeter: QtyD[Meter],
                val area     : QtyD[(Meter ^ 2)]
            ) derives Show:

                case Squared(
                    width: QtyD[Meter],
                    depth: QtyD[Meter]
                ) extends Base(
                        perimeter = (width + depth) * 2,
                        area      = width * depth
                    )

            object Base:
                given Show[Squared] = Show.show: b =>
                    val x = b.width.toUnit[Centimeter].showP
                    val y = b.depth.toUnit[Centimeter].showP
                    s"□ $x x $y"

                given TermDef[Base]        = TermDef("A_BR")
                given TermDefDetails[Base] = TermDefDetails(
                    I18N.en15544.terms.A_BR.name,
                    I18N.en15544.terms.A_BR.descr
                )
        end Dimensions

        enum AreaCalcMethod:
            case AutoIfCubic
            case Manual(value: Area)

        enum TestStandard:
            case EN_15250
            case EN_13229
            case National(name: String)

        final case class SingleTested(
            override val reference                : LocalizedString,
            override val type_of_appliance        : TypeOfAppliance,
            test_standard                         : TestStandard,
            firebox_depth                         : Length,
            firebox_width                         : Length,
            firebox_height                        : Length,
            ash_pit_height                        : Length,
            is_glass_surface_ratio_below_one_fifth: Boolean,
            glass_area                            : GlassArea,
            meanFireboxTemperature                : Option[TCelsius],
            tBurnout                              : Option[TCelsius],
            efficiency_nominal                    : Percentage,
            efficiency_reduced                    : Option[Percentage],
            pn_reduced                            : HeatOutputReduced,
            minimumFuelMass                       : Option[Mass],
            maximumFuelMass                       : Mass,
            airFuelRatio_nominal                  : Dimensionless,
            airFuelRatio_lowest                   : Option[Dimensionless],
            co2_dry_nominal                       : σ_CO2,
            co2_dry_lowest                        : Option[σ_CO2],
            pellets_load_burn_duration            : Option[t_BU],
            emissions_values                      : EmissionsAndEfficiencyValues
        ) extends Firebox_15544:
            override val firebox_type: Locale ?=> String = I18N.firebox_names.single_tested

            override def height_of_lowest_opening = ash_pit_height

            override def dimensions = Dimensions(
                base   = Dimensions.Base.Squared(
                    width = firebox_width,
                    depth = firebox_depth
                ),
                height = firebox_height
            )
            override def min_load   = minimumFuelMass match
                case Some(min) => MinLoad.FromTypeTest(min)
                case None      => MinLoad.NotDefined

            override def nominal_load = Some(maximumFuelMass)
            override def max_load     = Some(maximumFuelMass)

            override def showAsTableRows: Locale ?=> Seq[Seq[String]] =
                val I = I18N.firebox.tested
                (I18N.firebox.typ                  :: "" :: I18N.firebox_names.certified :: Nil) ::
                    (I18N.firebox.ref              :: "" :: reference.show               :: Nil) ::
                    (I18N.type_of_appliance.descr  :: "" :: type_of_appliance.show       :: Nil) ::
                    (I.efficiency_nominal          :: "" :: efficiency_nominal.showP     :: Nil) ::
                    (I.efficiency_reduced          :: "" :: efficiency_reduced.showP     :: Nil) ::
                    (I.heat_output_nominal         :: "" :: "TODO"                       :: Nil) ::
                    (I.heat_output_reduced         :: "" :: pn_reduced.showP             :: Nil) ::
                    (I.load_size_nominal           :: "" :: maximumFuelMass.showP        :: Nil) ::
                    (I.load_size_reduced           :: "" :: minimumFuelMass.showP        :: Nil) ::
                    (I.air_to_fuel_ratio_nominal   :: "" :: airFuelRatio_nominal.showP   :: Nil) ::
                    (I.air_to_fuel_ratio_reduced   :: "" :: airFuelRatio_lowest.showP    :: Nil) ::
                    (I.co2_perc_by_vol_dry_nominal :: "" :: co2_dry_nominal.showP        :: Nil) ::
                    (I.co2_perc_by_vol_dry_reduced :: "" :: co2_dry_lowest.showP         :: Nil) ::
                    (I.average_firebox_temperature :: "" :: meanFireboxTemperature.showP :: Nil) ::
                    (I.firebox_exit_temperature    :: "" :: tBurnout.showP               :: Nil) ::
                    Nil

        object SingleTested:
            given showAsTable: Locale => ShowAsTable[SingleTested] =
                ShowAsTable.mkLightFor(I18N.headers.firebox_description)(_.showAsTableRows)

        trait Traditional extends Firebox_15544

        object Traditional:
            final case class CustomForLab(
                override val reference        : LocalizedString,
                override val type_of_appliance: TypeOfAppliance,
                emissions_values              : EmissionsAndEfficiencyValues,
                pn_reduced                    : HeatOutputReduced,
                dimensions                    : Dimensions,
                glass_area                    : GlassArea,
                height_of_lowest_opening      : Length
            ) extends Traditional {
                override val firebox_type: Locale ?=> String = I18N.firebox_names.custom_lab_tested
                override val min_load = MinLoad.HalfOfMaxLoad.makeWithoutValue

                override def co2_dry_nominal: σ_CO2         = co2_dry_nominal_default
                override def co2_dry_lowest : Option[σ_CO2] = None

                override def nominal_load = None
                override def max_load     = None

                override def showAsTableRows: Locale ?=> Seq[Seq[String]] =
                    (I18N.firebox.typ                                              :: "" :: I18N.firebox_names.custom_lab_tested :: Nil) ::
                        (I18N.firebox.traditional.firebox_floor_shape              :: "" :: dimensions.base.showP                :: Nil) ::
                        (I18N.firebox.firebox_height_H                             :: "" :: dimensions.height.to_cm.showP        :: Nil) ::
                        (I18N.en15544.terms_xtra.height_of_the_lowest_opening.name :: "" :: height_of_lowest_opening.to_cm.showP :: Nil) ::
                        (I18N.firebox.traditional.glass_surface_area               :: "" :: glass_area.showP                     :: Nil) ::
                        Nil
            }

            object CustomForLab:
                given showAsTable: Locale => ShowAsTable[CustomForLab] =
                    ShowAsTable.mkLightFor(I18N.headers.firebox_description)(_.showAsTableRows)

        /** Placeholder branch for certified designs (e.g. Ecolabeled, AFPMA_PRSE). */
        trait CertifiedDesign extends Firebox_15544

    end Firebox_15544

    case class Outputs(
        technicalSpecs        : TechnicalSpecficiations,
        pipesResult_15544     : PipesResult_15544_VNelMcalcErr,
        reference_temperatures: ReferenceTemperatures,
        efficiencies_values   : EfficienciesValues
        // pressureRequirement_EN15544: VNelString[PressureRequirement],
        // estimated_output_temperatures: EstimatedOutputTemperatures,
        // flue_gas_triple_of_variates: VNelString[FlueGasTripleOfVariates],
    )

    object Outputs:
        case class TechnicalSpecficiations(
            P_n                      : P_n,
            t_n                      : t_n,
            m_B                      : m_B,
            m_B_min                  : Option[m_B_min],
            n_min                    : n_min,
            facing_type              : FacingType,
            innerConstructionMaterial: InnerConstructionMaterial
        )

end std
