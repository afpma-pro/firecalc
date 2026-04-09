/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.i18n

import afpma.laminar.form.*
import afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

/** i18n extensions for Form[A] — automatic field name overwriting from translations. */
object FormI18nExtensions:

    extension [A](form: Form[A])
        def autoOverwriteFieldNames(using tc: HasTranslatedFieldsWithValues[A]): Form[A] =
            val translFV = tc.getTranslatedFieldsWithValues
            val lbl      = translFV.classNameTransl.getOrElse(translFV.classNameOrig)
            // Apply per-field name overwrites via FormConfig
            val fieldOverrides = translFV.paramsTransl.toMap
            new Form[A]:
                def defaultable = form.defaultable
                def validateVar = form.validateVar
                def render(v: Var[A], config: FormConfig)(using FormRenderer) =
                    val updatedConfig = fieldOverrides.foldLeft(config.withFieldName(lbl)):
                        case (cfg, (pOrig, pTransl)) =>
                            cfg.withFieldNameForParam(pOrig, pTransl.getOrElse(s"{{$pOrig}}"))
                    form.render(v, updatedConfig)
