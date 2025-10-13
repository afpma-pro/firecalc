/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.icons

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg.*
import com.raquo.laminar.codecs.*

object lucide:

    val strokeWidth    = svgAttr("stroke-width", StringAsIsCodec, None)
    val strokeLinecap  = svgAttr("stroke-linecap", StringAsIsCodec, None)
    val strokeLinejoin = svgAttr("stroke-linejoin", StringAsIsCodec, None)

    def plus = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-plus",
        path(d := "M5 12h14"),
        path(d := "M12 5v14")
    )

    def `circle-check` = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-circle-check",
        circle(cx := "12", cy := "12", r := "10"),
        path(d    := "m9 12 2 2 4-4")
    )

    def `circle-help` = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-circle-help",
        circle(cx := "12", cy := "12", r := "10"),
        path(d    := "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"),
        path(d    := "M12 17h.01")
    )

    def `circle-x` = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-circle-x",
        circle(cx := "12", cy := "12", r := "10"),
        path(d    := "m15 9-6 6"),
        path(d    := "m9 9 6 6")
    )

    def `chevron-down` = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-chevron-down",
        path(d := "m6 9 6 6 6-6")
    )

    def `chevron-up` = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-chevron-up",
        path(d := "m18 15-6-6-6 6")
    )

    def `chevron-right` = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-chevron-right",
        path(d := "m9 18 6-6-6-6")
    )

    def `copy`(w: Int = 24, h: Int = 24, stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := s"$w",
        height         := s"$h",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-copy",
        rect(
            width  := "14",
            height := "14",
            x      := "8",
            y      := "8",
            rx     := "2",
            ry     := "2"
        ),
        path(d     := "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
    )

    def file(stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-file-icon lucide-file",
        path(d := "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"),
        path(d := "M14 2v4a2 2 0 0 0 2 2h4")
    )

    def `file-down`(stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-file-down-icon lucide-file-down",
        path(d := "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"),
        path(d := "M14 2v4a2 2 0 0 0 2 2h4"),
        path(d := "M12 18v-6"),
        path(d := "m9 15 3 3 3-3")
    )

    def `file-up`(stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-file-up-icon lucide-file-up",
        path(d := "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"),
        path(d := "M14 2v4a2 2 0 0 0 2 2h4"),
        path(d := "M12 12v6"),
        path(d := "m15 15-3-3-3 3")
    )

    def `flask-conical`(stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-flask-conical-icon lucide-flask-conical",
        path(
            d  := "M14 2v6a2 2 0 0 0 .245.96l5.51 10.08A2 2 0 0 1 18 22H6a2 2 0 0 1-1.755-2.96l5.51-10.08A2 2 0 0 0 10 8V2"
        ),
        path(d := "M6.453 15h11.094"),
        path(d := "M8.5 2h7")
    )

    def `folder-open`(stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-folder-open-icon lucide-folder-open",
        path(
            d := "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2"
        )
    )

    def hide(w: Int = 24, h: Int = 24, stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := s"$w",
        height         := s"$h",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-eye-off-icon lucide-eye-off",
        path(
            d  := "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49"
        ),
        path(d := "M14.084 14.158a3 3 0 0 1-4.242-4.242"),
        path(
            d  := "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143"
        ),
        path(d := "m2 2 20 20")
    )

    def languages(stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-languages",
        path(d := "m5 8 6 6"),
        path(d := "m4 14 6-6 2-3"),
        path(d := "M2 5h12"),
        path(d := "M7 2h1"),
        path(d := "m22 22-5-10-5 10"),
        path(d := "M14 18h6")
    )

    def settings = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := "24",
        height         := "24",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := "2",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-settings",
        path(
            d     := "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"
        ),
        circle(cx := "12", cy := "12", r := "3")
    )

    def show(w: Int = 24, h: Int = 24, stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := s"$w",
        height         := s"$h",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-eye-icon lucide-eye",
        path(
            d     := "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"
        ),
        circle(cx := "12", cy := "12", r := "3")
    )

    def square(w: Int = 24, h: Int = 24, stroke_width: Double) =
        svg(
            xmlns          := "http://www.w3.org/2000/svg",
            width          := s"$w",
            height         := s"$h",
            viewBox        := "0 0 24 24",
            fill           := "none",
            stroke         := "currentColor",
            strokeWidth    := s"$stroke_width",
            strokeLinecap  := "round",
            strokeLinejoin := "round",
            cls            := "lucide lucide-square-icon lucide-square",
            rect(
                width  := "18",
                height := "18",
                x      := "3",
                y      := "3",
                rx     := "2"
            )
        )

    def `square-x`(w: Int = 24, h: Int = 24, stroke_width: Double) =
        svg(
            xmlns          := "http://www.w3.org/2000/svg",
            width          := s"$w",
            height         := s"$h",
            viewBox        := "0 0 24 24",
            fill           := "none",
            stroke         := "currentColor",
            strokeWidth    := s"$stroke_width",
            strokeLinecap  := "round",
            strokeLinejoin := "round",
            cls            := "lucide lucide-square-x-icon lucide-square-x",
            rect(
                width  := "18",
                height := "18",
                x      := "3",
                y      := "3",
                rx     := "2",
                ry     := "2"
            ),
            path(d     := "m15 9-6 6"),
            path(d     := "m9 9 6 6")
        )

    def `square-check`(w: Int = 24, h: Int = 24, stroke_width: Double) =
        svg(
            xmlns          := "http://www.w3.org/2000/svg",
            width          := s"$w",
            height         := s"$h",
            viewBox        := "0 0 24 24",
            fill           := "none",
            stroke         := "currentColor",
            strokeWidth    := s"$stroke_width",
            strokeLinecap  := "round",
            strokeLinejoin := "round",
            cls            := "lucide lucide-square-check-icon lucide-square-check",
            rect(
                width  := "18",
                height := "18",
                x      := "3",
                y      := "3",
                rx     := "2"
            ),
            path(d     := "m9 12 2 2 4-4")
        )

    def `square-chevron-down`(w: Int = 24, h: Int = 24, stroke_width: Double) =
        svg(
            xmlns          := "http://www.w3.org/2000/svg",
            width          := s"$w",
            height         := s"$h",
            viewBox        := "0 0 24 24",
            fill           := "none",
            stroke         := "currentColor",
            strokeWidth    := s"$stroke_width",
            strokeLinecap  := "round",
            strokeLinejoin := "round",
            cls            := "lucide lucide-square-chevron-down",
            rect(width := "18", height := "18", x := "3", y := "3", rx := "2"),
            path(d     := "m16 10-4 4-4-4")
        )

    def `square-chevron-up`(w: Int = 24, h: Int = 24, stroke_width: Double) =
        svg(
            xmlns          := "http://www.w3.org/2000/svg",
            width          := s"$w",
            height         := s"$h",
            viewBox        := "0 0 24 24",
            fill           := "none",
            stroke         := "currentColor",
            strokeWidth    := s"$stroke_width",
            strokeLinecap  := "round",
            strokeLinejoin := "round",
            cls            := "lucide lucide-square-chevron-up",
            rect(width := "18", height := "18", x := "3", y := "3", rx := "2"),
            path(d     := "m8 14 4-4 4 4")
        )

    def `tag`(w: Int = 24, h: Int = 24, stroke_width: Double) =
        svg(
            xmlns          := "http://www.w3.org/2000/svg",
            width          := s"$w",
            height         := s"$h",
            viewBox        := "0 0 24 24",
            fill           := "none",
            stroke         := "currentColor",
            strokeWidth    := s"$stroke_width",
            strokeLinecap  := "round",
            strokeLinejoin := "round",
            cls            := "lucide lucide-tag-icon lucide-tag",
            path(
                d     := "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"
            ),
            circle(cx := "7.5", cy := "7.5", r := ".5", fill := "currentColor")
        )

    def `trash-2`(w: Int = 24, h: Int = 24, stroke_width: Double) = svg(
        xmlns          := "http://www.w3.org/2000/svg",
        width          := s"$w",
        height         := s"$h",
        viewBox        := "0 0 24 24",
        fill           := "none",
        stroke         := "currentColor",
        strokeWidth    := s"$stroke_width",
        strokeLinecap  := "round",
        strokeLinejoin := "round",
        cls            := "lucide lucide-trash-2",
        path(d  := "M3 6h18"),
        path(d  := "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"),
        path(d  := "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"),
        line(x1 := "10", x2 := "10", y1 := "11", y2 := "17"),
        line(x1 := "14", x2 := "14", y1 := "11", y2 := "17")
    )

end lucide
