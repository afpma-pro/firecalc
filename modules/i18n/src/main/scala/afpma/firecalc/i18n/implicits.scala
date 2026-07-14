/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import afpma.firecalc.i18n.customloader.CustomLoader
import afpma.firecalc.i18n.utils.macros

import cats.Show

import io.taig.babel.*
import io.taig.babel.DerivedDecoder.derivedProduct
import io.taig.babel.generic.semiauto.deriveDecoder

inline def I(f: I18nData => String) = macros.tPath[I18nData](f)

type ShowUsingLocale[A] = Locale ?=> Show[A]
object ShowUsingLocale:
    def apply[A](using ev: ShowUsingLocale[A]): ShowUsingLocale[A] = ev

def showUsingLocale[A](f: Locale ?=> A => String): ShowUsingLocale[A] =
    Show.show[A](a => f(a))

object implicits {

    // helpers so that inline is split and works (otherwise semiauto derivation of the big I18nData case class fails)
    given Decoder[I18nData.DirectionBadge]                                                                          = deriveDecoder[I18nData.DirectionBadge]
    given Decoder[I18nData.AddElement]                                                                              = deriveDecoder[I18nData.AddElement]
    given Decoder[I18nData.Address]                                                                                 = deriveDecoder[I18nData.Address]
    given Decoder[I18nData.AppendLayerDescr]                                                                        = deriveDecoder[I18nData.AppendLayerDescr]
    given Decoder[I18nData.BuilderErrors]                                                                           = deriveDecoder[I18nData.BuilderErrors]
    given Decoder[I18nData.Customer]                                                                                = deriveDecoder[I18nData.Customer]
    given Decoder[I18nData.Errors]                                                                                  = deriveDecoder[I18nData.Errors]
    given Decoder[I18nData.Firebox_15544]                                                                           = deriveDecoder[I18nData.Firebox_15544]
    given Decoder[I18nData.Firebox_15544Obj.AFPMA_PRSE]                                                             = deriveDecoder[I18nData.Firebox_15544Obj.AFPMA_PRSE]
    given Decoder[I18nData.Firebox_15544Obj.Ecolabeled]                                                             = deriveDecoder[I18nData.Firebox_15544Obj.Ecolabeled]
    given Decoder[I18nData.Firebox_15544Obj.Tested]                                                                 = deriveDecoder[I18nData.Firebox_15544Obj.Tested]
    given Decoder[I18nData.Firebox_15544Obj.Traditional]                                                            = deriveDecoder[I18nData.Firebox_15544Obj.Traditional]
    given Decoder[I18nData.Firebox_15544Obj.SingleTested]                                                           = deriveDecoder[I18nData.Firebox_15544Obj.SingleTested]
    given Decoder[I18nData.Firebox_15544Obj.Door15aFirebox]                                                         = deriveDecoder[I18nData.Firebox_15544Obj.Door15aFirebox]
    given Decoder[I18nData.PolluantNames]                                                                           = deriveDecoder[I18nData.PolluantNames]
    given Decoder[I18nData.FireboxNames]                                                                            = deriveDecoder[I18nData.FireboxNames]
    given Decoder[I18nData.FacingType]                                                                              = deriveDecoder[I18nData.FacingType]
    given Decoder[I18nData.EmissionsAndEfficiencyValues]                                                            = deriveDecoder[I18nData.EmissionsAndEfficiencyValues]
    given Decoder[I18nData.EN13384]                                                                                 = deriveDecoder[I18nData.EN13384]
    given Decoder[I18nData.EN13384Obj.AirSpaceDetailed]                                                             = deriveDecoder[I18nData.EN13384Obj.AirSpaceDetailed]
    given Decoder[I18nData.EN13384Obj.AirSpaceDetailed.VentilDirection]                                             =
        deriveDecoder[I18nData.EN13384Obj.AirSpaceDetailed.VentilDirection]
    given Decoder[I18nData.EN13384Obj.AirSpaceDetailed.VentilOpenings]                                              =
        deriveDecoder[I18nData.EN13384Obj.AirSpaceDetailed.VentilOpenings]
    given Decoder[I18nData.EN13384Obj.AmbiantAirTemperature]                                                        = deriveDecoder[I18nData.EN13384Obj.AmbiantAirTemperature]
    given Decoder[I18nData.EN13384Obj.AmbiantAirTemperatures]                                                       =
        deriveDecoder[I18nData.EN13384Obj.AmbiantAirTemperatures]
    given Decoder[I18nData.EN13384Obj.Requirements]                                                                 = deriveDecoder[I18nData.EN13384Obj.Requirements]
    given Decoder[I18nData.EN13384_Materials]                                                                       = deriveDecoder[I18nData.EN13384_Materials]
    given Decoder[I18nData.EN13384_Terms]                                                                           = deriveDecoder[I18nData.EN13384_Terms]
    given Decoder[I18nData.EN13384_Errors]                                                                          = deriveDecoder[I18nData.EN13384_Errors]
    given Decoder[I18nData.EN15544]                                                                                 = deriveDecoder[I18nData.EN15544]
    given Decoder[I18nData.EN15544_Materials]                                                                       = deriveDecoder[I18nData.EN15544_Materials]
    given Decoder[I18nData.EN15544_PressureRequirements]                                                            = deriveDecoder[I18nData.EN15544_PressureRequirements]
    given Decoder[I18nData.EN15544_TermDef]                                                                         = deriveDecoder[I18nData.EN15544_TermDef]
    given Decoder[I18nData.EN15544_Terms]                                                                           = deriveDecoder[I18nData.EN15544_Terms]
    given Decoder[I18nData.EN15544_Terms_Xtra]                                                                      = deriveDecoder[I18nData.EN15544_Terms_Xtra]
    given Decoder[I18nData.EN15544_Errors]                                                                          = deriveDecoder[I18nData.EN15544_Errors]
    given Decoder[I18nData.EN16510]                                                                                 = deriveDecoder[I18nData.EN16510]
    given Decoder[I18nData.Headers]                                                                                 = deriveDecoder[I18nData.Headers]
    given Decoder[I18nData.HeatingAppliance]                                                                        = deriveDecoder[I18nData.HeatingAppliance]
    given Decoder[I18nData.HeatingApplianceObj.FlueGas]                                                             = deriveDecoder[I18nData.HeatingApplianceObj.FlueGas]
    given Decoder[I18nData.HeatingApplianceObj.Powers]                                                              = deriveDecoder[I18nData.HeatingApplianceObj.Powers]
    given Decoder[I18nData.HeatingApplianceObj.Temperatures]                                                        = deriveDecoder[I18nData.HeatingApplianceObj.Temperatures]
    given Decoder[I18nData.HeatingApplianceObj.MassFlows]                                                           = deriveDecoder[I18nData.HeatingApplianceObj.MassFlows]
    given Decoder[I18nData.HeatingApplianceObj.Pressures]                                                           = deriveDecoder[I18nData.HeatingApplianceObj.Pressures]
    given Decoder[I18nData.HeatingApplianceObj.VolumeFlows]                                                         = deriveDecoder[I18nData.HeatingApplianceObj.VolumeFlows]
    given Decoder[I18nData.Inputs_Error]                                                                            = deriveDecoder[I18nData.Inputs_Error]
    given Decoder[I18nData.Inputs_ErrorObj.InvalidTypeOfAppliance]                                                  =
        deriveDecoder[I18nData.Inputs_ErrorObj.InvalidTypeOfAppliance]
    given Decoder[I18nData.LocalConditions]                                                                         = deriveDecoder[I18nData.LocalConditions]
    given Decoder[I18nData.LocalConditionsObj.ChimneyTermination]                                                   =
        deriveDecoder[I18nData.LocalConditionsObj.ChimneyTermination]
    given Decoder[I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof]                             =
        deriveDecoder[I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof]
    given Decoder[I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.ChimneyHeightAboveRidgeline] =
        deriveDecoder[I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.ChimneyHeightAboveRidgeline]
    given Decoder[
        I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgeline
    ]                                                                                                               = deriveDecoder[
        I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgeline
    ]
    given Decoder[I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.Slope]                       =
        deriveDecoder[I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.Slope]
    given Decoder[
        I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.OutsideAirIntakeAndChimneyLocations
    ]                                                                                                               = deriveDecoder[
        I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.OutsideAirIntakeAndChimneyLocations
    ]
    given Decoder[
        I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgelineBis
    ]                                                                                                               = deriveDecoder[
        I18nData.LocalConditionsObj.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgelineBis
    ]
    given Decoder[I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings]                                 =
        deriveDecoder[I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings]
    given Decoder[
        I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings.HorizontalDistanceBetweenChimneyAndAdjacentBuildings
    ]                                                                                                               = deriveDecoder[
        I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings.HorizontalDistanceBetweenChimneyAndAdjacentBuildings
    ]
    given Decoder[
        I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings.HorizontalAngleBetweenChimneyAndAdjacentBuildings
    ]                                                                                                               = deriveDecoder[
        I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings.HorizontalAngleBetweenChimneyAndAdjacentBuildings
    ]
    given Decoder[
        I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings.VerticalAngleBetweenChimneyAndAdjacentBuildings
    ]                                                                                                               = deriveDecoder[
        I18nData.LocalConditionsObj.ChimneyTermination.AdjacentBuildings.VerticalAngleBetweenChimneyAndAdjacentBuildings
    ]
    given Decoder[I18nData.LocalRegulations]                                                                        = deriveDecoder[I18nData.LocalRegulations]
    given Decoder[I18nData.MecaFlu]                                                                                 = deriveDecoder[I18nData.MecaFlu]
    given Decoder[I18nData.MecaFlu_Errors]                                                                          = deriveDecoder[I18nData.MecaFlu_Errors]
    given Decoder[I18nData.Panels]                                                                                  = deriveDecoder[I18nData.Panels]
    given Decoder[I18nData.PipeLocation]                                                                            = deriveDecoder[I18nData.PipeLocation]
    given Decoder[I18nData.PipeShape]                                                                               = deriveDecoder[I18nData.PipeShape]
    given Decoder[I18nData.PipeType]                                                                                = deriveDecoder[I18nData.PipeType]
    given Decoder[I18nData.HeatOutputReduced]                                                                       = deriveDecoder[I18nData.HeatOutputReduced]
    given Decoder[I18nData.ProjectDescription]                                                                      = deriveDecoder[I18nData.ProjectDescription]
    given Decoder[I18nData.Reports]                                                                                 = deriveDecoder[I18nData.Reports]
    given Decoder[I18nData.ReportsObj.Headings]                                                                     = deriveDecoder[I18nData.ReportsObj.Headings]
    given Decoder[I18nData.ReportsObj.Document]                                                                     = deriveDecoder[I18nData.ReportsObj.Document]
    given Decoder[I18nData.SetProp]                                                                                 = deriveDecoder[I18nData.SetProp]
    given Decoder[I18nData.TechnicalSpecficiations]                                                                 = deriveDecoder[I18nData.TechnicalSpecficiations]
    given Decoder[I18nData.Terms]                                                                                   = deriveDecoder[I18nData.Terms]
    given Decoder[I18nData.TypeOfAppliance]                                                                         = deriveDecoder[I18nData.TypeOfAppliance]
    given Decoder[I18nData.TypeOfLoad]                                                                              = deriveDecoder[I18nData.TypeOfLoad]
    given Decoder[I18nData.Units]                                                                                   = deriveDecoder[I18nData.Units]
    given Decoder[I18nData.AreaHeatingStatus]                                                                       = deriveDecoder[I18nData.AreaHeatingStatus]
    given Decoder[I18nData.Warnings]                                                                                = deriveDecoder[I18nData.Warnings]
    given Decoder[I18nData.TestReportI18n]                                                                          = deriveDecoder[I18nData.TestReportI18n]
    given Decoder[I18nData.TestEmissionValueI18n]                                                                   = deriveDecoder[I18nData.TestEmissionValueI18n]
    given Decoder[I18nData.EmissionValuesI18n]                                                                      = deriveDecoder[I18nData.EmissionValuesI18n]

