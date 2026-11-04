/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.i18n

import afpma.firecalc.i18n.utils.CustomLoader

import io.taig.babel.*

/**
 * The strings the global error dialog needs, resolved without ever throwing.
 *
 * Why this type exists: `implicits.I18Ns` decodes the whole generated HOCON payload into
 * one `I18nData_UI` and throws on the first mismatch, so the typed layer and the embedded
 * payload only have to disagree on ONE leaf (a key added to `I18nData_UI` and one bundle
 * only, or a stale copy of the generated `files` payload reaching a classpath — the
 * ui_i18n source generator was duplicated into `ui` and produced exactly that) for EVERY
 * `I18N_UI` / `currentI18N` access to fail. The error dialog is the surface that must
 * stay reachable precisely then, so it cannot take its labels from that path unguarded:
 * the failure this prevents is an unreadable persisted project that never got a dialog,
 * because the code reporting it read `currentI18N.errors.unreadable_project_state` first
 * and threw before the dialog was reached.
 *
 * Every string here comes from the bundles (`resources/i18n/{en,fr}.conf` through the
 * generated `configs`), never from a Scala literal: a second copy of the wording would be
 * a second source of translation truth, free to drift unseen until the payload breaks —
 * which is the only moment it would ever be read.
 *
 * When no locale supplies a leaf, its value is that leaf's KEY PATH
 * (`errors/unreadable_project_state`) and the path is listed in `unresolved`: the dialog
 * stays rendered and clickable, and what is shown is exactly what the console line names.
 *
 * No DOM, no Laminar, no console output: presentation and logging belong to the caller
 * (GlobalErrorDialog), this is a plain value callable from a plain test.
 */
final case class ErrorDialogLabels(
    errorTitle            : String,
    unreadableProjectState: String,
    transactionMsg        : String,
    reloadButton          : String,
    closeButton           : String,
    goToProjects          : String,
    saveButton            : String,
    saveDone              : String,
    // HOCON paths no locale supplied, in the order the dialog shows them; Nil when the
    // payload covered every leaf.
    unresolved            : List[String]
)

