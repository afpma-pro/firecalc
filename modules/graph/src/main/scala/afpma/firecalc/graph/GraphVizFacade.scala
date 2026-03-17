/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.graph

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom

/** Facade for the pre-bundled TypeScript graph module.
  * The JS file is bundled into JAR resources at build time.
  */
@js.native
@JSImport("/afpma/firecalc/graph/graph-viz.js", "initGraphViz")
private[graph] object GraphVizFacade extends js.Object:
    def apply(
        container: dom.HTMLElement,
        data     : ChartDataJS,
        config   : GraphConfigJS
    ): GraphVizHandleJS = js.native
