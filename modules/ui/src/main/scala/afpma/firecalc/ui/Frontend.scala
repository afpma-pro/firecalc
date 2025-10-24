/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import scala.scalajs.js
import scala.scalajs.js.`import`

import cats.data.Validated

import afpma.firecalc.engine.utils.VNelString

import afpma.firecalc.ui.views.*
import afpma.firecalc.ui.services.VersionService

import afpma.firecalc.dto.all.*
import com.raquo.laminar.api.L.{Owner as _, *}
import io.taig.babel.Locale
import org.scalajs.dom

object Frontend {
  
  import models.*

  val writeUnifiedSchemaSubscription = appStateSchemaVar
      .signal
      .changes
      .distinct
      .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS) --> appStateSchemaWebStorageVar.writer

  val app: Div = div(cls := "",
    child <-- router.currentPageSignal.map(renderPage)
  ).amend(
    writeUnifiedSchemaSubscription,
    // results_en15544_outputs.map(err => ("OUTPUTS 15544", err))
    //     .tapEach(consoleLogVNelStringErrors) --> errorBusConsole
  )

//   def consoleLogVNelStringErrors(k_vnel: (String, VNelString[?])): Unit = k_vnel match
//     case (key, Validated.Invalid(nel)) =>
//         scala.scalajs.js.Dynamic.global.console.log(s"[$key] ======")
//         scala.scalajs.js.Dynamic.global.console.log(s"[$key] ERROR :")
//         nel.toList.foreach(x => scala.scalajs.js.Dynamic.global.console.log(x))
//         scala.scalajs.js.Dynamic.global.console.log(s"[$key] ======")
//     case _ => ()  


  def renderPage(page: Page): Div = page match
      
      case HomePage(lang, displayUnitsOpt) => 
        given loc: Locale = Locale(language = lang)
        given du: DisplayUnits = displayUnitsOpt.getOrElse(DisplayUnits.SI)
        
        HomeView().node
        .amend(onMountCallback(_ => 
            localeVar.set(loc)
            displayUnitsVar.set(du)
        ))
      
      case DefaultPage => 
        renderPage(HomePage(
            lang = localeVar.now().language,
            displayUnitsOpt = Some(displayUnitsVar.now())
        ))

  def main(args: Array[String]): Unit =

    // Log version information to console on startup
    VersionService.logVersionToConsole()

    // com.raquo.airstream.core.Transaction.maxDepth = Int.MaxValue
    com.raquo.airstream.core.Transaction.maxDepth = 1000

    waitForLoad {
      val appContainer = dom.document.querySelector("#app")
      appContainer.innerHTML = ""
      unmount()


      val rootNode = render(appContainer, app)
      storeUnmount(rootNode)
      
      // val elements: js.Array[String | org.scalajs.dom.HTMLElement] = 
      //   Seq("#carneaux-split-left", "#carneaux-split-right").toJSArray

      // typings.splitJs.mod(elements)
      
    //   JSpreadsheetTable.init("jspreadsheet-carneaux")
    }

  def waitForLoad(f: => Any): Unit =
    if (dom.window.asInstanceOf[js.Dynamic].documentLoaded == null)
      documentEvents(_.onDomContentLoaded).foreach { _ =>
        dom.window.asInstanceOf[js.Dynamic].documentLoaded = true
        f
      }(using unsafeWindowOwner)
    else
      f

  def unmount(): Unit =
    if (scala.scalajs.LinkingInfo.developmentMode) {
      Option(dom.window.asInstanceOf[js.Dynamic].__laminar_root_unmount)
        .collect {
          case x if !js.isUndefined(x) =>
            x.asInstanceOf[js.Function0[Unit]]
        }
        .foreach(_.apply())
    }

  def storeUnmount(rootNode: RootNode): Unit = {
    val unmountFunction: js.Function0[Any] = () => rootNode.unmount()
    dom.window.asInstanceOf[js.Dynamic].__laminar_root_unmount = unmountFunction
  }

  if (!js.isUndefined(`import`.meta.hot) && !js.isUndefined(`import`.meta.hot.accept)) {
    `import`.meta.hot.accept { (_: Any) => }
  }
}
