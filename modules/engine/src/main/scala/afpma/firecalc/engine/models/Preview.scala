/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import cats.*
import cats.data.ValidatedNel
import cats.syntax.all.*

import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.utils.VNelString
import afpma.firecalc.engine.standard.MecaFlu_Error

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.units.coulombutils.{*, given}
import io.taig.babel.Locale

case class Preview(
    sectionType: String,
    sectionId: String,
    sectionName: String,
    sectionDefinition: String,
    dhi: Option[Length],
    dhe: Option[Length],
    asd: Option[AirSpaceDetailed],
    tamb: Option[TCelsius],
    lambda: Option[WattsPerMeterKelvin],
    thermalResistance: Option[SquareMeterKelvinPerWatt],
    n_flows: Option[NbOfFlows],
    mass_flow: Option[MassFlow],
    velocity_start: Option[Velocity],
    velocity_middle: Option[Velocity],
    velocity_end: Option[Velocity],
    gas_temp_start: TempD[Celsius],
    gas_temp_middle: TempD[Celsius],
    gas_temp_end: TempD[Celsius],
    density_middle: Option[Density],
    density_mean: Option[Density],
    dynamicPressure: Option[Pressure],
    zeta: Option[ζ],
    dynamicFriction: ValidatedNel[MecaFlu_Error, Pressure],
    roughness: Option[Roughness],
    staticFriction: Pressure,
    vChangeFriction: Pressure,
    standingPressure: Pressure,
    length: Option[Length],
    elev_gain: Option[Length],
    angle: Option[Angle],
    innerShape: Option[PipeShape],
    pipeLoc: Option[PipeLocation],
)

