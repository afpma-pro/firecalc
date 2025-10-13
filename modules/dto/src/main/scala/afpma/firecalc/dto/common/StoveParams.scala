/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import scala.annotation.nowarn

import cats.Show

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.common.StoveParams.SizingMethod
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

import io.taig.babel.Locale
import magnolia1.Transl

@Transl(I(_.stove_params))
case class StoveParams(
    @Transl(I(_.technical_specifications.sizing_method))
    val sizing_method: StoveParams.SizingMethod,
    @Transl(I(_.technical_specifications.maximum_load))
    val maximum_load: Option[QtyD[Kilogram]],
    @Transl(I(_.technical_specifications.nominal_heat_output))
    val nominal_heat_output: Option[QtyD[Kilo * Watt]],
    @Transl(I(_.technical_specifications.heating_cycle))
    val heating_cycle: QtyD[Hour],
    @Transl(I(_.emissions_and_efficiency_values.min_efficiency_full_stove_nominal))
    val min_efficiency: QtyD[Percent],
    @Transl(I(_.technical_specifications.facing_type))
    val facing_type: FacingType,
    @Transl(I(_.technical_specifications.inner_construction_material))
    val inner_construction_material: InnerConstructionMaterial = InnerConstructionMaterial.WithinSpecs,
) {
    def mB_or_pn: Either[QtyD[Kilogram], QtyD[Kilo * Watt]] = sizing_method match
        case SizingMethod.MaxLoad           => Left(maximum_load.get)
        case SizingMethod.NominalHeatOutput => Right(nominal_heat_output.get)

    def mB: Option[Mass] = maximum_load
    def pn: Option[Power] = nominal_heat_output

    def with_mB(mB: Mass): StoveParams = 
        this.copy(maximum_load = Some(mB), nominal_heat_output = None)
    def with_pn(pn: Power): StoveParams = 
        this.copy(maximum_load = None, nominal_heat_output = Some(pn))

    // prevent edit using copy
    private def copy(
        sizing_method: StoveParams.SizingMethod = this.sizing_method,
        @nowarn maximum_load: Option[QtyD[Kilogram]] = this.maximum_load,
        @nowarn nominal_heat_output: Option[QtyD[Kilo * Watt]] = this.nominal_heat_output,
        heating_cycle: QtyD[Hour] = this.heating_cycle,
        min_efficiency: QtyD[Percent] = this.min_efficiency,
        facing_type: FacingType = this.facing_type,
        inner_construction_material: InnerConstructionMaterial = this.inner_construction_material,
    ) = StoveParams(
        sizing_method,
        maximum_load,
        nominal_heat_output,
        heating_cycle,
        min_efficiency,
        facing_type,
        inner_construction_material,
    )
}

object StoveParams:

    enum SizingMethod:
        case MaxLoad
        case NominalHeatOutput

    object SizingMethod:
        given show: Locale => Show[SizingMethod] = Show.show:
            case SizingMethod.MaxLoad           => I18N.technical_specifications.maximum_load
            case SizingMethod.NominalHeatOutput => I18N.technical_specifications.nominal_heat_output

    // prevent default constructor
    private def apply(
        sizing_method: StoveParams.SizingMethod,
        maximum_load: Option[QtyD[Kilogram]],
        nominal_heat_output: Option[QtyD[Kilo * Watt]],
        heating_cycle: QtyD[Hour],
        min_efficiency: QtyD[Percent],
        facing_type: FacingType,
        inner_construction_material: InnerConstructionMaterial = InnerConstructionMaterial.WithinSpecs,
    ): StoveParams = new StoveParams(
        sizing_method,
        maximum_load,
        nominal_heat_output,
        heating_cycle,
        min_efficiency,
        facing_type,
        inner_construction_material,
    )

    def fromMaxLoadAndStoragePeriod(
        maximum_load: QtyD[Kilogram],
        heating_cycle: QtyD[Hour],
        min_efficiency: QtyD[Percent],
        facing_type: FacingType
    ) = StoveParams(SizingMethod.MaxLoad, Some(maximum_load), None, heating_cycle, min_efficiency, facing_type)

    def fromNominalHeatOutput(
        nominal_heat_output: QtyD[Kilo * Watt],
        heating_cycle: QtyD[Hour],
        min_efficiency: QtyD[Percent],
        facing_type: FacingType
    ) = StoveParams(SizingMethod.NominalHeatOutput, None, Some(nominal_heat_output), heating_cycle, min_efficiency, facing_type)

enum FacingType:

    /**
     * Note 1 à l’article : Il s'agit d'une construction sans lame d’air si la distance entre l'enveloppe intérieure et
     * l'enveloppe extérieure est inférieure à 2,5 cm et si au moins 50 % du poêle en faïence ou du poêle en maçonnerie
     * est construit de cette manière.
     */
    case WithAirGap

    case WithoutAirGap

object FacingType:
    given show: Locale => Show[FacingType] = Show.show:
        case FacingType.WithAirGap    => I18N.facing_type.with_air_gap
        case FacingType.WithoutAirGap => I18N.facing_type.without_air_gap

enum InnerConstructionMaterial:
    case WithinSpecs

object InnerConstructionMaterial:
    given show_InnerConstructionMaterial: Locale => Show[InnerConstructionMaterial] = 
        Show.show:
            case InnerConstructionMaterial.WithinSpecs => 
                    I18N.technical_specifications.inner_construction_material_within_specs