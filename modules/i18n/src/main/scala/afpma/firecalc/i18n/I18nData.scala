/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import afpma.firecalc.i18n.I18nData.*

import io.taig.babel.*

trait LocalizedAlg:
    val language: Language
    given Locale = Locale(language)

type Localized[A] = Locale ?=> A
object Localized:
    extension [A](la: Localized[A]) def map[B](f: A => B): Localized[B] = f(la)

case class LocalizedString(f: Locale => String) {
    def map (g      : String => String): LocalizedString = LocalizedString(f andThen g)
    def show(using l: Locale          ): String          = f(l)
}
object LocalizedString:
    def from(ls: Locale ?=> String) = LocalizedString(loc => ls(using loc))

final case class I18nData(
    add_element                    : AddElement,
    address                        : Address,
    append_layer_descr             : AppendLayerDescr,
    builder_errors                 : BuilderErrors,
    customer                       : Customer,
    firebox                        : Firebox_15544,
    firebox_names                  : FireboxNames,
    draft_min                      : String,
    draft_max                      : String,
    emissions_and_efficiency_values: EmissionsAndEfficiencyValues,
    en13384                        : EN13384,
    en15544                        : EN15544,
    en15544_errors                 : EN15544_Errors,
    topology_errors                : TopologyErrors,
    en16510                        : EN16510,
    direction_badge                : DirectionBadge,
    errors                         : Errors,
    facing_type                    : FacingType,
    forbidden_dto                  : ForbiddenDto,
    headers                        : Headers,
    heat_output_reduced            : HeatOutputReduced,
    heating_appliance              : HeatingAppliance,
    incremental_validation         : IncrementalValidation,
    inputs_data                    : String,
    inputs_error                   : Inputs_Error,
    local_conditions               : LocalConditions,
    local_regulations              : LocalRegulations,
    mecaflu                        : MecaFlu,
    min_load                       : MinLoad,
    no                             : String,
    panels                         : Panels,
    pipe_location                  : PipeLocation,
    pipe_type                      : PipeType,
    pollutant_names                : PolluantNames,
    pressure_requirements          : String,
    project_description            : ProjectDescription,
    set_prop                       : SetProp,
    split_merge                    : SplitMerge,
    stove_params                   : String,
    subtotal                       : String,
    technical_specifications       : TechnicalSpecficiations,
    terms                          : Terms,
    total                          : String,
    type_of_appliance              : TypeOfAppliance,
    type_of_load                   : TypeOfLoad,
    units                          : Units,
    yes                            : String,
    area_name                      : String,
    area_heating_status            : AreaHeatingStatus,
    reports                        : Reports,
    warnings                       : Warnings,
    not_applicable_short           : String,
    not_defined                    : String,
    not_respected                  : String,
    missing_data                   : String,
    test_report                    : TestReportI18n,
    test_emission_value            : TestEmissionValueI18n,
    emission_values                : EmissionValuesI18n,
    country_names                  : CountryNames
)

