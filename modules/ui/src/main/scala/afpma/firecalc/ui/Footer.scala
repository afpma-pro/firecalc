/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import com.raquo.laminar.api.L.*
import afpma.firecalc.ui.services.VersionService
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import io.taig.babel.Locale
import afpma.firecalc.utils.BuildInfo
import afpma.firecalc.ui.config.ViteEnv

final case class Footer()(using Locale) extends Component {

  import Footer.*

  val node = {
    val versionInfo = VersionService.getVersionInfo()

    val versionElement = if (versionInfo.showTooltip) {
      p(
        cls := "col-md-4 mb-0 text-gray-400",
        idAttr := "afpmauiSemver",
        title := versionInfo.fullVersion, // Tooltip showing full version with git hash
        versionInfo.displayVersion
      )
    } else {
      p(
        cls := "col-md-4 mb-0 text-gray-400",
        idAttr := "afpmauiSemver",
        versionInfo.displayVersion
      )
    }

    footerTag(
      idAttr := "footer",
      div(
        cls := "flex flex-col justify-center items-center py-2 my-2",
        p(
          cls := "col-md-4 mb-0 text-gray-400",
          s"${I18N_UI.footer.app_name}"
        ),
        versionElement,
        p(
          cls := "col-md-4 mb-0 text-gray-400",
          s"[${ViteEnv.modeString}]"
        ),
      ),
    //   div(
    //     cls := "flex justify-center items-center",
    //     p(
    //       cls := "col-md-4 mb-0 text-gray-400",
    //       I18N_UI.footer.copyright
    //     )
    //   ),
      div(
        cls := "flex justify-center items-center py-2 my-2",
        img(src := logoUrl, height := "75px")
      ),
      div(
        cls := "flex flex-col justify-center items-center py-2 my-2",
        p(
          cls := "col-md-4 mb-0 text-gray-400",
          I18N_UI.footer.developed_by
        ),
        a(
          cls := "col-md-4 mb-0 text-gray-400 underline",
          href := I18N_UI.footer.website,
          target := "_blank",
          I18N_UI.footer.website
        ),
        p(cls := "mb-6"), // blank line for spacing
        p(
          cls := "col-md-4 mb-0 text-gray-400",
          I18N_UI.footer.supporters_title
        )
      ),
      div(
        cls := "flex justify-center items-center py-2 my-2",
        img(src := partnerLogosUrl, height := "150px")
      ),
      div(
        cls := "flex flex-col justify-center items-center py-2 mt-4",
        p(
          cls := "col-md-4 mb-2 text-gray-400",
          I18N_UI.footer.license
        ),
        a(
          href := BuildInfo.Repository.url,
          target := "_blank",
          title := "View on GitHub",
          svg.svg(
            svg.height := "37.5px",
            svg.width := "37.5px",
            svg.viewBox := "0 0 16 16",
            svg.fill := "currentColor",
            svg.cls := "text-gray-400",
            svg.path(
              svg.d := "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
            )
          )
        )
      )
    )
  }
}

object Footer {
  @js.native @JSImport("/assets/img/AFPMA_LogoMenu.png", JSImport.Default)
  object JS_logo_URL extends js.Object
  
  @js.native @JSImport("/assets/img/logos-partners/logos-bandeau.jpg", JSImport.Default)
  object JS_partnerLogos_URL extends js.Object
  
  val logoUrl: String = JS_logo_URL.asInstanceOf[String]
  val partnerLogosUrl: String = JS_partnerLogos_URL.asInstanceOf[String]
}