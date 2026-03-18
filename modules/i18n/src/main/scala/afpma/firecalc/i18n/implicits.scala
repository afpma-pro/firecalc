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
    given Decoder[I18nData.AddElement]                                                                           = deriveDecoder[I18nData.AddElement]
    given Decoder[I18nData.Address]                                                                              = deriveDecoder[I18nData.Address]
    given Decoder[I18nData.AppendLayerDescr]                                                                     = deriveDecoder[I18nData.AppendLayerDescr]
    given Decoder[I18nData.BuilderErrors]                                                                        = deriveDecoder[I18nData.BuilderErrors]
    given Decoder[I18nData.Customer]                                                                             = deriveDecoder[I18nData.Customer]
    given Decoder[I18nData.Errors]                                                                               = deriveDecoder[I18nData.Errors]
    given Decoder[I18nData.Firebox_15544]                                                                        = deriveDecoder[I18nData.Firebox_15544]
    given Decoder[I18nData.Firebox_15544.AFPMA_PRSE]                                                             = deriveDecoder[I18nData.Firebox_15544.AFPMA_PRSE]
    given Decoder[I18nData.Firebox_15544.Ecolabeled]                                                             = deriveDecoder[I18nData.Firebox_15544.Ecolabeled]
    given Decoder[I18nData.Firebox_15544.Tested]                                                                 = deriveDecoder[I18nData.Firebox_15544.Tested]
    given Decoder[I18nData.Firebox_15544.Traditional]                                                            = deriveDecoder[I18nData.Firebox_15544.Traditional]
    given Decoder[I18nData.Firebox_15544.SingleTested]                                                           = deriveDecoder[I18nData.Firebox_15544.SingleTested]
    given Decoder[I18nData.Firebox_15544.Door15aFirebox]                                                         = deriveDecoder[I18nData.Firebox_15544.Door15aFirebox]
    given Decoder[I18nData.PolluantNames]                                                                        = deriveDecoder[I18nData.PolluantNames]
    given Decoder[I18nData.FireboxNames]                                                                         = deriveDecoder[I18nData.FireboxNames]
    given Decoder[I18nData.FacingType]                                                                           = deriveDecoder[I18nData.FacingType]
    given Decoder[I18nData.EmissionsAndEfficiencyValues]                                                         = deriveDecoder[I18nData.EmissionsAndEfficiencyValues]
    given Decoder[I18nData.EN13384]                                                                              = deriveDecoder[I18nData.EN13384]
    given Decoder[I18nData.EN13384.AirSpaceDetailed]                                                             = deriveDecoder[I18nData.EN13384.AirSpaceDetailed]
    given Decoder[I18nData.EN13384.AirSpaceDetailed.VentilDirection]                                             =
        deriveDecoder[I18nData.EN13384.AirSpaceDetailed.VentilDirection]
    given Decoder[I18nData.EN13384.AirSpaceDetailed.VentilOpenings]                                              =
        deriveDecoder[I18nData.EN13384.AirSpaceDetailed.VentilOpenings]
    given Decoder[I18nData.EN13384.AmbiantAirTemperature]                                                        = deriveDecoder[I18nData.EN13384.AmbiantAirTemperature]
    given Decoder[I18nData.EN13384.AmbiantAirTemperatures]                                                       = deriveDecoder[I18nData.EN13384.AmbiantAirTemperatures]
    given Decoder[I18nData.EN13384.Requirements]                                                                 = deriveDecoder[I18nData.EN13384.Requirements]
    given Decoder[I18nData.EN13384_Materials]                                                                    = deriveDecoder[I18nData.EN13384_Materials]
    given Decoder[I18nData.EN13384_Terms]                                                                        = deriveDecoder[I18nData.EN13384_Terms]
    given Decoder[I18nData.EN13384_Errors]                                                                       = deriveDecoder[I18nData.EN13384_Errors]
    given Decoder[I18nData.EN15544]                                                                              = deriveDecoder[I18nData.EN15544]
    given Decoder[I18nData.EN15544_Materials]                                                                    = deriveDecoder[I18nData.EN15544_Materials]
    given Decoder[I18nData.EN15544_PressureRequirements]                                                         = deriveDecoder[I18nData.EN15544_PressureRequirements]
    given Decoder[I18nData.EN15544_TermDef]                                                                      = deriveDecoder[I18nData.EN15544_TermDef]
    given Decoder[I18nData.EN15544_Terms]                                                                        = deriveDecoder[I18nData.EN15544_Terms]
    given Decoder[I18nData.EN15544_Terms_Xtra]                                                                   = deriveDecoder[I18nData.EN15544_Terms_Xtra]
    given Decoder[I18nData.EN15544_Errors]                                                                       = deriveDecoder[I18nData.EN15544_Errors]
    given Decoder[I18nData.EN16510]                                                                              = deriveDecoder[I18nData.EN16510]
    given Decoder[I18nData.Headers]                                                                              = deriveDecoder[I18nData.Headers]
    given Decoder[I18nData.HeatingAppliance]                                                                     = deriveDecoder[I18nData.HeatingAppliance]
    given Decoder[I18nData.HeatingAppliance.FlueGas]                                                             = deriveDecoder[I18nData.HeatingAppliance.FlueGas]
    given Decoder[I18nData.HeatingAppliance.Powers]                                                              = deriveDecoder[I18nData.HeatingAppliance.Powers]
    given Decoder[I18nData.HeatingAppliance.Temperatures]                                                        = deriveDecoder[I18nData.HeatingAppliance.Temperatures]
    given Decoder[I18nData.HeatingAppliance.MassFlows]                                                           = deriveDecoder[I18nData.HeatingAppliance.MassFlows]
    given Decoder[I18nData.HeatingAppliance.Pressures]                                                           = deriveDecoder[I18nData.HeatingAppliance.Pressures]
    given Decoder[I18nData.HeatingAppliance.VolumeFlows]                                                         = deriveDecoder[I18nData.HeatingAppliance.VolumeFlows]
    given Decoder[I18nData.Inputs_Error]                                                                         = deriveDecoder[I18nData.Inputs_Error]
    given Decoder[I18nData.Inputs_Error.InvalidTypeOfAppliance]                                                  =
        deriveDecoder[I18nData.Inputs_Error.InvalidTypeOfAppliance]
    given Decoder[I18nData.LocalConditions]                                                                      = deriveDecoder[I18nData.LocalConditions]
    given Decoder[I18nData.LocalConditions.ChimneyTermination]                                                   =
        deriveDecoder[I18nData.LocalConditions.ChimneyTermination]
    given Decoder[I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof]                             =
        deriveDecoder[I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof]
    given Decoder[I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.ChimneyHeightAboveRidgeline] =
        deriveDecoder[I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.ChimneyHeightAboveRidgeline]
    given Decoder[
        I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgeline
    ]                                                                                                            = deriveDecoder[
        I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgeline
    ]
    given Decoder[I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.Slope]                       =
        deriveDecoder[I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.Slope]
    given Decoder[
        I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.OutsideAirIntakeAndChimneyLocations
    ]                                                                                                            = deriveDecoder[
        I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.OutsideAirIntakeAndChimneyLocations
    ]
    given Decoder[
        I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgelineBis
    ]                                                                                                            = deriveDecoder[
        I18nData.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgelineBis
    ]
    given Decoder[I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings]                                 =
        deriveDecoder[I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings]
    given Decoder[
        I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings.HorizontalDistanceBetweenChimneyAndAdjacentBuildings
    ]                                                                                                            = deriveDecoder[
        I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings.HorizontalDistanceBetweenChimneyAndAdjacentBuildings
    ]
    given Decoder[
        I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings.HorizontalAngleBetweenChimneyAndAdjacentBuildings
    ]                                                                                                            = deriveDecoder[
        I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings.HorizontalAngleBetweenChimneyAndAdjacentBuildings
    ]
    given Decoder[
        I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings.VerticalAngleBetweenChimneyAndAdjacentBuildings
    ]                                                                                                            = deriveDecoder[
        I18nData.LocalConditions.ChimneyTermination.AdjacentBuildings.VerticalAngleBetweenChimneyAndAdjacentBuildings
    ]
    given Decoder[I18nData.LocalRegulations]                                                                     = deriveDecoder[I18nData.LocalRegulations]
    given Decoder[I18nData.MecaFlu]                                                                              = deriveDecoder[I18nData.MecaFlu]
    given Decoder[I18nData.MecaFlu_Errors]                                                                       = deriveDecoder[I18nData.MecaFlu_Errors]
    given Decoder[I18nData.Panels]                                                                               = deriveDecoder[I18nData.Panels]
    given Decoder[I18nData.PipeLocation]                                                                         = deriveDecoder[I18nData.PipeLocation]
    given Decoder[I18nData.PipeShape]                                                                            = deriveDecoder[I18nData.PipeShape]
    given Decoder[I18nData.PipeType]                                                                             = deriveDecoder[I18nData.PipeType]
    given Decoder[I18nData.HeatOutputReduced]                                                                    = deriveDecoder[I18nData.HeatOutputReduced]
    given Decoder[I18nData.ProjectDescription]                                                                   = deriveDecoder[I18nData.ProjectDescription]
    given Decoder[I18nData.Reports]                                                                              = deriveDecoder[I18nData.Reports]
    given Decoder[I18nData.Reports.Headings]                                                                     = deriveDecoder[I18nData.Reports.Headings]
    given Decoder[I18nData.Reports.Document]                                                                     = deriveDecoder[I18nData.Reports.Document]
    given Decoder[I18nData.SetProp]                                                                              = deriveDecoder[I18nData.SetProp]
    given Decoder[I18nData.TechnicalSpecficiations]                                                              = deriveDecoder[I18nData.TechnicalSpecficiations]
    given Decoder[I18nData.Terms]                                                                                = deriveDecoder[I18nData.Terms]
    given Decoder[I18nData.TypeOfAppliance]                                                                      = deriveDecoder[I18nData.TypeOfAppliance]
    given Decoder[I18nData.TypeOfLoad]                                                                           = deriveDecoder[I18nData.TypeOfLoad]
    given Decoder[I18nData.Units]                                                                                = deriveDecoder[I18nData.Units]
    given Decoder[I18nData.AreaHeatingStatus]                                                                    = deriveDecoder[I18nData.AreaHeatingStatus]
    given Decoder[I18nData.Warnings]                                                                             = deriveDecoder[I18nData.Warnings]
    given Decoder[I18nData.TestReportI18n]                                                                     = deriveDecoder[I18nData.TestReportI18n]
    given Decoder[I18nData.TestEmissionValueI18n]                                                                = deriveDecoder[I18nData.TestEmissionValueI18n]
    given Decoder[I18nData.EmissionValuesI18n]                                                                   = deriveDecoder[I18nData.EmissionValuesI18n]

    given Decoder[I18nData.IncrementalValidation]                       = deriveDecoder[I18nData.IncrementalValidation]
    given Decoder[I18nData.IncrementalValidation.NotDefinedYet]         =
        deriveDecoder[I18nData.IncrementalValidation.NotDefinedYet]
    given Decoder[I18nData.IncrementalValidation.PropertyMustBeSet]     =
        deriveDecoder[I18nData.IncrementalValidation.PropertyMustBeSet]
    given Decoder[I18nData.IncrementalValidation.PropertyMustBeDefined] =
        deriveDecoder[I18nData.IncrementalValidation.PropertyMustBeDefined]
    given Decoder[I18nData.IncrementalValidation.Prerequisites]         =
        deriveDecoder[I18nData.IncrementalValidation.Prerequisites]
    given Decoder[I18nData.IncrementalValidation.Conflicts]             = deriveDecoder[I18nData.IncrementalValidation.Conflicts]

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
