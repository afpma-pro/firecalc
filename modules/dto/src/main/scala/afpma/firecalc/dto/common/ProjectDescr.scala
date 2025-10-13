/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.Show
import cats.derived.*
import cats.implicits.toShow

import afpma.firecalc.i18n.*

import magnolia1.Transl

@Transl(I(_.headers.project_description))
final case class ProjectDescr(
    @Transl(I(_.project_description.reference))
    reference: String,
    @Transl(I(_.project_description.date))
    date: String,
    @Transl(I(_.project_description.country))
    country: Country
)

object ProjectDescr:
    val empty = ProjectDescr(
        reference = "",
        date = "",
        country = Country.France // Default to France, needed for regulatory compliance
    )

case class Customer(
    @Transl(I(_.customer.first_name))
    first_name: String,
    @Transl(I(_.customer.last_name))
    last_name: String,
    @Transl(I(_.customer.phone))
    phone: String,
    @Transl(I(_.customer.email))
    email: String,
)

object Customer:

    val empty = Customer(
        first_name = "",
        last_name = "",
        phone = "",
        email = ""
    )

case class Address(
    @Transl(I(_.address.header))
    header: String,
    @Transl(I(_.address.num))
    num: String,
    @Transl(I(_.address.street))
    street: String,
    @Transl(I(_.address.zip))
    zip: String,
    @Transl(I(_.address.city))
    city: String,
    @Transl(I(_.address.region_state))
    region_state: String,
    @Transl(I(_.address.country))
    country: Country
)

object Address:
    val empty_butInFrance = Address(
        header = "",
        num = "",
        street = "",
        zip = "",
        city = "",
        region_state = "",
        country = Country.France
    )
    given Show[Address] = Show.show: a =>
        List(
            a.header,
            List(a.num, a.street).filter(_.nonEmpty).mkString(" "),
            List(a.zip, a.city).filter(_.nonEmpty).mkString(" "),
            List(a.region_state, a.country.show).filter(_.nonEmpty).mkString(" ")
        ).filter(_.nonEmpty).mkString(", ")

// TODO: AvailableCountries ?
enum Country derives Show:
    case France, Belgique, Autriche