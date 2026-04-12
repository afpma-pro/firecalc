/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import cats.data.Validated.Invalid
import cats.data.Validated.Valid

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

/** Standalone Var synchronization utilities.
  *
  * These were previously methods on `LaminarForm` companion object.
  * Now they are free functions in the core module.
  */
object VarSync:

    /** Mono-directional sync: Var[A] -> Var[Option[A]], validated before propagation. */
    def makeAndValidateOptionVarFromVar_MonoDirSync[A](
        va: Var[A]
    )(using ValidateVar[A]): (Var[Option[A]], Binder[HtmlElement]) =
        val voa: Var[Option[A]] = Var(Some(va.now()))

        val obs = Observer[Option[A]] {
            case Some(a) =>
                ValidateVar[A].validate(a) match
                    case Valid(())  => va.set(a)
                    case Invalid(_) => ()
            case None => ()
        }
        val binder = voa.signal --> obs
        (voa, binder)

    /** Bi-directional synchronous: Var[A] <-> Var[Option[A]]. */
    def makeOptionVarFromVar_BiDirSync[A](
        va: Var[A]
    )(using da: Defaultable[A]): Var[Option[A]] =
        va.bimap(Some(_))(_.getOrElse(da.default))

    /** Bi-directional asynchronous: Var[A] <-> Var[Option[A]] with debounce. */
    def makeOptionVarFromVar_BiDirAsync[A](
        va                 : Var[A],
        writeDefaultDelayMs: Int = LAMINAR_WRITE_DEFAULT_VALUE_WHEN_EMPTY_DELAY_MS
    )(using da: Defaultable[A]): (Var[Option[A]], Seq[Binder[HtmlElement]]) =
        makeOptionVarFromVar_BiDirAsync_Tuple1(
            va                  = va,
            f                   = identity,
            f_inv               = identity,
            writeDefaultDelayMs = writeDefaultDelayMs
        )

    /** Bi-directional asynchronous with transformation: Var[A] <-> Var[Option[B]]. */
    def makeOptionVarFromVar_BiDirAsync_Tuple1[A, B](
        va                 : Var[A],
        f                  : A => B,
        f_inv              : B => A,
        writeDefaultCond   : Option[B] => Boolean = (_: Option[B]).isEmpty,
        writeDefaultDelayMs: Int                  = LAMINAR_WRITE_DEFAULT_VALUE_WHEN_EMPTY_DELAY_MS
    )(using da: Defaultable[A]): (Var[Option[B]], Seq[Binder[HtmlElement]]) =
        val a_init = va.now()
        val vob: Var[Option[B]] = Var(Some(f(a_init)))

        val vobDebounced = vob.signal.distinct
            .changes
            .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)

        val maybeWriteDefaultAfterDelay = vobDebounced
            .filter(writeDefaultCond)
            .delay(writeDefaultDelayMs)
            .withCurrentValueOf(vob.signal)
            .map { (_, ob) =>
                Option.when(ob.isEmpty)(da.default)
            }
            .filter(_.isDefined)
            .map(_.get)

        val ob_to_a_binder = vobDebounced
            .filter(_.isDefined)
            .map(ob => f_inv(ob.get)) --> va.writer

        val maybeWriteDefault_binder =
            maybeWriteDefaultAfterDelay --> va.writer

        val va_to_vob_binder = va.signal.map(f andThen Some.apply) --> vob.writer

        val binders = Seq(
            ob_to_a_binder,
            maybeWriteDefault_binder,
            va_to_vob_binder
        )

        (vob, binders)

    /** Bi-directional asynchronous with 2-tuple decomposition: Var[A] <-> (Var[Option[B]], Var[Option[C]]). */
    def makeOptionVarFromVar_BiDirAsync_Tuple2[A, B, C](
        va                 : Var[A],
        f                  : A => (B, C),
        f_inv              : (B, C) => A,
        writeDefaultCond   : (Option[B], Option[C]) => Boolean,
        writeDefaultDelayMs: Int = LAMINAR_WRITE_DEFAULT_VALUE_WHEN_EMPTY_DELAY_MS
    )(using da: Defaultable[A]): ((Var[Option[B]], Var[Option[C]]), Seq[Binder[HtmlElement]]) =
        val a_init = va.now()

        def a2b(a: A): B = f(a)._1
        def a2c(a: A): C = f(a)._2

        val vob: Var[Option[B]] = Var(Some(a2b(a_init)))
        val voc: Var[Option[C]] = Var(Some(a2c(a_init)))

        val vobc          = vob.signal.combineWith(voc.signal)
        val vobcDebounced = vobc.distinct.changes.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)

        val maybeWriteDefaultAfterDelay =
            vobcDebounced
                .filter((ob, oc) => writeDefaultCond(ob, oc))
                .delay(writeDefaultDelayMs)
                .withCurrentValueOf(vobc)
                .map[Option[A]] { case (_, _, ob_now, oc_now) =>
                    Option.when(writeDefaultCond(ob_now, oc_now))(da.default)
                }
                .filter(_.isDefined)
                .map(_.get)

        val bc_to_a_binder = vobcDebounced
            .filter((ob, oc) => ob.isDefined && oc.isDefined)
            .map((ob, oc) => (ob.get, oc.get))
            .mapN(f_inv) --> va.writer

        val bc_to_a_maybeWriteDefault_binder = maybeWriteDefaultAfterDelay --> va.writer

        val va_to_vob_binder = va.signal.map(a2b andThen Some.apply) --> vob.writer
        val va_to_voc_binder = va.signal.map(a2c andThen Some.apply) --> voc.writer

        val binders = Seq(
            bc_to_a_binder,
            bc_to_a_maybeWriteDefault_binder,
            va_to_vob_binder,
            va_to_voc_binder
        )

        ((vob, voc), binders)

    /** Mono-directional sync (alias). */
    def makeOptionVarFromVar_MonoDirSync[A](
        va: Var[A]
    )(using ValidateVar[A]): (Var[Option[A]], Binder[HtmlElement]) =
        makeAndValidateOptionVarFromVar_MonoDirSync(va)

    /** Convert a Var[A] to Var[T] via bidirectional Conversion. */
    def convertToOpaqueVar[A, T](
        va: Var[A]
    )(using
        in : Conversion[A, T],
        out: Conversion[T, A]
    ): Var[T] =
        va.zoomLazy(in)((_, t) => out(t))
