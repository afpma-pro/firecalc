/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import java.lang.Exception

import scala.annotation.tailrec

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.std.ThermalResistance
import afpma.firecalc.engine.models.en13384.std.ThermalResistance.*
import afpma.firecalc.engine.models.gtypedefs.D_h
import afpma.firecalc.engine.models.gtypedefs.ThermalConductivity
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.readtable.*

import afpma.firecalc.dto.all.*
import coulomb.syntax.{withUnit as _, *}
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import coulomb.ops.resolution.given


open class `EN13384_1_A1_2019_Formulas`
    extends EN13384_1_A1_2019_Formulas_Alg:

    val doc: Document = Document(
        name = "EN 13384-1+A1:2019",
        date = "2019-07",
        version = "2019-07",
        revision = "2019",
        status = "Current",
        author = "EUROPEAN COMMITTEE FOR STANDARDIZATION"
    )

    import EN13384_1_A1_2019_Formulas.*

    // type definitions
    import afpma.firecalc.engine.models.en13384.typedefs.*

    // Section "5.2"
    // "Pressure Requirements"

    // Section "5.2.1"
    // "Conduits de fumée fonctionnant sous pression négative"

    /**
      * tirage minimal au niveau de l’admission des fumées dans le conduit (voir 5.10), en Pa
      *
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @param P_L pression de la vitesse du vent, en Pa ;
      * @return
      */
    def P_Z(
        P_H: Pressure,
        P_R: Pressure,
        P_L: Pressure,
    ): Pressure =
        P_H - P_R - P_L

    /**
      * tirage minimal requis au niveau de l’admission des fumées dans le conduit, en Pa ;
      *
      * @param P_W tirage minimal de l'appareil à combustion, en Pa ;
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air (voir 5.11.3), en Pa ;
      * @return
      */
    def P_Ze(
        P_W: Pressure,
        P_FV: Pressure,
        P_B: Pressure,
    ): Pressure =
        P_W + P_FV + P_B

    // Section "5.2.2"
    // "Conduits de fumée fonctionnant sous pression positive"

    /**
      * pression positive maximale au niveau de l'admission des fumées dans le conduit, en Pa ;
      *
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @param P_L pression de la vitesse du vent, en Pa ;
      * @return
      */
    def P_ZO(
        P_R: Pressure,
        P_H: Pressure,
        P_L: Pressure,
    ): Pressure =
        P_R - P_H + P_L

    /**
      * pression positive minimale au niveau de l’admission des fumées dans le conduit, en Pa ;
      *
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @return
      */
    def P_ZOmin(
        P_R: Pressure,
        P_H: Pressure,
    ): Pressure =
        P_R - P_H

    // Section "5.3"
    // Exigence relative à la température

    /**
      * limite de température, en K 
      *
      * @param fgCondition flue gas condition (dry or wet)
      * @param T_sp limite de température de condensation des fumées
      * @return
      */
    def T_ig_calc(fgCondition: FlueGasCondition, T_sp: TKelvin): TKelvin = 
        // NOTE: T_irb not implemented
        fgCondition match
            case FlueGasCondition.Dry_NonCondensing  => T_sp
            case FlueGasCondition.Wet_Condensing     => 273.15.degreesKelvin

    // Section "5.5.2"
    // "Débit massique des fumées et débit massique de l'air de combustion"

    // m_dot
    // m_B_dot
    // connu ou à l'aide de B.1 B.2 ou B.3

    /**
     * Calcule le débit massique des fumées ṁ, en g/s
     *
     * @param f_m1 coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s)
     * @param f_m2 coefficient de calcul du débit massique des fumées, en g/(kW ⋅ s)
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @param Q_F débit calorifique de l'appareil à combustion, en kW
     * @return débit massique des fumées, en g/s
     */
    def m_dot_calc(
        f_m1: Double,
        f_m2: Double,
        σ_CO2: QtyD[Percent],
        Q_F: QtyD[Kilo * Watt]
    ): QtyD[Gram / Second] =
        (
            (f_m1 / σ_CO2.value + f_m2)
            *
            Q_F.value
        ).withUnit[Gram / Second]

    /**
     * Calcule le débit massique de l'air de combustion ṁB, en g/s
     *
     * @param f_m1 coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s)
     * @param f_m3 coefficient de calcul du débit massique de l'air de combustion, en g/(kW ⋅ s) ;
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @param Q_F débit calorifique de l'appareil à combustion, en kW
     * @return débit massique des fumées, en g/s
     */
    def mB_dot_calc(
        f_m1: Double,
        f_m3: Double,
        σ_CO2: QtyD[Percent],
        Q_F: QtyD[Kilo * Watt]
    ): QtyD[Gram / Second] =
        (
            (f_m1 / σ_CO2.value + f_m3)
            *
            Q_F.value
        ).withUnit[Gram / Second]

    /**
     * Calcule le débit calorifique de l'appareil à combustion Q_F, en kW
     *
     * @param η_W rendement de l'appareil à combustion, en %
     * @param Q puissance utile de l'appareil à combustion, en kW
     * @return débit calorifique de l'appareil à combustion, en kW
     */
    def Q_F_calc(η_W: QtyD[Percent], Q: QtyD[Kilo * Watt]): QtyD[Kilo * Watt] =
        100.0.percent / η_W * Q

    /**
     * Calcule la teneur en dioxyde de carbone des fumées sèches, en %
     *
     * @param combustible type de combustible
     * @return teneur en dioxyde de carbone des fumées sèches, en %
     */
    def σ_CO2_calc(
        combustible: FuelType
    ): Either[Throwable, Dimensionless] =
        combustible match
            case FuelType.WoodLog30pHumidity | FuelType.Pellets =>
                Left(
                    NotImplementedError(
                        "la teneur en dioxyde de carbone des fumées sèches 'σ_CO2' est calculable uniquement pour les chaudières (tableau B.2) pour le combustible bois"
                    )
                )

    // Section "5.5.3", "Température des fumées"

    /**
      * Température des fumées à la puissance utile nominale (T_WN)
      *
      * @param twn Température des fumées à la puissance utile nominale (T_WN)
      * @return Température des fumées à la puissance utile nominale (T_WN)
      */
    def T_WN(twn: TCelsius): TCelsius = twn

    /**
      * Température des fumées à la puissance utile la plus faible possible (T_Wmin)
      *
      * @param twmin (optional) Température des fumées à la puissance utile la plus faible possible
      * @param twn Température des fumées à la puissance utile nominale (T_WN)
      * @return Température des fumées à la puissance utile nominale (T_WN)
      */
    def T_Wmin(twmin: Option[TCelsius], twn: TCelsius): TCelsius =
        twmin match
            case None => 
                val tv = twn.toUnit[Celsius].value
                (2.0 / 3.0 * tv).withTemperature[Celsius]
            case Some(twmin) => twmin
        
    // Section "5.5.4", 
    // Tirage minimal de l'appareil à combustion (P_W) 
    // pour les conduits de fumée fonctionnant sous pression négative

    /**
      * Tirage minimal de l'appareil à combustion (P_W) 
      * pour les conduits de fumée fonctionnant sous pression négative
      * 
      * @param pw valeur donnée par le fabricant
      * @return
      */
    def P_W_calc(pw: Pressure): Pressure = 
        if (pw < 0.pascals) 0.pascals // See 5.5.4
        else pw // (cas du foyer ouvert non traité)

    /**
      * Tirage maximal de l'appareil à combustion (P_Wmax) 
      * pour les conduits de fumée fonctionnant sous pression négative
      * 
      * @param pwmax valeur donnée par le fabricant
      * @return
      */
    def P_Wmax_calc(pwmax: Pressure): Pressure = pwmax

    // Section "5.6.3", "Résistance Thermique (1 / Λ)"

    def thermal_resistance_for_layer_calc(
        cof: CoefficientOfForm, 
        dhi: Length, 
        dho: Length, 
        lambda: ThermalConductivity
    ): SquareMeterKelvinPerWatt =
        SquareMeterKelvinPerWatt(
            cof.unwrap * dhi.to_m.value / (2.0 * lambda.toUnit[Watt / (Meter * Kelvin)].value) * math.log((dho / dhi).value)
        )

    def thermal_resistance_for_layers_calc(
        mean_gas_temp: TCelsius, 
        startGeom: PipeShape, 
        layers: List[AppendLayerDescr]
    ): Either[ThermalResistance_Error, SquareMeterKelvinPerWatt] =
        import AppendLayerDescr.*
        import ThermalResistance_Error as TRError
        
        enum Status:
            case Ok
            case Error_MissingLayerAfterAirSpace
        import Status.*

        val DEBUG = false
        def debug(msg: String): Unit = if (DEBUG) println(msg) else ()

        def computeTR(
            dhi: D_h, 
            outer_shape: PipeShape, // __INTERPRETATION__: outer_shape or inner_shape or mean of outer + inner ?
            lambda: ThermalConductivity
        ): Either[TRError.SideRatioTooHighForRectangularForm, SquareMeterKelvinPerWatt] =
            coefficient_of_form(outer_shape) match
                case Left(e @ TRError.SideRatioTooHighForRectangularForm(_)) => e.asLeft
                case Right(cof) => thermal_resistance_for_layer_calc(cof, dhi, outer_shape.dh, lambda).asRight

        // acc = (current layer inner geom, sum (tr / dhi), status)
        val tr_dh_sum_zero = 0.0.withUnit[(Meter ^ 2) * Kelvin / Watt / Meter]
        val z: Either[ThermalResistance_Error, (PipeShape, QtyD[(Meter ^ 2) * Kelvin / Watt / Meter], Status)] =
            (startGeom, tr_dh_sum_zero, Status.Ok).asRight[ThermalResistance_Error]

        val finalAcc: Either[ThermalResistance_Error, (PipeShape, QtyD[(Meter ^ 2) * Kelvin / Watt / Meter], Status)] = 
            layers.foldLeft(z): (acc, l) =>
                acc.flatMap:
                    case (ig, _, _) if coefficient_of_form(ig).isLeft =>
                        Left(ThermalResistance_Error.SideRatioTooHighForRectangularForm(ig))
                    case (ig, trdhsum, Ok | Error_MissingLayerAfterAirSpace) =>
                        val dhi = ig.dh
                        l match                            
                            case AppendLayerDescr.FromLambda(og, lambda) =>
                                computeTR(dhi, og, lambda).map: tr =>
                                    debug(s"Rth = ${tr.show} (${dhi.show}, ${og.show}, ${lambda.show}) [ig = ${ig.show} & og = ${og.show}]")
                                    (og, trdhsum + tr / dhi, Status.Ok)
                            case AppendLayerDescr.FromLambdaUsingThickness(e, lambda) =>
                                val og = ig.expandGeomWithThickness(e)
                                computeTR(dhi, og, lambda).map: tr =>
                                    debug(s"Rth = ${tr.show} (${dhi.show}, ${e.show}, ${lambda.show}) [ig = ${ig.show} & og = ${og.show}]")
                                    (og, trdhsum + tr / dhi, Status.Ok)
                            case AppendLayerDescr.FromThermalResistanceUsingThickness(e, tr) =>
                                val og = ig.expandGeomWithThickness(e)
                                debug(s"Rth = ${tr.show} (${e.show}) [ig = ${ig.show} & og = ${og.show}]")
                                (og, trdhsum + tr / dhi, Status.Ok).asRight
                            case AppendLayerDescr.FromThermalResistance(og, tr) =>
                                debug(s"Rth = ${tr.show} [ig = ${ig.show} & og = ${og.show}]")
                                (og, trdhsum + tr / dhi, Status.Ok).asRight
                            case AppendLayerDescr.AirSpaceUsingOuterShape(osh, _, _) =>
                                val dn = (osh.dh - ig.dh) / 2.0
                                val t_emittingSurfaceTemp = mean_gas_temp
                                deadAirSpaceThermalResistance(t_emittingSurfaceTemp, dn, ig).map: tr =>
                                    debug(s"Rth = ${tr.show} (air space: ${mean_gas_temp.show}, ${dn.show}) [ig = ${ig.show} & outer = ${osh.show}]")
                                    (osh, trdhsum + tr / dhi, Status.Error_MissingLayerAfterAirSpace)
                            case AppendLayerDescr.AirSpaceUsingThickness(e, _, _) =>
                                val osh = ig.expandGeomWithThickness(e)
                                val t_emittingSurfaceTemp = mean_gas_temp
                                deadAirSpaceThermalResistance(t_emittingSurfaceTemp, e, ig).map: tr =>
                                    debug(s"Rth = ${tr.show} (air space: ${mean_gas_temp.show}, ${e.show}) [ig = ${ig.show} & osh = ${osh.show}]")
                                    (osh, trdhsum + tr / dhi, Status.Error_MissingLayerAfterAirSpace)
        finalAcc.map(_._3).flatMap:
            case Error_MissingLayerAfterAirSpace => Left(TRError.CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing)
            case Ok => 
                val dh = startGeom.dh
                finalAcc.map(_._2).map(dh * _)
            
        // (
        //     for 
        //         l <- layers.toList
        //         (rth_n, dh_n) = l match
        //             case LayerRthManual(dhin, rth, _)  => (rth, dhin)
        //             case LayerRthSealedAirGap => 
        //                 val rthn = ??? // we need mean flue gas temperature here
        //                 val dhin = ???
        //                 (rthn, dhin) 
        //     yield 
        //         dh * rth_n / dh_n
        // ).sum
        
    // Section "5.7.1.2", "Température de l'air extérieur (T_L)"

    /**
     * Calcule la température de l’air extérieur T_L, en K
     *
     * @param pReq pressure requirements (min or max draft)
     * @param T_L_override température de l’air extérieur T_L, en K (si spécification nationale)
     * @return température de l’air extérieur, en K
     */
    def T_L_calc(
        pReq: DraftCondition,
        T_L_override: Map[DraftCondition, T_L]
    ): T_L =
        pReq match
            case draftMin: DraftCondition.DraftMinOrPositivePressureMax.type =>
                T_L_override.getOrElse(draftMin, 288.15.degreesKelvin)
            case draftMax: DraftCondition.DraftMaxOrPositivePressureMin.type =>
                T_L_override.getOrElse(draftMax, 258.15.degreesKelvin)

    // Section "5.7.1.3", "Température du l'air ambiant (T_u)"

    /**
     * Calcule la température de l'air ambiant T_u (conditions humides ou sèches), en K
     *
     * @param T_L température de l’air extérieur T_L, en K
     * @param airSpace air space
     * @param pReq pressure requirements (min or max draft)
     * @param flueGasCond flue gas condition (dry or wet)
     * @param unheatedHeightInsideAndOutside hauteur de la zone non chauffée à l'intérieur et à l'extérieur du bâtiment, en m
     * @param T_uo_wet_override température de l'air ambiant (conditions humides) à la sortie du conduit de fumée, en K (si spécification nationale)
     * @param T_uo_dry_override température de l'air ambiant (conditions humides) à la sortie du conduit de fumée, en K (si spécification nationale)
     * @param T_u_custom_area température de l'air ambiant dans la zone spécifique définie (labo)
     * @param A_ub surface extérieure du conduit de fumée dans la salle des chaudières, in m2
     * @param A_uh surface extérieure du conduit de fumée dans les zones chauffées, en m2
     * @param A_uu surface extérieure du conduit de fumée dans les zones non chauffées à l'intérieur du bâtiment, en m2
     * @param A_ul surface extérieure du conduit de fumée à l'extérieur du bâtiment, en m2
     * @param A_u_custom_area surface extérieure du conduit de fumée dans la zone spécifique (labo), en m2
     * @return Température de l'air ambiant, en K
     */
    def T_u_calc(
        T_L: T_L,
        tla: AirSpace,
        pReq: DraftCondition,
        flueGasCond: FlueGasCondition,
        unheatedHeightInsideAndOutside: QtyD[Meter],
        T_uo_override: T_uo_temperature_override,
        T_u_custom_area: Option[TKelvin],
    )(
        A_ub: QtyD[(Meter ^ 2)],
        A_uh: QtyD[(Meter ^ 2)],
        A_uu: QtyD[(Meter ^ 2)],
        A_ul: QtyD[(Meter ^ 2)],
        A_u_custom_area: QtyD[(Meter ^ 2)],
    ): Either[NoOutsideSurfaceFound, TempD[Kelvin]] =
        def helper(ploc: PipeLocation) = 
            val tt = ploc match
                case BoilerRoom         => T_ux_defaults.boilerRoom
                case HeatedArea         => T_ux_defaults.heatedArea
                case UnheatedInside     => T_ux_defaults.unheatedInside
                case OutsideOrExterior  => T_ux_defaults.outsideOrExterior
                case x: CustomArea      => x.ambiant_air_temperature_set
            
            // println("")
            // println("---")
            // println(s"ploc = $ploc")
            // println(s"pReq = $pReq")
            // println(s"tt = $tt")
            // println(s"T_uo_wet_override = ${T_uo_wet_override.unwrap.map(_.to_degC.show).getOrElse("-")}")
            // println(s"T_uo_dry_override = ${T_uo_dry_override.unwrap.map(_.to_degC.show).getOrElse("-")}")
            // println(s"unheatedHeightInsideAndOutside = ${unheatedHeightInsideAndOutside.show}")
            val tu = T_u_temp_helper(
                T_L,
                tla,
                pReq,
                flueGasCond,
                unheatedHeightInsideAndOutside,
                T_uo_override,
            )(tt)
            // println(s"tu = ${tu.to_degC.show}")
            // println("")
            // println(s"""| A_ub             = ${A_ub.show}
            //             | A_uh             = ${A_uh.show}
            //             | A_uu             = ${A_uu.show}
            //             | A_ul             = ${A_ul.show}
            //             | A_u_custom_area  = ${A_u_custom_area.show}""".stripMargin)
            // println("---")
            tu

        val T_ub = helper(BoilerRoom)
        val T_uh = helper(HeatedArea)
        val T_ul = helper(OutsideOrExterior)
        val T_uu = helper(UnheatedInside)

        given tempToKelvin: Conversion[TempD[Kelvin], Double] =
            (_: TempD[Kelvin]).value
        given areaToSquareMeters: Conversion[QtyD[(Meter ^ 2)], Double] =
            (_: QtyD[(Meter ^ 2)]).value

        val num =
            (T_ub * A_ub) + (T_uh * A_uh) + (T_uu * A_uu) + (T_ul * A_ul) + (T_u_custom_area.getOrElse(0.degreesKelvin) * A_u_custom_area)
        val A_tot = A_ub + A_uh + A_uu + A_ul + T_u_custom_area.map(_ => A_u_custom_area).getOrElse(0.m2)

        if (A_tot == 0.0) 
            Left(NoOutsideSurfaceFound("T_u (ambiant air) calculation : could not compute 'T_u' if no external surface found"))
        else
            Right((num / A_tot).withTemperature[Kelvin])

    def T_u_temp_helper(
        T_L: T_L,
        airSpace: AirSpace,
        pReq: DraftCondition,
        flueGasCond: FlueGasCondition,
        unheatedHeightInsideAndOutside: QtyD[Meter],
        T_uo_override: T_uo_temperature_override,
    )(
        ambAirTempSet: AmbiantAirTemperatureSet
    ): TKelvin = 
        extension (e: Either[UseTuoOverride, TCelsius])
            def getOrUseTuo: TKelvin = 
                e
                .leftMap(_ => T_uo_calc(T_L, unheatedHeightInsideAndOutside, airSpace, pReq, flueGasCond)(using T_uo_override))
                .fold(identity, _.toUnit[Kelvin])

        pReq match
            case DraftCondition.DraftMinOrPositivePressureMax => T_L
            case DraftCondition.DraftMaxOrPositivePressureMin =>
                airSpace match
                    case AirSpace.DeadAirSpace => 
                        flueGasCond match
                            case _: FlueGasCondition.Wet_Condensing => 
                                // println(s"ambAirTempSet.withoutairspace_wetcond = ${ambAirTempSet.withoutairspace_wetcond}")
                                ambAirTempSet.withoutairspace_wetcond.getOrUseTuo
                            case _: FlueGasCondition.Dry_NonCondensing  => 
                                // println(s"ambAirTempSet.withoutairspace_drycond = ${ambAirTempSet.withoutairspace_drycond}")
                                ambAirTempSet.withoutairspace_drycond.getOrUseTuo
                    case AirSpace.VentilatedAirSpace_SameDirectionAsFlueGas =>
                        flueGasCond match
                            case _: FlueGasCondition.Wet_Condensing =>
                                if (unheatedHeightInsideAndOutside <= 5.meters)
                                    // println(s"ambAirTempSet.withairspace_wetcond_hextBelow5m = ${ambAirTempSet.withairspace_wetcond_hextBelow5m}")
                                    ambAirTempSet.withairspace_wetcond_hextBelow5m.getOrUseTuo
                                else
                                    // println(s"ambAirTempSet.withairspace_wetcond_hextAbove5m = ${ambAirTempSet.withairspace_wetcond_hextAbove5m}")
                                    ambAirTempSet.withairspace_wetcond_hextAbove5m.getOrUseTuo
                            case _: FlueGasCondition.Dry_NonCondensing => 
                                if (unheatedHeightInsideAndOutside <= 5.meters)
                                    // println(s"ambAirTempSet.withairspace_drycond_hextBelow5m = ${ambAirTempSet.withairspace_drycond_hextBelow5m}")
                                    ambAirTempSet.withairspace_drycond_hextBelow5m.getOrUseTuo
                                else
                                    // println(s"ambAirTempSet.withairspace_drycond_hextAbove5m = ${ambAirTempSet.withairspace_drycond_hextAbove5m}")
                                    ambAirTempSet.withairspace_drycond_hextAbove5m.getOrUseTuo
                                
    final override val T_uo_default = T_uo_temperature.from(
        AmbiantAirTemperatureSet( // T_uo
            withoutairspace_wetcond          = Right(-15.degreesCelsius),
            withoutairspace_drycond          = Right(0.degreesCelsius),
            withairspace_wetcond_hextBelow5m = Right(0.degreesCelsius),
            withairspace_drycond_hextBelow5m = Right(0.degreesCelsius),
            withairspace_wetcond_hextAbove5m = Right(-15.degreesCelsius),
            withairspace_drycond_hextAbove5m = Right(0.degreesCelsius),
        )
    )
    
    protected val T_ux_defaults: TuTemperatures = 
        TuTemperatures(
            outsideOrExterior = AmbiantAirTemperatureSet(                                  // T_ul
                withoutairspace_wetcond           = Left(UseTuoOverride),
                withoutairspace_drycond            = Left(UseTuoOverride),
                withairspace_wetcond_hextBelow5m = Right(15.degreesCelsius),
                withairspace_drycond_hextBelow5m  = Right(15.degreesCelsius),
                withairspace_wetcond_hextAbove5m = Left(UseTuoOverride),
                withairspace_drycond_hextAbove5m  = Left(UseTuoOverride),
            ),
            boilerRoom        = AmbiantAirTemperatureSet.fromSingleTu(15.degreesCelsius),  // T_ub
            heatedArea        = AmbiantAirTemperatureSet.fromSingleTu(20.degreesCelsius),  // T_uh
            unheatedInside    = AmbiantAirTemperatureSet.from(                             // T_uu
                withoutAirSpace             = 0.degreesCelsius,
                withAirSpace_hextBelow5m    = 15.degreesCelsius,
                withAirSpace_hextAbove5m    = 0.degreesCelsius
            ),
            xtras = Nil,
        )

    def T_uo(using T_uo_override: T_uo_temperature_override): T_uo_temperature =
        T_uo_override.opaqueGetOrElse(T_uo_default)

    def T_uo_calc(
        T_L: T_L,
        unheatedHeightInsideAndOutside: Length,
        airSpace: AirSpace,
        pReq: DraftCondition,
        flueGasCond: FlueGasCondition,
    )(using
        T_uo_override: T_uo_temperature_override
    ): TKelvin = 
        val tuo = T_uo.unwrap.get
        pReq match
            case DraftCondition.DraftMinOrPositivePressureMax => T_L
            case DraftCondition.DraftMaxOrPositivePressureMin =>
                airSpace match
                    case AirSpace.DeadAirSpace => 
                        flueGasCond match
                            case _: FlueGasCondition.Wet_Condensing => tuo.withoutairspace_wetcond.toOption.get
                            case _: FlueGasCondition.Dry_NonCondensing  => tuo.withoutairspace_drycond.toOption.get
                    case AirSpace.VentilatedAirSpace_SameDirectionAsFlueGas =>
                        flueGasCond match
                            case _: FlueGasCondition.Wet_Condensing =>
                                if (unheatedHeightInsideAndOutside <= 5.meters)
                                    // println(s"tuo.withairspace_wetcond_hextBelow5m = ${tuo.withairspace_wetcond_hextBelow5m.toOption.get}")
                                    tuo.withairspace_wetcond_hextBelow5m.toOption.get
                                else
                                    // println(s"tuo.withairspace_wetcond_hextAbove5m = ${tuo.withairspace_wetcond_hextAbove5m.toOption.get}")
                                    tuo.withairspace_wetcond_hextAbove5m.toOption.get
                            case _: FlueGasCondition.Dry_NonCondensing => 
                                if (unheatedHeightInsideAndOutside <= 5.meters)
                                    // println(s"tuo.withairspace_drycond_hextBelow5m = ${tuo.withairspace_drycond_hextBelow5m.toOption.get}")
                                    tuo.withairspace_drycond_hextBelow5m.toOption.get
                                else
                                    // println(s"tuo.withairspace_drycond_hextAbove5m = ${tuo.withairspace_drycond_hextAbove5m.toOption.get}")
                                    tuo.withairspace_drycond_hextAbove5m.toOption.get

    /**
      * Interpretation of 5.7.1.3
      * 
      * If at least one air gap is marked as "not ventilated", 
      * returns [[afpma.firecalc.engine.models.en13384.typedefs.TypeLameAir.SansLameAirVentilee]]
      * 
      * If all air gaps :
      *     - are marked as "ventilated in the same direction as flue gas"
      *     - have width above threshold of `1.cm`
      * returns [[afpma.firecalc.engine.models.en13384.typedefs.TypeLameAir.AvecLameAirVentileeDansSensCiruclationFumees]]
      * 
      * Otherwise, returns [[afpma.firecalc.engine.models.en13384.typedefs.TypeLameAir.SansLameAirVentilee]]
      * 
      *
      * @param airGap
      * @return TypeLameAir
      */
    // def interpretTypeLameAirFromAirGap(airGap: AirGap): TypeLameAir =
    //     val parts       = airGap.parts
    //     val uniqTypes   = parts.map(_.airGapT).distinct
    //     uniqTypes.length match
    //         case 0 => throw new IllegalStateException("air gap type should be defined. fix error in code.")
    //         case 1 => interpretTypeLameAirFromAirSpaceDetailed(uniqTypes.head)
    //         case n =>
    //             val WIDTH_THRESHOLD = 1.cm
                
    //             val onlyVentilatedInSameDirectionAsFlueGasAndAboveWidthThreshold = 
    //                 uniqTypes.forall(_.isVentilatedInSameDirectionAsFlueGasAndAboveWidthThreshold(WIDTH_THRESHOLD))
                
    //             if (uniqTypes.contains(AirSpaceDetailed.noAirGap))
    //                 TypeLameAir.SansLameAirVentilee
    //             else if (onlyVentilatedInSameDirectionAsFlueGasAndAboveWidthThreshold)
    //                 TypeLameAir.AvecLameAirVentileeDansSensCiruclationFumees
    //             else 
    //                 TypeLameAir.SansLameAirVentilee

    /**
      * Interpretation of 5.7.1.3
      * 
      * @param airSpaceDetailed
      * @return TypeLameAir
      */
    def interpretAirSpaceFromAirSpaceDetailed(airSpaceDetailed: AirSpaceDetailed): AirSpace =
        import AirSpaceDetailed.*
        airSpaceDetailed match
            case WithoutAirSpace           => 
                AirSpace.DeadAirSpace
            case WithAirSpace(_, VentilDirection.SameDirAsFlueGas, VentilOpenings.AnnularAreaFullyOpened) => 
                // Source: 
                //
                // Rapport EAE / GPE N°00-195
                //  TUBAGE DES CONDUITS DE FUMÉE
                //  Détermination de la résistance
                //  Thermique de la lame d’air ventilée
                // Authors: Lionel DRUETTE & Jacques CHANDELLIER
                // Date: Mars 2000

                // According to this report, 
                AirSpace.DeadAirSpace
            case WithAirSpace(w, VentilDirection.SameDirAsFlueGas, VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1) if w >= 1.cm =>
                // Source: 
                //
                // Rapport EAE / GPE N°00-195
                //  TUBAGE DES CONDUITS DE FUMÉE
                //  Détermination de la résistance
                //  Thermique de la lame d’air ventilée
                // Authors: Lionel DRUETTE & Jacques CHANDELLIER
                // Date: Mars 2000

                // According to this report, ventilated air space made 
                // according to DTU 24.1 can not be considered as 'ventilated'

                // TypeLameAir.AvecLameAirVentileeDansSensCiruclationFumees
                AirSpace.DeadAirSpace
            case _ =>
                AirSpace.DeadAirSpace


    // Section "5.7.2"
    // Pression de l'air extérieur (p_L)

    /**
     * Pression de l'air extérieur (p_L)
     *
     * @param T_L température de l'air extérieur, en K
     * @param z hauteur au-dessus du niveau de la mer, en m
     * @return Pression de l'air extérieur (p_L)
     */
    def p_L_calc(
        T_L: TempD[Kelvin],
        z: QtyD[Meter]
    ): QtyD[Pascal] =
        val zv = z.toUnit[Meter].value
        val rl = R_L.toUnit[Joule / (Kilogram * Kelvin)].value
        val tl = T_L.toUnit[Kelvin].value
        (
            97000.0 
            * 
            math.exp(
                ( - 9.81 * zv )
                / 
                ( rl * tl ) 
            )
        ).withUnit[Pascal]

    // Section "5.7.3.1"
    // Constante des gaz de l'air (R_L)

    /** Constante des gaz de l'air */
    def R_L = JoulesPerKilogramKelvin(288)

    // Section "5.7.3.2"
    // Constante des gaz des fumées (R), en J/(kg.K) 

    /**
      * Constante des gaz des fumées, en J/(kg.K) 
      *
      * @param comb type de combustible
      * @param σ_H2O teneur en vapeur d'eau des fumées, en %
      * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
      */
    def R_calc(
        comb: FuelType,
        σ_H2O: QtyD[Percent],
        σ_CO2: QtyD[Percent]
    ): JoulesPerKilogramKelvin = 
        val f_R1 = f_R1_calc(comb)
        val f_R2 = f_R2_calc(comb)

        val σH2O = σ_H2O.toUnit[Percent].value
        val σCO2 = σ_CO2.toUnit[Percent].value

        R_L * (
            0.996 
            + 
            f_R1 * σH2O
            + 
            f_R2 * (1.0 - (σH2O / 100.0)) * σCO2
        )

    // Section 5.7.4, Masse volumique de l'air extérieur (ρ_L)

    /**
      * Masse volumique de l'air extérieur (ρ_L)
      *
      * @param p_L pression de l’air extérieur, en Pa
      * @param T_L température de l’air extérieur, en K
      * @return
      */
    def ρ_L_calc(
        p_L: Pressure,
        T_L: TKelvin,
    ): Density = 
        (
            p_L.toUnit[Pascal].value / (R_L.value * T_L.toUnit[Kelvin].value)
        ).withUnit[Kilogram / (Meter ^ 3)]

    /**
      * Masse volumique de l'air de combustion (ρ_B)
      *
      * @param T_B temperature de l’air de combustion, en Pa
      * @param z hauteur au-dessus du niveau de la mer, en m
      * @return
      */
    def ρ_B_calc(
        T_B: TKelvin,
        z: Length
    ): Density = 
        val pB = p_L_calc(T_B, z)
        ρ_L_calc(pB, T_B) // reuse formula (13) see last paragraph of 5.11.4


    // Section "5.7.5", "Capacité calorifique spécifique des fumées (c_p)"

    /**
     * Calcule la capacité calorifique spécifique des fumées c_p, en J/(kg⋅K)
     *
     * @param t_m température moyenne des fumées, en °C
     * @param f_c0 coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K⋅%)
     * @param f_c1 coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 2 ⋅%)
     * @param f_c2 coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 3 ⋅%)
     * @param f_c3 coefficient de calcul de la capacité calorifique spécifique des fumées, en 1/%
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @return
     */
    def c_p_calc(
        t_m: TempD[Celsius],
        f_c0: Double,
        f_c1: Double,
        f_c2: Double,
        f_c3: Double,
        σ_CO2: QtyD[Percent]
    ): JoulesPerKilogramKelvin =
        val tm = t_m.value
        val tm2 = math.pow(tm, 2)
        val num =
            1011 + 0.05 * tm + 0.0003 * tm2 + (f_c0 + f_c1 * tm + f_c2 * tm2) * σ_CO2.value
        val denom = 1 + f_c3 * σ_CO2.value
        JoulesPerKilogramKelvin(num / denom)

    /**
     * Calcule la capacité calorifique spécifique des fumées c_p, en J/(kg⋅K)
     * (depuis le type de combustible)
     *
     * @param t_m température moyenne des fumées, en °C
     * @param comb type de combustible utilisé
     * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches, en %
     * @return capacité calorifique spécifique des fumées c_p, en J/(kg⋅K)
     */
    def c_p_calc(
        t_m: TempD[Celsius],
        comb: FuelType,
        σ_CO2: QtyD[Percent]
    ): JoulesPerKilogramKelvin =
        val f_c0 = f_c0_calc(comb)
        val f_c1 = f_c1_calc(comb)
        val f_c2 = f_c2_calc(comb)
        val f_c3 = f_c3_calc(comb)
        c_p_calc(t_m, f_c0, f_c1, f_c2, f_c3, σ_CO2)

    // Section 5.7.6
    // Température de condensation (T_sp)

    /**
      * Température de condensation (T_sp)
      *
      * @param comb type de combustible
      * @param T_p point de rosée de l'eau des fumées pour différents combustibles et différentes concentrations envolume de CO 2 dans les fumées doit être calculé à l'aide des Formules (B.5), (B.6) et (B.7).
      * @param ΔT_sp température de condensation des fumées est le point de rosée acide
      * @return
      */
    def T_sp(
        comb: FuelType,
        T_p: TKelvin,
        ΔT_sp: Option[TKelvin],
    ): TKelvin = 
        comb match
            case FuelType.WoodLog30pHumidity | FuelType.Pellets => T_p

    // Section "5.7.7", Facteur de correction de l'instabilité de la température (S_H)

    /**
      * Calcule le facteur de correction de l'instabilité de la température (S_H)
      *
      * @param pReq pressure requirements (min or max draft)
      * @return S_H
      */
    def S_H_calc(pReq: DraftCondition): Dimensionless =
        pReq match
            case DraftCondition.DraftMinOrPositivePressureMax => 0.5.withUnit[1]
            case DraftCondition.DraftMaxOrPositivePressureMin => 1.0.withUnit[1]

    // Section "5.7.8", "Coefficient de sécurité du débit (S_E)"

    def S_E_calc(pReq: DraftCondition): Dimensionless =
        import DraftCondition.*
        pReq match
            case _: DraftMinOrPositivePressureMax => 
                // 1.2.ea => valeur non considérée car applicable aux installations sous controle permanent, etc...
                1.5.ea
            case _: DraftMaxOrPositivePressureMin => 1.0.ea

    // Section "5.8", "Détermination des températures"

    // Section "5.8.1", "Généralités"

    /**
     * Calcule la température moyenne des fumées T_m
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_e température des fumées au niveau de l'orifice d'admission du conduit, en K
     * @param K coefficient de refroidissement du conduit de fumée (voir 5.8.2)
     * @return température moyenne des fumées T_m, en K
     */
    def T_m_calc(
        T_u: TempD[Kelvin],
        T_e: TempD[Kelvin],
        K: Dimensionless
    ): TempD[Kelvin] =
        (
            T_u.value
            +
            (T_e.value - T_u.value) / K.value
            *
            (1 - math.exp(-K.value))
        ).degreesKelvin

    /**
     * Calcule la température des fumées à la sortie du conduit de fumée T_o
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_e température des fumées au niveau de l'orifice d'admission du conduit, en K
     * @param K coefficient de refroidissement du conduit de fumée (voir 5.8.2)
     * @return température des fumées à la sortie du conduit de fumée, en K
     */
    def T_o_calc(
        T_u: TempD[Kelvin],
        T_e: TempD[Kelvin],
        K: Dimensionless
    ): TempD[Kelvin] =
        T_u + (T_e - T_u) * math.exp(-K.value)
        

    /**
     * Calcule la température moyenne des fumées dans le conduit de raccordement T_mV
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_W température des fumées de l'appareil à combustion", en K.
     * @param K_V coefficient de refroidissement du conduit de raccordement (voir 5.8.2)
     * @return température moyenne des fumées dans le conduit de raccordement, en K
     */
    def T_mV_calc(
        T_u: TempD[Kelvin],
        T_W: TempD[Kelvin],
        K_V: Dimensionless
    ): TempD[Kelvin] =
        T_u
        +
        (T_W - T_u) / K_V.value
        *
        (1 - math.exp(-K_V.value))

    /**
     * Calcule la température des fumées au niveau de l'orifice d'admission du conduit de fumée T_e
     *
     * @param T_u température de l'air ambiant (voir 5.7.1.3), en K
     * @param T_w température des fumées de l'appareil à combustion", en K.
     * @param K_V coefficient de refroidissement du conduit de raccordement (voir 5.8.2)
     * @return température des fumées au niveau de l'orifice d'admission du conduit de fumée, en K
     */
    def T_e_calc(
        T_u: TempD[Kelvin],
        T_w: TempD[Kelvin],
        K_V: Dimensionless
    ): TempD[Kelvin] =
        T_u + (T_w - T_u) * math.exp(-K_V.value)

    // Section "5.8.2", "Calcul du coefficient de refroidissement (K)"

    /**
     * Calcule le coefficient de refroidissement du conduit de fumée K
     *
     * @param c_p capacité calorifique spécifique des fumées (voir 5.7.5), en J/(kg ⋅ K)
     * @param k coefficient de transfert thermique du conduit de fumée (voir 5.8.3), en W/(m 2 ⋅ K)
     * @param L longueur du conduit de fumée, en m
     * @param m_dot débit massique des fumées (voir 5.5.2), en kg/s
     * @param U circonférence intérieure du conduit de fumée, en m
     * @return Coefficient de refroidissement du conduit de fumée
     */
    def K_calc(
        c_p: JoulesPerKilogramKelvin,
        k: WattsPerSquareMeterKelvin,
        L: QtyD[Meter],
        m_dot: QtyD[Kilogram / Second],
        U: QtyD[Meter]
    ): Dimensionless =
        ((U.value * k.value * L.value) / (m_dot.value * c_p.value)).withUnit[1]

    /**
     * Calcule le coefficient de refroidissement du conduit de raccordement K_V
     *
     * @param c_p capacité calorifique spécifique des fumées (voir 5.7.5), en J/(kg ⋅ K)
     * @param k_V coefficient de transfert thermique du conduit de raccordement (voir 5.8.3), en W/(m 2 ⋅ K)
     * @param L_V longueur du conduit de raccordement, en m
     * @param m_dot débit massique des fumées (voir 5.5.2), en kg/s
     * @param U_V circonférence intérieure du conduit de raccordement, en m
     * @return Coefficient de refroidissement du conduit de raccordement
     */
    def K_V_calc(
        c_p: JoulesPerKilogramKelvin,
        k_V: WattsPerSquareMeterKelvin,
        L_V: QtyD[Meter],
        m_dot: QtyD[Kilogram / Second],
        U_V: QtyD[Meter]
    ): K_V =
        (
            (U_V.value * k_V.value * L_V.value)
            /
            (m_dot.value * c_p.value)
        ).ea

    // Section "5.8.3", "Coefficient de transfert thermique (k_b)"

    // Section "5.8.3.1", "Généralités"

    /**
     * Calcule le coefficient de transfert thermique du conduit de fumée à la température d'équilibre K_b
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param D_ha diamètre hydraulique extérieur, en m
     * @param α_a coefficient externe de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param α_i coefficient interne de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param Λinverse résistance thermique (voir 5.6.3), en m 2 · K/W
     * @return coefficient de transfert thermique du conduit de fumée à la température d'équilibre K_b, en W/(m 2 ⋅ K)
     */
    def k_b_calc(
        D_h: QtyD[Meter],
        D_ha: QtyD[Meter],
        α_a: WattsPerSquareMeterKelvin,
        α_i: WattsPerSquareMeterKelvin,
        Λinverse: SquareMeterKelvinPerWatt
    ): WattsPerSquareMeterKelvin =
        WattsPerSquareMeterKelvin(
            1 
            / 
            (
                (1 / α_i.value) 
                + Λinverse.value 
                + (D_h.value / (D_ha.value * α_a.value))
            )
        )

    /**
     * Calcule le coefficient de transfert thermique du conduit de fumée à une température de non-équilibre k
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param D_ha diamètre hydraulique extérieur, en m
     * @param S_H coefficient de correction de l'instabilité de température (voir 5.7.7)
     * @param α_a coefficient externe de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param α_i coefficient interne de transfert thermique (voir 5.8.3.2), en W/(m 2 ⋅ K)
     * @param Λinverse résistance thermique (voir 5.6.3), en m 2 · K/W
     * @return coefficient de transfert thermique du conduit de fumée à une température de non-équilibre k, en W/(m 2 ⋅ K)
     */
    def k_calc(
        D_h: QtyD[Meter],
        D_ha: QtyD[Meter],
        S_H: Dimensionless,
        α_a: WattsPerSquareMeterKelvin,
        α_i: WattsPerSquareMeterKelvin,
        Λinverse: SquareMeterKelvinPerWatt
    ): WattsPerSquareMeterKelvin =
        WattsPerSquareMeterKelvin(
            1 
            /
            (
                (1 / α_i.value) 
                + 
                S_H.value * (
                    Λinverse.value + (D_h.value / (D_ha.value * α_a.value))
                )
            )
        )

    // Section "5.8.3.2", "Coefficient interne de transfert thermique (α_i)"

    /**
     * Calcule le coefficient de transfert thermique dans le conduit de fumée α_i
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param N_u nombre de Nusselt
     * @param λ_A coefficient de conductivité thermique des fumées, en W/(m . K)
     * @return coefficient de transfert thermique dans le conduit de fumée α_i, en W/(m2 ⋅ K)
     */
    def α_i_calc(
        D_h: QtyD[Meter],
        N_u: Dimensionless,
        λ_A: WattsPerMeterKelvin
    ): WattsPerSquareMeterKelvin =
        WattsPerSquareMeterKelvin(
            λ_A.value * N_u.value / D_h.value
        )

    /**
     * Calcule le nombre de Nusselt moyen sur la hauteur du conduit de fumée
     *
     * @param D_h diamètre hydraulique intérieur, en m
     * @param L_tot longueur totale entre l'orifice d'admission des fumées dans le conduit et la sortie du conduit, en m
     * @param P_r nombre de Prandtl
     * @param R_e nombre de Reynolds
     * @param Ψ coefficient de résistance due au frottement pour un écoulement hydraulique brut (voir 5.10.3.3)
     * @param Ψ_smooth coefficient de résistance due au frottement pour un écoulement hydraulique régulier (voir 5.10.3.3 pour r = 0)
     * @return nombre de Nusselt moyen sur la hauteur du conduit de fumée
     */
    def N_u_calc(
        D_h: QtyD[Meter],
        L_tot: QtyD[Meter],
        P_r: Dimensionless,
        R_e: Dimensionless,
        Ψ: Dimensionless,
        Ψ_smooth: Dimensionless
    ): Op[Dimensionless] =
        import cats.syntax.all.*

        val R_e_corrected: Op[Double] = R_e.value match
            case re if re >= 10e6 => ReIsAbove10million(re).invalidNel
            case re if re < 2300  => (2300.0).validNel
            case re               => re.toDouble.validNel

        val Ψratio_checked: Op[Double] = Ψ.value / Ψ_smooth.value match
            case r if r < 3 => r.validNel
            case r          => 
                // r.validNel
                PsiRatioIsGreaterThan3(r).invalidNel[Double]

        val P_r_checked: Op[Double] = P_r.value match
            case pr if pr <= 0.6 => PrandtlTooSmall(pr).invalidNel
            case pr if pr >= 1.5 => PrandtlTooBig(pr).invalidNel
            case pr              => pr.toDouble.validNel

        (R_e_corrected, Ψratio_checked, P_r_checked)
            .mapN((R_e, Ψratio, P_r) =>
                val t1 = math.pow(Ψratio, 0.67)
                val t2 = 0.0214
                val t3 = math.pow(R_e, 0.8) - 100
                val t4 = math.pow(P_r, 0.4)
                val t5 = (1 + math.pow((D_h.value / L_tot.value), 0.67))

                (t1 * t2 * t3 * t4 * t5).ea
            )

    /**
     * Calcule le nombre de Prandtl
     *
     * @param c_p capacité calorifique spécifique des fumées, en J/(kg . K)
     * @param η_A viscosité dynamique des fumées, en N . s/m 2
     * @param λ_A coefficient de conductivité thermique des fumées, en W/(m . K)
     * @return nombre de Prandtl
     */
    def P_r_calc(
        c_p: JoulesPerKilogramKelvin,
        η_A: NewtonSecondsPerSquareMeter,
        λ_A: WattsPerMeterKelvin
    ): Dimensionless =
        η_A * c_p / λ_A

    // def w_m_corrected(w_m: QtyD[Meter / Second]): QtyD[Meter / Second] =
    //   w_m match
    //     case wm if wm.value < 0.5 => 0.5.meters / 1.seconds
    //     case wm => wm

    /**
     * Calcule le nombre de Reynolds R_e
     *
     * @param w_m vitesse moyenne des fumées (non-corrigée) (voir 5.9), en m/s
     * @param D_h diamètre hydraulique intérieur des fumées, en m
     * @param ρ_m masse volumique moyenne des fumées, en kg/m3
     * @param η_A viscosité dynamique des fumées, en N . s/m 2
     * @return nombre de Reynolds R_e
     */
    def R_e_calc(
        w_m_notCorrected: QtyD[Meter / Second],
        D_h: QtyD[Meter],
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        η_A: NewtonSecondsPerSquareMeter
    ): Dimensionless =
        // val w_m_corrected = w_m match
        //   case wm if wm.value < 0.5 => 0.5.meters / 1.seconds
        //   case wm => wm

        w_m_notCorrected * D_h * ρ_m
        /
        η_A

    /**
     * Calcule le coefficient de conductivité thermique des fumées λ_A, en W/(m . K)
     *
     * @param t_m température moyenne des fumées, en °C
     * @return coefficient de conductivité thermique des fumées λ_A, en W/(m . K)
     */
    def λ_A_calc(t_m: TempD[Celsius]): WattsPerMeterKelvin =
        WattsPerMeterKelvin(0.0223 + 0.000065 * t_m.value)

    /**
     * Calcule la viscosité dynamique des fumées η_A, en N . s/m2
     *
     * @param t_m température moyenne des fumées, en °C
     * @return viscosité dynamique des fumées, en N . s/m2
     */
    def η_A_calc(t_m: TempD[Celsius]): NewtonSecondsPerSquareMeter =
        NewtonSecondsPerSquareMeter(
            15e-6 
            + 47e-9 * t_m.value 
            - 20e-12 * math.pow(t_m.value, 2)
        )

    // Section "5.8.3.3", "Coefficient externe de transfert thermique (α_a)"

    /**
     * Calcule le coefficient externe de transfert thermique α_a du conduit
     *
     * @param pipeLoc localisation du conduit
     * @param airSpaceDetailed type de lame d'air
     * @return coefficient externe de transfert thermique dans le conduit de fumée α_a, en W/(m2 ⋅ K)
     */
    def α_a_calc(
        pipeLoc: PipeLocation,
        airSpaceDetailed: AirSpaceDetailed,
    ): WattsPerSquareMeterKelvin = {
        val W8 = WattsPerSquareMeterKelvin(8)
        val W23 = WattsPerSquareMeterKelvin(23)
        pipeLoc match
            case loc if loc.isInside => W8
            case loc if loc.isOutside =>
                // outside (valid air spaces)
                if (airSpaceDetailed.hasWidthFrom1to5cm) W8
                else if (airSpaceDetailed.isFullyOpened) W8
                // outside (no valid air space)
                else W23
            case loc =>
                throw new Exception(s"loc = $loc")
    }
    
    /**
     * Calcule le coefficient externe de transfert thermique α_a du conduit
     *
     * Ici, le diamètre hydraulique utilisé est bien celui de l'intérieur du conduit
     * et non de l'extérieur
     *
     * @param pipeCtx environnement du conduit (intérieur ou extérieur, avec ou sans lame d'air ventilée)
     * @param D_h diamètre hydraulique intérieur, en m
     * @return coefficient externe de transfert thermique dans le conduit de fumée α_a, en W/(m2 ⋅ K)
     */
    // def α_a_calc(
    //     // pipeT: ConnectorPipeT | ChimneyPipeT,
    //     pipeCtx: PipeContext,
    //     Dh: QtyD[Meter]
    // ): WattsPerSquareMeterKelvin = {

    //     val pipeLoc = pipeCtx.loc
    //     val airGap = pipeCtx.airGap

    //     val outsidePipesAreAirGappedWith1to5cm: Boolean =
    //         pipeCtx.outsidePipesAreAirGappedWith1to5cm

    //     val W8 = WattsPerSquareMeterKelvin(8)
    //     val W23 = WattsPerSquareMeterKelvin(23)

    //     // applicable pour conduit de raccordement ET conduit de fumée ?
    //     val wOutsideCoeff
    //         : WattsPerSquareMeterKelvin = // def wOutsideCoeff(part: ): WattsPerSquareMeterKelvin

    //         if (airGap.isFullyVentilatedInSameDirectionAsFlueGas) W8
    //         else if (outsidePipesAreAirGappedWith1to5cm) W8
    //         else if (pipeCtx.externalNonVentilatedLength <= Dh * 3.0) W8
    //         else W23 // (pipeCtx.externalNonVentilatedLength > 3*Dh)

    //     pipeLoc match
    //         case loc if loc.fullyInside => W8

    //         case loc if !loc.fullyInside && !loc.fullyOutside =>
    //             val wInside =
    //                 W8.value * loc.ratioInside.value
    //             val wOutside =
    //                 wOutsideCoeff.value * loc.ratioOutside.value

    //             WattsPerSquareMeterKelvin(
    //                 wInside +
    //                     wOutside
    //             )

    //         case loc if loc.fullyOutside =>
    //             // outside (valid air gaps)
    //             if (outsidePipesAreAirGappedWith1to5cm) W8
    //             else if (airGap.isFullyVentilatedInSameDirectionAsFlueGas) W8
    //             // outside (no valid air gap)
    //             else if (airGap.isFullyNonVentilated) W23
    //             else if (airGap.isNonExistent) W23
    //             else throw new Exception(s"loc = $loc")

    //         case loc =>
    //             throw new Exception(s"loc = $loc")
    // }

    // Section "5.9.1", Masse volumique des fumées (ρ_m)

    /**
     * Calcule la masse volumique moyenne des fumées ρ_m (formule 27)
     *
     * @param p_L pression atmosphérique extérieure, en Pa
     * @param R constante des gaz des fumées, en J/(kg.K)
     * @param T_m température moyenne des fumées, en K
     * @return ρ_m masse volumique moyenne des fumées, en kg/m3
     */
    def ρ_m_calc(
        p_L: QtyD[Pascal],
        R: JoulesPerKilogramKelvin,
        T_m: TempD[Kelvin]
    ): Density =
        val pL  = p_L   .toUnit[Pascal]                         .value
        val r   = R     .toUnit[Joule / (Kilogram * Kelvin)]    .value
        val tm  = T_m   .toUnit[Kelvin]                         .value
        (pL / ( r * tm)).withUnit[Kilogram / (Meter ^ 3)]

    // Section "5.9.2", Vitesse des fumées (w_m)

    /**
      * Calcule la vitesse moyenne des fumées w_m, en m/s
      *
      * @param A section transversale interne du conduit de fumée, en m2
      * @param m_dot débit massique des fumées (voir 5.5.1), en kg/s
      * @param ρ_m masse volumique moyenne des fumées, en kg/m 3
      * @return vitesse moyenne des fumées, en m/s
      */
    def w_m_calc(
        A: Area,
        m_dot: QtyD[Kilogram / Second],
        ρ_m: Density,
    ): QtyD[Meter / Second] = 
        m_dot / ( A * ρ_m )

    // Section "5.10", Détermination des pressions

    // Section "5.10.1.1"
    // Tirage au niveau de l'admission des fumées dans le conduit fonctionnant sous pression
    // négative (P_Z et P_Zmax )

    /**
      * tirage maximal au niveau de l’admission des fumées, en Pa.
      *
      * @param P_H tirage théorique disponible dû à l'effet de cheminée, en Pa ;
      * @param P_R perte de charge du conduit de fumée, en Pa ;
      * @return
      */
    def P_Zmax(
        P_H: Pressure,
        P_R: Pressure,
    ): Pressure = 
        P_H - P_R

    // Section "5.10.2"
    // "Tirage théorique disponible dû à l'effet de cheminée (P_H)"

    /**
      * tirage théorique disponible dû à l'effet de cheminée
      *
      * @param H hauteur utile du conduit de fumée, en m ;
      * @param ρ_L masse volumique de l'air extérieur (voir 5.7.4), en kg/m 3 ;
      * @param ρ_m masse volumique moyenne des fumées (voir 5.9.1), en kg/m 3 .
      * @return
      */
    def P_H_calc(
        H: QtyD[Meter],
        ρ_L: QtyD[Kilogram / (Meter ^ 3)],
        ρ_m: QtyD[Kilogram / (Meter ^ 3)]
    ): Pressure = {
        import gtypedefs.G
        H * G * (ρ_L - ρ_m)
    }

    // Section "5.10.3", "Perte de charge du conduit de fumée (P_R)"

    def P_R_dynamicPressure_calc(ρ_m: Density, w_m: FlowVelocity): Pressure = 
        ρ_m * w_m * w_m / 2

    def P_R_staticFriction_calc(
        Ψ: Dimensionless,
        L: QtyD[Meter],
        D_h: QtyD[Meter],
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        w_m: QtyD[Meter / Second],
        S_E: Dimensionless
    ): Pressure =
        if (D_h == 0.meters) 0.pascals
        else ( S_E.value * (Ψ.value * (L / D_h)) * P_R_dynamicPressure_calc(ρ_m, w_m) )

    def P_R_dynamicFriction_calc(
        Σ_ζ: Dimensionless,
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        w_m: QtyD[Meter / Second],
        S_E: Dimensionless
    ): Pressure =
        S_E.value * Σ_ζ.value * P_R_dynamicPressure_calc(ρ_m, w_m)

    /**
      * pression (de sécurité ?) due au changement de vitesse des fumées
      *
      * @param S_E coefficient de sécurité du débit (voir 5.7.8) ;
      * @param P_G différence de pression due au changement de vitesse des fumées dans le conduit, en Pa
      * @return
      */
    def P_R_velocityChange_calc(
        S_E: Dimensionless, 
        P_G: QtyD[Pascal]
    ): Pressure = 
        // coefficient de sécurité du débit pour la perte de charge due au changement de vitesse de flux
        // dans le conduit de fumée
        val S_EG: Dimensionless =
            if (P_G >= 0.pascals) S_E
            else 1.0.ea
        S_EG.value * P_G

    // Section 5.10.3.2
    // Différence de pression due au changement de vitesse des fumées dans le conduit, P_G

    /**
     * Calcule la variation de pression due au changement de vitesse des fumées dans le conduit, en Pa
     *
     * Pour w1 et w2 , ainsi que pour ρ1 et ρ2 , les valeurs moyennes de la section avant et après le changement de
     * vitesse peuvent être utilisées
     *
     * @param ρ1 masse volumique des fumées avant le changement de vitesse, en kg/m3
     * @param w1 vitesse des fumées avant le changement de vitesse, en m/s
     * @param ρ2 masse volumique des fumées après le changement de vitesse, en kg/m3
     * @param w2 vitesse des fumées après le changement de vitesse, en m/s
     * @return variation de pression due au changement de vitesse des fumées dans le conduit, en Pa
     */
    def P_G_calc(
        ρ1: QtyD[Kilogram / (Meter ^ 3)],
        w1: QtyD[Meter / Second],
        ρ2: QtyD[Kilogram / (Meter ^ 3)],
        w2: QtyD[Meter / Second]
    ): Pressure = {
        (
            ρ2.value * math.pow(w2.value, 2) / 2.0
            -
            ρ1.value * math.pow(w1.value, 2) / 2.0
        ).pascals
    }
    
    // Section 5.10.4
    // Pression de la vitesse du vent (P_L)
    
    /**
      * Pression de la vitesse du vent (P_L)
      */
    def P_L_calc(
        coastal_region: Boolean,
        chimney_termination: ChimneyTermination,
    ) = 
        val pl_not_adverse_cond = 0.pascals
        val pl_adverse_cond = coastal_region match
            case true  => 40.pascals
            case false => 25.pascals
        val chimney_location_on_roof_situation_is_not_adverse = chimney_termination.chimney_location_on_roof match
            case ChimneyLocationOnRoof(ChimneyHeightAboveRidgeline.MoreThan40cm, _, _, _, _) => true
            case ChimneyLocationOnRoof(
                ChimneyHeightAboveRidgeline.LessThan40cm, 
                Some(HorizontalDistanceBetweenChimneyAndRidgeline.MoreThan2m30), 
                _, 
                _, 
                _
            ) => true
            case ChimneyLocationOnRoof(
                ChimneyHeightAboveRidgeline.LessThan40cm, 
                Some(HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30), 
                Some(Slope.LessThan25Deg), 
                _, 
                _
            ) => true
            case ChimneyLocationOnRoof(
                ChimneyHeightAboveRidgeline.LessThan40cm, 
                Some(HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30), 
                Some(Slope.From25DegTo40Deg), 
                Some(OutsideAirIntakeAndChimneyLocations.OnSameSideOfRidgeline), 
                _
            ) => true
            case ChimneyLocationOnRoof(
                ChimneyHeightAboveRidgeline.LessThan40cm, 
                Some(HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30), 
                Some(Slope.From25DegTo40Deg), 
                Some(OutsideAirIntakeAndChimneyLocations.OnDifferentSidesOfRidgeline), 
                Some(HorizontalDistanceBetweenChimneyAndRidgelineBis.LessThan1m)
            ) => true
            case _ => false
        val adjacent_situation_is_adverse = chimney_termination.adjacent_buildings match
            case AdjacentBuildings(
                HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m,
                Some(HorizontalAngleBetweenChimneyAndAdjacentBuildings.MoreThan30Deg),
                Some(VerticalAngleBetweenChimneyAndAdjacentBuildings.MoreThan10DegAboveHorizon)
            ) => true
            case _ => false
        val adjacent_situation_is_not_adverse = !adjacent_situation_is_adverse
        if (chimney_location_on_roof_situation_is_not_adverse && adjacent_situation_is_not_adverse)
            pl_not_adverse_cond
        else
            pl_adverse_cond
    
    // Section "5.11.1"
    // Tirage minimal exigé au niveau de l’admission des fumées et tirage maximal admis
    // (P_Ze et P_Zemax), pression différentielle maximale et minimale au niveau de l’admission
    // des fumées dans le conduit (P_ZOe et P_ZOemin)

     /**
      * tirage maximal admis au niveau de l’admission des fumées dans le conduit, en Pa ;
      *
      * @param P_Wmax tirage maximal de l'appareil à combustion, en Pa ;
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air (voir 5.11.3), en Pa ;
      * @return
      */
    def P_Zemax(
        P_Wmax: Pressure,
        P_FV: Pressure,
        P_B: Pressure,
    ): Pressure =
        P_Wmax + P_FV + P_B

    /**
      * pression différentielle maximale au niveau de l'admission des fumées dans le conduit, en Pa ;
      *
      * @param P_WO pression différentielle maximale au niveau de la sortie de l'appareil à combustion, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air, en Pa.
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @return
      */
    def P_ZOe(
        P_WO: Pressure,
        P_B: Pressure,
        P_FV: Pressure,
    ): Pressure = 
        P_WO - P_B - P_FV

    /**
      * pression différentielle minimale au niveau de l'admission des fumées dans le conduit, en Pa ;
      *
      * @param P_WOmin pression différentielle minimale à la sortie de l'appareil à combustion, en Pa ;
      * @param P_B résultante de pression à l'alimentation en air, en Pa.
      * @param P_FV résultante de pression au conduit de raccordement des fumées, en Pa ;
      * @return
      */
    def P_ZOemin(
        P_WOmin: Pressure,
        P_B: Pressure,
        P_FV: Pressure,
    ): Pressure = 
        P_WOmin - P_B - P_FV

    // Section "5.11.3.2"
    // "Tirage théorique disponible dû à l'effet de cheminée du conduit de raccordement (P_HV)"

    def P_HV_calc(
        H_V: Length,
        ρ_L: Density,
        ρ_mV: Density
    ): Pressure =
        import gtypedefs.G
        H_V * G * (ρ_L - ρ_mV)

    // Section "5.11.4"
    // "Perte de charge de l'alimentation en air (P_B)"

    val P_B_without_ventilation_openings: Pressure = 4.0.pascals
    
    /**
      * Coefficient de sécurité du débit pour l'alimentation en air (la valeur de S EB est généralement 1,2)
      * @param pReq pressure requirements (min or max draft)
      * @return
      */
    def S_EB_calc(pReq: DraftCondition): Dimensionless = 1.2.unitless
    
    /**
      * Perte de charge de l'alimentation en air (P_B)
      *
      * @param ΨB coefficient de perte de charge due au frottement des ouvertures d'aération ou du conduit d'air de combustion ;
      * @param LB longueur des ouvertures d'aération ou du conduit d'air de combustion, en m ;
      * @param DhB diamètre hydraulique intérieur des ouvertures d'aération ou du conduit d'air de combustion, en m ;
      * @param ΣζB somme des coefficients de perte de charge due à un changement de direction et/ou de section transversale et/ou de débit massique dans les ouvertures d'aération ou le conduit d'air de combustion.
      * @param ρB masse volumique de l'air de combustion, en kg/m 3 ;
      * @param wB vitesse dans les ouvertures d'aération ou le conduit d'air de combustion, en m/s ;
      * @return Perte de charge de l'alimentation en air (P_B)
      */
    def P_B(
        pReq: DraftCondition,
        ΨB: Dimensionless,
        LB: Length,
        DhB: Length,
        ΣζB: Dimensionless,
        ρB: Density,
        wB: Velocity,
    ): Pressure = 
        S_EB_calc(pReq)
        * 
        (
            ΨB * LB / DhB 
            + 
            ΣζB
        )
        *
        ( ρB * (wB * wB) / 2.0 )

    // Section "5.12"
    // "Température de la paroi intérieure à la sortie du conduit de fumée (T_iob)"

    /**
     * Calcule de la température de la paroi intérieure à la sortie du conduit de fumée (T_iob)
     *
     * @param T_ob température des fumées à la sortie du conduit à une température d'équilibre, en K
     * @param k_ob coefficient de transfert thermique à la sortie du conduit de fumée à la température d'équilibre, en W/(m2.K)
     * @param α_i coefficient interne de transfert thermique, en W/(m2.K)
     * @param T_uo température de l'air ambiant à la sortie du conduit de fumée, en K
     * @return température de la paroi intérieure à la sortie du conduit de fumée, en K
     */
    def T_iob(
        T_ob: TempD[Kelvin],
        k_ob: WattsPerSquareMeterKelvin,
        α_i: WattsPerSquareMeterKelvin,
        T_uo: TempD[Kelvin]
    ): TempD[Kelvin] =
        (
            T_ob.value
            -
            (k_ob.value / α_i.value)
            *
            (T_ob.value - T_uo.value)
        ).degreesKelvin

    /**
      * Calcul le coefficient de transfert thermique à la sortie du conduit de fumée (k_ob) à la température d'équilibre
      *
      * @param α_i coefficient interne de transfert thermique, en W/(m2.K)
      * @param _1_Λ résistance thermique, en m2.K/W
      * @param _1_Λ_o résistance thermique de toute isolation supplémentaire de la partie du conduit de fumée au-dessus du toit relative au diamètre hydraulique interne par rapport au conduit, en m2.K/W
      * @param D_h diamètre hydraulique intérieur, en m
      * @param D_hao diamètre hydraulique extérieur à la sortie du conduit de fumée, en m
      * @param α_ao coefficient externe de transfert thermique à la sortie du conduit de fumée, en W/(m2.K)
      * @return coefficient de transfert thermique à la sortie du conduit de fumée (k_ob) à la température d'équilibre, en W/(m2.K)
      */
    def k_ob_calc(
        α_i: WattsPerSquareMeterKelvin,
        _1_Λ: SquareMeterKelvinPerWatt,
        _1_Λ_o: SquareMeterKelvinPerWatt,
        D_h: QtyD[Meter],
        D_hao: QtyD[Meter],
        α_ao: WattsPerSquareMeterKelvin
    ): WattsPerSquareMeterKelvin =
        WattsPerSquareMeterKelvin(
            1
            /
            (
                (1 / α_i.value)
                +
                _1_Λ.value
                +
                _1_Λ_o.value
                +
                D_h.value / (D_hao.value * α_ao.value)
            )
        )

    // Section "7.7", "Valeurs de base de calcul"
    // Section "7.7.1", "Températures de l'air"
    // Section "7.7.1.2", "Températures de l'air extérieur (T_L)"

    // same as 5.7.1.2 (see implementation there)

    // Section "7.8", "Détermination des températures"

    // IMPLEMENTED only for non-concentric ducts with high thermal resistance
    // Section "7.8.1","Détermination des températures"

    /** mean temperature of the combustion air (in K) */
    def T_mB_calc(ductType: DuctType, tL: T_L): Op[T_mB] =
        ductType match
            case DuctType.NonConcentricDuctsHighThermalResistance => 
                (tL: T_mB).validNel
            case _                                                => 
                // not implemented yet (non urgent or even useful ?)
                DuctTypeError("only non concentric ducts with high thermal resistance (> 0.65 m2.K/W) are implemented").invalidNel

    // Section "7.8.4", "Températures moyennes pour le calcul des pressions"

    /** moyenne des temperature moyenne par section */
    private def _T_mX_common_calc(
        TmXs: Seq[TKelvin]
    ): TKelvin = 
        val nSegX = TmXs.size
        val Σ_t_inv = TmXs.map(t => 1 / t.toUnit[Kelvin].value).sum
        ( nSegX / Σ_t_inv ).withTemperature[Kelvin]

    /**
      * température moyenne des fumées sur la longueur du conduit des fumées, en K ;
      *
      * @param nSeg nombre de segments dans le conduit des fumées
      * @param Tms températures moyennes des fumées sur la longueur des segments de conduit des fumées, en K ;
      * @return
      */
    def T_m_calc(Tms: Seq[TKelvin]): TKelvin = 
        _T_mX_common_calc(Tms)

    /**
      * température moyenne des fumées sur la longueur du conduit de raccordement des fumées, en K ;
      *
      * @param nSegV nombre de segments dans le conduit de raccordement des fumées
      * @param TmVs températures moyennes des fumées sur la longueur des segments de conduit de raccordement des fumées, en K ;
      * @return
      */
    def T_mV_calc(TmVs: Seq[TKelvin]): TKelvin = 
        _T_mX_common_calc(TmVs)

    /**
      * température moyenne des fumées sur la longueur du conduit d'air comburant, en K ;
      *
      * @param nSegB nombre de segments dans le conduit d'air comburant
      * @param TmBs températures moyennes des fumées sur la longueur des segments de conduit d'air comburant, en K ;
      * @return
      */
    def T_mB_calc(TmBs: Seq[TKelvin]): TKelvin = 
        _T_mX_common_calc(TmBs)


    // NOT IMPLEMENTED
    // Section "7.8.x","..."

    // Section "7.11.4", "Perte de charge de l'alimentation en air"

    // Section "7.11.4.1"
    // "Tirage dû à l'effet de cheminée du conduit d'air de combustion"

    /**
     * Calcule le tirage dû à l'effet de cheminée du conduit d'air de combustion P_HB
     *
     * @param H_B hauteur du conduit d'air comburant, en m
     * @param ρ_L masse volumique de l'air ambiant, en kg/m3
     * @param ρ_mB masse volumique moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en kg/m3
     * @return tirage dû à l'effet de cheminée du conduit d'air de combustion, en Pa
     */
    def P_HB_calc(
        H_B: QtyD[Meter],
        ρ_L: QtyD[Kilogram / (Meter ^ 3)],
        ρ_mB: QtyD[Kilogram / (Meter ^ 3)]
    ): Pressure = {
        import gtypedefs.G
        H_B * G * (ρ_L - ρ_mB)
    }

    // Section "7.11.4.2"
    // "Tirage dû à l'effet de cheminée du conduit de raccordement côté air"

    /**
     * Calcule le tirage dû à l'effet de cheminée du conduit de raccordement côté air, P_HBV
     *
     * @param H_BV hauteur du conduit de raccordement côté air, en m
     * @param ρ_L masse volumique de l'air ambiant, en kg/m3
     * @param ρ_mBV masse volumique moyenne de l'air de combustion sur la longueur du conduit de raccordement côté air, en kg/m3
     * @return tirage dû à l'effet de cheminée du conduit de raccordement côté air, en Pa
     */
    def P_HBV_calc(
        H_BV: QtyD[Meter],
        ρ_L: QtyD[Kilogram / (Meter ^ 3)],
        ρ_mBV: QtyD[Kilogram / (Meter ^ 3)]
    ): Pressure = {
        import gtypedefs.G
        H_BV * G * (ρ_L - ρ_mBV)
    }

    // Section "7.11.4.3", "Perte de charge du conduit d'air comburant P_RB"

    /**
     * Calcule la perte de charge du conduit d'air comburant P_RB
     *
     * @param P_GB variation de pression due au changement de vitesse du flux dans le conduit d'air comburant, en Pa
     * @param Ψ_B coefficient de frottement du conduit d'air comburant
     * @param L longueur du conduit d'air comburant
     * @param D_hB diamètre hydraulique du conduit d'air comburant
     * @param Σ_ζ_B somme des coefficients de perte de charge dans le conduit d'air comburant
     * @param ρ_mB masse volumique moyenne de l'air de combustion sur la longueur du conduit d'air comburant', en kg/m3
     * @param w_mB vitesse moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en m/s
     * @param S_EB coefficient de sécurité du débit pour le conduit d'air comburant
     * @return perte de charge du conduit d'air comburant, en Pa
     */
    def P_RB(
        P_GB: QtyD[Pascal],
        Ψ_B: Dimensionless,
        L: QtyD[Meter],
        D_hB: QtyD[Meter],
        Σ_ζ_B: Dimensionless,
        ρ_mB: QtyD[Kilogram / (Meter ^ 3)],
        w_mB: QtyD[Meter / Second],
        S_EB: Dimensionless
    ): Pressure = {

        // coefficient de sécurité du débit pour la perte de charge due au changement de vitesse de flux
        // dans le conduit de fumée
        val S_EGB: Dimensionless =
            if (P_GB >= 0.pascals) S_EB
            else 1.0.ea

        (
            S_EB.value
            *
            (Ψ_B.value * L.value / D_hB.value + Σ_ζ_B.value)
            *
            ρ_mB.value / 2 * math.pow(w_mB.value, 2)
            +
            S_EGB.value * P_GB.value
        ).pascals
    }

    /**
     * Résolution itérative du coefficient de perte de charge due au frottement dans le conduit
     *
     * Solve equation (135) in Ψ
     *
     * @param D_h diamètre hydraulique du conduit, en m
     * @param r valeur moyenne de rugosité de la paroi intérieure du conduit, en m
     * @param Re nombre de Reynolds dans le conduit
     * @param approxPrecision precision de l'approximation (iteration stops when two consecutive values differs less than this value)
     * @return coefficient de perte de charge due au frottement dans le conduit
     */
    def solvepsi(
        D_h: QtyD[Meter],
        r: QtyD[Meter],
        Re: Dimensionless,
        approxPrecision: Double = 1e-5
    ): Dimensionless =

        val Re_corrected: Dimensionless =
            if (Re >= 2300.0) Re else 2300.0.ea

        @tailrec
        def step(
            psi: Double,
            multipl: Double,
            lastPsi: Option[Double]
        ): Double =
            val gauche = 1 / (math.sqrt(psi))
            val droite =
                (-2.0)
                *
                math.log10(
                    2.51 / (Re_corrected.value * math.sqrt(psi))
                    +
                    r.value / (3.71 * D_h.value)
                )

            val psiDiff = lastPsi.map(last => math.abs(last - psi))

            if (psiDiff.isDefined && psiDiff.get <= approxPrecision)
                // println(s"psi = $psi | psiDiff=${psiDiff.get} | gauche $gauche | droite=$droite")
                psi
            else if (gauche < droite)
                step(
                    psi = psi * multipl,
                    multipl = multipl,
                    lastPsi = Some(psi)
                )
            else if (gauche > droite)
                step(
                    psi = psi / multipl,
                    multipl = multipl + ((1.0 - multipl) / 2.0),
                    lastPsi = Some(psi)
                )
            else psi

        step(psi = 1.0, multipl = 0.5, lastPsi = None).ea

    /** solvepsi for r = 0 */
    def solvepsi_smooth(
        D_h: QtyD[Meter],
        Re: Dimensionless,
        approxPrecision: Double = 1e-5
    ): Dimensionless =
        solvepsi(D_h, 0.meters, Re, approxPrecision)

    /**
     * Calcule la variation de pression due au changement de vitesse du flux dans le conduit d'air comburant, en Pa
     *
     * @param ρ_mB masse volumique moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en kg/m3
     * @param w_mB vitesse moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en m/s
     * @return variation de pression due au changement de vitesse du flux dans le conduit d'air comburant, en Pa
     */
    def P_GB(
        ρ_mB: QtyD[Kilogram / (Meter ^ 3)],
        w_mB: QtyD[Meter / Second]
    ): Pressure =
        (ρ_mB.value * math.pow(w_mB.value, 2) / 2.0).pascals

    // Section "Annexe A - Calcul de la résistance thermique"

    val COEFFICIENT_OF_FORM_MAX_SIDES_RATIO = 1.5

    def coefficient_of_form(forShape: PipeShape): Either[ThermalResistance_Error.SideRatioTooHighForRectangularForm, CoefficientOfForm] = 
        import cats.syntax.either.catsSyntaxEitherId
        forShape match
            case PipeShape.Circle(_)          => CoefficientOfForm.wrap(1.0).asRight
            case PipeShape.Square(_)          => CoefficientOfForm.wrap(1.10).asRight
            case rect @ PipeShape.Rectangle(a, b)    => 
                val ratio = if (a >= b) (a/b) else (b/a)
                if (ratio <= COEFFICIENT_OF_FORM_MAX_SIDES_RATIO) CoefficientOfForm.wrap(1.10).asRight
                else ThermalResistance_Error.SideRatioTooHighForRectangularForm(rect).asLeft

    // def Λinverse(
    //     layers: ThermalResistance.Layers
    // ): SquareMeterKelvinPerWatt = 
    //     layers.Λinverse
    
    // Section "Tableau B.1"

    /** coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s) */
    def f_m1_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => 6.89
        case FuelType.Pellets  => 6.66

    /** coefficient de calcul du débit massique des fumées, en g/(kW ⋅ s) */
    def f_m2_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => 0.076
        case FuelType.Pellets  => 0.060

    /** coefficient de calcul du débit massique de l'air de combustion, en g/(kW ⋅ s) */
    def f_m3_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => 0.001
        case FuelType.Pellets  => 0.002

    def f_R1_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => 0.0038
        case FuelType.Pellets  => 0.0037
    
    def f_R2_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => -0.0041
        case FuelType.Pellets  => -0.0041

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K⋅%) */
    def f_c0_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => 15.4
        case FuelType.Pellets  => 11.6

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 2 ⋅%) */
    def f_c1_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => 0.016
        case FuelType.Pellets  => 0.015

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 3 ⋅%) */
    def f_c2_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => -0.000011
        case FuelType.Pellets  => -0.000012

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en 1/% */
    def f_c3_calc(comb: FuelType): Double = comb match
        case FuelType.WoodLog30pHumidity => 0.0111
        case FuelType.Pellets  => 0.0091
    
    /** coefficient de calcul de la teneur en vapeur d'eau des fumées, en % */
    def f_w_calc(comb: FuelType): QtyD[Percent] = comb match
        case FuelType.WoodLog30pHumidity => 90.percent
        case FuelType.Pellets  => 127.percent
   
    /**
      * teneur en vapeur d'eau des fumées, en %
      *
      * formule (B.5)
      * 
      * @param comb type de combustible
      * @param σ_CO2 teneur en dioxyde de carbone des fumées sèches
      * @return
      */
    def σ_H2O_from_σ_CO2_calc(
        comb: FuelType,
        σ_CO2: QtyD[Percent]
    ): QtyD[Percent] = 
        val f_w = f_w_calc(comb)
        (
            (
                100.0
                /
                ( 1.0 + (f_w / σ_CO2).value )
            )
            + 1.1
        ).withUnit[Percent]

    /**
      * teneur en dioxyde de carbone des fumées sèches, en %
      *
      * formule (B.5) inversée
      * 
      * @param comb type de combustible
      * @param σ_H2O teneur en en vapeur d'eau des fumées, en %
      * @return
      */
    @deprecated def σ_CO2_from_σ_H2O_calc(
        comb: FuelType,
        σ_H2O: QtyD[Percent]
    ): QtyD[Percent] = 
        if (σ_H2O.value == 1.1) 
            0.percent
        else
            val f_w = f_w_calc(comb).value
            (
                f_w / ( 100 / (σ_H2O.value - 1.1) - 1 )
            ).percent

    /**
     * pression partielle de la vapeur d'eau, en Pa
     *
     * formule (B.6)
     * 
     * @param σ_H2O teneur en en vapeur d'eau des fumées, en %
     * @param p_L pression de l'air extérieur, en Pa ;
     * @return
     */
    def p_D_from_σ_H2O_calc(σ_H2O: Percentage, p_L: Pressure): Pressure =
        σ_H2O.value / 100 * p_L

    /**
      * température du point de rosée, en °C ;
      * 
      * formule (B.7)
      *
      * @param p_D pression partielle de la vapeur d'eau, en Pa ;
      * @return
      */
    def t_p_calc(p_D: Pressure): TCelsius = 
        val pD = p_D.toUnit[Pascal].value
        ( 4077.9 / ( 23.6448 - math.log(pD) ) - 236.67 ).degreesCelsius

    /**
         * teneur en vapeur d'eau des fumées, en %
         * 
         * formule (B.12)
         *
         * @param P_D pression partielle de la vapeur d'eau, en Pa ;
         * @param p_L pression de l'air extérieur, en Pa ;
         * @return
         */
    def σ_H2O_from_PD_pL(
        P_D: Pressure,
        p_L: Pressure,
    ): QtyD[Percent] = 
        P_D / p_L * 100.0.percent
    /**
     * pression partielle de la vapeur d'eau, en Pa
     *
     * formule (B.13)
     * @param T_P température du point de rosée, en Kelvin ;
     * @return
     */
    def p_D_from_T_p_calc(T_P: TKelvin): Pressure = 
        val TP = T_P.toUnit[Kelvin].value
        math.exp( 23.6448 - 4077.9 / (TP - 36.48) ).pascals

    // Section "Tableau B.6"

    val EN13384_1_2015_A1_2019_table_B6 = 
        """|temp	dn	rth
           |40.0	0.00	0.0
           |40.0	0.01	0.123
           |40.0	0.02	0.147
           |40.0	0.03	0.153
           |40.0	0.04	0.152
           |40.0	0.05	0.150
           |100.0	0.00	0.0
           |100.0	0.01	0.087
           |100.0	0.02	0.101
           |100.0	0.03	0.101
           |100.0	0.04	0.100
           |100.0	0.05	0.099
           |150.0	0.00	0.0
           |150.0	0.01	0.065
           |150.0	0.02	0.075
           |150.0	0.03	0.075
           |150.0	0.04	0.074
           |150.0	0.05	0.074
           |200.0	0.00	0.0
           |200.0	0.01	0.050
           |200.0	0.02	0.055
           |200.0	0.03	0.055
           |200.0	0.04	0.055
           |200.0	0.05	0.054""".stripMargin

    override def thermalResistanceFromConductivity_forCylindricalLayers(
        y: CoefficientOfForm,
        D_hn: Length,
        λ_n: ThermalConductivity,
        dn: Length,
    ): SquareMeterKelvinPerWatt = 
        y.unwrap * D_hn / ( 2.0 * λ_n) * math.log( ( (D_hn + 2.0 * dn) / D_hn).value )

    override def deadAirSpaceThermalResistance(
        t_emittingSurfaceTemp: TCelsius, 
        dn_airSpaceWidth: Length,
        innerShape: PipeShape,
    ): Either[ThermalResistance_Error.CouldNotComputeThermalResistance, SquareMeterKelvinPerWatt] = 
        // __INTERPRETATION__
        val t_interpol = 
            if (t_emittingSurfaceTemp < 40.degreesCelsius) 40.degreesCelsius
            else if (t_emittingSurfaceTemp > 200.degreesCelsius) 200.degreesCelsius
            else t_emittingSurfaceTemp

        val dn_in_meters = dn_airSpaceWidth.to_m

        val ei = afpma.firecalc.engine.utils.readtable.fromTSVTableRaw_withBiInterpolatation(
            resName = "EN 13384 // Table B.6",
            tsvTableRawString = EN13384_1_2015_A1_2019_table_B6,
            xHeader = "temp",
            yHeader = "dn", 
            zHeader = "rth",
            xi = t_interpol.to_degC.value,
            yi = dn_in_meters.value,
            xMinMax = (40.0, 200.0),
            yMinMax = (0.00, 0.05)
        )

        ei match
            case Left(err: ReadTableError) => 
                ThermalResistance_Error.CouldNotComputeThermalResistance(
                    err.msg
                ).asLeft
            case Right(rth) => 

                // __INTERPRETATION__ (?)
                
                // Found 2 possible interpretations here
                
                // 1. rth is directly the thermal resistance of the cylindrical air space.
                // However rth does not depend on radius or diameter, which is confounding
                
                // SquareMeterKelvinPerWatt(rth).asRight


                // 2. rth given in the table corresponds to an air space between 2 planar layers seperated by distance dn
                // We should then convert the given 'planar' rth :
                // - first, to a conductivity λ using R = e/λ formula
                // - then to a 'cylindrical' rth using formula from NOTE 1 of table B.6
                val λ_planar: QtyD[Watt / (Meter * Kelvin)] = dn_in_meters / SquareMeterKelvinPerWatt(rth)
                val dhn = innerShape.dh
                
                coefficient_of_form(innerShape)
                    .leftMap(sideRatioTooHigh => ThermalResistance_Error.CouldNotComputeThermalResistance(sideRatioTooHigh.msg))
                    .map: y =>
                        val rth = thermalResistanceFromConductivity_forCylindricalLayers(
                            y,
                            dhn,
                            λ_planar,
                            dn_in_meters
                            )
                        // println(s"""
                        //             |y              = $y
                        //             |dhn            = ${dhn.show}
                        //             |λ_planar       = ${λ_planar}
                        //             |dn_in_meters   = ${dn_in_meters.show}
                        //             |
                        //             |rth            => ${rth.show}
                        //             |""".stripMargin)
                        rth
    
    def openedAirSpaceThermalResistance(t_emittingSurfaceTemp: TCelsius, dn_airSpaceWidth: Length): SquareMeterKelvinPerWatt = 
        // Source: NF_DTU_24.1_P1:2006 (annulé)
        // est à considérer comme un vide d'air ouvert, à
        // condition que l'épaisseur de ce vide d'air soit supérieure ou égale à 30 mm. La résistance thermique du vide d'air est alors obtenue
        // conformément à la note 4 du paragraphe 4.2.4.3.2 de la norme NF EN 12391-1 mais en prenant un coefficient de transmission
        // thermique extérieur de 5 W/m.K.
        ??? // TODO:

end EN13384_1_A1_2019_Formulas

object EN13384_1_A1_2019_Formulas:

    export afpma.firecalc.engine.standard.{EN13384_Error as Error}
    export afpma.firecalc.engine.standard.{NuCalcError}
    export afpma.firecalc.engine.standard.{
        ReIsAbove10million,
        PsiRatioIsGreaterThan3,
        PrandtlOutOfBound,
        PrandtlTooSmall,
        PrandtlTooBig
    }

    type Op[A] = ValidatedNel[Error, A]

    val make = new EN13384_1_A1_2019_Formulas {}

end EN13384_1_A1_2019_Formulas