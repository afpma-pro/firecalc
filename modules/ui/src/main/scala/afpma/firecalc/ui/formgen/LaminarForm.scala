/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.formgen

import cats.data.Validated.*

import afpma.firecalc.i18n.utils.NameUtils

import afpma.firecalc.ui.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import magnolia1.TypeInfo
import org.scalajs.dom.HTMLElement

// TODO
//   - move val and var to constructor ?
//   - implement child using case classes ?
// Note
// 
// 1. `DF <: DaisyUI5Form[A, DF]` is important here : 
//    it allows us to preserve precise type information when calling withFieldName 
//    and other similar methods. This way, type of implicits instances are preserved 
//    after they are summoned so we can modifiy them after summoning

trait LaminarForm[A, DF <: LaminarForm[A, DF]] extends FormAnnotations[A]:
    self =>

    def defaultable_instance: Defaultable[A]
    lazy val validate_var: ValidateVar[A]
    
    given given_ValidateVar: ValidateVar[A] = scala.compiletime.deferred

    def fromString(s: String): Option[A] = None

    def toString(a: A) = a.toString

    // new api
    inline def finalFieldName: String = 
        LaminarForm.mkFinalFieldName(_formConfigOverwrite, anns, typeInfo)

    def formConfig: FormConfig = 
        _formConfigOverwrite.getOrElse(afpma.firecalc.ui.formgen.FormConfig.default)

    def updateFormConfigWhenExists(f: FormConfig => FormConfig): DF =
        withFormConfig(_formConfigOverwrite.map(f))

    def withFieldName(n: String): DF = 
        updateFormConfigWhenExists(_.withFieldName(n))

    def withoutFieldName: DF = 
        updateFormConfigWhenExists(_.withoutFieldName)

    def showFieldName: DF = updateFormConfigWhenExists(_.doShowFieldName)
    def hideFieldName: DF = updateFormConfigWhenExists(_.doHideFieldName)

    // TODO: remove Option, make it a FormConfig
    var _formConfigOverwrite: Option[FormConfig] = Some(
        afpma.firecalc.ui.formgen.FormConfig.default
    )
    
    def getPanelConfig: Option[FormConfig] = _formConfigOverwrite

    def withFormConfig(
        formConfigOpt: Option[FormConfig]
    ): DF

    def setFieldNameOverwriteForParam(
        paramLabel: String,
        fieldName: String
    ): DF = withFormConfig(
        _formConfigOverwrite.map(_.withFieldNameForParam(paramLabel, fieldName))
    )

    def getFieldNameOverwriteForParam(paramLabel: String): Option[String] =
        _formConfigOverwrite.flatMap(_.fieldNameForParam(paramLabel))

    def render(
        variable: Var[A],
        formConfig: FormConfig,
    )(using ValidateVar[A]): HtmlElement

    // returns a new DF with render defined as wrapper(render(...))
    def wrappedInto(contentWrapper: HtmlElement => HtmlElement): DF 

end LaminarForm

