/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.units.all.*

import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUIInputs.CommonRenderingFactory
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.utils.OptionalField

import cats.Show
import cats.syntax.show.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement

import scala.annotation.nowarn

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
        renderFunc: (Var[A], FormConfig) => HtmlElement
    ): VV_to_DF[A] =
        new DaisyUIHorizontalForm[A]:

            def defaultable_instance: Defaultable[A] =
                mkDefaultableInstanceFromFormConfigOverwrite(_formConfigOverwrite)

            lazy val validate_var = ValidateVar[A]

            @nowarn override def render(
                variable  : Var[A],
                formConfig: FormConfig
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

    def forList_fromComponent[A](mkComp: Var[List[A]] => HtmlElement)(using
        ValidateVar[List[A]]
    ): DaisyUIHorizontalForm[List[A]] =
        given dlist: Defaultable[List[A]] = Defaultable(List.empty)
        makeFor(dlist): (variable, _) =>
            mkComp(variable)

    override given forList
        : [A, K] => (fa: DaisyUIHorizontalForm[A]) => (idOf: A => K) => DaisyUIHorizontalForm[List[A]] =
        import fa.given_ValidateVar
        val dlist                  = afpma.firecalc.ui.formgen.Defaultable.forList[A](using fa.defaultable_instance)
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
                                formConfig
                            )
                        )
                    )
                })
            )

    private def makeIdsForSelectOptions[A: Show](options: Seq[A]): List[(String, A)] =
        options.map(a => (a.show, a)).toList

    override def forEnumOrSumTypeLike_UsingShowAsId[A: {Show, Defaultable, ValidateVar}](
        options        : List[A],
        updateFieldName: Option[String] => Option[String] = identity
    ) =
        makeFor[A](afpma.firecalc.ui.formgen.Defaultable.summon[A]): (variable, formConfig) =>
            val ids = makeIdsForSelectOptions(options)
            def getById(id: String)      : A = ids
                .find(x => x._1 == id)
                .getOrElse(throw new Exception("unexpected error: can not get element back"))
                ._2
            DaisyUIInputs.LabelledSelectInputWithUnitAndTooltip(
                variable,
                options,
                show       = _.show,
                makeId     = _.show,
                getById    = getById,
                labelStart = updateFieldName(formConfig.shownFieldName)
            )

    /**
     * Creates a form with TextInputWithDatalist for selection and an editable default value.
     * When selection changes, the default value is automatically updated.
     *
     * @tparam A The main type being edited (e.g., Material_15544_V2)
     * @tparam T The type of the default value field (e.g., Roughness)
     * @param selectOptions List of valid selectable A instances (used to build datalist)
     * @param getDefaultValue Function to extract the default value T from A
     * @param withDefaultValue Function to create A from existing A with new T value
     * @param getId Function to get the unique ID/name for each A (used for datalist value)
     * @param showA Implicit Show[A] for displaying localized labels
     * @param formForT Implicit DF[T] for rendering the default value field
     * @param defaultableA Implicit Defaultable[A] for form defaults
     * @param validateVarA Implicit ValidateVar[A] for validation
     */
    def forSelectionWithDefaultValue_usingDataList[A, T](
        selectOptions   : List[A],
        getDefaultValue : A => T,
        withDefaultValue: (A, T) => A,
        getId           : A => String
    )(using
        showA       : Show[A],
        formForT    : DaisyUIHorizontalForm[T],
        defaultableA: Defaultable[A],
        validateVarA: ValidateVar[A],
        validateVarT: ValidateVar[T]
    ): DaisyUIHorizontalForm[A] =

        makeFor[A](defaultableA): (variable, formConfig) =>
            // Build lookup maps
            val idToOption  : Map[String, A]      = selectOptions.map(a => getId(a) -> a).toMap
            val optionLabels: Seq[String]         = selectOptions.map(showA.show)
            val optionIds   : Seq[String]         = selectOptions.map(getId)
            val labelToId   : Map[String, String] = (optionLabels zip optionIds).toMap
            val idToLabel   : Map[String, String] = (optionIds zip optionLabels).toMap

            // Current selection ID
            val currentId = getId(variable.now())

            // Var for the displayed text (localized label) - can be invalid free text
            val displayTextVar = Var[Option[String]](idToLabel.get(currentId))

            // Var for the default value (type T)
            val defaultValueVar = variable.zoomLazy(getDefaultValue)(withDefaultValue)

            // Derive the selected A option from display text
            // If text matches a known label, resolve to Some(A), otherwise None
            val selectedOptionSignal: com.raquo.airstream.core.Signal[Option[A]] = displayTextVar.signal.map:
                case Some(text) =>
                    labelToId.get(text).flatMap(idToOption.get)
                case None       => None

            // Binder: When selection changes, update parent variable
            val selectionChangeBinder = selectedOptionSignal.changes --> Observer[Option[A]]: opt =>
                opt.foreach(variable.set)

            // Build datalist options: value = id, displayed = localized label
            val datalistId = s"select-options-${formConfig.fieldName.getOrElse("default")}"

            // Create TextInputWithDatalist
            val selectionInput = DaisyUIInputs.TextInputWithDatalist(
                valueOptVar = displayTextVar,
                datalistId  = datalistId,
                options     = optionLabels,
                placeholder = formConfig.shownFieldName.getOrElse("Select...")
            )

            // Validation indicator for invalid selection
            val invalidIndicator  = span(
                cls := "text-error text-sm",
                display <-- selectedOptionSignal.map(_.fold("inline")(_ => "none")),
                "x"
            )
            // Default value input using the provided form for T
            val defaultValueInput = formForT.render(
                defaultValueVar,
                formForT.formConfig.doShowFieldName
            )

            div(
                cls := "flex flex-row gap-2 items-end",
                div(cls := "flex-auto", selectionInput.node),
                invalidIndicator,
                div(cls := "flex-auto", defaultValueInput  )
            ).amend(
                selectionChangeBinder
            )

    /**
     * Creates a form with SelectInput for selection and an editable default value.
     * When selection changes, the default value is automatically updated.
     *
     * @tparam A The main type being edited (e.g., Material_15544_V2)
     * @tparam T The type of the default value field (e.g., Roughness)
     * @param selectOptions List of valid selectable A instances (used to build select options)
     * @param getDefaultValue Function to extract the default value T from A
     * @param withDefaultValue Function to create A from existing A with new T value
     * @param getId Function to get the unique ID/name for each A (used for select option value)
     * @param showA Implicit Show[A] for displaying localized labels
     * @param formForT Implicit DF[T] for rendering the default value field
     * @param defaultableA Implicit Defaultable[A] for form defaults
     * @param validateVarA Implicit ValidateVar[A] for validation
     */
    def forSelectionWithDefaultValue_usingSelectInput[A, T](
        selectOptions   : List[A],
        getDefaultValue : A => T,
        withDefaultValue: (A, T) => A,
        getId           : A => String
    )(using
        showA       : Show[A],
        formForT    : DaisyUIHorizontalForm[T],
        defaultableA: Defaultable[A],
        validateVarA: ValidateVar[A],
        validateVarT: ValidateVar[T]
    ): DaisyUIHorizontalForm[A] =

        makeFor[A](defaultableA): (variable, formConfig) =>
            // Var for the default value (type T)
            val defaultValueVar = variable.zoomLazy(getDefaultValue)(withDefaultValue)

            // Create select dropdown using existing component
            val selectionInput = DaisyUIInputs.SelectFieldsetLabelAndInput(
                labelOpt      = None, // No label since we're side-by-side
                selectedVar   = variable,
                options       = selectOptions,
                show          = showA.show,
                makeId        = getId,
                getById       = id => selectOptions.find(a => getId(a) == id).getOrElse(defaultableA.default),
                optionalField = OptionalField.No
            )

            // Default value input using the provided form for T
            val defaultValueInput = formForT.render(
                defaultValueVar,
                formForT.formConfig
            )

            div(
                cls := "flex flex-row gap-2 items-end",
                div(cls := "flex-auto", selectionInput.node),
                div(cls := "flex-auto", defaultValueInput  )
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
        optionalField   = OptionalField.No
    )

    protected def mkRenderingFactoryForNumberWithUnitsAndValidation(
        sunitsVar      : Var[List[SUnit[?]]],
        sunitCurrentVar: Var[SUnit[?]]
    ): CommonRenderingFactory[Double] =
        DaisyUIInputs.NumberInputWithUnitsAndFloatingLabelAndTooltipValidation
            .WithUnits(
                sunitsVar,
                sunitCurrentVar,
                withFloatingLabel = true
            )

    protected def mkRenderingFactoryForEnum_UsingShowAsId[A: Show](
        options: List[A]
    ) = new CommonRenderingFactory[A] {
        @nowarn def make(
            v            : Var[Option[A]],
            label        : Option[String],
            optionalField: OptionalField
        )(using ValidateVar[Option[A]]): L.HtmlElement =
            // because we want an optional field, we add empty string "" in the list, it will be matched as 'None'
            val optionsNew = None :: options.map(Some(_))
            val showsNew   = ""   :: makeIdsForSelectOptions(options)
            val idsNew     = showsNew zip optionsNew
            def getById(id: String)      : Option[A] = idsNew
                .find(x => x._1 == id)
                .getOrElse(throw new Exception("unexpected error: can not get element back"))
                ._2
            DaisyUIInputs.LabelledSelectInputWithUnitAndTooltip(
                v,
                optionsNew,
                show       = _.show,
                makeId     = _.show,
                getById    = getById,
                labelStart = label
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
                        val p          = param.deref(a)
                        val vnel       = param.typeclass.validate_var.validate(p)
                        vnel.leftMap(_.map(err => s"$paramLabel / $err"))

                paramsVNelString.forall(_.isValid) match
                    case true  => VNelString.validUnit
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
                variable  : Var[A],
                formConfig: FormConfig
            )(using ValidateVar[A]): HtmlElement =
                val renderedParams = caseClass.params.map: param =>
                    val plabel          = param.label
                    // scala.scalajs.js.Dynamic.global.console.log(s"plabel = ${plabel}")
                    val overwrite       = getFieldNameOverwriteForParam(plabel)
                    // scala.scalajs.js.Dynamic.global.console.log(s"overwrite = ${overwrite}")
                    val fn              = param.fieldNameFrom(overwrite)
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
            panelConfig   : FormConfig,
            renderedParams: Seq[HtmlElement]
        ): HtmlElement =
            div(
                cls := "flex flex-row gap-1 items-end",
                renderedParams.map: el =>
                    div(cls := "flex-auto", el)
            )
