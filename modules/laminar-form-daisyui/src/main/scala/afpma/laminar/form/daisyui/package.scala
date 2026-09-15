/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

/**
 * laminar-form-daisyui — DaisyUI form renderer implementations.
 *
 * Two FormRenderer implementations:
 *   - DaisyUIVertical: fieldset/legend layout
 *   - DaisyUIHorizontal: floating labels, inline layout
 *
 * Usage at render site:
 * {{{
 *   given FormRenderer = DaisyUIVertical
 *   myVar.as_HtmlElement
 * }}}
 */
package object daisyui

/**
 * Text typed or pasted into a single-line field, with control characters dropped.
 *
 * A pasted spreadsheet cell carries a TAB and a browser `<input>` keeps it (LF/CR it folds
 * to a space). A stored string containing a control character is re-escaped by the YAML codec
 * pair on every save/open cycle and grows without bound — see
 * `.scratch/yaml-escape-defect/issues/01`. Control characters carry no meaning in these
 * fields, so they are dropped at the edge rather than persisted.
 */
def withoutControlChars(text: String): String = text.filter(_ >= ' ')
