/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import algebra.instances.all.given

import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.laminar.form.ValidateVar
import afpma.laminar.form.FormMessages
import afpma.firecalc.ui.models.BillableCountry
import afpma.firecalc.ui.models.BillableCustomerType
import afpma.firecalc.ui.models.BillingLanguage
import afpma.firecalc.ui.models.ClientProjectData
import afpma.firecalc.ui.i18n.implicits.I18N_UI as I18N
import io.taig.babel.Locale

import cats.Show
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*

import afpma.firecalc.dto.common.DisplayUnits

class ValidateVarCommonInstances(using DisplayUnits, Locale):
    import SUnits.given

    private given FormMessages with
        def valueIsUndefined = I18N.errors.value_is_undefined
        def notImplementedYet = I18N.ui_messages.not_implemented_yet

    inline def validOption_WhenDefinedAndPositive[A: Show](isPositive: A => Boolean): ValidateVar[Option[A]] =
        ValidateVar.validOption_WhenDefinedAnd[A](isPositive)(a => I18N.errors.value_ge_0(a.showP))
    
    inline def validOption_WhenDefinedAndStrictlyPositive[A: Show](isStrictlyPositive: A => Boolean): ValidateVar[Option[A]] =
        ValidateVar.validOption_WhenDefinedAnd[A](isStrictlyPositive)(a => I18N.errors.value_gt_0(a.showP))

    inline def validOptionQtyD_WhenDefinedAndPositive[U](using su: SUnit[U]): ValidateVar[Option[QtyD[U]]] =
        import su.given
        validOption_WhenDefinedAndPositive[QtyD[U]](_ >= 0.0.withUnit[U])

    inline def validOptionQtyD_WhenDefinedAndStrictlyPositive[U](using su: SUnit[U]): ValidateVar[Option[QtyD[U]]] =
        import su.given
        validOption_WhenDefinedAndStrictlyPositive[QtyD[U]](_ > 0.0.withUnit[U])

    inline def validOptionTempD_WhenDefinedAndPositive[U](using su: SUnit[U]): ValidateVar[Option[TempD[U]]] =
        import su.given
        validOption_WhenDefinedAndPositive[TempD[U]](_ >= 0.0.withTemperature[U])

    // units

    trait ValidOptionQtyD_WhenPositive_Factory[U: SUnit]:
        given validOption_whenPositive: ValidateVar[Option[QtyD[U]]] =
            validOptionQtyD_WhenDefinedAndPositive[U]
        given valid_whenPositive      : ValidateVar[QtyD[U]]         =
            validOption_whenPositive.flatten

    trait ValidOptionQtyD_WhenStrictlyPositive_Factory[U: SUnit]:
        given validOption_whenStrictlyPositive: ValidateVar[Option[QtyD[U]]] =
            validOptionQtyD_WhenDefinedAndStrictlyPositive[U]
        given valid_whenStrictlyPositive      : ValidateVar[QtyD[U]]         =
            validOption_whenStrictlyPositive.flatten

    trait ValidOption_WhenPositive_Factory[A: Show](isPositive: A => Boolean):
        given validOption_whenPositive: ValidateVar[Option[A]] =
            ValidateVar.validOption_WhenDefinedAnd[A](isPositive)(a => I18N.errors.value_ge_0(a.showP))
        given valid_whenPositive      : ValidateVar[A]         =
            validOption_whenPositive.flatten

    trait ValidOption_WhenStrictlyPositive_Factory[A: Show](isStrictlyPositive: A => Boolean):
        given validOption_whenStrictlyPositive: ValidateVar[Option[A]] =
            ValidateVar.validOption_WhenDefinedAnd[A](isStrictlyPositive)(a => I18N.errors.value_gt_0(a.showP))
        given valid_whenStrictlyPositive      : ValidateVar[A]         =
            validOption_whenStrictlyPositive.flatten

    object angle       
        extends ValidOptionQtyD_WhenPositive_Factory[Degree]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Degree]
    
    object area        
        extends ValidOptionQtyD_WhenPositive_Factory[(Meter ^ 2)]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[(Meter ^ 2)]
    
    object area_in_cm2 
        extends ValidOptionQtyD_WhenPositive_Factory[(Centimeter ^ 2)]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[(Centimeter ^ 2)]

    object centimeter 
        extends ValidOptionQtyD_WhenPositive_Factory[Centimeter]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Centimeter]

    object hour
        extends ValidOptionQtyD_WhenPositive_Factory[Hour]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Hour]

    object minute
        extends ValidOptionQtyD_WhenPositive_Factory[Minute]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Minute]

    object meter 
        extends ValidOptionQtyD_WhenPositive_Factory[Meter]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Meter]

    object kilogram
        extends ValidOptionQtyD_WhenPositive_Factory[Kilogram]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Kilogram]

    object kilowatt
        extends ValidOptionQtyD_WhenPositive_Factory[Kilo * Watt]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Kilo * Watt]

    object percent:
        given validOption_whenPositive: ValidateVar[Option[QtyD[Percent]]] =
            validOptionQtyD_WhenDefinedAndPositive

    object square_meter_kelvin_per_watt
        extends ValidOptionQtyD_WhenPositive_Factory[(Meter ^ 2) * Kelvin / Watt]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[(Meter ^ 2) * Kelvin / Watt]

    object unitless:
        given validOption_whenPositive: ValidateVar[Option[QtyD[1]]] =
            validOptionQtyD_WhenDefinedAndPositive

    object temp:

        object celsius:
            given validOption_whenPositive: ValidateVar[Option[TempD[Celsius]]] =
                validOptionTempD_WhenDefinedAndPositive

        object kelvin:
            given validOption_whenPositive: ValidateVar[Option[TempD[Kelvin]]] =
                validOptionTempD_WhenDefinedAndPositive

    object watt_per_meter_kelvin
        extends ValidOptionQtyD_WhenPositive_Factory[Watt / (Meter * Kelvin)]
        with ValidOptionQtyD_WhenStrictlyPositive_Factory[Watt / (Meter * Kelvin)]

    object roughness 
        extends ValidOption_WhenPositive_Factory[Roughness](_ >= 0.meters)
        with ValidOption_WhenStrictlyPositive_Factory[Roughness](_ > 0.meters)

