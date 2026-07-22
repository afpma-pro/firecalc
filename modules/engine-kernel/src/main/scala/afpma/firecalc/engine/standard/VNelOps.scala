/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.standard

import cats.data.NonEmptyList
import cats.syntax.all.*

extension [X1, X2, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2]))
    def mapN_andThen_impl(f: X1 ?=> X2 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
        (
            vmcex_tup._1,
            vmcex_tup._2
        )
            .mapN:
                case (x1, x2) => (x1, x2)
            .andThen: (x1, x2) =>
                given X1 = x1
                given X2 = x2
                f

    def mapN_andThen(f: (X1, X2) => VNelMcalcErr[O]): VNelMcalcErr[O] =
        (
            vmcex_tup._1,
            vmcex_tup._2
        )
            .mapN:
                case (x1, x2) => (x1, x2)
            .andThen: (x1, x2) =>
                f(x1, x2)

extension [X1, X2, X3, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3]))
    def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
        (
            vmcex_tup._1,
            vmcex_tup._2,
            vmcex_tup._3
        )
            .mapN:
                case (x1, x2, x3) => (x1, x2, x3)
            .andThen: (x1, x2, x3) =>
                given X1 = x1
                given X2 = x2
                given X3 = x3
                f

extension [X1, X2, X3, X4, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3], VNelMcalcErr[X4]))
    def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> X4 ?=> VNelMcalcErr[O])       : VNelMcalcErr[O] =
        (
            vmcex_tup._1,
            vmcex_tup._2,
            vmcex_tup._3,
            vmcex_tup._4
        )
            .mapN:
                case (x1, x2, x3, x4) => (x1, x2, x3, x4)
            .andThen: (x1, x2, x3, x4) =>
                given X1 = x1
                given X2 = x2
                given X3 = x3
                given X4 = x4
                f
extension [X1, X2, X3, X4, X5, O](
    vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3], VNelMcalcErr[X4], VNelMcalcErr[X5])
)
    def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> X4 ?=> X5 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
        (
            vmcex_tup._1,
            vmcex_tup._2,
            vmcex_tup._3,
            vmcex_tup._4,
            vmcex_tup._5
        )
            .mapN:
                case (x1, x2, x3, x4, x5) => (x1, x2, x3, x4, x5)
            .andThen: (x1, x2, x3, x4, x5) =>
                given X1 = x1
                given X2 = x2
                given X3 = x3
                given X4 = x4
                given X5 = x5
                f
