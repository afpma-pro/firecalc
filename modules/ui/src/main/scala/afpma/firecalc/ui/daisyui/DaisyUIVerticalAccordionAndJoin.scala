/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import cats.Show
import cats.data.Validated
import cats.syntax.option.*
import cats.syntax.show.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.panels.*

import afpma.firecalc.dto.all.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import io.taig.babel.Locale

final case class DaisyUIVerticalAccordionAndJoin(
)(using Locale, DisplayUnits) extends Component:
    import DaisyUIVerticalAccordionAndJoin.*

    val name = htmlAttr[String]("name", StringAsIsCodec)

    val localConditionsUI   = LocalConditionsUI.make()
    val projectDescrUI      = ProjectDescrUI.make().node
    val stoveParamsUI       = StoveParamsUI()._form

    lazy val subtotal_quadrions_sig = results_en15544_outputs.map:
            case Validated.Valid(outputs) =>
                outputs.pipesResult_15544 match
                    case Validated.Valid(presults) =>
                        val out = presults.Σ_pu.andThen(Σ_pu =>
                            presults.`Σ_ph-Σ_pR-Σ_pu`.map(`Σ_ph-Σ_pR-Σ_pu` =>
                                QuadrionSubtotal(
                                    ph = presults.Σ_ph.value.some, 
                                    pr = (-1.0 * presults.Σ_pR.value).some, 
                                    pu = (-1.0 * Σ_pu.value).some,
                                    sigma = `Σ_ph-Σ_pR-Σ_pu`.value.some,
                                )
                            )
                        )
                        out.toOption
                    case _ => None
            case _ => None

    lazy val penalty_sig = en13384_P_L_sig.map:
        case Validated.Valid(pL) if pL != 0.pascals =>
            QuadrionSubtotal(
                ph = None, 
                pr = None, 
                pu = None,
                sigma = (-1.0 * pL).value.some,
            ).some
        case _ =>
            None

    val node = div(
        cls := "relative flex flex-wrap items-center justify-center gap-2",
        DaisyUIVerticalAccordionAndJoin.Element(
            idx = 0,
            title = Title.WithTitleOnly(
                I18N.panels.client_project,
                xtra_sig = project_descr_var.signal.map: prj =>
                    div(cls := "flex flex-row max-w-3/4",
                        div(cls := "flex-auto w-60",
                            text <-- clientProjectDataVar.signal.map { cpd =>
                                s"${cpd.customer.first_name} ${cpd.customer.last_name}"
                            },
                        ),
                        div(cls := "flex-auto w-60", prj.reference),
                        div(cls := "flex-none w-42", prj.date),
                    ).some
            ),
            content = projectDescrUI,
        ),
        DaisyUIVerticalAccordionAndJoin.Element(
            idx = 0,
            title = Title.WithTitleOnly(
                I18N.panels.output_and_other_parameters,
                xtra_sig = stove_params_var.signal.map: pdm =>
                    given Show[QtyD[Kilogram]] = shows.defaults.show_Kilograms_1
                    given showPound0: Show[QtyD[Pound]] = shows.defaults.show_Pound_0
                    val mb_or_pn = pdm
                        .maximum_load.map(mb => s"${I18N.technical_specifications.maximum_load_short} ${mb.showP_orImpUnits[Pound]}")
                        .orElse(
                            pdm.nominal_heat_output.map(_.showP_orImpUnits[BTU / Hour]))
                        .getOrElse("")
                    div(cls := "flex flex-row max-w-3/4",
                        div(cls := "flex-grow w-36", mb_or_pn),
                        div(cls := "flex-grow w-36", s"${I18N.technical_specifications.heating_cycle} = ${pdm.heating_cycle.show}"),
                        div(cls := "flex-grow w-36", I18N.technical_specifications.facing_type_sentence(pdm.facing_type.show.toLowerCase())),
                    ).some,
            ),
            content = stoveParamsUI
        ),
        DaisyUIVerticalAccordionAndJoin.Element(
            idx = 0,
            title = Title.WithQuadrionSubtotal(
                I18N.panels.geographical_location_and_external_factors,
                xtra_sig = 
                    local_conditions_var.signal
                    .combineWith(en13384_P_L_sig)
                    .map: (lc, pL) =>
                        given Show[Length] = shows.defaults.show_Meters_0
                        given Show[QtyD[Foot]] = shows.defaults.show_Foot_0
                        pL match
                            case Validated.Invalid(_) => None
                            case Validated.Valid(pL) =>
                                val pL_formatted = pL.showP
                                val zShow = lc.altitude.showP_orImpUnits[Foot]
                                val warningCoastalRegion = s"${Option.when(lc.coastal_region)(s"(${I18N_UI.local_conditions.coastal_region})").getOrElse("")}"
                                val warningPenalty = if (pL != 0.pascals) s"(${I18N.en13384.terms.P_L.toLowerCase()} = ${pL_formatted})" else ""
                                p(s"$zShow $warningCoastalRegion $warningPenalty").some
                ,
                quadrionSubtotal_sig = penalty_sig 
            ),
            content = localConditionsUI
        ),
        AirIntakePipePanel(),
        FireboxPanel(),
        FluePipePanel(),
        ConnectorPipePanel(),
        ChimneyPipePanel(),
        DaisyUIVerticalAccordionAndJoin.Element(
            idx = 10,
            title = Title.WithQuadrionSubtotal(
                I18N.panels.total,
                quadrionSubtotal_sig = subtotal_quadrions_sig
            ),
            allowCollapse = false
        )
        
        // childWithJoin
    )

