/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.i18n

import afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues

import com.raquo.airstream.state.Var

import afpma.laminar.form.*

/** i18n extensions for Form[A] — automatic field name overwriting from translations. */
object FormI18nExtensions:

    /** Non-extension version for use from inline contexts (e.g. FormDerivation.derived). */
    def autoOverwriteFieldNamesFor[A](form: Form[A])(using tc: HasTranslatedFieldsWithValues[A]): Form[A] =
        val translFV       = tc.getTranslatedFieldsWithValues
        val lbl            = translFV.classNameTransl.getOrElse(translFV.classNameOrig)
        val fieldOverrides = translFV.paramsTransl.toMap
        new Form[A]:
            def defaultable = form.defaultable
            def validateVar = form.validateVar
            override def configuredFieldName: Option[String] = Some(lbl)
            def render(v: Var[A], config: FormConfig)(using FormRenderer) =
                val baseConfig    = if config.fieldName.isDefined then config else config.withFieldName(lbl)
                val updatedConfig = fieldOverrides.foldLeft(baseConfig):
                    case (cfg, (pOrig, pTransl)) =>
                        cfg.withFieldNameForParam(pOrig, pTransl.getOrElse(s"{{$pOrig}}"))
                form.render(v, updatedConfig)

    extension [A](form: Form[A])
        /**
         * Apply i18n translations from `@Transl` annotations to field names.
         * Delegates to `autoOverwriteFieldNamesFor`.
         */
        def autoOverwriteFieldNames(using tc: HasTranslatedFieldsWithValues[A]): Form[A] =
            autoOverwriteFieldNamesFor(form)(using tc)
