/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import scala.annotation.nowarn

import cats.Show

import afpma.firecalc.engine.models.TermDef
import afpma.firecalc.engine.models.TermDefDetails
import afpma.firecalc.engine.utils.ShowAsSeqOf3

import afpma.firecalc.i18n.Localized

import afpma.firecalc.units.coulombutils.*
import coulomb.conversion.*
import coulomb.policy.standard.given
import io.taig.babel.Locale

sealed trait OTypedF[F[_], QD[_], U]:
    type Q = QD[U]
    opaque type Type <: F[Q] = F[Q]
    
    def valueFormat: String = "%.2f"
    def pretty(q: Q): String
    
    def show: Show[F[Q]]
    def showUnitString: String
    // inline def showQ: Show[Q]
    given showType: Show[Type] = show
    // inline def mkShow(using inline s: Show[F[Q]]): Show[Type] =
    //     Show.show((t: Type) => s.show(t))

    given ordering_Type: Ordering[Type] = scala.compiletime.deferred

    def termDef: TermDef[F[Q]]
    def termDefDetails: TermDefDetails[F[Q]]
    given termDef_Type: TermDef[Type] = termDef
    given termDefDetails_Type: TermDefDetails[Type] = termDefDetails
    given _showSeqOf3: Locale => ShowAsSeqOf3[F[Q]] = scala.compiletime.deferred

    given fromHidden: Conversion[F[Q], Type] = (fq: F[Q]) => fq
    given Conversion[Type, F[Q]] = identity
    
    extension (@nowarn t: Type)
        def showO: String = showType.show(t)
        def unwrap: F[Q] = t
        def showAsSeqOf3(using Locale): ShowAsSeqOf3[F[Q]] = _showSeqOf3
        def showSeqOf3(using Locale): Seq[String] = _showSeqOf3.seqOf3Cells(t)
        def showSymbol: String                   = termDef.symbol
        def showName: Localized[String]          = termDefDetails.name
        def showDescription: Localized[String]   = termDefDetails.description
    extension (ot: Option[Type])
        def showSeqOf3(using Locale): Seq[String] = 
            ot.fold(_showSeqOf3.seqOf3CellsNoValue)(_showSeqOf3.seqOf3Cells)

// cats.Id
sealed trait OTyped[QD[_], U] extends OTypedF[cats.Id, QD, U]
    
sealed trait OTypedQtyD_Generic[U] extends OTyped[QtyD, U]:
    type PrettyU
    def toPrettyUnit: QtyD[U] => QtyD[PrettyU]
    final def pretty(q: Q): String = valueFormat.format(toPrettyUnit(q).value)
    def show = Show.show(q => s"${pretty(q)} ${showUnitString}")
    override given ordering_Type: Ordering[Type] = 
        Ordering.by[Type, Double](_.value)
    override given _showSeqOf3: Locale => ShowAsSeqOf3[cats.Id[Q]] = 
        ShowAsSeqOf3.show(
            Seq(termDefDetails.name, termDef.symbol),
            a => a.showP(using show)
        )
    extension (t: Type)
        def toUnit[U2](using conv: UnitConversion[Double, U, U2]) = 
            coulomb.Quantity.toUnit[Double, U](t.unwrap)[U2](using conv)

// only for testing (we skip QtyD wrapper)
trait OTypedId[U: {Ordering, Show}] extends OTyped[cats.Id, U]:
    import cats.syntax.all.*
    type PrettyU = U
    def toPrettyUnit: cats.Id[U] => cats.Id[PrettyU] = identity
    final def pretty(q: Q): String = q.show
    def show = Show.show(q => s"${pretty(q)}")
    override given ordering_Type: Ordering[Type] = Ordering.by[Type, U](identity)

trait OTypedQtyD[U: ShowUnit] extends OTypedQtyD_Generic[U]:
    def showUnitString: String = ShowUnit[U].showUnit
    type PrettyU = U
    def toPrettyUnit: QtyD[U] => QtyD[PrettyU] = identity
trait OTypedQtyDPretty[U, PrettyU0: ShowUnit] extends OTypedQtyD_Generic[U]:
    def showUnitString: String = ShowUnit[PrettyU0].showUnit
    type PrettyU = PrettyU0
    def toPrettyUnit: QtyD[U] => QtyD[PrettyU]

trait OTypedTempD[U: ShowUnit] extends OTyped[TempD, U]:
    def showUnitString: String = ShowUnit[U].showUnit
    final def pretty(q: Q): String = valueFormat.format(q.value)
    override def valueFormat: String = "%.1f"
    def show = Show.show(t => s"${pretty(t)} ${showUnitString}")
    override given ordering_Type: Ordering[Type] = 
        Ordering.by[Type, Double](_.value)
    override given _showSeqOf3: Locale => ShowAsSeqOf3[cats.Id[Q]] = 
        ShowAsSeqOf3.show(
            Seq(termDefDetails.name, termDef.symbol),
            a => a.showP(using show)
        )
    extension (t: Type)
        def toUnit[U2](using conv: DeltaUnitConversion[Double, Kelvin, U, U2]) = 
            coulomb.DeltaQuantity.toUnit[Double, U, Kelvin](t.unwrap)[U2](using conv)

// Option
sealed trait OTypedOpt[QD[_], U: ShowUnit] extends OTypedF[Option, QD, U]:
    def showUnitString: String = ShowUnit[U].showUnit
    def showQ: Show[Q] = Show.show(q => s"${pretty(q)} ${showUnitString}")
    override def show: Show[Option[Q]] = cats.Show.catsShowForOption(using showQ)

trait OTypedOptQtyD[U] extends OTypedOpt[QtyD, U]:
    type PrettyU = U
    final def pretty(q: Q): String = valueFormat.format(q.toUnit[PrettyU].value)
    override given ordering_Type: Ordering[Type] = 
        given Ordering[Q] = Ordering.by[QtyD[U], Double](_.value)
        given Ordering[Option[Q]] = Ordering.Option[QtyD[U]]
        Ordering.by[Type, Option[Q]](identity)
    override given _showSeqOf3: Locale => ShowAsSeqOf3[Option[Q]] = 
        ShowAsSeqOf3.show(
            Seq(termDefDetails.name, termDef.symbol),
            a => a.showP(using show)
        )
        // ShowAsSeqOf3.show(a => Seq(termDefDetails.name, termDef.symbol, a.map(_.showP(using showQ)).getOrElse("-")))

trait OTypedOptTempD[U] extends OTypedOpt[TempD, U]:
    final def pretty(q: Q): String = valueFormat.format(q.value)
    override given ordering_Type: Ordering[Type] = 
        given Ordering[Q] = Ordering.by[TempD[U], Double](_.value)
        given Ordering[Option[Q]] = Ordering.Option[TempD[U]]
        Ordering.by[Type, Option[Q]](identity)
    override given _showSeqOf3: Locale => ShowAsSeqOf3[Option[Q]] = 
        ShowAsSeqOf3.show(
            Seq(termDefDetails.name, termDef.symbol),
            a => a.showP(using show)
        )
    // given _showSeqOf3(using Locale): ShowAsSeqOf3[Option[Q]] = 
    //     ShowAsSeqOf3.show(a => Seq(termDefDetails.name, termDef.symbol, a.map(_.showP(using showQ)).getOrElse("-")))
