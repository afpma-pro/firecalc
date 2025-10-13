/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui


import scala.annotation.nowarn

import cats.Show

import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUIInputs.CommonRenderingFactory
import afpma.firecalc.ui.formgen.Defaultable
import afpma.firecalc.ui.formgen.FormConfig
import afpma.firecalc.ui.formgen.LaminarForm
import afpma.firecalc.ui.formgen.LaminarFormFactory
import afpma.firecalc.ui.formgen.ValidateVar

import afpma.firecalc.units.all.*
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import magnolia1.*

// TODO: use a case class instead as implementation ?
trait DaisyUIVerticalForm[A] extends LaminarForm[A, DaisyUIVerticalForm[A]]:
    self =>
    
    def withFormConfig(
        formConfigOpt: Option[FormConfig]
    ): DaisyUIVerticalForm[A] =
        _formConfigOverwrite = formConfigOpt
        self

    def toHorizontalForm: DaisyUIHorizontalForm[A] = 
        DaisyUIHorizontalForm.makeFor(self.defaultable_instance): (v, cfg) =>
            self.render(v, cfg)

    def wrappedInto(contentWrapper: L.HtmlElement => L.HtmlElement): DaisyUIVerticalForm[A] = 
        DaisyUIVerticalForm.makeFor[A](defaultable_instance): (va, fc) =>
            contentWrapper(render(va, fc))

object DaisyUIVerticalForm 
extends LaminarFormFactory[DaisyUIVerticalForm] 
with AutoDerivation[DaisyUIVerticalForm]:

    type TypeClass[T] = DaisyUIVerticalForm[T]

    def makeForUsingOverwrite[A](
        mkDefaultableInstanceFromFormConfigOverwrite: Option[FormConfig] => Defaultable[A]
    )(
        renderFunc: (Var[A], FormConfig) => HtmlElement,
    ): VV_to_DF[A] =
        new DaisyUIVerticalForm[A]:

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

    override given forList: [A, K] => (fa: DaisyUIVerticalForm[A]) => (idOf: A => K) => DaisyUIVerticalForm[List[A]] =
        import fa.given_ValidateVar
        val dlist = afpma.firecalc.ui.formgen.Defaultable.forList[A](using fa.defaultable_instance)
        given ValidateVar[List[A]] = ValidateVar.forList
        makeFor(dlist): (variable, formConfig) =>
            // TODO: use list representation from DaisyUI5
            div(
                children <-- variable.split(idOf)((id, _, aVar) => {
                    div(
                        idAttr := s"list-item-$id",
                        div(
                            fa.render(
                                aVar,
                                formConfig
                            )
                        )
                    )
                })
            )

    override def forEnumOrSumTypeLike_UsingShowAsId[A: {Show, Defaultable, ValidateVar}](
        options: List[A],
        updateFieldName: Option[String] => Option[String] = identity,
    ) = 
        makeFor[A](afpma.firecalc.ui.formgen.Defaultable.summon[A]): (variable, formConfig) =>
            DaisyUIInputs.SelectFieldsetLabelAndInput.makeUsingShowAsId_required(
                updateFieldName(formConfig.shownFieldName),
                variable,
                options,
            )

       
    // =================
    // Option

    override val optionStringFactory    = DaisyUIInputs.TextFieldsetLabelAndInput
    override val optionDoubleFactory    = DaisyUIInputs.DoubleFieldsetLabelAndInput
    override val optionLocalDateFactory = DaisyUIInputs.LocalDateFieldsetLabelAndInput
    
    protected def mkRenderingFactoryForNumberWithUnitsAndValidation(
        sunitsVar: Var[List[SUnit[?]]],
        sunitCurrentVar: Var[SUnit[?]],
    ): CommonRenderingFactory[Double] =
        DaisyUIInputs
            .NumberInputWithUnitsAndFloatingLabelAndTooltipValidation
                .WithUnits(
                    sunitsVar, 
                    sunitCurrentVar, 
                    withFloatingLabel = false, 
                )

    protected def mkRenderingFactoryForEnum_UsingShowAsId[A: Show](
        options: List[A]
    ) = 
        DaisyUIInputs
            .SelectFieldsetLabelAndInput
            .UsingShowAsId_Optional[A](options)

    // ========================================
    // implementation for magnolia derivation

    def join[A](
        caseClass: CaseClass[DaisyUIVerticalForm.Typeclass, A]
    ): DaisyUIVerticalForm[A] = 
        new DaisyUIVerticalForm[A]:
            
            val defaultable_instance = Defaultable[A]:
                caseClass.construct: param =>
                    param.typeclass.defaultable_instance.default
            
            // TODO: make it dry: impl is common to horizontal and vertical impl.
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

            @nowarn override def render(
                variable: Var[A],
                formConfig: FormConfig,
            )(using ValidateVar[A]): HtmlElement = 
                val formConfig = caseClass.formConfigFrom(_formConfigOverwrite)
                val renderedParams = caseClass.params.map: param =>
                    val fn = param.fieldNameFrom(getFieldNameOverwriteForParam(param.label))
                    caseClass.renderParam(param, variable, formConfig.withFieldName(fn))
                caseClass.renderCaseClass(formConfig, renderedParams)

    val render_SumType_WrapperCls: String = "flex flex-col gap-2 items-start"

    extension [A](caseClass: CaseClass[DaisyUIVerticalForm, A])
        def renderCaseClass(
            formConfig: FormConfig,
            renderedParams: Seq[HtmlElement]
        ): HtmlElement =
            DaisyUIInputs.FieldsetLegend_WithLabelAndInputSeq(
                legendOpt = formConfig.fieldName
            ).amend(
                renderedParams
            )