object LaminarForm:
    
    // ========================================
    // high-level helpers / transformers

    def makeAndValidateOptionVarFromVar_MonoDirSync[A](
        va: Var[A]
    )(using 
        ValidateVar[A]
    ): (Var[Option[A]], Binder[HtmlElement]) = 
        val voa: Var[Option[A]] = Var(Some(va.now())) // init with current value of Var[A]

        val obs = Observer[Option[A]] { 
            case Some(a) =>
                ValidateVar[A].validate(a) match
                    case Valid(())  => va.set(a) // when voa is update in the form, it will update va ONLY if failOrPropage is valid
                    case Invalid(e) => () // va is not updated if validation fails (use errors bus here ?)
            case None => ()
        }
        // do note that Var[Option[A]] is not updated when va is updated (only one way up from Form to original Var[A])
        // Var[Option[A]] is here to store the status of the form variable, whereas va: Var[A] is the final value for state / app after we account for Option[] or Validation
        val binder = voa.signal --> obs
        (voa, binder)

    /* bi-directional and synchronous updates */
    def makeOptionVarFromVar_BiDirSync[A](
        va: Var[A]
    )(using da: Defaultable[A]): Var[Option[A]] = 
        va.bimap(Some(_))(_.getOrElse(da.default))

    /* bi-directional and asynchronous updates */
    def makeOptionVarFromVar_BiDirAsync[A](
        va: Var[A],
        writeDefaultDelayMs: Int = LAMINAR_WRITE_DEFAULT_VALUE_WHEN_EMPTY_DELAY_MS,
    )(using da: Defaultable[A]): (Var[Option[A]], Seq[Binder[HtmlElement]]) = 
        makeOptionVarFromVar_BiDirAsync_Tuple1(
            va = va,
            f = identity,
            f_inv = identity,
            writeDefaultDelayMs = writeDefaultDelayMs 
        )
    
    /* bi-directional and asynchronous updates */
    def makeOptionVarFromVar_BiDirAsync_Tuple1[A, B](
        va: Var[A],
        f: A => B,
        f_inv: B => A,
        writeDefaultCond: Option[B] => Boolean = (_: Option[B]).isEmpty,
        writeDefaultDelayMs: Int = LAMINAR_WRITE_DEFAULT_VALUE_WHEN_EMPTY_DELAY_MS,
    )(using da: Defaultable[A]): (Var[Option[B]], Seq[Binder[HtmlElement]]) = 
        val a_init = va.now()

        val vob: Var[Option[B]] = Var(Some(f(a_init)))

        val vobDebounced = vob.signal
            .distinct // prevent loops
            .changes
            // keep last update in the last LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS to prevent too much rendering / updating
            .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)

        val maybeWriteDefaultAfterDelay = vobDebounced
            .filter(writeDefaultCond)       // only write default on specific condition
            .delay(writeDefaultDelayMs)                // wait for delay_ms
            .withCurrentValueOf(vob.signal) // compare to current value (to see if it's still None)
            .map { (_, ob) =>
                // only overwrite / output Some if no valud user input since delay_ms, 
                // meaning voa.now() is empty (no user input at now())
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
            va_to_vob_binder,
        )

        (vob, binders)

    /* bi-directional and asynchronous updates */
    def makeOptionVarFromVar_BiDirAsync_Tuple2[A, B, C](
        va: Var[A],
        f: A => (B, C),
        f_inv: (B, C) => A,
        writeDefaultCond: (Option[B], Option[C]) => Boolean,
        writeDefaultDelayMs: Int = LAMINAR_WRITE_DEFAULT_VALUE_WHEN_EMPTY_DELAY_MS,
    )(using 
        da: Defaultable[A]
    ): ((Var[Option[B]], Var[Option[C]]), Seq[Binder[HtmlElement]]) = 
        val a_init = va.now()

        def a2b(a: A): B = f(a)._1
        def a2c(a: A): C = f(a)._2
           
        val vob: Var[Option[B]] = Var(Some(a2b(a_init)))
        val voc: Var[Option[C]] = Var(Some(a2c(a_init)))

        val vobc = vob.signal.combineWith(voc.signal)
        val vobcDebounced = vobc.distinct.changes.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)

        val maybeWriteDefaultAfterDelay = 
                vobcDebounced
                .filter((ob, oc) => writeDefaultCond(ob, oc))
                .delay(writeDefaultDelayMs)
                .withCurrentValueOf(vobc)
                .map[Option[A]] { 
                    case (_, _, ob_now, oc_now) =>
                        // only overwrite / output Some if no valud user input since delay_ms, 
                        // meaning vob.now() is empty (no user input at now())
                        Option.when(
                            writeDefaultCond(ob_now, oc_now)
                        )(da.default)
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

    /* mono-directional and synchronous updates */
    def makeOptionVarFromVar_MonoDirSync[A](
        va: Var[A]
    )(using ValidateVar[A]): (Var[Option[A]], Binder[HtmlElement]) = 
        makeAndValidateOptionVarFromVar_MonoDirSync(va)
    
    /* render var */

    def renderVar[A, DF <: LaminarForm[A, DF]](v: Var[A])(using
        fa: DF
    ): ReactiveHtmlElement[HTMLElement] =
        fa.render(
            v,
            fa.formConfig,
        )(using 
            fa.validate_var
        )

    /* helpers */
    
    def _fieldNameFromFieldNameAnnotation(annots: List[Any]): Option[String] = 
        annots
            .find(_.isInstanceOf[FieldName])
            .map(_.asInstanceOf[FieldName])
            .map(_.value)

    def _fieldNameFromFormConfigAnnotation(annots: List[Any]): Option[String] = 
        annots
            .find(_.isInstanceOf[FormConfig])
            .map(_.asInstanceOf[FormConfig])
            .flatMap(_.fieldName)

    def _fieldNameFromFormConfigOverwrite(formConfigOverwrite: Option[FormConfig]): Option[String] = 
        formConfigOverwrite.flatMap(_.fieldName)

    def mkFinalFieldName(
        formConfigOverwrite: Option[FormConfig], 
        anns: List[Any], 
        typeInfo: TypeInfo
    ): String = 
        _fieldNameFromFormConfigOverwrite(formConfigOverwrite)
        .orElse(_fieldNameFromFormConfigAnnotation(anns))
        .orElse(_fieldNameFromFieldNameAnnotation(anns))
        .getOrElse(NameUtils.titleCase(typeInfo.short))

end LaminarForm