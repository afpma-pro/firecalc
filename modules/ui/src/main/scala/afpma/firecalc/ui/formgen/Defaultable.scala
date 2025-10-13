/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.formgen


import java.time.LocalDate
import java.time.ZoneId

import afpma.firecalc.i18n.utils.NameUtils

import magnolia1.AutoDerivation
import magnolia1.CaseClass
import magnolia1.SealedTrait

/** Typeclass for default values.
  *
  * This typeclass is used to provide default values for a given type. It is
  * used to provide default values for form fields when creating a new object,
  * for example.
  *
  * It is necessary to provide a default value for every type used in the
  * application and wrapped in a option.
  */
trait Defaultable[+A] {
  self =>
  /** The default value for the type.
    */
  def default: A

  /** The label for the type.
    */
  def label: String =
    NameUtils.titleCase(default.getClass.getSimpleName)

  def map[B](f: A => B): Defaultable[B] = 
    new Defaultable[B]:
        def default = f(self.default)
}

object Defaultable extends AutoDerivation[Defaultable] {

  def summon[A](using ev: Defaultable[A]): Defaultable[A] = ev
  def apply[A](v: A): Defaultable[A] = new Defaultable[A]:
    def default = v

  given Defaultable[Boolean]:
    def default = false

  /** Default value for Int is 0.
    */
  given Defaultable[Int]:
    def default = 0

  given Defaultable[Double]:
    def default = 0

  given Defaultable[Float]:
    def default = 0

  given Defaultable[BigDecimal]:
    def default = 0
  given Defaultable[BigInt]:
    def default = 0

  /** Default value for String is "".
    */
  given Defaultable[String]:
    def default = ""

  given Defaultable[LocalDate]:
    def default = LocalDate.now(ZoneId.of("UTC"))

  given forList: [A] => (d: Defaultable[A]) => Defaultable[List[A]]:
    def default: List[A] = List(d.default)

  given makeOptionUsingDefaultValueFor: [A] => (d: Defaultable[A]) => Defaultable[Option[A]]:
    def default: Option[A] = Some(d.default)
  
  def makeOptionWithNoneFor[A]: Defaultable[Option[A]] = new Defaultable[Option[A]]:
    def default: Option[A] = None

  def join[T](caseClass: CaseClass[Defaultable, T]): Defaultable[T] = 
    new Defaultable[T]:
        def default: T = caseClass.construct: p => 
            p.typeclass.default
    
  def split[T](sealedTrait: SealedTrait[Defaultable, T]): Defaultable[T] = 
    selectFirstSubtypeAsDefaultableOrThrow(sealedTrait.typeInfo.short)(sealedTrait.subtypes.map(_.typeclass))

  def selectFirstSubtypeAsDefaultableOrThrow[T](parentTypeInfo: String)(subtypes: IArray[Defaultable[?]]): Defaultable[T] = 
    subtypes.headOption match
        case None      => throw new Exception(s"SealedTrait(${parentTypeInfo}) has no subtypes. Expecting at least one to generate instance")
        case Some(sub) => sub.asInstanceOf[Defaultable[T]]

}
