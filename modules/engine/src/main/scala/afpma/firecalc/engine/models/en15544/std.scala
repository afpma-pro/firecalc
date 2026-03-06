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

import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.FireboxFormulas
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LocalRegulations.TypeOfAppliance
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.ShowAsTable
import afpma.firecalc.engine.OTypedQtyD


import cats.*
import cats.derived.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog.SB

object std:

    import afpma.firecalc.engine.models.en13384.std.*

    export Firebox_15544.Dimensions
    export Firebox_15544.CertifiedDesign
    export Firebox_15544.Traditional
    export Firebox_15544.AreaCalcMethod
    export Firebox_15544.SingleTested
    export Firebox_15544.Door15aFirebox_Catalog
    export Firebox_15544.Door15aFirebox_Catalog_Example

    object PressureLossCoeff:

        type Err  = afpma.firecalc.engine.standard.PressureLossCoeff_Error
        type GErr = Err // TODO: remove alias

    end PressureLossCoeff

    sealed trait Inputs_15544_Alg extends HasPipeModules_15544Only_Alg:
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

    case class Inputs_15544_Strict(
        localConditions            : LocalConditions,
        en13384NationalAcceptedData: NationalAcceptedData,
        stoveParams                : StoveParams,
        design                     : Design,
        pipes                      : Pipes_15544_Strict
        // wood: Wood,
    ) extends Inputs_15544_Alg
        with HasPipeModules_15544Only_Strict:
        override type Pipes_15544 = Pipes_15544_Strict

    case class Inputs_15544_MCE(
        localConditions                          : LocalConditions,
        en13384NationalAcceptedData              : NationalAcceptedData,
        stoveParams                              : StoveParams,
        design                                   : Design,
        pipes                                    : Pipes_15544_MCE,
        wood                                     : Wood,
        kindOfWood                               : KindOfWood,
        computeWoodCalorificValueUsingComposition: "Yes" | "No",
        combustionDuration                       : Duration,
        fluegas_co2_dry_nominal                  : Percentage,
        fluegas_co2_dry_lowest                   : Option[Percentage],
        fluegas_h2o_perc_vol_nominal             : Option[Percentage],
        fluegas_h2o_perc_vol_lowest              : Option[Percentage],
        massFlows_override                       : HeatingAppliance.MassFlows,
        ext_air_rel_hum_default                  : Percentage
    ) extends Inputs_15544_Alg
        with HasPipeModules_15544Only_MCE:
        override type Pipes_15544 = Pipes_15544_MCE

    case class Design(
        firebox: Firebox_15544
    )

    sealed trait Firebox_15544:
        /** Self-referential type preserving the concrete firebox type.
         *
         * The lower bound `>: this.type` guarantees that `this: Self` holds,
         * so `formulas` and `constraints` can be called with `this` directly.
         * Contravariance on [[FireboxFormulas]] / [[FireboxConstraints]]
         * ensures that a `FireboxFormulas[Firebox_15544]` satisfies
         * `FireboxFormulas[Self]` for any concrete subtype.
         */
        type Self >: this.type <: Firebox_15544

        def firebox_type     : Locale ?=> String // Ecolabeled, 15a, etc...
        def reference        : LocalizedString
        def type_of_appliance: TypeOfAppliance

        def dimensions: Dimensions

        def glass_area: GlassArea

        def height_of_lowest_opening: Length

        def emissions_values: EmissionsAndEfficiencyValues

        def min_load       : MinLoad
        def pn_reduced     : HeatOutputReduced
        def co2_dry_nominal: σ_CO2
        def co2_dry_lowest : Option[σ_CO2]

        def formulas   : FireboxFormulas[Self]
        def constraints: FireboxConstraints[Self]

    object Firebox_15544:

        given showAsTable: Locale => ShowAsTable[Firebox_15544] =
            ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
                import x.*
                val I = I18N.firebox
                (I18N.firebox.typ                                              :: "" :: firebox_type                                      :: Nil) ::
                    (I18N.firebox.ref                                          :: "" :: reference.show                                    :: Nil) ::
                    (I18N.type_of_appliance.descr                              :: "" :: type_of_appliance.show                            :: Nil) ::
                    (I.base_geometry                                           :: "" :: dimensions.base.showP                             :: Nil) ::
                    (I18N.en15544.terms.H_BR.name                              :: "" :: dimensions.height.showP                           :: Nil) ::
                    (I18N.en15544.terms_xtra.height_of_the_lowest_opening.name :: "" :: height_of_lowest_opening.showP                    :: Nil) ::
                    (I.firebox_glass_surface_ratio_below_one_fifth             :: "" :: x.formulas.firebox_glass_surface_ratio_below_one_fifth(x).showP :: Nil) ::
                    (I.glass_area                                              :: "" :: glass_area.showP                                  :: Nil) ::
                    (I18N.en15544.terms.m_B_min.name                           :: "" :: x.min_load.show                                   :: Nil) ::
                    (I18N.en15544.terms.P_n_reduced.name                       :: "" :: x.pn_reduced.show                                 :: Nil) ::
                    (I18N.firebox.tested.co2_perc_by_vol_dry_nominal           :: "" :: x.co2_dry_nominal.showP                           :: Nil) ::
                    (I18N.firebox.tested.co2_perc_by_vol_dry_reduced           :: "" :: x.co2_dry_lowest.showP                            :: Nil) ::
                    Nil

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
            tBurnout                              : TCelsius,
            efficiency_nominal                    : Percentage,
            efficiency_reduced                    : Option[Percentage],
            pn_reduced                            : HeatOutputReduced,
            minimumFuelMass                       : Option[Mass],
            maximumFuelMass                       : Mass,
            airFuelRatio_nominal                  : Dimensionless,
            airFuelRatio_lowest                   : Option[Dimensionless],
            co2_dry_nominal                       : σ_CO2,
            co2_dry_lowest                        : Option[σ_CO2],
            emissions_values                      : EmissionsAndEfficiencyValues
        ) extends Firebox_15544:
            type Self = SingleTested
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

            override def formulas: FireboxFormulas[Self] =
                import afpma.firecalc.engine.impl.en15544.common.fireboxFormulas_Strict
                fireboxFormulas_Strict

            override def constraints: FireboxConstraints[Self] =
                import afpma.firecalc.engine.impl.en15544.instances.singleTestedConstraints
                singleTestedConstraints

        object SingleTested:
            given showAsTable: Locale => ShowAsTable[SingleTested] =
                ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
                    import x.*
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
                type Self = CustomForLab
                override val firebox_type: Locale ?=> String = I18N.firebox_names.custom_lab_tested
                override val min_load = MinLoad.HalfOfMaxLoad.makeWithoutValue

                override def co2_dry_nominal: σ_CO2         = 7.05.percent
                override def co2_dry_lowest : Option[σ_CO2] = None

                override def formulas: FireboxFormulas[Self] =
                    import afpma.firecalc.engine.impl.en15544.common.fireboxFormulas_Strict
                    fireboxFormulas_Strict

                override def constraints: FireboxConstraints[Self] =
                    import afpma.firecalc.engine.impl.en15544.instances.customForLabConstraints
                    customForLabConstraints
            }

            object CustomForLab:
                given showAsTable: Locale => ShowAsTable[CustomForLab] =
                    ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
                        import x.*

                        (I18N.firebox.typ                                              :: "" :: I18N.firebox_names.custom_lab_tested :: Nil) ::
                            (I18N.firebox.traditional.firebox_floor_shape              :: "" :: dimensions.base.showP                :: Nil) ::
                            (I18N.firebox.firebox_height                               :: "" :: dimensions.height.to_cm.showP        :: Nil) ::
                            (I18N.en15544.terms_xtra.height_of_the_lowest_opening.name :: "" :: height_of_lowest_opening.to_cm.showP :: Nil) ::
                            (I18N.firebox.traditional.glass_surface_area               :: "" :: glass_area.showP                     :: Nil) ::
                            Nil

        /** Placeholder branch for certified designs (e.g. Ecolabeled, AFPMA_PRSE). */
        trait CertifiedDesign extends Firebox_15544

        // Just output results from supplier datasheet
        trait Door15aFirebox_Catalog extends Firebox_15544:

            // ── Input data (provided by each catalog entry / database row) ──

            val uniq_id: String
            val mb: Option[Mass]
            val sb: SB

            /** Optional per-entry SB constraint bounds; defaults applied in Door15aCatalogConstraints. */
            val sb_min: Option[SB]
            val sb_max: Option[SB]

            /** Optional per-entry fuel mass constraint bounds; defaults applied in Door15aCatalogConstraints. */
            val mb_min: Option[Mass]
            val mb_max: Option[Mass]

            /** TSV table for pressure-loss interpolation (first col = mb in kg, header cols = sb in cm). */
            val pressure_loss_table_raw: String

            /** The pipe shape the firebox expects at its air intake (e.g. round 200 mm). */
            val expectedAirIntakePipeShape: PipeShape

            // ── Computed from input data ────────────────────────────────

            def pressure_loss: Option[Pressure]

            lazy val factory: Factory

            trait Factory:

                /** The raw TSV string for pressure-loss data. */
                val rawString: String

                lazy val tsv_table: TSVTableString =
                    TSVTableString.fromString(rawString, sep = "\\s+")

                lazy val pressureLossTable: PressureLossTSVTableString =
                    PressureLossTSVTableString(rawString)

                lazy val available_sb_values: List[QtyD[Centimeter]] =
                    pressureLossTable.availableSbValues

                /** Interpolated pressure loss for any (mb, sb) within table bounds. */
                def get_pressure_loss_for_mb_sb(mb: Mass, sb_value: QtyD[Centimeter]): Option[Pressure] =
                    pressureLossTable.interpolate(mb, sb_value)

        object Door15aFirebox_Catalog:
            
            /** supply air slot width (SB) (in cm) */
            type SB = SupplyAirSlotWidth.Type
            object SupplyAirSlotWidth extends OTypedQtyD[Centimeter]:
                def termDef        = TermDef(
                    "SB",
                    "15a firebox"
                )
                def termDefDetails = TermDefDetails(
                    I18N.firebox.door_15a_firebox.sb,
                    I18N.firebox.door_15a_firebox.sb
                )

            given showAsTable: Locale => ShowAsTable[Door15aFirebox_Catalog] =
                ShowAsTable.mkLightFor(I18N.headers.firebox_description): x =>
                    import x.*
                    val I               = I18N.firebox.door_15a_firebox
                    val list            =
                        (I18N.firebox.typ :: ""   :: I18N.firebox_names.door_15a_firebox :: Nil) ::
                            (I.load_size_nominal  :: "mB" :: mb.showP                    :: Nil) ::
                            (I.sb                 :: "SB" :: sb.to_cm.showP              :: Nil) ::
                            Nil
                    list.filter(_.nonEmpty)

        val Door15aFirebox_Catalog_Example: Door15aFirebox_Catalog_DatabaseEntry =
            Door15aFirebox_Catalog_DatabaseEntry(
                uniq_id                    = "Door15aFirebox_Catalog_Example",
                mb                         = None,
                sb                         = 1.6.cm.to_cm,
                dimensions                 = Dimensions(
                    base = Dimensions.Base.Squared(width = 29.cm, depth = 39.cm),
                    height = 100.cm // example, wrong
                ),
                sb_min                     = Some(1.6.cm.to_cm),
                sb_max                     = Some(4.0.cm.to_cm),
                mb_min                     = Some(10.kg),
                mb_max                     = Some(25.kg),
                pressure_loss_table_raw    =
                    """|mb_in_kg/sb_in_cm 1.6     2.4     3.2     4.0
                       |10 4       3       2       1
                       |25 22      20      18      16
                       |""".stripMargin,
                expectedAirIntakePipeShape = PipeShape.Circle(200.mm),
                co2_dry_nominal            = 12.percent,
                co2_dry_lowest             = None,
                emissions_values           = afpma.firecalc.engine.biblio.kov.firebox_emissions.`15A_Combustion_Firebox`,
                glass_area                 = 500.cm2,
                height_of_lowest_opening   = 5.cm,
                pn_reduced                 = HeatOutputReduced.NotDefined,
            )

        case class Door15aFirebox_Catalog_DatabaseEntry(
            uniq_id: String,
            mb: Option[Mass], 
            sb: SB,
            dimensions: Dimensions,
            sb_min: Option[SB],
            sb_max: Option[SB],
            mb_min: Option[Mass],
            mb_max: Option[Mass],
            pressure_loss_table_raw: String,
            expectedAirIntakePipeShape: PipeShape,
            co2_dry_nominal: σ_CO2,
            co2_dry_lowest: Option[σ_CO2],
            emissions_values: EmissionsAndEfficiencyValues,
            glass_area: GlassArea,
            height_of_lowest_opening: Length,
            pn_reduced: HeatOutputReduced,
        )
            extends Door15aFirebox_Catalog:

            type Self = Door15aFirebox_Catalog_DatabaseEntry

            override lazy val pressure_loss = mb.flatMap: mb =>
                factory.get_pressure_loss_for_mb_sb(mb, sb)

            override def firebox_type      = I18N.firebox_names.door_15a_firebox
            override def min_load          = mb_min.fold(MinLoad.NotDefined)(m => MinLoad.FromTypeTest(m))
            override def reference         = LocalizedString(_ => uniq_id)
            override def type_of_appliance = TypeOfAppliance.WoodLogs

            override def formulas: FireboxFormulas[Self] =
                import afpma.firecalc.engine.impl.en15544.instances.door15aCatalogFormulas
                door15aCatalogFormulas

            override def constraints: FireboxConstraints[Self] =
                import afpma.firecalc.engine.impl.en15544.instances.door15aCatalogConstraints
                door15aCatalogConstraints

            lazy val factory = new Factory:
                override val rawString = pressure_loss_table_raw

        // TODO: implement MB 17 specs
        // trait Door15aFirebox_Generic extends Firebox_15544


    end Firebox_15544

    case class Outputs(
        technicalSpecs        : TechnicalSpecficiations,
        pipesResult_15544     : VNelMcalcErr[PipesResult_15544],
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
