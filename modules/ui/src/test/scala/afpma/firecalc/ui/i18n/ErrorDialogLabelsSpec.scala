/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.i18n

import io.taig.babel.Language
import io.taig.babel.Locale
import io.taig.babel.Locales

import utest.*

/**
 * Two guarantees about the strings the global error dialog shows when the typed i18n layer
 * and the embedded HOCON payload disagree — the state in which every `currentI18N` /
 * `I18N_UI` access throws, so the dialog is the only surface left to report an unreadable
 * persisted project (and the one that stayed silent).
 *
 * 1. Against the real bundles, the dialog's labels are the bundle text, field by field,
 *    with nothing unresolved. `enBundle` / `frBundle` below are hand-copied from
 *    `modules/ui-i18n/src/main/resources/i18n/{en,fr}.conf`, and that duplication is the
 *    point: `resolve` reads the generated payload, so only a copy taken from the bundles
 *    can catch the payload going stale (the `ui_i18n` source generator wired a second,
 *    outdated `files` object onto the `ui` classpath and the app served an error page with
 *    no text in it), a typed field mapped to the wrong key, or one locale serving another
 *    locale's text. A typed field no bundle declares lands in `unresolved` instead — the
 *    first assertion below is what fails, and it names the key. HOCON substitutions are
 *    written with the value the bundle resolves them to (`global_error.save_button` =
 *    `${tooltips.save_project}`, `global_error.go_to_projects` =
 *    `${project_selector.title}`), so editing a bundle MUST be mirrored here.
 *
 * 2. Against a synthetic payload with ONE leaf missing, the rest of the dialog still
 *    resolves from that payload, and the missing leaf is reported by key path rather than
 *    replaced by invented prose. That is the incident this degradation exists for:
 *    `global_error` was intact and `errors/unreadable_project_state` was missing, so a
 *    whole-record decode failed while every action label was still available.
 *
 * uTest, no DOM: the `ui` tests run on a plain Node.js jsEnv (build.sbt `Test / jsEnv`
 * and `modules/ui/test/vite-env-register.mjs`, no jsdom), which is exactly why
 * [[ErrorDialogLabels]] carries no DOM and `GlobalErrorDialog` itself stays untested.
 */
