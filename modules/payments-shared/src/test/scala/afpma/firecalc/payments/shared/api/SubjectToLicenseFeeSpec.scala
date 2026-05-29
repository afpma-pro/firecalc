/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.api

import afpma.firecalc.payments.shared.api.v1.SubjectToLicenseFee
import afpma.firecalc.payments.shared.api.v1.Sku

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SubjectToLicenseFeeSpec extends AnyFlatSpec with Matchers:

    "Sku.isLicenseFeeProduct" should "return true for production license-fee product" in {
        Sku.isLicenseFeeProduct(
            v1.ProductionProductCatalog.PDF_REPORT_EN_15544_2023_WITH_FIREBOX_LICENSE_FEE.id
        ) shouldBe true
    }

    it should "return true for development license-fee product" in {
        Sku.isLicenseFeeProduct(
            v1.DevelopmentProductCatalog.PDF_REPORT_EN_15544_2023_WITH_FIREBOX_LICENSE_FEE.id
        ) shouldBe true
    }

    it should "return true for staging license-fee product" in {
        Sku.isLicenseFeeProduct(
            v1.StagingProductCatalog.PDF_REPORT_EN_15544_2023_WITH_FIREBOX_LICENSE_FEE.id
        ) shouldBe true
    }

    it should "return false for base product" in {
        Sku.isLicenseFeeProduct(v1.ProductionProductCatalog.PDF_REPORT_EN_15544_2023.id) shouldBe false
    }

    "Sku.licenseFeeSuffix" should "match production catalog license-fee SKU" in {
        v1.ProductionProductCatalog.PDF_REPORT_EN_15544_2023_WITH_FIREBOX_LICENSE_FEE.sku.value should
            endWith(Sku.licenseFeeSuffix)
    }

    it should "not match non-license-fee SKU" in {
        v1.ProductionProductCatalog.PDF_REPORT_EN_15544_2023.sku.value should
            not(endWith(Sku.licenseFeeSuffix))
    }

    it should "match development catalog license-fee SKU" in {
        v1.DevelopmentProductCatalog.PDF_REPORT_EN_15544_2023_WITH_FIREBOX_LICENSE_FEE.sku.value should
            endWith(Sku.licenseFeeSuffix)
    }

    it should "match staging catalog license-fee SKU" in {
        v1.StagingProductCatalog.PDF_REPORT_EN_15544_2023_WITH_FIREBOX_LICENSE_FEE.sku.value should
            endWith(Sku.licenseFeeSuffix)
    }

    "SubjectToLicenseFee instances" should "return correct requiresLicenseFee values" in {
        import afpma.firecalc.dto.all.Firebox
        val unused = null.asInstanceOf[Firebox]

        SubjectToLicenseFee[Firebox.Ecolabeled].requiresLicenseFee(
            unused.asInstanceOf[Firebox.Ecolabeled]
        ) shouldBe true
        SubjectToLicenseFee[Firebox.Door15aFirebox_Catalog].requiresLicenseFee(
            unused.asInstanceOf[Firebox.Door15aFirebox_Catalog]
        ) shouldBe true
        SubjectToLicenseFee[Firebox.Traditional].requiresLicenseFee(
            unused.asInstanceOf[Firebox.Traditional]
        ) shouldBe false
        SubjectToLicenseFee[Firebox.AFPMA_PRSE].requiresLicenseFee(
            unused.asInstanceOf[Firebox.AFPMA_PRSE]
        ) shouldBe false
        SubjectToLicenseFee[Firebox.SingleTested].requiresLicenseFee(
            unused.asInstanceOf[Firebox.SingleTested]
        ) shouldBe false
    }
