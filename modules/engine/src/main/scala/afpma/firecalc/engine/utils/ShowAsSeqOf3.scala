/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import cli_table.*

trait ShowAsSeqOf3[A]:
    def seqOf3Cells(a: A): Seq[String]
    def seqOf3CellsNoValue: Seq[String]
    extension (a: A)
        def showSeqOf3: Seq[String] = seqOf3Cells(a)
        def showSeqOf3NoValue: Seq[String] = seqOf3CellsNoValue

object ShowAsSeqOf3:

    def apply[A](using ev: ShowAsSeqOf3[A]) = ev
    
    def show[A](preseq: Seq[String], f: A => String, ifEmpty: String = "-"): ShowAsSeqOf3[A] = 
        new ShowAsSeqOf3[A]:
            private def mergeAndCheckLen(seq: Seq[String], el: String) = 
                val out = seq appended el
                val l = out.length
                require(l == 3, s"unexpected sequence of $l element(s), expected 3")
                out
            def seqOf3Cells(a: A): Seq[String] = mergeAndCheckLen(preseq, f(a))
            def seqOf3CellsNoValue: Seq[String] = mergeAndCheckLen(preseq, ifEmpty)

    // def showOpt[A](preseq: Seq[String], f: Option[A] => String, ifEmpty: String = "-"): ShowAsSeqOf3[A] = 
    //     new ShowAsSeqOf3[A]:
    //         private def mergeAndCheckLen(seq: Seq[String], el: String) = 
    //             val out = seq appended el
    //             val l = out.length
    //             require(l == 3, s"unexpected sequence of $l element(s), expected 3")
    //             out
    //         def seqOf3Cells(a: A): Seq[String] = mergeAndCheckLen(preseq, f(Some(a)))
    //         def seqOf3CellsNoValue: Seq[String] = mergeAndCheckLen(preseq, ifEmpty)