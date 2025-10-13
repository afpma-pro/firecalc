/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.labo

import cats.syntax.all.*

import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Application.LabConditions
import afpma.firecalc.engine.impl.en15544.mce.*
import afpma.firecalc.engine.models.en13384.typedefs.T_L_override
import afpma.firecalc.engine.models.en15544.std.Inputs_EN15544_MCE
import afpma.firecalc.engine.wood_combustion.WoodCombustionAlg
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Alg

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

export EN15544_Labo_Application.AmbiantAir_Temperatures

class EN15544_Labo_Formulas(
    override val net_calorific_value_of_wet_wood: HeatCapacity,
    override val net_calorific_value_of_dry_wood: HeatCapacity,
)(labConditions: LabConditions) extends EN15544_MCE_Formulas(net_calorific_value_of_wet_wood, net_calorific_value_of_dry_wood):
    override lazy val t_BR_default       = labConditions.firebox_mean_temp
    override lazy val t_burnout_default  = labConditions.firebox_output_temp

object EN15544_Labo_Application:

    case class AmbiantAir_Temperatures(
        EnterreExterieur    : TCelsius,
        EnterreInterieur    : TCelsius,
        AirDansLePoele      : TCelsius,
        Foyer               : TCelsius,
        Accumulateur        : TCelsius,
        DansLaPieceDuPoele  : TCelsius,
        Chauffee            : TCelsius,
        NonChauffee         : TCelsius,
        Exterieure          : TCelsius,
    )

    trait SpecificPipeLocations:
        
        def ambiant_air_temperatures: AmbiantAir_Temperatures
        object Area:
            val EnterreExterieur    = CustomArea.sameTuForAllConditions("EnterreExterieur"    , NotHeated    , inside = false, ambiant_air_temperatures.EnterreExterieur)
            val EnterreInterieur    = CustomArea.sameTuForAllConditions("EnterreInterieur"    , NotHeated    , inside = true , ambiant_air_temperatures.EnterreInterieur)
            val AirDansLePoele      = CustomArea.sameTuForAllConditions("AirDansLePoele"      , Heated       , inside = true , ambiant_air_temperatures.AirDansLePoele)
            val Foyer               = CustomArea.sameTuForAllConditions("Foyer"               , Heated       , inside = true , ambiant_air_temperatures.Foyer)
            val Accumulateur        = CustomArea.sameTuForAllConditions("Accumulateur"        , Heated       , inside = true , ambiant_air_temperatures.Accumulateur)
            val DansLaPieceDuPoele  = CustomArea.sameTuForAllConditions("DansLaPieceDuPoele"  , Heated       , inside = true , ambiant_air_temperatures.DansLaPieceDuPoele)
            val Chauffee            = CustomArea.sameTuForAllConditions("Chauffee"            , Heated       , inside = true , ambiant_air_temperatures.Chauffee)
            val NonChauffee         = CustomArea.sameTuForAllConditions("NonChauffee"         , NotHeated    , inside = true , ambiant_air_temperatures.NonChauffee)
            val Exterieure          = CustomArea.sameTuForAllConditions("Exterieure"          , NotHeated    , inside = false, ambiant_air_temperatures.Exterieure)

    trait LabConditions extends SpecificPipeLocations:
        def exterior_air_pressure: Pressure
        def exterior_air_temperature: TCelsius
        def exterior_air_rel_hum: Percentage

        final def exterior_air = afpma.firecalc.engine.wood_combustion.ExteriorAir(
            exterior_air_temperature, 
            exterior_air_rel_hum, 
            exterior_air_pressure
        )
        
        def firebox_mean_temp: TCelsius
        def firebox_output_temp: TCelsius

        val Refractory_Bricks: Roughness = 3.mm
        val Tube_PVC: Roughness = 1.5.mm

class EN15544_Labo_Application(
    f: EN15544_Labo_Formulas,
    bs845: BS845_Alg,
    wComb: WoodCombustionAlg
)(
    inputs: Inputs_EN15544_MCE
)(
    labConditions: LabConditions
)
    extends EN15544_MCE_Application(f, bs845, wComb)(inputs):

    // TODO: facteur de correction de αi
    // TODO: constante de correction de αi
    // TODO: H2O fumée ?? facteur de correction = 1 signifie qu'il n'est pas utilisé ou ne corrige rien. Selon discussion du 20/9/24

    override final lazy val en13384_p_L_override = labConditions.exterior_air_pressure.some
    override final lazy val en13384_T_L_override = T_L_override.forTCelsius(
        whenDraftMinOrDraftMax = labConditions.exterior_air_temperature
    )

