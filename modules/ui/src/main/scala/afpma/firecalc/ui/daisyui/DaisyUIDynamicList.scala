/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import scala.annotation.nowarn
import scala.util.Failure
import scala.util.Success

import cats.syntax.all.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.icons.lucide

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import org.scalajs.dom.MouseEvent

trait DaisyUIDynamicList extends Component:
    
    type Elem
    type A = Elem
    
    lazy val elems_v: Var[Seq[A]]

    lazy val welems_var: Var[Seq[(Int, A)]] = elems_v.bimap(xs =>
        xs.mapWithIndex((x, i) => (i, x))
    )(_.map(_._2))
    
    type XtraInputs
    type XtraOutputs

    lazy val xtras_input_sig: Signal[XtraInputs]
    
    def getElemXtra(fromWElems: Seq[(Int, Elem)], fromXtras: XtraInputs, key: (Int, Elem)): XtraOutputs

    lazy val welem_xtraoutput_sig: Signal[Seq[(Int, Elem, XtraOutputs)]] = welems_var.signal
        .combineWith(xtras_input_sig)
        .map[Seq[(Int, Elem, XtraOutputs)]]: (welems, xtraIns) =>
            welems.map: (id, elem) =>
                val xtra = getElemXtra(
                    fromWElems = welems, 
                    fromXtras = xtraIns, 
                    key = (id, elem)
                )
                (id, elem, xtra)
    
    // collection size var + binder
    lazy val elems_size_v: Var[Int] = Var(elems_v.now().size)
    lazy val elems_size_binder = elems_v.signal.map(_.size) --> elems_size_v.writer

    val command_bus: EventBus[CollectionCommand[(Int, A)]] = new EventBus[CollectionCommand[(Int, A)]]
    
    lazy val command_bus_binder = 
        command_bus.events --> welems_var.tryUpdater[CollectionCommand[(Int, A)]]:
            case (Success(elems), command) =>
                // scala.scalajs.js.Dynamic.global.console.log(s"=> Received command : ${command}")
                Success( CollectionCommand.vectorProcessor[(Int, A)](elems.toVector, command) )
            case (Failure(e), _) =>
                scala.scalajs.js.Dynamic.global.console.log("Failure !")
                scala.scalajs.js.Dynamic.global.console.log(e.getMessage)
                Failure(throw e)

    // Var for update content only, not position in Seq
    def makeAssociatedVarForIdx[AA <: A](idx: Int): (Seq[Binder[HtmlElement]], Var[AA]) =
        // create new var and init with current value
        val v: Var[AA] = Var(welems_var.now()(idx)._2.asInstanceOf[AA])
        // then bind new var
        val elementToSeqBinder = v.signal.distinct --> welems_var.updater[A]: (xs, x) =>
            // we only update the element at index of current signal, do not try to account for moving, replacing, inserting, deleting:
            // it should be handled by the command & control pattern
            // scalajs.js.Dynamic.global.console.log(s"element -> seq (update) : $idx")
            val (oldidx, _) = xs(idx)
            val newEl = (oldidx, x)
            xs.updated(idx, newEl)

        val seqToElementBinder = 
            welems_var.signal
                .mapLazy(_.find(_._1 == idx)) --> v.writer.contracollectOpt[Option[(Int, A)]] {
                    case Some((_, elem)) => Some(elem.asInstanceOf[AA])
                    case None => None
                }

        val binders = Seq(
            elementToSeqBinder, 
            seqToElementBinder
        )

        (binders, v.asInstanceOf[Var[AA]])

    def renderIdWithIncrDescr[AA <: A](idx: Int, @nowarn welem: (Int, AA), sig: Signal[(Int, AA, XtraOutputs)], fullChildNode: HtmlElement, summaryChildNode: Option[HtmlElement]): HtmlElement =
        val displayFull = Var[Boolean](true)
        
        val showFullNodeSig = displayFull.signal
        val showSummaryNodeSig = displayFull.signal.map(b => !b)

        div(cls := "flex-none flex items-center", 
            // duplicate button
            div(cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center cursor-pointer w-6 h-6", 
                lucide.`copy`(stroke_width = 0.5),
                onClick(_.withCurrentValueOf(sig).collect: (_, id, a, _) =>
                    CollectionCommand.Insert((id, a), id + 1)
                ) --> command_bus
            ),
            // remove button
            div(cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center cursor-pointer w-6 h-6", 
                lucide.`trash-2`(stroke_width = 0.5),
                onClick(_.withCurrentValueOf(sig).collect: (_, id, a, _) =>
                    CollectionCommand.Remove((id, a))) --> command_bus
            ),
            // up button
            div(cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center w-6 h-6", 
                when(idx > 0)(
                    cls := "hover:text-base-content cursor-pointer",
                    lucide.`square-chevron-up`(stroke_width = 0.5),
                    onClick(_.withCurrentValueOf(sig)) --> Observer[(MouseEvent, Int, AA, XtraOutputs)] { (_, id, a, _) => 
                        command_bus.emit(CollectionCommand.Insert((id, a), atIndex = if (id > 0) id - 1 else 0))
                        command_bus.emit(CollectionCommand.Remove((id + 1, a)))
                    },
                )
            ),
            // down button
            div(cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center w-6 h-6", 
                when(idx >= 0)(
                    cls := "hover:text-base-content cursor-pointer",
                    child <-- welems_var.signal.map(welems => 
                        if (idx < welems.size - 1) 
                            lucide.`square-chevron-down`(stroke_width = 0.5)
                        else
                            emptyNode
                    ),
                    onClick(_.withCurrentValueOf(sig)) --> Observer[(MouseEvent, Int, AA, XtraOutputs)] { (_, id, a, _) => 
                        command_bus.emit(CollectionCommand.Insert((id, a), atIndex = id + 2))
                        command_bus.emit(CollectionCommand.Remove((id, a)))
                    },
                )
            ),
            // show button
            div(
                cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center cursor-pointer w-6 h-6 pr-1", 
                cls("hidden") <-- showFullNodeSig,
                lucide.show(stroke_width = 0.5),
                onClick(_.mapTo(true)) --> displayFull.writer
            ),
            // hide button
            div(
                cls := "flex-none flex items-center text-base-content hover:bg-secondary hover:text-secondary-content justify-center cursor-pointer w-6 h-6 pr-1", 
                cls("hidden") <-- showSummaryNodeSig,
                lucide.hide(stroke_width = 0.5),
                onClick(_.mapTo(false)) --> displayFull.writer
            ),
            div(cls := "flex-none",
                when(summaryChildNode.isDefined)(
                    cls("hidden") <-- showSummaryNodeSig
                ),
                fullChildNode 
            ),
            when(summaryChildNode.isDefined)(
                div(cls := "flex-none",
                    cls("hidden") <-- showFullNodeSig,
                    summaryChildNode.get
                )
            ),
            // debug
            // div(cls := "flex-none",
            //     div(text <-- v.signal.map(x => s"var = ${x.toString}")),
            //     div(text <-- sig.map(x => s"sig = ${x.toString}")),
            // )
        )

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]]

    def renderContent: HtmlElement =
        div(cls := "py-4 gap-2",
            div(cls := "flex flex-col gap-2",
                children <-- rendered_elems_sig,
            )
        )

    override final lazy val node = renderContent.amend(
        elems_size_binder,
        command_bus_binder
    )