object ValidateVarCommonInstances:

    object valid_always:
        inline given given_ValidateVar_AlwaysValid        : [A] => ValidateVar[A]       =
            ValidateVar.valid
        inline given given_ValidateVar_AlwaysValid_ForList: [A] => ValidateVar[List[A]] =
            ValidateVar.valid

    object validOption_always:
        inline given given_ValidateVarOption_AlwaysValid: [A] => ValidateVar[Option[A]] =
            ValidateVar.valid

    // basic types

    trait Valid_and_ValidOption_Always_Factory[T]:
        given valid_Always      : ValidateVar[T]         =
            valid_always.given_ValidateVar_AlwaysValid[T]
        given validOption_Always: ValidateVar[Option[T]] =
            validOption_always.given_ValidateVarOption_AlwaysValid[T]

    object boolean extends Valid_and_ValidOption_Always_Factory[Boolean]
    object string  extends Valid_and_ValidOption_Always_Factory[String]
    object int     extends Valid_and_ValidOption_Always_Factory[Int]
    object double  extends Valid_and_ValidOption_Always_Factory[Double]
    object float   extends Valid_and_ValidOption_Always_Factory[Float]
    object long    extends Valid_and_ValidOption_Always_Factory[Long]

    // business logic types

    object areaName                  extends Valid_and_ValidOption_Always_Factory[PipeLocation.AreaName]
    object billingLanguage           extends Valid_and_ValidOption_Always_Factory[BillingLanguage]
    object FacingType                extends Valid_and_ValidOption_Always_Factory[FacingType]
    object billableCountry           extends Valid_and_ValidOption_Always_Factory[BillableCountry]
    object billableCustomerType      extends Valid_and_ValidOption_Always_Factory[BillableCustomerType]
    object clientProjectData         extends Valid_and_ValidOption_Always_Factory[ClientProjectData]
    object country                   extends Valid_and_ValidOption_Always_Factory[Country]
    object customer                  extends Valid_and_ValidOption_Always_Factory[Customer]
    object innerConstructionMaterial extends Valid_and_ValidOption_Always_Factory[InnerConstructionMaterial]
    object sizingMethod              extends Valid_and_ValidOption_Always_Factory[SizingMethod]
    object pipeLocation              extends Valid_and_ValidOption_Always_Factory[PipeLocation]
    object testStandard              extends Valid_and_ValidOption_Always_Factory[TestStandard]
