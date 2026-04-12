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
