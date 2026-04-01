/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.engine.standard.VNelMcalcErr
import afpma.firecalc.engine.utils.VNelString

import com.raquo.airstream.core.Signal

extension [A](svnele: Signal[VNelMcalcErr[A]])

    def mapVNelE[B](f: A => B): Signal[VNelMcalcErr[B]] =
        svnele.map(_.map(f))

    def flatMapVNelE[B](f: A => VNelMcalcErr[B]): Signal[VNelMcalcErr[B]] =
        svnele.map(_.andThen(f))

    def mapAndFoldVNelE[B](f: A => B, default: B): Signal[B] =
        svnele.map(_.map(f).getOrElse(default))

    def flatMapAndFoldVNelE[B](f: A => VNelMcalcErr[B], default: B): Signal[B] =
        svnele.map(_.andThen(f).getOrElse(default))

extension [A](svnel: Signal[VNelString[A]])

    def mapVNelString[B](f: A => B): Signal[VNelString[B]] =
        svnel.map(_.map(f))

    def flatMapVNelString[B](f: A => VNelString[B]): Signal[VNelString[B]] =
        svnel.map(_.andThen(f))

    def mapAndFoldVNel[B](f: A => B, default: B): Signal[B] =
        svnel.map(_.map(f).getOrElse(default))

    def flatMapAndFoldVNel[B](f: A => VNelString[B], default: B): Signal[B] =
        svnel.map(_.andThen(f).getOrElse(default))
