/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import afpma.firecalc.ui.Component

import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.CompositeKeySetter

final case class Indicator(
  config_and_sig_mods: Seq[(IndicatorConfig, Signal[Boolean])],
  title: HtmlElement,
  subtitle_sig: Signal[String],
)(child: HtmlElement)
  extends Component:

  val configs_mods = config_and_sig_mods.map((icfg, bsig) =>
    icfg.classes <-- bsig
  )
  
  // def body: HtmlElement = ???
  lazy val node: HtmlElement =
    div(
      cls := "inline-block mx-2",
      div(
        cls := "flex flex-col",
        div(
          configs_mods,
          cls := "relative border-3 border-solid rounded-lg",
          div(
            configs_mods,
            cls := "absolute top-0 left-1/2 transform -translate-x-1/2 -translate-y-3/4 px-2 text-center font-semibold bg-white w-4/5",
            p(
              cls := "leading-tight text-xs",
              title
            )
          ),
          div(
            cls := "flex items-center",
            child,
          )
        ),
        div(
          configs_mods,
          cls := "text-center text-xs",
          p(
            text <-- subtitle_sig,
          )
        )
      )
    )

final case class IndicatorConfig(style: IndicatorConfig.IndicatorStyle):
  def classes: CompositeKeySetter[HtmlAttr[String], HtmlElement] = 
    style.classes

object IndicatorConfig:

    val amber = IndicatorConfig(IndicatorStyle.Amber)
    val green = IndicatorConfig(IndicatorStyle.Green)
    val rose = IndicatorConfig(IndicatorStyle.Rose)
    val sky = IndicatorConfig(IndicatorStyle.Sky)

    sealed trait IndicatorStyle extends Product with Serializable:
        self =>
        def classes: CompositeKeySetter[HtmlAttr[String], HtmlElement] = 
            self match
                case IndicatorStyle.Amber => cls("text-amber-400 border-amber-400")
                case IndicatorStyle.Green => cls("text-green-500 border-green-500")
                case IndicatorStyle.Rose  => cls("text-rose-500 border-rose-500")
                case IndicatorStyle.Sky   => cls("text-sky-500 border-sky-500")

    object IndicatorStyle:
        case object Amber extends IndicatorStyle
        case object Green extends IndicatorStyle
        case object Rose extends IndicatorStyle
        case object Sky extends IndicatorStyle