/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import scala.language.adhocExtensions

import afpma.firecalc.dto.all.*
import com.raquo.waypoint.*
import io.taig.babel.Language
import upickle.default.*
import io.taig.babel.Languages

sealed trait Page derives ReadWriter
object Page:
    given rwLanguage: ReadWriter[Language] = 
        readwriter[String].bimap[Language](_.value, Language.apply)
    given rwDisplayUnits: ReadWriter[DisplayUnits] = 
        readwriter[String].bimap[DisplayUnits](_.toString, DisplayUnits.valueOf)


val defaultRoute = Route.static(DefaultPage, root / endOfSegments)

case object DefaultPage extends Page
case class HomePage(lang: Language, displayUnitsOpt: Option[DisplayUnits] = None) extends Page

val homeRoute = Route[HomePage, (String)](
    encode = homePage => homePage.lang.value,
    decode = args => HomePage(lang = Language(args)),
    pattern = root / segment[String] / endOfSegments
)
    
// Route.static(Page.HomePage, root / endOfSegments)

object router extends com.raquo.waypoint.Router[Page](
    routes = List(defaultRoute, homeRoute),
    routeFallback = _ => HomePage(lang = Languages.Fr),
    serializePage = page => write(page),         // serialize page data for storage in History API log
    deserializePage = pageStr => read(pageStr),  // deserialize the above
    getPageTitle = _ => "FireCalc AFPMA",        // mock page title (displayed in the browser tab next to favicon)
)