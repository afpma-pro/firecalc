/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.filaire

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom

/** Facade for the pre-bundled TypeScript visualization module.
  * The JS file is bundled into JAR resources at build time.
  *
  * Note: The bundled filaire-viz.js exports initFilaireViz as a named export,
  * so we import it directly rather than as a namespace.
  */
@js.native
@JSImport("/afpma/firecalc/filaire/filaire-viz.js", "initFilaireViz")
private[filaire] object ThreeVizFacade extends js.Object:
  /** Initialize the Filaire visualization in the given container.
    *
    * @param container DOM element to render into
    * @param pipes Array of pipe data to visualize
    * @param config Visualization configuration
    * @param onPipeClick Optional callback when a pipe is clicked
    * @param onPipeHover Optional callback when a pipe is hovered (index -1 = deselect)
    * @return A handle with lifecycle management methods (dispose, etc.)
    */
  def apply(
    container: dom.HTMLElement,
    pipeGroups: js.Array[PipeGroupJS],
    config: VizConfigJS,
    onPipeClick: js.UndefOr[js.Function1[Int, Unit]] = js.undefined,
    onPipeHover: js.UndefOr[js.Function1[Int, Unit]] = js.undefined
  ): FilaireVizHandleJS = js.native
