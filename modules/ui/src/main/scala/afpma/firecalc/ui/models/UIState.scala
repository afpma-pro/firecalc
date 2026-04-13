/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*

case class CameraState(position: List[Double], up: List[Double], target: List[Double])

case class UIState(
    cameraState          : Option[CameraState]  = None,
    panelStates          : Map[String, Boolean]  = Map.empty,
    vizDisplayType       : Option[String]        = None,
    vizAnnotationsVisible: Option[Boolean]       = None
)

object UIState:
    given Decoder[CameraState] = deriveDecoder
    given Encoder[CameraState] = deriveEncoder

    given Decoder[UIState] = Decoder.instance { c =>
        for
            cameraState           <- c.downField("cameraState").as[Option[CameraState]]
            panelStates           <- c.downField("panelStates").as[Option[Map[String, Boolean]]].map(_.getOrElse(Map.empty))
            vizDisplayType        <- c.downField("vizDisplayType").as[Option[String]]
            vizAnnotationsVisible <- c.downField("vizAnnotationsVisible").as[Option[Boolean]]
        yield UIState(cameraState, panelStates, vizDisplayType, vizAnnotationsVisible)
    }

    given Encoder[UIState] = Encoder.instance { s =>
        Json.obj(
            "cameraState"          -> s.cameraState.asJson,
            "panelStates"          -> s.panelStates.asJson,
            "vizDisplayType"       -> s.vizDisplayType.asJson,
            "vizAnnotationsVisible" -> s.vizAnnotationsVisible.asJson
        )
    }

    val empty: UIState = UIState()
