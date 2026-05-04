/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.typst

import java.time.LocalDate

import afpma.firecalc.invoices.i18n.I18nData_Invoices
import afpma.firecalc.invoices.i18n.implicits.I18Ns
import afpma.firecalc.invoices.models.PaymentMethod
import afpma.firecalc.invoices.models.PaymentTerms
import afpma.firecalc.invoices.typst.InvoiceTypstInstances.given

import io.taig.babel.Locales
import org.scalatest.funsuite.AnyFunSuite

class PaymentTermsTypstShowSuite extends AnyFunSuite:

    private val i18nEn: I18nData_Invoices = I18Ns(Locales.en)
    private val i18nFr: I18nData_Invoices = I18Ns(Locales.fr)

    private val baseTerms = PaymentTerms(
        dueDays           = 30,
        description       = "Net 30",
        lateFeePercentage = Some(BigDecimal("1.5"))
    )

    private val bankTransfer = PaymentMethod.BankTransfer(
        iban = Some("FR7612345678901234567890123"),
        bic  = Some("BNPAFRPP")
    )

    private def render(terms: PaymentTerms)(using I18nData_Invoices): String =
        TypstShow[PaymentTerms].showAsTypst(terms)

    test("no SEPA mandate present - existing behavior, no SEPA block, no fallback") {
        given I18nData_Invoices = i18nEn
        val terms               = baseTerms.copy(methods = List(bankTransfer, PaymentMethod.Check))
        val out                 = render(terms)

        assert(out.contains("Payment due within 30 days") )
        assert(out.contains("Late fee:")                  )
        assert(out.contains("Bank Transfer")              )
        assert(out.contains("Check")                      )
        assert(!out.contains("SEPA Direct Debit")         )
        assert(!out.contains("If the SEPA direct debit")  )
        assert(!out.contains("SEPA mandate setup pending"))
    }

    test("SEPA mandate fully populated - shows reference, date, next-charge, fallback prose") {
        given I18nData_Invoices = i18nEn
        val sepa                = PaymentMethod.SepaMandate(
            mandateReference       = Some("MD000ABC123"),
            mandateDate            = Some(LocalDate.of(2026, 4, 15)),
            iban                   = Some("FR7612345678901234567890123"),
            nextPossibleChargeDate = Some(LocalDate.of(2026, 5, 2))
        )
        val terms               = baseTerms.copy(methods = List(sepa, bankTransfer))
        val out                 = render(terms)

        assert(out.contains("SEPA Direct Debit")                           )
        assert(out.contains("Mandate reference: MD000ABC123")              )
        assert(out.contains("Mandate date: 15/04/2026")                    )
        assert(out.contains("Next debit possible from: 02/05/2026")        )
        assert(out.contains("If the SEPA direct debit is refused or fails"))
        assert(!out.contains("SEPA mandate setup pending")                 )
        assert(out.contains("Bank Transfer")                               )
        // Methods bullet list must NOT re-list the SEPA mandate
        assert(!out.contains("- SEPA Direct Debit")                        )
    }

    test("SEPA mandate with all fields None - shows pending placeholder + fallback") {
        given I18nData_Invoices = i18nEn
        val terms               = baseTerms.copy(methods = List(PaymentMethod.SepaMandate(), bankTransfer))
        val out                 = render(terms)

        assert(out.contains("SEPA Direct Debit")                           )
        assert(out.contains("SEPA mandate setup pending")                  )
        assert(out.contains("If the SEPA direct debit is refused or fails"))
        assert(!out.contains("Mandate reference:")                         )
        assert(!out.contains("Next debit possible from:")                  )
        assert(out.contains("Bank Transfer")                               )
        assert(!out.contains("- SEPA Direct Debit")                        )
    }

    test("SEPA mandate with mandate reference only - renders other available fields") {
        given I18nData_Invoices = i18nEn
        val sepa                = PaymentMethod.SepaMandate(
            mandateReference       = Some("MD000XYZ"),
            mandateDate            = Some(LocalDate.of(2026, 4, 15)),
            nextPossibleChargeDate = None
        )
        val terms               = baseTerms.copy(methods = List(sepa))
        val out                 = render(terms)

        assert(out.contains("SEPA Direct Debit")          )
        assert(!out.contains("SEPA mandate setup pending"))
        assert(out.contains("Mandate reference: MD000XYZ"))
        assert(out.contains("Mandate date: 15/04/2026")   )
        assert(!out.contains("Next debit possible from")  )
    }

    test("SEPA mandate fully populated - French rendering uses FR locale strings") {
        given I18nData_Invoices = i18nFr
        val sepa                = PaymentMethod.SepaMandate(
            mandateReference       = Some("MD000ABC123"),
            mandateDate            = Some(LocalDate.of(2026, 4, 15)),
            iban                   = Some("FR7612345678901234567890123"),
            nextPossibleChargeDate = Some(LocalDate.of(2026, 5, 2))
        )
        val terms               = baseTerms.copy(methods = List(sepa, bankTransfer))
        val out                 = render(terms)

        assert(out.contains("Prélèvement par Mandat SEPA")                           )
        assert(out.contains("Référence du mandat : MD000ABC123")                     )
        assert(out.contains("Date du mandat : 15/04/2026")                           )
        assert(out.contains("Prochain prélèvement possible à partir du : 02/05/2026"))
        assert(out.contains("Si le prélèvement SEPA est refusé ou échoue")           )
        // English strings must not leak through
        assert(!out.contains("SEPA Direct Debit")                                    )
        assert(!out.contains("Mandate reference:")                                   )
    }
