/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import afpma.firecalc.ui.AFPMAAccordion.QuadrionValues

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*
import com.raquo.laminar.keys.HtmlAttr

final case class AFPMAAccordion(
  prefix: String,
  subtotal: QuadrionValues
)(
  children: AFPMAAccordion.Child*
) extends Component:
  
  val node =
    div(
      idAttr := AFPMAAccordion.idParent(prefix),
      AFPMAAccordion.Header(prefix, subtotal),
      children
    )

object AFPMAAccordion:

  opaque type QuadrionHeader = HtmlElement
  val QuadrionHeader: QuadrionHeader = 
    Quadrion(
      QuadrionEl("ph"),
      QuadrionEl("pr"),
      QuadrionEl("pu"),
      QuadrionEl("Σ"),
    )

  opaque type QuadrionValues = HtmlElement
  def QuadrionValues(ph: Double, pr: Double, pu: Double, Σ: Double, useBold: Boolean = false): QuadrionValues =
    Quadrion(
      QuadrionEl(ph, useBold),
      QuadrionEl(pr, useBold),
      QuadrionEl(pu, useBold),
      QuadrionEl(Σ, useBold),
    )

  def EmptyQuadrionValues(): QuadrionValues = 
    Quadrion(
      QuadrionEl("", false),
      QuadrionEl("", false),
      QuadrionEl("", false),
      QuadrionEl("", false),
    )
  
  private final case class QuadrionEl(value: String | Double, useBold: Boolean = false) extends Component:
    val node = 
      val kls = value match
        case _: String => "w-10 text-center text-gray-500"
        case d: Double => 
          if (d < 0) "w-10 text-center text-amber-400" 
          else "w-10 text-center text-green-500"
      val klsWithStyle = if (useBold) kls + " font-semibold" else kls   
      val n = value match
        case s: String => div(cls := klsWithStyle, s)
        case d: Double => div(cls := klsWithStyle, s"${"%.1f".format(d)}")
      div(cls := "px-2", n)

  private def Quadrion(qa: QuadrionEl, qb: QuadrionEl, qc: QuadrionEl, qd: QuadrionEl): HtmlElement =
    div(
      cls := "mr-24 ml-auto flex gap-4",
      qa, qb, qc, qd
    )

  object Attrs:
    val dataTeCollapseInit: HtmlAttr[String]      = htmlAttr("data-te-collapse-init", StringAsIsCodec)
    val dataTeCollapseItem: HtmlAttr[String]      = htmlAttr("data-te-collapse-item", StringAsIsCodec)
    val dataTeCollapseCollapsed: HtmlAttr[Boolean] = htmlAttr("data-te-collapse-collapsed", BooleanAsAttrPresenceCodec)
    val dataTeCollapseShow: HtmlAttr[Boolean]      = htmlAttr("data-te-collapse-show", BooleanAsAttrPresenceCodec)
    val dataTeTarget: HtmlAttr[String]            = htmlAttr("data-te-target", StringAsIsCodec)
    val dataTeParent: HtmlAttr[String]            = htmlAttr("data-te-parent", StringAsIsCodec)

  private def idParent(prefix: String) = s"${prefix}-parent"
  private def idHeading(prefix: String, idx: String) = s"${prefix}-heading-$idx"
  private def idCollapse(prefix: String, idx: String) = s"${prefix}-collapse-$idx"

  final case class Header(prefix: String, quadrionValues: QuadrionValues) extends Component:
    import AFPMAAccordion.Attrs.*

    val node = 
      div(
        makeForIdx("0a")(QuadrionHeader),
        makeForIdx("0b")(quadrionValues)
      )

    private def makeForIdx(idx: String)(quadrion: QuadrionHeader | QuadrionValues) =
      // val idx = idx
      div(
        cls := "bg-white dark:border-neutral-600 dark:bg-neutral-800",
        h2(
          cls    := "mb-0",
          idAttr := idHeading(prefix, idx),
          button(
            cls                     := "group relative flex w-full items-center bg-white px-5 py-2 text-left text-base text-neutral-800 transition [overflow-anchor:none] hover:z-[2] focus:z-[3] focus:outline-none dark:bg-neutral-800 dark:text-white [&:not([data-te-collapse-collapsed])]:bg-white [&:not([data-te-collapse-collapsed])]:text-primary [&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(229,231,235)] dark:[&:not([data-te-collapse-collapsed])]:bg-neutral-800 dark:[&:not([data-te-collapse-collapsed])]:text-primary-400 dark:[&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(75,85,99)]",
            `type`                  := "button",
            dataTeCollapseInit      := "",
            dataTeCollapseCollapsed := true,
            dataTeTarget            := s"#${idCollapse(prefix, idx)}",
            aria.expanded           := false,
            aria.controls           := idCollapse(prefix, idx),
            p(""),
            div(
              cls := "relative flex w-full gap-4 text-center",
              div(),
              quadrion
            ),
            span(
              cls := "-mr-1 ml-4 h-5 w-5 shrink-0 rotate-[-180deg] fill-[#336dec] transition-transform duration-200 ease-in-out group-[[data-te-collapse-collapsed]]:mr-0 group-[[data-te-collapse-collapsed]]:rotate-0 group-[[data-te-collapse-collapsed]]:fill-[#212529] motion-reduce:transition-none dark:fill-blue-300 dark:group-[[data-te-collapse-collapsed]]:fill-white"
            )
          )
        ),
        div(
          idAttr             := idCollapse(prefix, idx),
          cls                := "!visible hidden",
          dataTeCollapseItem := "",
          aria.labelledBy    := idHeading(prefix, idx),
          dataTeParent       := s"#${idParent(prefix)}",
        )
      )
    

  enum ChildPosition:
    case First, Middle, Last

  final case class Child(prefix: String, title: HtmlElement, quadrionV: Option[QuadrionValues], idx: Int, maxIdx: Int, expanded: Boolean = false)(children: HtmlElement*) 
  extends Component:
    import ChildPosition.*
    import AFPMAAccordion.Attrs.*

    given Conversion[Int, String] = (_.toString)

    val node = 
        val childPosition = 
          if (idx == 1) First
          else if (idx == maxIdx) Last
          else Middle

        div(
          cls := (childPosition match 
            case First  => "rounded-t-lg border border-neutral-200 bg-white dark:border-neutral-600 dark:bg-neutral-800"
            case Middle => "border border-t-0 border-neutral-200 bg-white dark:border-neutral-600 dark:bg-neutral-800"
            case Last   => "rounded-b-lg border border-t-0 border-neutral-200 bg-white dark:border-neutral-600 dark:bg-neutral-800"),
          h2(
            cls    := "mb-0",
            idAttr := idHeading(prefix, idx),
            button(
              cls                     := (childPosition match
                case First  => "group relative flex w-full items-center rounded-t-[15px] border-0 bg-white px-5 py-4 text-left text-base text-neutral-800 transition [overflow-anchor:none] hover:z-[2] focus:z-[3] focus:outline-none dark:bg-neutral-800 dark:text-white [&:not([data-te-collapse-collapsed])]:bg-white [&:not([data-te-collapse-collapsed])]:text-primary [&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(229,231,235)] dark:[&:not([data-te-collapse-collapsed])]:bg-neutral-800 dark:[&:not([data-te-collapse-collapsed])]:text-primary-400 dark:[&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(75,85,99)]"
                case Middle => "group relative flex w-full items-center rounded-none border-0 bg-white px-5 py-4 text-left text-base text-neutral-800 transition [overflow-anchor:none] hover:z-[2] focus:z-[3] focus:outline-none dark:bg-neutral-800 dark:text-white [&:not([data-te-collapse-collapsed])]:bg-white [&:not([data-te-collapse-collapsed])]:text-primary [&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(229,231,235)] dark:[&:not([data-te-collapse-collapsed])]:bg-neutral-800 dark:[&:not([data-te-collapse-collapsed])]:text-primary-400 dark:[&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(75,85,99)]"
                case Last   => "group relative flex w-full items-center border-0 bg-white px-5 py-4 text-left text-base text-neutral-800 transition [overflow-anchor:none] hover:z-[2] focus:z-[3] focus:outline-none dark:bg-neutral-800 dark:text-white [&:not([data-te-collapse-collapsed])]:bg-white [&:not([data-te-collapse-collapsed])]:text-primary [&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(229,231,235)] dark:[&:not([data-te-collapse-collapsed])]:bg-neutral-800 dark:[&:not([data-te-collapse-collapsed])]:text-primary-400 dark:[&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(75,85,99)] [&[data-te-collapse-collapsed]]:rounded-b-[15px] [&[data-te-collapse-collapsed]]:transition-none"),
              `type`                  := "button",
              dataTeCollapseInit      := "",
              dataTeCollapseCollapsed := !expanded,
              // dataTeCollapseShow      := expanded,
              dataTeTarget            := s"#${idCollapse(prefix, idx)}",
              aria.expanded           := expanded,
              aria.controls           := idCollapse(prefix, idx),
              title,
              quadrionV.getOrElse(EmptyQuadrionValues()),
              span(
                cls := (childPosition match
                  case First  => "-mr-1 ml-4 h-5 w-5 shrink-0 rotate-[-180deg] fill-[#336dec] transition-transform duration-200 ease-in-out group-[[data-te-collapse-collapsed]]:mr-0 group-[[data-te-collapse-collapsed]]:rotate-0 group-[[data-te-collapse-collapsed]]:fill-[#212529] motion-reduce:transition-none dark:fill-blue-300 dark:group-[[data-te-collapse-collapsed]]:fill-white"
                  case Middle => "-mr-1 ml-4 h-5 w-5 shrink-0 rotate-[-180deg] fill-[#336dec] transition-transform duration-200 ease-in-out group-[[data-te-collapse-collapsed]]:mr-0 group-[[data-te-collapse-collapsed]]:rotate-0 group-[[data-te-collapse-collapsed]]:fill-[#212529] motion-reduce:transition-none dark:fill-blue-300 dark:group-[[data-te-collapse-collapsed]]:fill-white"
                  case Last   => "-mr-1 ml-4 h-5 w-5 shrink-0 rotate-[-180deg] fill-[#336dec] transition-transform duration-200 ease-in-out group-[[data-te-collapse-collapsed]]:mr-0 group-[[data-te-collapse-collapsed]]:rotate-0 group-[[data-te-collapse-collapsed]]:fill-[#212529] motion-reduce:transition-none dark:fill-blue-300 dark:group-[[data-te-collapse-collapsed]]:fill-white"),
                svg.svg(
                  svg.xmlns       := "http://www.w3.org/2000/svg",
                  svg.fill        := "none",
                  svg.viewBox     := "0 0 24 24",
                  svg.strokeWidth := "1.5",
                  svg.stroke      := "currentColor",
                  svg.cls         := "h-6 w-6",
                  svg.path(
                    svg.strokeLineCap  := "round",
                    svg.strokeLineJoin := "round",
                    svg.d              := "M19.5 8.25l-7.5 7.5-7.5-7.5"
                  )
                )
              )
            )
          ),
        div(
          idAttr             := idCollapse(prefix, idx),
          cls                := s"!visible ${if (expanded) "" else "hidden"}",
          dataTeCollapseItem := "",
          dataTeCollapseShow := expanded,
          aria.labelledBy    := idHeading(prefix, idx),
          dataTeParent       := s"#${idParent(prefix)}",
          div(
            cls := "px-5 py-4",
            children
          )
        )
      )
    