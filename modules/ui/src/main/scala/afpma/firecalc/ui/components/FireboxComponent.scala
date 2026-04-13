/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import scala.scalajs.js
import scala.scalajs.js.annotation.*

import _root_.coulomb.policy.standard.given
import afpma.laminar.form.Form
import afpma.laminar.form.Form.as_HtmlElement
import afpma.laminar.form.FormRenderer
import afpma.laminar.form.daisyui.DaisyUIVertical
import afpma.laminar.form.derivation.FormDerivation
import afpma.laminar.form.i18n.FormI18nExtensions.autoOverwriteFieldNames
import io.scalaland.chimney.dsl.*
import io.taig.babel.Locale

case class FireboxComponent(
    v: Var[Firebox]
)                          (using Locale, DisplayUnits)
    extends Component:

    val vertical_form  = new VerticalFormCommonInstances()
    given FormRenderer = DaisyUIVertical

    import FireboxComponent.*

    val showEcolabeledV1Img = firebox_var.signal.map:
        case eco: Firebox.Ecolabeled if eco.version == Left("Version 1") => true
        case _ => false

    val showEcolabeledV2Img = firebox_var.signal.map:
        case eco: Firebox.Ecolabeled if eco.version == Right("Version 2") => true
        case _ => false

    val showAFPMAPRSEImg = firebox_var.signal.map:
        case _: Firebox.AFPMA_PRSE => true
        case _ => false

    val nodeSeq_Ecolabeled_V1 = Seq(
        div(cls := "col-span-2 justify-center align-center", ecolabeled_v1_side_img),
        div(cls := "row-span-1", ecolabeled_front_img                              ),
        div(cls := "row-span-1", ecolabeled_top_img                                )
    )

    val nodeSeq_Ecolabeled_V2 = Seq(
        div(cls := "col-span-2 justify-center align-center", ecolabeled_v2_side_img),
        div(cls := "row-span-1", ecolabeled_front_img                              ),
        div(cls := "row-span-1", ecolabeled_top_img                                )
    )

    val nodeSeq_AFPMAPRSE = Seq(
        div(cls := "row-span-1 col-span-1", afpma_prse_side_img),
        div(cls := "row-span-1 col-span-1", afpma_prse_top_img )
    )

    lazy val node =
        import vertical_form.given
        div(
            cls := "grid grid-flow-col grid-cols-3 grid-rows-2 gap-10",
            div(cls := "row-span-2", v.as_HtmlElement, outputResults),
            children(nodeSeq_Ecolabeled_V1) <-- showEcolabeledV1Img,
            children(nodeSeq_Ecolabeled_V2) <-- showEcolabeledV2Img,
            children(nodeSeq_AFPMAPRSE) <-- showAFPMAPRSEImg
        )

    val DISABLED_TRUE_SIG = Var(true).signal

    given Form[QtyD[Meter]] =
        val dual: DualCommonInstances = new DualCommonInstances()
        import defaultable.qty_d.meter.zero
        import ValidateVarCommonInstances.valid_always.given_ValidateVar_AlwaysValid
        dual.given_dual_Length_cm.form(disabled = DISABLED_TRUE_SIG)

    given Form[afpma.firecalc.engine.models.en15544.firebox.Ecolabeled.Outputs] =
        import hastranslations.given
        FormDerivation
            .derived[afpma.firecalc.engine.models.en15544.firebox.Ecolabeled.Outputs]
            .autoOverwriteFieldNames

    val outputResults = div(
        child <-- firebox_var.signal.map:
            case fb: Firebox.Ecolabeled =>
                import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given
                val eco = fb.transformInto[afpma.firecalc.engine.models.en15544.firebox.Ecolabeled]
                Var(eco.outputs).as_HtmlElement
            case _ => emptyNode
    )

object FireboxComponent:

    val showDimensionsSummary: DisplayUnits ?=> ShowUsingLocale[Firebox] = showUsingLocale: cc =>
        I18N.firebox.dimensions_summary_w_d_h(
            cc.firebox_width.to_cm.showP_orImpUnits[Inch],
            cc.firebox_depth.to_cm.showP_orImpUnits[Inch],
            cc.firebox_height.to_cm.showP_orImpUnits[Inch]
        )

    @js.native @JSImport("/assets/img/afpma_prse_side.png", JSImport.Default)
    object JS_afpma_prse_side_URL    extends js.Object
    @js.native @JSImport("/assets/img/afpma_prse_top.png", JSImport.Default)
    object JS_afpma_prse_top_URL     extends js.Object
    @js.native @JSImport("/assets/img/ecolabeled_front.png", JSImport.Default)
    object JS_ecolabeled_front_URL   extends js.Object
    @js.native @JSImport("/assets/img/ecolabeled_top.png", JSImport.Default)
    object JS_ecolabeled_top_URL     extends js.Object
    @js.native @JSImport("/assets/img/ecolabeled_v1_side.png", JSImport.Default)
    object JS_ecolabeled_v1_side_URL extends js.Object
    @js.native @JSImport("/assets/img/ecolabeled_v2_side.png", JSImport.Default)
    object JS_ecolabeled_v2_side_URL extends js.Object

    // Convert js.Object to string
    val afpma_prse_side_url   : String = JS_afpma_prse_side_URL.asInstanceOf[String]
    val afpma_prse_top_url    : String = JS_afpma_prse_top_URL.asInstanceOf[String]
    val ecolabeled_front_url  : String = JS_ecolabeled_front_URL.asInstanceOf[String]
    val ecolabeled_top_url    : String = JS_ecolabeled_top_URL.asInstanceOf[String]
    val ecolabeled_v1_side_url: String = JS_ecolabeled_v1_side_URL.asInstanceOf[String]
    val ecolabeled_v2_side_url: String = JS_ecolabeled_v2_side_URL.asInstanceOf[String]

    // Now use this URL - Vite will resolve these properly for both browser and Electron
    def afpma_prse_side_img    = img(src := afpma_prse_side_url, widthAttr(400))
    def afpma_prse_top_img     = img(src := afpma_prse_top_url, widthAttr(400))
    def ecolabeled_front_img   = img(src := ecolabeled_front_url)
    def ecolabeled_top_img     = img(src := ecolabeled_top_url)
    def ecolabeled_v1_side_img = img(src := ecolabeled_v1_side_url)
    def ecolabeled_v2_side_img = img(src := ecolabeled_v2_side_url)