object I18nData:

    // ── Common1 (flat types) ─────────────────────────────────────────
    type DirectionBadge          = I18nData_Common1.DirectionBadge
    type AddElement              = I18nData_Common1.AddElement
    type Address                 = I18nData_Common1.Address
    type AppendLayerDescr        = I18nData_Common1.AppendLayerDescr
    type Customer                = I18nData_Common1.Customer
    type Errors                  = I18nData_Common1.Errors
    type Headers                 = I18nData_Common1.Headers
    type HeatOutputReduced       = I18nData_Common1.HeatOutputReduced
    type Panels                  = I18nData_Common1.Panels
    type SplitMerge              = I18nData_Common1.SplitMerge
    type TechnicalSpecficiations = I18nData_Common1.TechnicalSpecficiations
    type Terms                   = I18nData_Common1.Terms

    // ── Common2 (flat types) ─────────────────────────────────────────
    type FireboxNames                 = I18nData_Common2.FireboxNames
    type PolluantNames                = I18nData_Common2.PolluantNames
    type FacingType                   = I18nData_Common2.FacingType
    type EmissionsAndEfficiencyValues = I18nData_Common2.EmissionsAndEfficiencyValues
    type PipeShape                    = I18nData_Common2.PipeShape
    type PipeLocation                 = I18nData_Common2.PipeLocation
    type PipeType                     = I18nData_Common2.PipeType
    type ProjectDescription           = I18nData_Common2.ProjectDescription
    type LocalRegulations             = I18nData_Common2.LocalRegulations
    type MinLoad                      = I18nData_Common2.MinLoad
    type TypeOfAppliance              = I18nData_Common2.TypeOfAppliance
    type TypeOfLoad                   = I18nData_Common2.TypeOfLoad
    type Units                        = I18nData_Common2.Units
    type AreaHeatingStatus            = I18nData_Common2.AreaHeatingStatus

    // ── Common3 (flat types) ─────────────────────────────────────────
    type BuilderErrors         = I18nData_Common3.BuilderErrors
    type ForbiddenDto          = I18nData_Common3.ForbiddenDto
    type TopologyErrors        = I18nData_Common3.TopologyErrors
    type EN16510               = I18nData_Common3.EN16510
    type Warnings              = I18nData_Common3.Warnings
    type TestReportI18n        = I18nData_Common3.TestReportI18n
    type TestEmissionValueI18n = I18nData_Common3.TestEmissionValueI18n
    type EmissionValuesI18n    = I18nData_Common3.EmissionValuesI18n
    type CountryNames          = I18nData_Common3.CountryNames
    type MecaFlu               = I18nData_Common3.MecaFlu
    type MecaFlu_Errors        = I18nData_Common3.MecaFlu_Errors
    type EN15544_Errors        = I18nData_Common3.EN15544_Errors

    // ── Firebox (nested types) ───────────────────────────────────────
    type Firebox_15544 = I18nData_Firebox.Firebox_15544
    val Firebox_15544Obj = I18nData_Firebox.Firebox_15544

    // ── EN13384 (nested types) ───────────────────────────────────────
    type EN13384 = I18nData_EN13384.EN13384
    val EN13384Obj = I18nData_EN13384.EN13384
    type EN13384_Materials = I18nData_EN13384.EN13384_Materials
    type EN13384_Terms     = I18nData_EN13384.EN13384_Terms
    type EN13384_Errors    = I18nData_EN13384.EN13384_Errors

    // ── EN15544 (flat types with underscored names) ──────────────────
    type EN15544                      = I18nData_EN15544.EN15544
    type EN15544_Materials            = I18nData_EN15544.EN15544_Materials
    type EN15544_PressureRequirements = I18nData_EN15544.EN15544_PressureRequirements
    type EN15544_TermDef              = I18nData_EN15544.EN15544_TermDef
    type EN15544_Terms                = I18nData_EN15544.EN15544_Terms
    type EN15544_Terms_Xtra           = I18nData_EN15544.EN15544_Terms_Xtra

    // ── LocalConditions (deeply nested types) ────────────────────────
    type LocalConditions = I18nData_LocalConditions.LocalConditions
    val LocalConditionsObj = I18nData_LocalConditions.LocalConditions

    // ── HeatingAppliance (nested types) ──────────────────────────────
    type HeatingAppliance = I18nData_HeatingAppliance.HeatingAppliance
    val HeatingApplianceObj = I18nData_HeatingAppliance.HeatingAppliance

    // ── IncrementalValidation (nested types) ─────────────────────────
    type IncrementalValidation = I18nData_IncrementalValidation.IncrementalValidation
    val IncrementalValidationObj = I18nData_IncrementalValidation.IncrementalValidation

    // ── Reports (nested types) ───────────────────────────────────────
    type Reports = I18nData_Reports.Reports
    val ReportsObj = I18nData_Reports.Reports

    // ── SetProp (nested types) ───────────────────────────────────────
    type SetProp = I18nData_SetProp.SetProp
    val SetPropObj = I18nData_SetProp.SetProp

    // ── InputsError (nested types) ───────────────────────────────────
    type Inputs_Error = I18nData_InputsError.Inputs_Error
    val Inputs_ErrorObj = I18nData_InputsError.Inputs_Error