object ErrorDialogLabels:

    /**
     * Resolve `locale` against the app's generated bundles. Never throws.
     *
     * Fast path first: the whole-record decode (`implicits.I18N_UI`) is what every other
     * UI read uses, so normal operation is unchanged and costs nothing extra — on success
     * `I18Ns` has it decoded once. Only when it fails does the per-leaf decode run:
     * decoding each leaf the dialog needs on its own still succeeds when an unrelated leaf
     * is what broke the record, which is the real failure mode — the payload of the
     * incident had `global_error` intact and `errors/unreadable_project_state` missing.
     * A failed `lazy val` is NOT memoized (SIP-20 resets the cell and rethrows), so in
     * exactly that state every access re-decodes both bundles: a caller repeating this
     * read MUST cache the result itself, which is why GlobalErrorDialog holds it.
     */
    def resolve(locale: Locale): ErrorDialogLabels =
        fullRecord(locale) match
            case Some(i18n) => fromRecord(i18n)
            case None       => resolve(locale, afpma.firecalc.ui.i18n.configs)

    /**
     * Resolve `locale` against explicit bundle texts — the same shape as the generated
     * `configs` (language tag -> `.conf` content), so a synthetic payload can be fed in
     * (ErrorDialogLabelsSpec uses that for the incident this degradation covers).
     *
     * No whole-record fast path here: the caller named the payload, so the payload is what
     * gets decoded.
     *
     * Per leaf, the requested locale is tried first, then `Locales.fr` — the project's
     * configured fallback (`implicits.scala`, `.withFallback(Locales.fr)`), applied per
     * leaf instead of per record, so one absent key cannot cost the whole dialog.
     */
    def resolve(locale: Locale, configs: Map[String, String]): ErrorDialogLabels =
        // A payload CustomLoader cannot even parse (broken syntax, an unresolvable
        // substitution) resolves nothing, and must not take the caller down with it: every
        // leaf then reports its own key path, which is what the console line names.
        val babels: Translations[Babel] =
            scala.util
                .Try(new CustomLoader(configs).load("babel", Set(locale, Locales.fr)))
                .getOrElse(Translations.Empty)

        def decoded(path: Path): Option[String] =
            List(locale, Locales.fr)
                .flatMap(loc => babels.get(loc))
                .flatMap(translation => leaf(translation.value, path))
                .headOption

        // One decode per leaf, keyed by the very path it looked for. A Map's own order is
        // not the order the dialog shows its controls, so `leafPaths` stays the reporting
        // order and `unresolved` is read out of the Map through it — the console line
        // cannot shuffle between runs.
        val resolved: Map[Path, Option[String]] = leafPaths.map(path => path -> decoded(path)).toMap

        def valueOf(path: Path): String =
            resolved.get(path).flatten.getOrElse(path.print)

        ErrorDialogLabels            (
            errorTitle             = valueOf(errorTitlePath),
            unreadableProjectState = valueOf(unreadableProjectStatePath),
            transactionMsg         = valueOf(transactionMsgPath),
            reloadButton           = valueOf(reloadButtonPath),
            closeButton            = valueOf(closeButtonPath),
            goToProjects           = valueOf(goToProjectsPath),
            saveButton             = valueOf(saveButtonPath),
            saveDone               = valueOf(saveDonePath),
            unresolved             = leafPaths.collect { case path if resolved(path).isEmpty => path.print }
        )

    /**
     * Whole-record decode. `I18Ns` THROWS on the first leaf where the typed layer and the
     * payload disagree — the state this type exists for — so the failure is a value here,
     * never an exception at the caller.
     */
    private def fullRecord(locale: Locale): Option[I18nData_UI] =
        scala.util.Try(implicits.I18N_UI(using locale)).toOption

    private def fromRecord(i18n: I18nData_UI): ErrorDialogLabels =
        ErrorDialogLabels            (
            errorTitle             = i18n.global_error.title,
            unreadableProjectState = i18n.errors.unreadable_project_state,
            transactionMsg         = i18n.global_error.transaction_msg,
            reloadButton           = i18n.global_error.reload_button,
            closeButton            = i18n.global_error.close_button,
            goToProjects           = i18n.global_error.go_to_projects,
            saveButton             = i18n.global_error.save_button,
            saveDone               = i18n.global_error.save_done,
            unresolved             = Nil
        )

    // Leaf paths are read out of the typed layer: `I` resolves at compile time, so a
    // renamed or misspelled field is a compile error here rather than a runtime
    // "Missing key", and the path `unresolved` reports is the path the decode actually
    // looked for — a log line can never name a key the payload does not use. Declared in
    // field order so `unresolved` reads in the order the dialog shows the controls.
    private val errorTitlePath            : Path = Path(I(_.global_error.title)             )
    private val unreadableProjectStatePath: Path = Path(I(_.errors.unreadable_project_state))
    private val transactionMsgPath        : Path = Path(I(_.global_error.transaction_msg)   )
    private val reloadButtonPath          : Path = Path(I(_.global_error.reload_button)     )
    private val closeButtonPath           : Path = Path(I(_.global_error.close_button)      )
    private val goToProjectsPath          : Path = Path(I(_.global_error.go_to_projects)    )
    private val saveButtonPath            : Path = Path(I(_.global_error.save_button)       )
    private val saveDonePath              : Path = Path(I(_.global_error.save_done)         )

    private val leafPaths: List[Path] =
        List(
            errorTitlePath,
            unreadableProjectStatePath,
            transactionMsgPath,
            reloadButtonPath,
            closeButtonPath,
            goToProjectsPath,
            saveButtonPath,
            saveDonePath
        )

    /**
     * One leaf of one locale's payload: None when the path is absent, or when an object
     * sits where a value was expected (`Decoder[String]` rejects both rather than
     * inventing text). babel's path-aware `decode(babel, path)` is `protected[babel]`, so
     * the tree is walked with the public `Babel.get` and only the leaf is decoded.
     */
    private def leaf(babel: Babel, path: Path): Option[String] =
        path.values
            .foldLeft(Option(babel))((tree, segment) => tree.flatMap(_.get(segment)))
            .flatMap(value => Decoder[String].decode(value).toOption)