    given Decoder[I18nData.CountryNames] = deriveDecoder[I18nData.CountryNames]

    given Decoder[I18nData.IncrementalValidation]                          = deriveDecoder[I18nData.IncrementalValidation]
    given Decoder[I18nData.IncrementalValidationObj.NotDefinedYet]         =
        deriveDecoder[I18nData.IncrementalValidationObj.NotDefinedYet]
    given Decoder[I18nData.IncrementalValidationObj.PropertyMustBeSet]     =
        deriveDecoder[I18nData.IncrementalValidationObj.PropertyMustBeSet]
    given Decoder[I18nData.IncrementalValidationObj.PropertyMustBeDefined] =
        deriveDecoder[I18nData.IncrementalValidationObj.PropertyMustBeDefined]
    given Decoder[I18nData.IncrementalValidationObj.Prerequisites]         =
        deriveDecoder[I18nData.IncrementalValidationObj.Prerequisites]
    given Decoder[I18nData.IncrementalValidationObj.Conflicts]             =
        deriveDecoder[I18nData.IncrementalValidationObj.Conflicts]

    given Decoder[I18nData] = deriveDecoder[I18nData]

    given I18N : (loc: Locale) => I18nData = I18Ns(loc)
    given I18Ns: NonEmptyTranslations[I18nData] =
        val ei = Decoder[I18nData].decodeAll(babels)
        ei match {
            case Left(err) => throw err
            case Right(ts) =>
                ts
                    .withFallback(Locales.fr)
                    .getOrElse(
                        throw new IllegalStateException(
                            "Translations for fr missing"
                        )
                    )
        }

    private val babels =
        new CustomLoader(afpma.firecalc.i18n.configs) // file autogenerated using build.sbt
            .load(
                "babel",
                Set(
                    Locales.fr,
                    Locales.en
                )
            )
}