object Preview:

    extension (pres: PipeResult)
        def toPreviews(using Locale): Vector[Preview] = 
            pres match
                case pres: PipeResult.WithSections => 
                    pres.elements.map(el => Preview.fromPipeSectionResult(el))
                case pres: PipeResult.WithoutSections => 
                    Vector(Preview.fromPipeResultWithoutSections(pres))

        def toPreviewAndShowValuesAsSeq(using Locale): Seq[Seq[String]] = 
            pres match
                case pres: PipeResult.WithSections => 
                    val details = pres.elements.map(el => Preview.fromPipeSectionResult(el).showValuesAsSeq)
                    val summary_headers = List("length", "h", "ζ", "pu", "pRs", "pRg", "pH")
                    val summary_values = List(
                        pres.lengthSum.showP,
                        pres.heightSum.showP,
                        pres.ζ.map(_.showO).getOrElse(""),
                        pres.pu.toOption.map(_.showP_IfNonZero).getOrElse(""),
                        pres.pRs.showP_IfNonZero,
                        pres.pRg.showP_IfNonZero,
                        pres.ph.showP_IfNonZero,
                    )
                    val summary = make_summary_line(
                        summary_headers, 
                        summary_values, 
                        descr_idx = Some(0), 
                        descr_value = s"-> ${I18N.subtotal.toUpperCase()}")
                    details.appended(summary).appended(empty_line)
                case pres: PipeResult.WithoutSections => 
                    Preview.fromPipeResultWithoutSections(pres).showValuesAsSeq :: empty_line :: Nil
    
    def fromPipeSectionResult(
        psr: PipeSectionResult[?]
    )(using Locale): Preview = 
        import psr.*
        Preview(
            sectionType         = section_typ.show,
            sectionId           = section_id.show,
            sectionName         = section_name,
            sectionDefinition   = descr.toString,
            dhi                 = descr match
                case el: afpma.firecalc.engine.models.en13384.pipedescr.StraightSection => 
                    el.innerShape.dh.some
                case _ => None
            ,
            dhe                 = descr match
                case el: afpma.firecalc.engine.models.en13384.pipedescr.StraightSection => 
                    el.outer_shape.dh.some
                case _ => None
            ,
            asd                 = psr.air_space_detailed,
            tamb                = psr.temperature_amb,
            lambda              = descr match
                case el: afpma.firecalc.engine.models.en13384.pipedescr.StraightSection => 
                    el.layers match
                        case Nil => None
                        case h :: Nil => h match
                            case AppendLayerDescr.FromLambda(_, lamb)   => lamb.some
                            case AppendLayerDescr.FromLambdaUsingThickness(_, lamb) => lamb.some
                            case _ => None
                        case _ => None
                case _ => None
            ,
            thermalResistance   = descr match
                case el: afpma.firecalc.engine.models.en13384.pipedescr.StraightSection => 
                    el.layers match
                        case Nil => None
                        case h :: Nil => h match
                            case AppendLayerDescr.FromThermalResistance(_, tr)   => tr.some
                            case AppendLayerDescr.FromThermalResistanceUsingThickness(_, tr) => tr.some
                            case _ => thermal_resistance
                        case _ => thermal_resistance
                case _ => thermal_resistance
            ,
            n_flows             = n_flows.some,
            // add resistance thermique ???
            mass_flow           = mass_flow.some,
            velocity_start      = v_start.some,
            velocity_middle     = v_middle,
            velocity_end        = v_end.some,
            gas_temp_start      = gas_temp_start,
            gas_temp_middle     = gas_temp_middle,
            gas_temp_end        = gas_temp_end,
            density_middle      = density_middle,
            density_mean        = density_mean,
            dynamicPressure     = pd,
            zeta                = psr.zeta,
            dynamicFriction     = pu,
            roughness           = roughness,
            staticFriction      = pRs,
            vChangeFriction     = pRg,
            standingPressure    = ph,
            length              = section_length.some,
            elev_gain               = descr match
                case el: afpma.firecalc.engine.models.en13384.pipedescr.StraightSection => el.elevation_gain.some
                case el: afpma.firecalc.engine.models.en15544.pipedescr.StraightSection => el.elevation_gain.some
                case _ => None,
            angle               = descr match
                case el: afpma.firecalc.engine.models.en13384.pipedescr.DirectionChange => el.angleN1.some
                case el: afpma.firecalc.engine.models.en15544.pipedescr.DirectionChange => el.angleN1.some
                case _ => None,
            innerShape           = psr.innerShape_middle.some,
            pipeLoc             = descr match
                case el: afpma.firecalc.engine.models.en13384.pipedescr.StraightSection => el.pipeLoc.some
                case _: afpma.firecalc.engine.models.en15544.pipedescr.PipeElDescr => PipeLocation.BoilerRoom.some
                case _ => None
        )

    private def fromPipeResultWithoutSections(
        prwo: PipeResult.WithoutSections
    )(using Locale): Preview = 
        import prwo.*
        Preview(
            sectionType         = typ.show,
            sectionId           = "-",
            sectionName         = "-",
            sectionDefinition   = "-",
            dhi                 = None,
            dhe                 = None,
            asd                 = None,
            tamb                = None,
            lambda              = None,
            thermalResistance   = None,
            n_flows             = None,
            mass_flow           = None,
            velocity_start      = v_start,
            velocity_middle     = None,
            velocity_end        = v_end,
            gas_temp_start      = gas_temp_start,
            gas_temp_middle     = gas_temp_middle,
            gas_temp_end        = gas_temp_end,
            density_middle      = density_middle,
            density_mean        = density_mean,
            dynamicPressure     = pd,
            zeta                = None,
            dynamicFriction     = pu,
            roughness           = None,
            staticFriction      = pRs,
            vChangeFriction     = pRg,
            standingPressure    = ph,
            length              = None,
            elev_gain               = None,
            angle               = None,
            innerShape           = None,
            pipeLoc             = None,
        )

    given showHeadersAsSeq: Locale => List[String] =
        List(
            "type",
            "id",
            "name",
            "length",
            "geom",
            "h",
            "α",
            // "definition",
            // "dhi",
            // "dhe",
            // "λ",
            "//",
            "R th.",
            "air space",
            I18N.pipe_location._column_header,
            "t amb.",
            // "ṁ",
            // "t start",
            "t",
            // "t end",
            "ρ",
            // "v start",
            "v",
            // "v end",
            // "pd",
            "ζ",
            "pu",
            "kf",
            "pRs",
            "pRg",
            "pH",
        )

    private def _find_idx_for(header: String)(using Locale): Option[Int] = 
        val headers = showHeadersAsSeq
        val idx = headers.indexOf(header)
        if (idx != -1) Some(idx) else None

    private def _find_indices_for(headers: List[String])(using Locale): Option[List[Int]] = 
        headers.map(_find_idx_for).sequence[Option, Int]
    
    private def empty_line(using Locale) = Vector.fill(showHeadersAsSeq.size)("")

    def make_summary_line(summary_headers: List[String], summary_values: List[String], descr_idx: Option[Int] = None, descr_value: String = "")(using Locale): List[String] =
        val summary_indices = _find_indices_for(summary_headers).getOrElse(throw new IllegalStateException(s"make sur headers contains '${summary_headers.mkString(", ")}'"))
        val summary = (summary_indices.zip(summary_values)).foldLeft(empty_line): 
            case (z, (idx, sh)) => z.updated(idx, sh)
        val descr_idx_auto = summary_indices.min - 2
        val descr_idx_final = descr_idx.getOrElse(if (descr_idx_auto > 0) descr_idx_auto else 0)
        summary.updated(descr_idx_final, descr_value).toList
    
    extension (self: Preview)
        def showValuesAsSeq(using Locale): List[String] = 
            import self.*
            List(
                sectionType,
                sectionId,
                sectionName,
                length              .fold("-")(l => if (l == 0.cm) "-" else l.showP),
                innerShape           .map(_.show).getOrElse("-"),
                elev_gain               .map(h => if (h == 0.cm) "-" else h.showP).getOrElse("-"),
                angle               .map(a => if (a == 0.degrees) "-" else a.showP).getOrElse("-"),
                // sectionDefinition,
                // dhi                 .map(_.show).getOrElse("-"),
                // dhe                 .map(_.show).getOrElse("-"),
                // lambda              .map(_.show).getOrElse("-"),
                n_flows             .map(_.show).getOrElse("-"),
                thermalResistance   .map(_.showP).getOrElse("-"),
                asd                 .map(_.show).getOrElse("-"),
                pipeLoc             .map(_.show).getOrElse("-"),
                tamb                .map(_.show).getOrElse("-"),
                // mass_flow           .map(_.show).getOrElse("-"),
                // gas_temp_start      .show,
                gas_temp_middle     .show,
                // gas_temp_end        .show,
                density_middle      .map(_.show).getOrElse("-"),
                // density_mean        .map(_.show).getOrElse("-"),
                // velocity_start      .show,
                velocity_middle     .map(_.show).getOrElse("-"),
                // velocity_end        .show,
                // dynamicPressure     .map(_.show).getOrElse("-"),
                zeta                .map(_.showP).getOrElse("-"),
                dynamicFriction     .map(_.showP_IfNonZero).fold(errs => errs.head.msg, identity),
                roughness           .map(_.show).getOrElse("-"),
                staticFriction      .showP_IfNonZero,
                vChangeFriction     .showP_IfNonZero,
                standingPressure    .showP_IfNonZero,
            )
