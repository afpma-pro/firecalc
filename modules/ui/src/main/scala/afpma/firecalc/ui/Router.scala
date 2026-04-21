/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.models.project.ProjectId

import com.raquo.waypoint.*
import org.scalajs.dom

import scala.language.adhocExtensions

import io.taig.babel.Language
import io.taig.babel.Languages
import upickle.default.*

sealed trait Page derives ReadWriter
object Page:
    given rwLanguage    : ReadWriter[Language]     =
        readwriter[String].bimap[Language](_.value, Language.apply)
    given rwDisplayUnits: ReadWriter[DisplayUnits] =
        readwriter[String].bimap[DisplayUnits](_.toString, DisplayUnits.valueOf)
    given rwProjectId   : ReadWriter[ProjectId]    =
        readwriter[String].bimap[ProjectId](_.value, ProjectId.apply)

/**
 * Fragment base path including the deployment pathname prefix (e.g. `/app/#`).
 * Waypoint's `pushState` passes `basePath + routePath` directly to `history.pushState()`,
 * so the prefix must be included here — the Router `origin` param is NOT used for navigation.
 */
private lazy val fragmentBasePath: String =
    val pathname = dom.document.location.pathname.stripSuffix("/")
    pathname + "/#"

lazy val defaultRoute = Route.static(DefaultPage, root / endOfSegments, basePath = fragmentBasePath)

case object DefaultPage                                                                                    extends Page
case class ProjectSelectorPage(lang: Language)                                                             extends Page
case class ProjectPage(lang: Language, projectId: ProjectId, displayUnitsOpt: Option[DisplayUnits] = None) extends Page

lazy val projectSelectorRoute = Route[ProjectSelectorPage, String](
    encode   = page => page.lang.value,
    decode   = args => ProjectSelectorPage(lang = Language(args)),
    pattern  = root / segment[String] / endOfSegments,
    basePath = fragmentBasePath
)

lazy val projectRoute = Route[ProjectPage, (String, String)](
    encode   = page => (page.lang.value, page.projectId.value),
    decode   = args => ProjectPage(lang = Language(args._1), projectId = ProjectId(args._2)),
    pattern  = root / segment[String] / "project" / segment[String] / endOfSegments,
    basePath = fragmentBasePath
)

object router
    extends com.raquo.waypoint.Router[Page]         (
        routes          = List(projectRoute, projectSelectorRoute, defaultRoute),
        routeFallback   = _ => ProjectSelectorPage(lang = Languages.Fr),
        serializePage   = page => write(page), // serialize page data for storage in History API log
        deserializePage = pageStr => read(pageStr), // deserialize the above
        getPageTitle    = _ => "FireCalc AFPMA" // mock page title (displayed in the browser tab next to favicon)
    )
