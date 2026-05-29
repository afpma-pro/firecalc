/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

final case class FireboxAvailability(
  traditional: Boolean,
  ecolabeled: Boolean,
  afpmaPrse: Boolean,
  singleTested: Boolean,
  door15aCatalog: Boolean
)

object FireboxAvailability:

  val AllEnabled: FireboxAvailability = FireboxAvailability(
    traditional = true,
    ecolabeled = true,
    afpmaPrse = true,
    singleTested = true,
    door15aCatalog = true
  )

  def disabledTypeNames(fa: FireboxAvailability): Set[String] =
    val names = List(
      fa.traditional   -> "Traditional",
      fa.ecolabeled    -> "Ecolabeled",
      fa.afpmaPrse     -> "AFPMA_PRSE",
      fa.singleTested  -> "SingleTested",
      fa.door15aCatalog -> "Door15aFirebox_Catalog"
    )
    names.collect { case (false, name) => name }.toSet