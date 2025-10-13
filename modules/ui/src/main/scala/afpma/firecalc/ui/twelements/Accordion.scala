/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.twelements

import afpma.firecalc.ui.Component

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.keys.HtmlAttr

// import scalatags.Text.all._

final case class Accordion() extends Component:

  val dataTeCollapseInit: HtmlAttr[String] = htmlAttr("data-te-collapse-init", StringAsIsCodec)
  val dataTeCollapseItem: HtmlAttr[String] = htmlAttr("data-te-collapse-item", StringAsIsCodec)
  val dataTeCollapseCollapsed: HtmlAttr[String] = htmlAttr("data-te-collapse-collapsed", StringAsIsCodec)
  val dataTeCollapseShow: HtmlAttr[String] = htmlAttr("data-te-collapse-show", StringAsIsCodec)
  val dataTeTarget: HtmlAttr[String]       = htmlAttr("data-te-target", StringAsIsCodec)
  val dataTeParent: HtmlAttr[String]       = htmlAttr("data-te-parent", StringAsIsCodec)

  val node =
    div(
      idAttr := "accordionExample",
      div(
        cls := "rounded-t-lg border border-neutral-200 bg-white dark:border-neutral-600 dark:bg-neutral-800",
        h2(
          cls := "mb-0",
          idAttr := "headingOne",
          button(
            cls                           := "group relative flex w-full items-center rounded-t-[15px] border-0 bg-white px-5 py-4 text-left text-base text-neutral-800 transition [overflow-anchor:none] hover:z-[2] focus:z-[3] focus:outline-none dark:bg-neutral-800 dark:text-white [&:not([data-te-collapse-collapsed])]:bg-white [&:not([data-te-collapse-collapsed])]:text-primary [&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(229,231,235)] dark:[&:not([data-te-collapse-collapsed])]:bg-neutral-800 dark:[&:not([data-te-collapse-collapsed])]:text-primary-400 dark:[&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(75,85,99)]",
            `type`                        := "button",
            dataTeCollapseInit            := "",
            dataTeTarget                  := "#collapseOne",
            aria.expanded                 := true,
            aria.controls                 := "collapseOne",
            
            "Accordion Item #1",
            span(
              cls := "ml-auto h-5 w-5 shrink-0 rotate-[-180deg] fill-[#336dec] transition-transform duration-200 ease-in-out group-[[data-te-collapse-collapsed]]:rotate-0 group-[[data-te-collapse-collapsed]]:fill-[#212529] motion-reduce:transition-none dark:fill-blue-300 dark:group-[[data-te-collapse-collapsed]]:fill-white",
              svg.svg(
                svg.xmlns                := "http://www.w3.org/2000/svg",
                svg.fill                 := "none",
                svg.viewBox              := "0 0 24 24",
                svg.strokeWidth          := "1.5",
                svg.stroke               := "currentColor",
                svg.cls                      := "h-6 w-6",
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
          idAttr                := "collapseOne",
          cls                   := "!visible",
          dataTeCollapseItem    := "",
          dataTeCollapseShow    := "",
          aria.labelledBy       := "headingOne",
          dataTeParent          := "#accordionExample",
          div(
            cls := "px-5 py-4",
            strong("This is the first item's accordion body."),
            """It is
            shown by default, until the collapse plugin adds the appropriate
            classes that we use to style each element. These classes control
            the overall appearance, as well as the showing and hiding via CSS
            transitions. You can modify any of this with custom CSS or
            overriding our default variables. It's also worth noting that just
            about any HTML can go within the""",
            code(".accordion-body"),
            """,
            though the transition does limit overflow."""
          )
        )
      ),
      div(
        cls := "border border-t-0 border-neutral-200 bg-white dark:border-neutral-600 dark:bg-neutral-800",
        h2(
          cls := "mb-0",
          idAttr := "headingTwo",
          button(
            cls                                := "group relative flex w-full items-center rounded-none border-0 bg-white px-5 py-4 text-left text-base text-neutral-800 transition [overflow-anchor:none] hover:z-[2] focus:z-[3] focus:outline-none dark:bg-neutral-800 dark:text-white [&:not([data-te-collapse-collapsed])]:bg-white [&:not([data-te-collapse-collapsed])]:text-primary [&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(229,231,235)] dark:[&:not([data-te-collapse-collapsed])]:bg-neutral-800 dark:[&:not([data-te-collapse-collapsed])]:text-primary-400 dark:[&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(75,85,99)]",
            `type`                             := "button",
            dataTeCollapseInit      := "",
            dataTeCollapseCollapsed := "",
            dataTeTarget             := "#collapseTwo",
            aria.expanded              :=  false,
            aria.controls              := "collapseTwo",
            "Accordion Item #2",
            span(
              cls := "-mr-1 ml-auto h-5 w-5 shrink-0 rotate-[-180deg] fill-[#336dec] transition-transform duration-200 ease-in-out group-[[data-te-collapse-collapsed]]:mr-0 group-[[data-te-collapse-collapsed]]:rotate-0 group-[[data-te-collapse-collapsed]]:fill-[#212529] motion-reduce:transition-none dark:fill-blue-300 dark:group-[[data-te-collapse-collapsed]]:fill-white",
              svg.svg(
                svg.xmlns                := "http://www.w3.org/2000/svg",
                svg.fill                 := "none",
                svg.viewBox              := "0 0 24 24",
                svg.strokeWidth          := "1.5",
                svg.stroke               := "currentColor",
                svg.cls                  := "h-6 w-6",
                svg.path(
                  svg.strokeLineCap  := "round",
                  svg.strokeLineJoin := "round",
                  svg.d                       := "M19.5 8.25l-7.5 7.5-7.5-7.5"
                )
              )
            )
          )
        ),
        div(
          idAttr                            := "collapseTwo",
          cls                           := "!visible hidden",
          dataTeCollapseItem := "",
          aria.labelledBy       := "headingTwo",
          dataTeParent        := "#accordionExample",
          div(
            cls := "px-5 py-4",
            strong("This is the second item's accordion body."),
            """It is
            hidden by default, until the collapse plugin adds the appropriate
            classes that we use to style each element. These classes control
            the overall appearance, as well as the showing and hiding via CSS
            transitions. You can modify any of this with custom CSS or
            overriding our default variables. It's also worth noting that just
            about any HTML can go within the""",
            code(".accordion-body"),
            """,
            though the transition does limit overflow."""
          )
        )
      ),
      div(
        cls := "rounded-b-lg border border-t-0 border-neutral-200 bg-white dark:border-neutral-600 dark:bg-neutral-800",
        h2(
          cls := "accordion-header mb-0",
          idAttr := "headingThree",
          button(
            cls                                := "group relative flex w-full items-center border-0 bg-white px-5 py-4 text-left text-base text-neutral-800 transition [overflow-anchor:none] hover:z-[2] focus:z-[3] focus:outline-none dark:bg-neutral-800 dark:text-white [&:not([data-te-collapse-collapsed])]:bg-white [&:not([data-te-collapse-collapsed])]:text-primary [&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(229,231,235)] dark:[&:not([data-te-collapse-collapsed])]:bg-neutral-800 dark:[&:not([data-te-collapse-collapsed])]:text-primary-400 dark:[&:not([data-te-collapse-collapsed])]:[box-shadow:inset_0_-1px_0_rgba(75,85,99)] [&[data-te-collapse-collapsed]]:rounded-b-[15px] [&[data-te-collapse-collapsed]]:transition-none",
            `type`                             := "button",
            dataTeCollapseInit      := "",
            dataTeCollapseCollapsed := "",
            dataTeTarget             := "#collapseThree",
            aria.expanded              := false,
            aria.controls              := "collapseThree",
            "Accordion Item #3",
            span(
              cls := "-mr-1 ml-auto h-5 w-5 shrink-0 rotate-[-180deg] fill-[#336dec] transition-transform duration-200 ease-in-out group-[[data-te-collapse-collapsed]]:mr-0 group-[[data-te-collapse-collapsed]]:rotate-0 group-[[data-te-collapse-collapsed]]:fill-[#212529] motion-reduce:transition-none dark:fill-blue-300 dark:group-[[data-te-collapse-collapsed]]:fill-white",
              svg.svg(
                svg.xmlns                := "http://www.w3.org/2000/svg",
                svg.fill                 := "none",
                svg.viewBox              := "0 0 24 24",
                svg.strokeWidth          := "1.5",
                svg.stroke               := "currentColor",
                svg.cls                  := "h-6 w-6",
                svg.path(
                  svg.strokeLineCap  := "round",
                  svg.strokeLineJoin := "round",
                  svg.d                       := "M19.5 8.25l-7.5 7.5-7.5-7.5"
                )
              )
            )
          )
        ),
        div(
          idAttr                        := "collapseThree",
          cls                           := "!visible hidden",
          dataTeCollapseItem := "",
          aria.labelledBy       := "headingThree",
          dataTeParent        := "#accordionExample",
          div(
            cls := "px-5 py-4",
            strong("This is the third item's accordion body."),
            """It is
            hidden by default, until the collapse plugin adds the appropriate
            classes that we use to style each element. These classes control
            the overall appearance, as well as the showing and hiding via CSS
            transitions. You can modify any of this with custom CSS or
            overriding our default variables. It's also worth noting that just
            about any HTML can go within the""",
            code(".accordion-body"),
            """,
            though the transition does limit overflow."""
          )
        )
      )
    )