end DaisyUIVerticalAccordionAndJoin

object DaisyUIVerticalAccordionAndJoin:

    val tooltip = dataAttr("tip")

    case class Element(
        idx: Int,
        title: Title,
        content: Content = div(),
        opened: Var[Boolean] = Var(false),
        allowCollapse: Boolean = true
    ) extends Component:
        val node =
            div(
                tabIndex := idx,
                cls      := "collapse bg-base-100 border-base-300 border overflow-visible",
                cls      <-- opened.signal.map(b => if (b) "collapse-open" else "collapse-close"),
                when(allowCollapse)(
                    cls := "collapse-arrow",
                ),
                div(
                    cls := "collapse-title font-semibold border-base-300 rounded-t-2xl",
                    cls <-- opened.signal.map: o =>
                        if (o) "bg-secondary text-secondary-content sticky w-full top-42 z-100"
                        // else "bg-primary text-primary-content"
                        else "bg-(--color-vlight-ocre) relative rounded-b-2xl"
                    ,
                    title,
                    when(allowCollapse)(
                        styleAttr := "cursor: pointer;",
                        onClick --> { _ => 
                            if (opened.now()) opened.set(false)
                            else opened.set(true)
                        }
                    )
                ),
                div(
                    cls := "relative px-0 collapse-content text-sm top-0",
                    content
                ),
            )

    sealed trait Title(
        title: String,
        xtra_sig: Signal[Option[HtmlElement]],
        quadrionSubtotal_sig: Signal[Option[Title.QuadrionSubtotal]]
    ) extends Component:

        protected def TitleChild = div(cls := "flex-none w-64", title)
        protected def XtraFlexChild: Node = div(cls := "flex-1 w-12", child.maybe <-- xtra_sig)

        val node = div(
            cls := "flex items-center",
            TitleChild,
            XtraFlexChild, // grows and shrink
            child <-- quadrionSubtotal_sig.map(_.map(_.node).getOrElse(emptyNode)),
            div(cls := "flex-none w-2") // right margin
        )

    object Title:

        def Divider = DaisyUIDividerVertical().node

        def maybeDividerIfNonEmpty(x: String | Option[Double]) = x match
            case str: String if str.nonEmpty        => Divider
            case od: Option[Double] if od.nonEmpty  => Divider
            case _                                  => emptyNode

        case class QuadrionSubtotal(
            ph: String | Option[Double],
            pr: String | Option[Double],
            pu: String | Option[Double],
            sigma: String | Option[Double],
        )(using Locale) extends Component:
            
            val node = div(
                cls := "flex-none inline",
                div(
                    cls := "flex",
                    QuadrionValueWithTooltip(
                        ph,
                        ttHeader = I18N_UI.tooltips.ph_static_pressure,
                        ttSubheader = "(Pa)".some
                    ),
                    maybeDividerIfNonEmpty(ph),
                    QuadrionValueWithTooltip(
                        pr,
                        ttHeader = I18N_UI.tooltips.pr_loss_to_friction,
                        ttSubheader = "(Pa)".some
                    ),
                    maybeDividerIfNonEmpty(pr),
                    QuadrionValueWithTooltip(
                        pu,
                        ttHeader = I18N_UI.tooltips.pu_loss_to_turn,
                        ttSubheader = "(Pa)".some
                    ),
                    maybeDividerIfNonEmpty(pu),
                    QuadrionValueWithTooltip(
                        sigma,
                        ttHeader = I18N_UI.tooltips.net_gain_or_loss,
                        ttSubheader = "(Pa)".some
                    ),
                )
            )

        final case class WithTitleOnly(
            title: String,
            xtra_sig: Signal[Option[HtmlElement]] = Signal.fromValue(None)
        ) extends Title(title, xtra_sig, quadrionSubtotal_sig = Signal.fromValue(None))

        final case class WithQuadrionSubtotal(
            title: String,
            xtra_sig: Signal[Option[HtmlElement]] = Signal.fromValue(None),
            quadrionSubtotal_sig: Signal[Option[Title.QuadrionSubtotal]]
        ) extends Title(title, xtra_sig, quadrionSubtotal_sig)

        final case class QuadrionValueWithTooltip(
            value: String | Option[Double],
            ttHeader: String,
            ttSubheader: Option[String]
        ) extends Component:
            val node =
                val common      = (cls := "w-10")
                val color       = value match
                    case _: String => emptyMod
                    case _: Option[Double] => cls := "text-base-800"
                val mods        = Seq(common, color)
                val valueString = value match
                    case s: String => s
                    case Some(d)   => s"${"%.1f".format(d)}"
                    case None      => ""
                div(
                    cls := "flex-none",
                    DaisyUITooltip(
                        ttContent = div(
                            cls := "p-2",
                            div(
                                cls := "font-semibold",
                                ttHeader
                            ),
                            ttSubheader.map(txt =>
                                div(cls := "font-light text-base-100", txt)
                            )
                        ),
                        element = div(
                            cls := "text-base-content/50 text-end",
                            mods,
                            valueString
                        )
                    )
                )
    end Title

    type Content = HtmlElement

end DaisyUIVerticalAccordionAndJoin
