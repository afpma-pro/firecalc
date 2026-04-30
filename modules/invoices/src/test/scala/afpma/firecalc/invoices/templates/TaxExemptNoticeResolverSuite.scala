/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.templates

import afpma.firecalc.invoices.models.Address
import afpma.firecalc.invoices.models.Company

import io.taig.babel.Locales
import org.scalatest.funsuite.AnyFunSuite

class TaxExemptNoticeResolverSuite extends AnyFunSuite:

    private val template = DefaultInvoiceTemplate()

    private def makeSender(notice: Option[Map[String, String]]): Company =
        Company           (
            name            = "Test Org",
            address         = Address(
                street     = "1 Test St",
                city       = "Paris",
                postalCode = "75001",
                region     = "Île-de-France",
                country    = "France"
            ),
            email           = "test@test.org",
            taxExemptNotice = notice
        )

    test("sender with exact locale match returns that value") {
        val sender                 = makeSender(Some(Map("fr" -> "Exonération FR")))
        given io.taig.babel.Locale = Locales.fr
        val result                 = template.resolveTaxExemptNotice(sender)
        assert(result == Some("Exonération FR"))
    }

    test("sender with only default key falls back to default") {
        val sender                 = makeSender(Some(Map("default" -> "Fallback notice")))
        given io.taig.babel.Locale = Locales.fr
        val result                 = template.resolveTaxExemptNotice(sender)
        assert(result == Some("Fallback notice"))
    }

    test("sender with whitespace-only value returns None") {
        val sender                 = makeSender(Some(Map("fr" -> "  ")))
        given io.taig.babel.Locale = Locales.fr
        val result                 = template.resolveTaxExemptNotice(sender)
        assert(result == None)
    }

    test("sender with None taxExemptNotice returns None") {
        val sender                 = makeSender(None)
        given io.taig.babel.Locale = Locales.en
        val result                 = template.resolveTaxExemptNotice(sender)
        assert(result == None)
    }

    test("locale match takes precedence over default") {
        val sender                 = makeSender(Some(Map("fr" -> "FR override", "default" -> "Default fallback")))
        given io.taig.babel.Locale = Locales.fr
        val result                 = template.resolveTaxExemptNotice(sender)
        assert(result == Some("FR override"))
    }

    test("missing locale falls back to default") {
        val sender                 = makeSender(Some(Map("de" -> "DE only", "default" -> "Default fallback")))
        given io.taig.babel.Locale = Locales.fr
        val result                 = template.resolveTaxExemptNotice(sender)
        assert(result == Some("Default fallback"))
    }

    test("missing locale and no default returns None") {
        val sender                 = makeSender(Some(Map("de" -> "DE only")))
        given io.taig.babel.Locale = Locales.fr
        val result                 = template.resolveTaxExemptNotice(sender)
        assert(result == None)
    }
