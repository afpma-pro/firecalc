/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import scala.annotation.nowarn

import cats.Show
import cats.syntax.show.*

import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUIInputs.CommonRenderingFactory
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.utils.OptionalField

import afpma.firecalc.units.all.*
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import magnolia1.*

// TODO: use a case class instead as implementation ?
trait DaisyUIHorizontalForm[A] extends LaminarForm[A, DaisyUIHorizontalForm[A]]:
    self =>

    def withFormConfig(
        formConfigOpt: Option[FormConfig]
    ): DaisyUIHorizontalForm[A] =
        _formConfigOverwrite = formConfigOpt
        self

    def toVerticalForm: DaisyUIVerticalForm[A] = 
        DaisyUIVerticalForm.makeFor(self.defaultable_instance): (v, cfg) =>
            self.render(v, cfg)

    def wrappedInto(contentWrapper: L.HtmlElement => L.HtmlElement): DaisyUIHorizontalForm[A] = 
        DaisyUIHorizontalForm.makeFor[A](defaultable_instance): (va, fc) =>
            contentWrapper(render(va, fc))

object DaisyUIHorizontalForm 
extends LaminarFormFactory[DaisyUIHorizontalForm]
with Derivation[DaisyUIHorizontalForm]: // use semi auto derivation for better control

    type TypeClass[A] = DaisyUIHorizontalForm[A]

    def makeForUsingOverwrite[A](
        mkDefaultableInstanceFromFormConfigOverwrite: Option[FormConfig] => Defaultable[A]
    )(
        renderFunc: (Var[A], FormConfig) => HtmlElement,
    ): VV_to_DF[A] =
        new DaisyUIHorizontalForm[A]:
            
            def defaultable_instance: Defaultable[A] = 
                mkDefaultableInstanceFromFormConfigOverwrite(_formConfigOverwrite)

            lazy val validate_var = ValidateVar[A]

            @nowarn override def render(
                variable: Var[A],
                formConfig: FormConfig,
            )(using ValidateVar[A]): HtmlElement = 
                renderFunc(variable, formConfig)

    // ========================================
    // automatic instances for basic types

    override given forBoolean: D_VV_to_DF[Boolean] =
        val db = Defaultable.summon[Boolean]
        makeFor(db): (variable, formConfig) =>
            DaisyUIInputs.CheckboxFieldsetInput(
                variable,
                withLabel = formConfig.shownFieldName
            )

    def forList_fromComponent[A](mkComp: Var[List[A]] => HtmlElement)(using ValidateVar[List[A]]): DaisyUIHorizontalForm[List[A]] = 
        given dlist: Defaultable[List[A]] = Defaultable(List.empty)
        makeFor(dlist): (variable, _) =>
            mkComp(variable)

    override given forList: [A, K] => (fa: DaisyUIHorizontalForm[A]) => (idOf: A => K) => DaisyUIHorizontalForm[List[A]] =
        import fa.given_ValidateVar
        val dlist = afpma.firecalc.ui.formgen.Defaultable.forList[A](using fa.defaultable_instance)
        given ValidateVar[List[A]] = ValidateVar.forList
        makeFor(dlist): (variable, formConfig) =>
            div(
                cls := "flex flex-row flex-nowrap",
                children <-- variable.split(idOf)((id, _, aVar) => {
                    div(
                        idAttr := s"list-item-$id",
                        div(
                            fa.render(
                                aVar, 
                                formConfig,
                            )
                        )
                    )
                })
            )

    private def makeIdsForSelectOptions[A: Show](options: Seq[A]): List[(String, A)] = 
        options.map(a => (a.show, a)).toList

    override def forEnumOrSumTypeLike_UsingShowAsId[A: {Show, Defaultable, ValidateVar}](
        options: List[A],
        updateFieldName: Option[String] => Option[String] = identity,
    ) = 
        makeFor[A](afpma.firecalc.ui.formgen.Defaultable.summon[A]): (variable, formConfig) =>
            val ids = makeIdsForSelectOptions(options)
            def getById(id: String): A = ids
                .find(x => x._1 == id)
                .getOrElse(throw new Exception("unexpected error: can not get element back"))
                ._2
            DaisyUIInputs.LabelledSelectInputWithUnitAndTooltip(
                variable,
                options,
                show = _.show,
                makeId = _.show,
                getById = getById,
                labelStart = updateFieldName(formConfig.shownFieldName),
            )
       
    // =================
    // Option

    // private def makeForOptionUsingCommonFactory[A](
    //     factory: CommonRenderingFactory[A]
    // )(
    //     updateFieldName: Option[String] => Option[String] = identity,
    //     isOptional: Boolean = false
    // ): DaisyUI5HorizontalForm[Option[A]] =
    //     makeFor[Option[A]]: (variable, formConfig) =>
    //         factory.make(
    //             v           = variable,
    //             label       = updateFieldName(Option.when(showFieldName)(fieldName)),
    //             isOptional  = isOptional,
    //         )

    override val optionStringFactory    = DaisyUIInputs.TextInputWithFloatingLabelAndTooltipValidation.Make(
        withFloatingLabel = true
    )
    override val optionDoubleFactory    = DaisyUIInputs.LabelledNumberInputWithUnitAndTooltip
    override val optionLocalDateFactory = DaisyUIInputs.LabelledLocalDateInputWithUnitAndTooltip

    override def forOptionQtyD_default[U: SUnit] = forOptionQtyD[U](
        updateFieldName = identity,
        optionalField = OptionalField.No
    )

    protected def mkRenderingFactoryForNumberWithUnitsAndValidation(
        sunitsVar: Var[List[SUnit[?]]],
        sunitCurrentVar: Var[SUnit[?]],
    ): CommonRenderingFactory[Double] =
        DaisyUIInputs
            .NumberInputWithUnitsAndFloatingLabelAndTooltipValidation
            .WithUnits(
                sunitsVar, 
                sunitCurrentVar,
                withFloatingLabel = true,
            )

    protected def mkRenderingFactoryForEnum_UsingShowAsId[A: Show](
        options: List[A]
    ) = new CommonRenderingFactory[A] {
        @nowarn def make(
            v: Var[Option[A]], 
            label: Option[String], 
            optionalField: OptionalField
        )(using ValidateVar[Option[A]]): L.HtmlElement = 
            // because we want an optional field, we add empty string "" in the list, it will be matched as 'None'
            val optionsNew  = None :: options.map(Some(_))
            val showsNew    = ""   :: makeIdsForSelectOptions(options)
            val idsNew      = showsNew zip optionsNew
            def getById(id: String): Option[A] = idsNew
                .find(x => x._1 == id)
                .getOrElse(throw new Exception("unexpected error: can not get element back"))
                ._2
            DaisyUIInputs.LabelledSelectInputWithUnitAndTooltip(
                v,
                optionsNew,
                show = _.show,
                makeId = _.show,
                getById = getById,
                labelStart = label,
            )
    }

    // ========================================
    // implementation for magnolia derivation

    def join[A](
        caseClass: CaseClass[DaisyUIHorizontalForm.Typeclass, A]
    ): DaisyUIHorizontalForm[A] = 
        new DaisyUIHorizontalForm[A]:
            
            val defaultable_instance = Defaultable[A]:
                caseClass.construct: param =>
                    param.typeclass.defaultable_instance.default

            // Algo :
            // Validate each params
            // If all are valid, return valid
            // otherwise, accumulate errors with the following format
            // "{param1.label}" / "error 11"
            // "{param1.label}" / "error 12"
            // "{param2.label}" / "error 21"
            // "{param3.label}" / "error 31"

            lazy val validate_var: ValidateVar[A] = ValidateVar.make: a =>
                val paramsVNelString: List[VNelString[Unit]] = 
                    caseClass.parameters.toList.map: param =>
                        val paramLabel = param.label
                        val p = param.deref(a)
                        val vnel = param.typeclass.validate_var.validate(p)
                        vnel.leftMap(_.map(err => s"$paramLabel / $err"))

                paramsVNelString.forall(_.isValid) match
                    case true => VNelString.validUnit
                    case false => 
                        VNelString.invalidUnsafe(
                            paramsVNelString
                                .filter(_.isInvalid)
                                .map(_.swap.toOption.get.toList)
                                .flatten
                        )

            override given given_ValidateVar: ValidateVar[A] = validate_var

            val panel = caseClass.formConfigFrom(_formConfigOverwrite)

            @nowarn override def render(
                variable: Var[A],
                formConfig: FormConfig
            )(using ValidateVar[A]): HtmlElement = 
                val renderedParams = caseClass.params.map: param =>
                    val plabel = param.label
                    // scala.scalajs.js.Dynamic.global.console.log(s"plabel = ${plabel}")
                    val overwrite = getFieldNameOverwriteForParam(plabel)
                    // scala.scalajs.js.Dynamic.global.console.log(s"overwrite = ${overwrite}")
                    val fn = param.fieldNameFrom(overwrite)
                    // scala.scalajs.js.Dynamic.global.console.log(s"fieldNameFrom returned = ${fn}")
                    val finalFormConfig = formConfig.withFieldName(fn)
                    // scala.scalajs.js.Dynamic.global.console.log(s"finalFormConfig = ${finalFormConfig}")
                    caseClass.renderParam(param, variable, finalFormConfig)
                caseClass.renderCaseClass(panel, renderedParams)
                    // .amend(
                    //     span(text <-- variable.signal.map(_.toString()))
                    // )

    val render_SumType_WrapperCls: String = "flex flex-row gap-1 items-end"

    extension [A](caseClass: CaseClass[DaisyUIHorizontalForm, A])
        def renderCaseClass(
            panelConfig: FormConfig,
            renderedParams: Seq[HtmlElement]
        ): HtmlElement =
            div(
                cls := "flex flex-row gap-1 items-end",
                renderedParams.map: el =>
                    div(cls := "flex-auto", el)
            )