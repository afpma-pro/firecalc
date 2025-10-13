/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import scala.annotation.nowarn

import afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.formgen.Defaultable
import afpma.firecalc.ui.icons.lucide

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import io.taig.babel.Locale

case class TagTreeMenuComponent[A](
    ttm: TagTreeMenu[A],
    appendBus: Observer[CollectionCommand[(Int, A)]],
    incrDescrSizeVar: Var[Int]
)(using Locale) extends Component:
    import TagTreeMenuComponent.*

    // lazy val appendObs = appendBus.toObserver

    private val treeStateVar: Var[TreeState[A]] =
        Var(TreeState.initWith(ttm))

    private def renderButtonAdd = button(
        cls := "btn btn-secondary btn-sm",
        lucide.plus,
        I18N_UI.buttons.add,
        onClick --> treeStateVar.update(_.openChoices())
    )

    private def renderButtonCancel(resetTo: TagTreeMenu[A]) = button(
        cls := "btn btn-error btn-sm",
        lucide.`circle-x`,
        I18N_UI.buttons.cancel,
        onClick.mapTo(TreeState.initWith(resetTo)) --> treeStateVar.writer,
    )
        
    private def renderSep = span(lucide.`chevron-right`)

    private def renderChoice(resetTo: TagTreeMenu[A])(
        txt: String,
        nl: TagTreeMenu.Elems[A],
        @nowarn snl: Signal[TagTreeMenu.Elems[A]]
    ): HtmlElement =
        button(
            cls := "btn btn-sm bg-base-200 hover:bg-secondary",
            lucide.`circle-help`,
            txt,
            onClick --> { _ =>
                // update tree state
                treeStateVar.update: s =>
                    nl match
                        case n: TagTreeMenu.Group[A] => s.selectNode(n)
                        case l: TagTreeMenu.Leaf[A]  => 
                            // and append line to table of incremental description
                            // TODO
                            appendBus.onNext:
                                val size = incrDescrSizeVar.now()
                                CollectionCommand.Append((size, l.elem))
                            TreeState.initWith(resetTo)
            },
            
        )

    private def renderSelectedNode(
        txt: String,
        n: TagTreeMenu.Group[A],
        @nowarn sn: Signal[TagTreeMenu.Group[A]]
    ): HtmlElement =
        button(
            cls := "btn btn-sm btn-secondary",
            lucide.`circle-check`,
            onClick --> treeStateVar.update(
                _.keepSelectedUntil(n)
            ),
            txt
        )

    private def renderWhenClosed: HtmlElement =
        div(renderButtonAdd)

    private def renderWhenSelectionPending(resetTo: TagTreeMenu[A]): HtmlElement =
        val selectedNodesRenderedStream = treeStateVar.signal
            .map(_.selectedNodes.toList)
            .split(_.txt)(renderSelectedNode)
            .map(vec => insertSepBetween(vec, renderSep))
            .map(cs => if (cs.nonEmpty) renderSep :: cs else cs)
            .map(_.map(el => div(cls := "flex-none", el)))

        val choicesRendered = treeStateVar.signal
            .map(_.choices)
            .split(_.txt)(renderChoice(resetTo))
            .map(cs => if (cs.nonEmpty) renderSep :: cs else cs)
            .map(_.map(el => div(cls := "flex-none", el)))
        // div(
        //     cls := "flex flex-row",
        //     div(cls := "flex-none", renderButtonCancel(resetTo)),
        //     div(cls := "flex-none", span(children <-- selectedNodesRenderedStream)),
        //     div(cls := "flex-none", span(children <-- choicesRendered))            
        // )
        div(
            cls := "flex flex-row flex-wrap gap-y-4 items-center gap-x-2",
            renderButtonCancel(resetTo),
            children <-- selectedNodesRenderedStream,
            children <-- choicesRendered,
        )

    private def render(resetTo: TagTreeMenu[A]): HtmlElement =
        val statusSignal = treeStateVar.signal.map(_.status)

        def displaySignalOn(expected: TreeState.Status): Signal[String] =
            statusSignal.map: s =>
                if (s == expected) "flex" else "none"

        val displayWhenClosed           = displaySignalOn(TreeState.Status.Closed)
        val displayWhenSelectionPending = displaySignalOn(
            TreeState.Status.SelectionPending
        )

        div(
            cls := "pt-2",
            renderWhenClosed.amend(display <-- displayWhenClosed),
            renderWhenSelectionPending(resetTo).amend(display <-- displayWhenSelectionPending)
        )

    val node = render(resetTo = ttm)

    // HELPERS

    private def insertSepBetween(
        list: List[HtmlElement],
        sep: HtmlElement
    ): List[HtmlElement] =
        list match
            case Nil          => Nil
            case head :: Nil  => List(head)
            case head :: tail => head :: sep :: insertSepBetween(tail, sep)

end TagTreeMenuComponent

object TagTreeMenuComponent:

    // ====
    // TreeState

    private case class TreeState[A](
        selectedNodes: Vector[TagTreeMenu.Group[A]],
        choices: List[TagTreeMenu.Elems[A]],
        choicesOpened: Boolean
    ):
        import TreeState.Status

        val status: TreeState.Status =
            if (choicesOpened) Status.SelectionPending
            else if (selectedNodes.nonEmpty) Status.SelectionPending
            else Status.Closed

        def openChoices() = this.copy(choicesOpened = true)

        def keepSelectedUntil(x: TagTreeMenu.Elems[A]): TreeState[A] =
            val idx     = selectedNodes.indexOf(x)
            val nextSel = selectedNodes.splitAt(idx + 1)._1
            this.copy(
                selectedNodes = nextSel,
                choices = x match
                    case n: TagTreeMenu.Group[A] => n.next
                    case _: TagTreeMenu.Leaf[A]  => Nil
            )

        def selectNode(n: TagTreeMenu.Group[A]): TreeState[A] =
            this.copy(
                selectedNodes = selectedNodes.appended(n),
                choices = n.next
            )

    end TreeState

    private object TreeState:
        enum Status:
            case Closed, SelectionPending

        def initWith[A](tree: TagTreeMenu[A]): TreeState[A] =
            TreeState(
                selectedNodes = Vector.empty,
                choices = tree.elems,
                choicesOpened = false
                // selectedNodes = Vector(geom_elements, grids),
                // choices = Leaf("avec le zeta et la section") :: Nil,
            )

    end TreeState

end TagTreeMenuComponent

// ===============================================
// ADT to describe hierarchy of section + elements

case class TagTreeMenu[A](elems: List[TagTreeMenu.Elems[A]])
object TagTreeMenu:
    def apply[A](elems: Elems[A]*): TagTreeMenu[A] = TagTreeMenu(elems.toList)

    sealed trait Elems[+A]:
        def txt: String

    case class Group[+A](txt: String, next: List[Elems[A]]) extends Elems[A]
    case class Leaf[+A](txt: String, elem: A)                     extends Elems[A]
    object Leaf:
        def apply[A](txt: String)(using d: Defaultable[A]): Leaf[A] = 
            Leaf(txt, elem = d.default)

        def apply[A](using d: Defaultable[A], t: HasTranslatedFieldsWithValues[A]): Leaf[A] =
            Leaf(t.classTransl, elem = d.default)
end TagTreeMenu
