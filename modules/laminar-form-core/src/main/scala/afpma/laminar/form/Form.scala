/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

/** The single form typeclass.
  *
  * No self-bounded type parameter. No mutable state.
  * The `FormRenderer` flows via context parameter at the render site.
  */
trait Form[A]:
    /** Render a form element for the given variable and config. */
    def render(v: Var[A], config: FormConfig)(using FormRenderer): HtmlElement

    /** Defaultable instance for this type. */
    def defaultable: Defaultable[A]

    /** Validation function for this type. */
    def validateVar: ValidateVar[A]

object Form:

    def apply[A](using ev: Form[A]): Form[A] = ev

    // =========
    // Extension method for rendering

    extension [A](v: Var[A])
        /** Render a form for this Var using the Form and FormRenderer in scope. */
        def as_HtmlElement(using Form[A], FormRenderer): HtmlElement =
            summon[Form[A]].render(v, FormConfig.default)

        /** Render a form with a specific config. */
        def as_HtmlElement(config: FormConfig)(using Form[A], FormRenderer): HtmlElement =
            summon[Form[A]].render(v, config)

    // =========
    // Factory methods

    /** Create a Form instance from explicit components. */
    def makeFor[A](d: Defaultable[A])(using vv: ValidateVar[A])(
        renderFunc: (Var[A], FormConfig) => FormRenderer ?=> HtmlElement
    ): Form[A] = new Form[A]:
        def render(v: Var[A], config: FormConfig)(using fr: FormRenderer): HtmlElement =
            renderFunc(v, config)(using fr)
        def defaultable: Defaultable[A] = d
        def validateVar: ValidateVar[A] = vv

    /** Create a Form[T] from a Form[A] via bidirectional Conversion. */
    def formConversionOpaque[T, A](using
        fa : Form[A],
        out: Conversion[T, A],
        in : Conversion[A, T]
    ): Form[T] = new Form[T]:
        def render(v: Var[T], config: FormConfig)(using FormRenderer): HtmlElement =
            val mappedVar = v.zoomLazy(out)((_, a) => in(a))
            fa.render(mappedVar, config)
        def defaultable: Defaultable[T] = fa.defaultable.map(in(_))
        def validateVar: ValidateVar[T] = fa.validateVar.contramap(out(_))

    /** Conditional form — shows field A only when condition on C is met. */
    def conditionalOn[C, A](condVar: Var[C])(using
        fa  : Form[A],
        da  : Defaultable[A],
        cond: ConditionalFor[C, A]
    ): Form[Option[A]] = new Form[Option[A]]:
        def render(v: Var[Option[A]], config: FormConfig)(using FormRenderer): HtmlElement =
            val shouldShow = condVar.signal.map(cond.check)
            div(
                child <-- shouldShow.map:
                    case true =>
                        val innerVar = v.zoomLazy(_.getOrElse(da.default))((_, a) => Some(a))
                        fa.render(innerVar, config)
                    case false =>
                        emptyNode
            )
        def defaultable: Defaultable[Option[A]] = Defaultable(None)
        def validateVar: ValidateVar[Option[A]] =
            ValidateVar.make:
                case Some(a) => fa.validateVar.validate(a)
                case None    => VNelString.validUnit

    // =========
    // Bimap / xmap extensions

    extension [A](form: Form[A])

        /** Transform a Form[A] into a Form[B] via bijection. */
        def bimap[B](to: A => B)(from: B => A): Form[B] = new Form[B]:
            def render(v: Var[B], config: FormConfig)(using FormRenderer): HtmlElement =
                val mappedVar = v.zoomLazy(from)((_, a) => to(a))
                form.render(mappedVar, config)
            def defaultable: Defaultable[B] = form.defaultable.map(to)
            def validateVar: ValidateVar[B] = form.validateVar.contramap(from)

        /** Transform with access to current B value (for partial updates). */
        def xmap[B: Defaultable](to: (B, A) => B)(from: B => A): Form[B] = new Form[B]:
            def render(v: Var[B], config: FormConfig)(using FormRenderer): HtmlElement =
                val mappedVar = v.zoomLazy(from)((b, a) => to(b, a))
                form.render(mappedVar, config)
            def defaultable: Defaultable[B] = summon[Defaultable[B]]
            def validateVar: ValidateVar[B] = form.validateVar.contramap(from)

        /** Return a new Form with the given field name (immutable). */
        def withFieldName(name: String): Form[A] = new Form[A]:
            def render(v: Var[A], config: FormConfig)(using FormRenderer): HtmlElement =
                form.render(v, config.withFieldName(name))
            def defaultable: Defaultable[A] = form.defaultable
            def validateVar: ValidateVar[A] = form.validateVar

        /** Return a new Form that shows the field name. */
        def showFieldName: Form[A] = new Form[A]:
            def render(v: Var[A], config: FormConfig)(using FormRenderer): HtmlElement =
                form.render(v, config.doShowFieldName)
            def defaultable: Defaultable[A] = form.defaultable
            def validateVar: ValidateVar[A] = form.validateVar

        /** Return a new Form that hides the field name. */
        def hideFieldName: Form[A] = new Form[A]:
            def render(v: Var[A], config: FormConfig)(using FormRenderer): HtmlElement =
                form.render(v, config.doHideFieldName)
            def defaultable: Defaultable[A] = form.defaultable
            def validateVar: ValidateVar[A] = form.validateVar

        /** Return a new Form that wraps render output in a wrapper element. */
        def wrappedInto(wrapper: HtmlElement => HtmlElement): Form[A] = new Form[A]:
            def render(v: Var[A], config: FormConfig)(using FormRenderer): HtmlElement =
                wrapper(form.render(v, config))
            def defaultable: Defaultable[A] = form.defaultable
            def validateVar: ValidateVar[A] = form.validateVar