object ErrorDialogLabelsSpec extends TestSuite:

    // ── What the bundles declare, copied verbatim from resources/i18n/en.conf ─────────
    private val enBundle = ErrorDialogLabels(
        errorTitle             = "Application Error",
        unreadableProjectState =
            "The saved data for this project could not be read, so the project could not be opened. It is still " +
                "in your browser's storage: reload the page to try again, or delete the project from the projects " +
                "list and start over.",
        transactionMsg         = "A reactive loop was detected. This is a bug that will be fixed.",
        reloadButton           = "Reload the page",
        closeButton            = "Close",
        goToProjects           = "Projects list",
        saveButton             = "Save",
        saveDone               = "Project saved",
        unresolved             = Nil
    )

    // ── … and from resources/i18n/fr.conf ────────────────────────────────────────────
    private val frBundle = ErrorDialogLabels(
        errorTitle             = "Erreur de l'application",
        unreadableProjectState =
            "Les données enregistrées pour ce projet n'ont pas pu être lues : le projet n'a pas pu être ouvert. " +
                "Elles sont restées dans le stockage de votre navigateur : rechargez la page pour réessayer, ou " +
                "supprimez le projet depuis la liste des projets pour repartir de zéro.",
        transactionMsg         = "Une boucle réactive a été détectée. Ce bug sera corrigé.",
        reloadButton           = "Recharger la page",
        closeButton            = "Fermer",
        goToProjects           = "Liste des projets",
        saveButton             = "Sauvegarder le projet",
        saveDone               = "Projet sauvegardé",
        unresolved             = Nil
    )

    /** `field: got […], expected […]` for every differing label, so a failure names it. */
    private def mismatchingFields(obtained: ErrorDialogLabels, expected: ErrorDialogLabels): List[String] =
        Vector                                    (
            ("errorTitle", obtained.errorTitle, expected.errorTitle                                    ),
            ("unreadableProjectState", obtained.unreadableProjectState, expected.unreadableProjectState),
            ("transactionMsg", obtained.transactionMsg, expected.transactionMsg                        ),
            ("reloadButton", obtained.reloadButton, expected.reloadButton                              ),
            ("closeButton", obtained.closeButton, expected.closeButton                                 ),
            ("goToProjects", obtained.goToProjects, expected.goToProjects                              ),
            ("saveButton", obtained.saveButton, expected.saveButton                                    ),
            ("saveDone", obtained.saveDone, expected.saveDone                                          )
        ).collect { case (field, got, want) if got != want => s"$field: got [$got], expected [$want]" }.toList

    /** Labels whose text is empty or blank: such a label renders an unusable control. */
    private def blankFields(labels: ErrorDialogLabels): List[String] =
        Vector                        (
            ("errorTitle", labels.errorTitle                        ),
            ("unreadableProjectState", labels.unreadableProjectState),
            ("transactionMsg", labels.transactionMsg                ),
            ("reloadButton", labels.reloadButton                    ),
            ("closeButton", labels.closeButton                      ),
            ("goToProjects", labels.goToProjects                    ),
            ("saveButton", labels.saveButton                        ),
            ("saveDone", labels.saveDone                            )
        ).collect { case (field, text) if text.trim.isEmpty => field }.toList

    /**
     * One synthetic locale bundle, in the shape the generated `configs` holds (language tag
     * -> `.conf` content). Every value names its language, so a test can tell which locale
     * a resolved string came from; `save_button` / `go_to_projects` stay HOCON
     * substitutions because that is how the real bundles declare them and the dialog's
     * labels must survive that indirection. `withUnreadable` / `withCloseButton` omit one
     * leaf, which is the only payload fault that matters here.
     */
    private def bundle(language: String, withUnreadable: Boolean = true, withCloseButton: Boolean = true): String =
        // One entry per line, no commas: the shape resources/i18n/{en,fr}.conf use, so the
        // loader sees here what it sees in the app. An omitted leaf drops its whole line.
        val unreadableLine =
            if withUnreadable then s"""    unreadable_project_state = "$language unreadable project state"\n"""
            else ""
        val closeLine      =
            if withCloseButton then s"""    close_button    = "$language close"\n""" else ""

        s"""
           |errors = {
           |    unknown_error              = "$language unknown error"
           |${unreadableLine}}
           |tooltips = {
           |    save_project         = "$language save"
           |}
           |project_selector = {
           |    title                = "$language projects list"
           |}
           |global_error = {
           |    title           = "$language title"
           |    transaction_msg = "$language reactive loop"
           |    save_button     = $${tooltips.save_project}
           |    save_done       = "$language project saved"
           |    go_to_projects  = $${project_selector.title}
           |    reload_button   = "$language reload"
           |${closeLine}}
           |""".stripMargin

    /** A generated-`configs` shaped payload for both locales. */
    private def bundlePair(en: String, fr: String): Map[String, String] = Map("en" -> en, "fr" -> fr)

    val tests = Tests:

        // ── Real bundles: the typed layer and each locale must agree, leaf by leaf ────

        test("resolve(fr) is the fr bundle, field by field, with nothing unresolved") {
            // `resolve` never throws, so a payload/typed-layer disagreement surfaces here as
            // a non-empty `unresolved` listing the key path babel could not serve — and,
            // because the label then holds that path, in the diff below too.
            val obtained = ErrorDialogLabels.resolve(Locales.fr)
            assert(obtained.unresolved == Nil                  )
            assert(blankFields(obtained) == Nil                )
            assert(mismatchingFields(obtained, frBundle) == Nil)
        }

        test("resolve(en) is the en bundle, field by field, with nothing unresolved") {
            val obtained = ErrorDialogLabels.resolve(Locales.en)
            assert(obtained.unresolved == Nil                  )
            assert(blankFields(obtained) == Nil                )
            assert(mismatchingFields(obtained, enBundle) == Nil)
        }

        // ── One leaf missing from every locale: the regression this degradation covers ─

        test("a payload without errors/unreadable_project_state still yields its own action labels") {
            val labels = ErrorDialogLabels.resolve(
                Locales.fr,
                bundlePair(
                    bundle("en", withUnreadable = false),
                    bundle("fr", withUnreadable = false)
                )
            )
            // Every label the dialog needs to be usable comes from the broken payload: an
            // unrelated missing key cannot cost the buttons.
            assert(labels.errorTitle == "fr title")
            assert(labels.transactionMsg == "fr reactive loop"                       )
            assert(labels.reloadButton == "fr reload"                                )
            assert(labels.closeButton == "fr close"                                  )
            assert(labels.goToProjects == "fr projects list"                         )
            assert(labels.saveButton == "fr save"                                    )
            assert(labels.saveDone == "fr project saved"                             )
            // The one missing leaf says which key is missing, and says it nowhere else.
            assert(labels.unreadableProjectState == "errors/unreadable_project_state")
            assert(labels.unresolved == List("errors/unreadable_project_state")      )
        }

        test("a leaf only the requested locale lacks comes from fr, with nothing unresolved") {
            // The project's configured fallback (implicits.scala, `.withFallback(Locales.fr)`)
            // applied per leaf: `en` lacks the close button, `fr` has it.
            val labels = ErrorDialogLabels.resolve(
                Locales.en,
                bundlePair(
                    bundle("en", withCloseButton = false),
                    bundle("fr"                         )
                )
            )
            assert(labels.closeButton == "fr close")
            // The rest still comes from the requested locale — the fallback covers the gap,
            // it does not replace the locale.
            assert(labels.errorTitle == "en title"   )
            assert(labels.reloadButton == "en reload")
            assert(labels.unresolved == Nil          )
        }

        test("a locale with no bundle at all falls back to fr, the configured fallback") {
            // The URL grammar rejects such a locale, the browser can still hand one to
            // `currentLocale`; the dialog then speaks the project's fallback language, not
            // nothing.
            val labels = ErrorDialogLabels.resolve(Locale(Language("de")), bundlePair(bundle("en"), bundle("fr")))
            assert(blankFields(labels) == Nil     )
            assert(labels.errorTitle == "fr title")
            assert(labels.unresolved == Nil       )
        }

end ErrorDialogLabelsSpec
