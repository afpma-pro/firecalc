/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import cats.Show
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.ui.formgen.ValidateVar

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.all.*
import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import coulomb.ops.standard.all.given
import coulomb.policy.standard.given

import afpma.firecalc.ui.models.{BillableCountry, BillableCustomerType, BillingLanguage, ClientProjectData}

object validatevar:
    import SUnits.given

    inline def validOption_WhenDefinedAndPositive[A: Show](isPositive: A => Boolean): ValidateVar[Option[A]] = 
        ValidateVar.validOption_WhenDefinedAnd[A](isPositive)(a => s"value should be >= 0 (got ${a.show})")

    inline def validOptionQtyD_WhenDefinedAndPositive[U](using su: SUnit[U]): ValidateVar[Option[QtyD[U]]] = 
        import su.given
        validOption_WhenDefinedAndPositive[QtyD[U]](_ >= 0.0.withUnit[U])

    inline def validOptionTempD_WhenDefinedAndPositive[U](using su: SUnit[U]): ValidateVar[Option[TempD[U]]] = 
        import su.given
        validOption_WhenDefinedAndPositive[TempD[U]](_ >= 0.0.withTemperature[U])

    object valid_always:
        inline given given_ValidateVar_AlwaysValid: [A] => ValidateVar[A] = 
            ValidateVar.valid
        inline given given_ValidateVar_AlwaysValid_ForList: [A] => ValidateVar[List[A]] = 
            ValidateVar.valid

    object validOption_always:
        inline given given_ValidateVarOption_AlwaysValid: [A] => ValidateVar[Option[A]] = 
            ValidateVar.valid

    // basic types

    trait Valid_and_ValidOption_Always_Factory[T]:
        given valid_Always: ValidateVar[T] = 
            valid_always.given_ValidateVar_AlwaysValid[T]
        given validOption_Always: ValidateVar[Option[T]] = 
            validOption_always.given_ValidateVarOption_AlwaysValid[T]

    object boolean extends Valid_and_ValidOption_Always_Factory[Boolean]
    object string extends Valid_and_ValidOption_Always_Factory[String]
    object int    extends Valid_and_ValidOption_Always_Factory[Int]
    object double extends Valid_and_ValidOption_Always_Factory[Double]
    object float  extends Valid_and_ValidOption_Always_Factory[Float]
    object long   extends Valid_and_ValidOption_Always_Factory[Long]

    // units

    trait ValidOptionQtyD_WhenPositive_Factory[U: SUnit]:
        given validOption_whenPositive: ValidateVar[Option[QtyD[U]]] = 
            validOptionQtyD_WhenDefinedAndPositive[U]
        given valid_whenPositive: ValidateVar[QtyD[U]] = 
            validOption_whenPositive.flatten

    trait ValidOption_WhenPositive_Factory[A: Show](isPositive: A => Boolean):
        given validOption_whenPositive: ValidateVar[Option[A]] = 
            ValidateVar.validOption_WhenDefinedAnd[A](isPositive)(a => s"value should be >= 0 (got ${a.show})")
        given valid_whenPositive: ValidateVar[A] = 
            validOption_whenPositive.flatten

    object angle extends ValidOptionQtyD_WhenPositive_Factory[Degree]
    object area extends ValidOptionQtyD_WhenPositive_Factory[(Meter ^ 2)]
    object area_in_cm2 extends ValidOptionQtyD_WhenPositive_Factory[(Centimeter ^ 2)]

    // object angle:
    //     given validOption_whenPositive: ValidateVar[Option[Angle]] = 
    //         validOptionQtyD_WhenDefinedAndPositive

    object hour:
        given validOption_whenPositive: ValidateVar[Option[QtyD[Hour]]] = 
            validOptionQtyD_WhenDefinedAndPositive

    object meter extends ValidOptionQtyD_WhenPositive_Factory[Meter]
    
    object kilogram:
        given validOption_whenPositive: ValidateVar[Option[QtyD[Kilogram]]] = 
            validOptionQtyD_WhenDefinedAndPositive
        given valid_whenPositive: ValidateVar[QtyD[Kilogram]] = 
            validOption_whenPositive.flatten
    
    object kilowatt:
        given validOption_whenPositive: ValidateVar[Option[QtyD[Kilo * Watt]]] = 
            validOptionQtyD_WhenDefinedAndPositive
        given valid_whenPositive: ValidateVar[QtyD[Kilo * Watt]] = 
            validOption_whenPositive.flatten

    object percent:
        given validOption_whenPositive: ValidateVar[Option[QtyD[Percent]]] = 
            validOptionQtyD_WhenDefinedAndPositive

    object square_meter_kelvin_per_watt:
        given validOption_whenPositive: ValidateVar[Option[SquareMeterKelvinPerWatt]] = 
            validOptionQtyD_WhenDefinedAndPositive

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
    
    object watt_per_meter_kelvin:
        given validOption_whenPositive: ValidateVar[Option[QtyD[Watt / (Meter * Kelvin)]]] = 
            validOptionQtyD_WhenDefinedAndPositive

    // business logic types

    object areaName                         extends Valid_and_ValidOption_Always_Factory[PipeLocation.AreaName]
    object billingLanguage                  extends Valid_and_ValidOption_Always_Factory[BillingLanguage]
    object FacingType                       extends Valid_and_ValidOption_Always_Factory[FacingType]
    object billableCountry                  extends Valid_and_ValidOption_Always_Factory[BillableCountry]
    object billableCustomerType             extends Valid_and_ValidOption_Always_Factory[BillableCustomerType]
    object clientProjectData                extends Valid_and_ValidOption_Always_Factory[ClientProjectData]
    object country                          extends Valid_and_ValidOption_Always_Factory[Country]
    object customer                         extends Valid_and_ValidOption_Always_Factory[Customer]
    object innerConstructionMaterial        extends Valid_and_ValidOption_Always_Factory[InnerConstructionMaterial]
    object sizingMethod                     extends Valid_and_ValidOption_Always_Factory[SizingMethod]
    object pipeLocation                     extends Valid_and_ValidOption_Always_Factory[PipeLocation]

    object roughness extends ValidOption_WhenPositive_Factory[Roughness](_ >= 0.meters)


    

            
            

