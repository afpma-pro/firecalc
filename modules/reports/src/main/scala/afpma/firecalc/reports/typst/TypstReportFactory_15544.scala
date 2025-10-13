/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports.typst

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.models.en15544.std.Inputs
import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.engine.models.Pipes_EN15544_Alg
import afpma.firecalc.engine.models.Pipes_EN15544_Strict
import afpma.firecalc.engine.models.Pipes_EN15544_MCE
import io.taig.babel.Locale
import afpma.firecalc.dto.common.ProjectDescr
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.utils.ShowAsTable
import afpma.firecalc.reports.typst.TypShow.sanitized
import afpma.firecalc.engine.utils.getOrThrow
import afpma.firecalc.engine.models.LocalRegulations
import afpma.firecalc.engine.models.EmissionsAndEfficiencyValues
import afpma.firecalc.engine.models.en13384.std.ReferenceTemperatures
import io.taig.babel.Language
import io.taig.babel.Languages
import io.taig.babel.Locales
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.utils.BuildInfo

trait TypstReportFactory_15544[
    Pipes <: Pipes_EN15544_Strict | Pipes_EN15544_MCE
](
    val en15544_app: EN15544_V_2023_Application_Alg[Inputs[Pipes]],
    val isDraft: Boolean
)(using Locale):

    val showAsTable_15544_instances =
        new afpma.firecalc.engine.ops.en15544.ShowAsTableInstances()
    val showAsTable_13384_instances =
        new afpma.firecalc.engine.ops.en13384.ShowAsTableInstances()
    val showAsTable_instances       =
        new afpma.firecalc.engine.ops.ShowAsTableInstances()

    import showAsTable_15544_instances.given
    import showAsTable_13384_instances.given
    import showAsTable_instances.given

    val stove_proj_15544_strict: v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg

    // TODO: remove me => report should now when to use which draft and load condition
    given params: en15544_app.Params_15544

    import TypShow.given

    // Typst sections by appearing order

    def imports: String =
        s"""|#import "@preview/fancy-units:0.1.1": num, unit, qty, fancy-units-configure, add-macros
            |#import "@preview/based:0.1.0": base64
            |""".stripMargin

    // Language as an ISO 639-1/2/3 language code (Typst requirement)
    // See: https://typst.app/docs/reference/text/text/#parameters-lang
    // See: https://en.wikipedia.org/wiki/ISO_639
    def default_txt_lang = summon[Locale] match
        case loc => loc.language.value

    private def draft_watermark_background: String =
        if (isDraft) {
            s"""|place(
                |      center + horizon,
                |      rotate(45deg,
                |        text(
                |          size: 4em,
                |          fill: red.transparentize(70%),
                |          weight: "bold",
                |          block[
                |            DRAFT \\
                |            \\*\\*\\*  \\
                |            THIS DOCUMENT IS NOT CERTIFIED
                |          ]
                |        )
                |      )
                |    )""".stripMargin
        } else {
            ""
        }

    def global_configuration: String =
        val backgroundContent = if (isDraft) {
            s"""|background: {
                |    $draft_watermark_background
                |  },""".stripMargin
        } else {
            ""
        }
        
        s"""|#fancy-units-configure(
            |  per-mode: "slash",
            |  unit-separator: sym.dot,
            |)
            |
            |#set document(
            |  title: "${stove_proj_15544_strict.project.reference}",
            |  author: "Association Française du Poêle Maçonné Artisanal (AFPMA)",
            |)
            |#set text(lang: "$default_txt_lang", size: 10pt)
            |#set table(stroke: 0.4pt)
            |
            |#set page(
            |  margin: (top: 30pt),
            |  $backgroundContent
            |  footer-descent: 0% + 0pt,
            |  footer: {
            |    set text(size: 9pt, style: "italic")
            |
            |    line(length: 100%, stroke: 0.5pt + rgb("#aaa"))
            |
            |    grid(
            |      columns: (1fr, auto, 1fr),
            |
            |      align(top + left)[
            |        *${I18N.reports.document.software_label} : ${I18N.reports.document.software_name}* \\
            |        ${I18N.reports.document.versions_label} : ${I18N.reports.document.reports_module_version.apply(BuildInfo.reportsBaseVersion)} ${I18N.reports.document.engine_module_version.apply(BuildInfo.engineVersion)} \\
            |        ${if (isDraft) I18N.reports.document.no_certification_text else I18N.reports.document.certification_text_with_source_and_date} \\
            |        \\
            |      ],
            |
            |      align(bottom + center)[
            |        page #context counter(page).display("1/1", both: true)
            |      ],
            |
            |      align(top + right)[
            |        *${stove_proj_15544_strict.project.reference.sanitized}* \\
            |        CALC_ID : (none) \\
            |        Date : #datetime.today().display("[day]/[month]/[year]") \\
            |        \\
            |      ],
            |    )
            |  }
            |)
            |
            |#show heading: smallcaps
            |#show heading: set align(center)
            |#show heading: set text(
            |  weight: "regular",
            |  size: 1em
            |)
            |""".stripMargin

    import java.util.Base64
    import java.nio.file.Files
    import afpma.firecalc.config.ConfigPathResolver

    def logo_base64_and_format: Option[(String, String)] = {
        ConfigPathResolver.resolveLogoPath("reports") match {
            case Some(logoPath) if Files.exists(logoPath) =>
                try {
                    val bytes = Files.readAllBytes(logoPath)
                    val base64 = Base64.getEncoder().encodeToString(bytes)
                    val format = logoPath.toString.toLowerCase() match {
                        case f if f.endsWith(".jpg") || f.endsWith(".jpeg") => "jpg"
                        case f if f.endsWith(".png") => "png"
                        case _ => "jpg" // fallback
                    }
                    Some((base64, format))
                } catch {
                    case ex: Exception =>
                        println(s"Warning: Could not load logo from $logoPath: ${ex.getMessage}")
                        None
                }
            case _ =>
                println("No logo file found, continuing without logo")
                None
        }
    }
    
    def main_header: String =
        logo_base64_and_format match {
            case Some((base64, format)) =>
                s"""|#grid(
                    |  columns: (60pt, 1fr),
                    |  align(top + left)[
                    |    #image(
                    |       base64.decode("$base64"),
                    |       format: "$format",
                    |       width: 60pt
                    |    )
                    |  ],
                    |  align(center + horizon)[
                    |    #set text(size: 1.5em)
                    |    #smallcaps[*${I18N.reports.document.title}*]
                    |  ]
                    |)
                    |""".stripMargin
            case None =>
                s"""|#grid(
                    |  columns: (1fr),
                    |  align(center + horizon)[
                    |    #set text(size: 1.5em)
                    |    #smallcaps[*${I18N.reports.document.title}*]
                    |  ]
                    |)
                    |""".stripMargin
        }

    def level_1_description: String =
        heading_1(I18N.reports.headings.input_data)

    def project_description: String =
        stove_proj_15544_strict.project.typ

    def technical_specifications: String =
        TypShow
        .mkFromShowAsTable[TechnicalSpecficiations](
            maxWidthForFirstColumn = false
        )
        .showAsTyp(en15544_app.outputs.technicalSpecs)

    def firebox_descr: String =
        en15544_app.inputs.design.firebox.typ

    def level_1_en15544: String =
        heading_1(I18N.reports.headings.compliance_en15544)

    def en15544_citedConstraints: String = 
        en15544_app.citedConstraints.typ

    def en15544_pressureRequirement: String = 
        en15544_app.pressureRequirement_EN15544.getOrThrow.typ

    def en15544_t_chimney_wall_top: String = 
        en15544_app.t_chimney_wall_top.typ

    def en15544_emissions_and_efficiency_values: String = 
        given LocalRegulations = stove_proj_15544_strict.localRegulations
        TypShow
        .mkFromShowAsTable[EmissionsAndEfficiencyValues](
            maxWidthForFirstColumn = false
        )
        .showAsTyp(en15544_app.emissions_and_efficiency_values)

    def en15544_flue_gas_triple_of_variates: String = 
        en15544_app.flue_gas_triple_of_variates.getOrThrow.typ

    def en15544_estimated_output_temperatures: String = 
        en15544_app.estimated_output_temperatures.typ

    def en15544_pipesResult: String = 
        s"""|#[
            |  #set page(flipped: true)
            |  #set text(size: 6pt)
            |  ${en15544_app.outputs.pipesResult_15544.getOrThrow.typ}
            |]
            |""".stripMargin

    def level_1_en13384: String =
        heading_1(I18N.reports.headings.compliance_en13384)

    def en13384_heatingAppliance_final: String = 
        en15544_app.en13384_heatingAppliance_final.getOrThrow.typ

    def en13384_localConditions: String = 
        en15544_app.inputs.localConditions.typ

    def en13384_windPressure: String = 
        en15544_app.en13384_application.P_L.typ

    def en13384_temperatureRequirements: String = 
        en15544_app.temperatureRequirements_EN13384.typ

    def en13384_pressureRequirements: String = 
        en15544_app.pressureRequirements_EN13384.getOrThrow.typ

    def en13384_reference_temperatures: String = 
        TypShow
        .mkFromShowAsTable[ReferenceTemperatures](
            maxWidthForFirstColumn = false
        )
        .showAsTyp(en15544_app.outputs.reference_temperatures)

    /* Build typst document (string representation) */
    final def build(): String =
        Seq(
            imports,
            global_configuration,
            main_header,
            level_1_description,
            project_description,
            technical_specifications,
            firebox_descr,
            page_break,
            // 15544
            level_1_en15544,
            en15544_citedConstraints,
            en15544_pressureRequirement,
            en15544_t_chimney_wall_top,
            en15544_emissions_and_efficiency_values,
            page_break,
            en15544_flue_gas_triple_of_variates,
            en15544_estimated_output_temperatures,
            en15544_pipesResult,
            page_break,
            // 13384
            level_1_en13384,
            en13384_heatingAppliance_final,
            en13384_localConditions,
            en13384_windPressure,
            page_break,
            en13384_temperatureRequirements,
            en13384_pressureRequirements,
            page_break,
            en13384_reference_temperatures,
        )
            .filter(_.trim.nonEmpty)
            .mkString("\n\n")

    // Private methods / helpers

    private val page_break = "#pagebreak()"

    private def heading_1(txt: String) =
        s"""|#block(
            |  width: 100%,
            |  height: 2em,
            |  fill: rgb("eee"),
            |  align(center + horizon)[= $txt]
            |)""".stripMargin
