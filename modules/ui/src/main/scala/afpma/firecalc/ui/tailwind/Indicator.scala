/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import afpma.firecalc.ui.Component

import com.raquo.laminar.api.L.*

final case class Indicator(
    style_sig   : Signal[Option[IndicatorConfig]],
    title       : HtmlElement,
    subtitle_sig: Signal[String]
)                         (child: HtmlElement)
    extends Component:

    private lazy val style_classes: Signal[String] =
        style_sig.map(_.fold("")(_.classString))

    lazy val node: HtmlElement =
        div(
            cls := "inline-block mx-2",
            div(
                cls := "flex flex-col",
                div(
                    cls <-- style_classes,
                    cls := "relative border-3 border-solid rounded-lg",
                    div(
                        cls <-- style_classes,
                        cls := "absolute top-0 left-1/2 transform -translate-x-1/2 -translate-y-3/4 px-2 text-center font-semibold bg-white w-4/5",
                        p(
                            cls := "leading-tight text-xs",
                            title
                        )
                    ),
                    div(
                        cls := "flex items-center",
                        child
                    )
                ),
                div(
                    cls <-- style_classes,
                    cls := "text-center text-xs",
                    p(
                        text <-- subtitle_sig
                    )
                )
            )
        )

final case class IndicatorConfig(style: IndicatorConfig.IndicatorStyle):
    def classString: String = style.classString

object IndicatorConfig:

    val amber = IndicatorConfig(IndicatorStyle.Amber)
    val green = IndicatorConfig(IndicatorStyle.Green)
    val rose  = IndicatorConfig(IndicatorStyle.Rose)
    val sky   = IndicatorConfig(IndicatorStyle.Sky)

    sealed trait IndicatorStyle extends Product with Serializable:
        def classString: String = this match
            case IndicatorStyle.Amber => "text-amber-400 border-amber-400"
            case IndicatorStyle.Green => "text-green-500 border-green-500"
            case IndicatorStyle.Rose  => "text-rose-500 border-rose-500"
            case IndicatorStyle.Sky   => "text-sky-500 border-sky-500"

    object IndicatorStyle:
        case object Amber extends IndicatorStyle
        case object Green extends IndicatorStyle
        case object Rose  extends IndicatorStyle
        case object Sky   extends IndicatorStyle
