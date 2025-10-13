/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import utest.*
import afpma.firecalc.payments.TestDatabaseSetup
import afpma.firecalc.payments.domain.{Currency, Product}
import afpma.firecalc.payments.shared.api.v1.*
import afpma.firecalc.payments.shared.api.ProductCatalogSelector
import afpma.firecalc.payments.shared.i18n.I18nData_PaymentsShared
import afpma.firecalc.payments.shared.i18n.implicits.{given, *}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.taig.babel.Locale
import io.taig.babel.Locales
import molecule.db.common.spi.Conn
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

object ProductCatalogIntegrationTest extends TestSuite with TestDatabaseSetup {

  override implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val tests = Tests {

    test("ProductRepository.upsert - create new product from catalog") {
      // Given a test product from the development catalog
      val testProduct = DevelopmentProductCatalog.PDF_REPORT_EN_15544_2023
      
      val result = (for {
        conn <- setupTestDatabase
        given Conn = conn
        productRepo <- ProductRepository.create[IO]
        
        // When upserting the product
        product <- productRepo.upsert(testProduct)
        
        // Then verify it was created correctly
        retrieved <- productRepo.findById(testProduct.id)
      } yield {
        // Product should be created with correct attributes
        assert(product.id == testProduct.id)
        assert(product.name == testProduct.nameKey)
        assert(product.description == testProduct.descriptionKey)
        assert(product.price == testProduct.price)
        assert(product.currency == Currency.EUR)
        assert(product.active == testProduct.active)
        
        // Product should be retrievable
        assert(retrieved.isDefined)
        assert(retrieved.get.id == testProduct.id)
      }).unsafeRunSync()
    }

    test("ProductRepository.upsert - update existing product from catalog") {
      // Given an existing product
      val productId = ProductId(UUID.fromString("00000000-0000-0000-0000-000000000999"))
      val originalProduct = ProductInfo(
        id = productId,
        nameKey = "products.original.name",
        descriptionKey = "products.original.description",
        price = BigDecimal(50.00),
        currency = "EUR",
        active = true
      )
      
      val updatedProduct = ProductInfo(
        id = productId,
        nameKey = "products.updated.name",
        descriptionKey = "products.updated.description",
        price = BigDecimal(75.00),
        currency = "EUR",
        active = false
      )
      
      val result = (for {
        conn <- setupTestDatabase
        given Conn = conn
        productRepo <- ProductRepository.create[IO]
        
        // Create original product
        _ <- productRepo.upsert(originalProduct)
        
        // When upserting with same ID but different values
        updated <- productRepo.upsert(updatedProduct)
        
        // Then verify it was updated
        retrieved <- productRepo.findById(productId)
      } yield {
        assert(updated.name == "products.updated.name")
        assert(updated.description == "products.updated.description")
        assert(updated.price == BigDecimal(75.00))
        assert(updated.active == false)
        
        assert(retrieved.isDefined)
        assert(retrieved.get.name == "products.updated.name")
        assert(retrieved.get.price == BigDecimal(75.00))
      }).unsafeRunSync()
    }

    test("ProductCatalogSelector - return development catalog") {
      // When requesting development catalog
      val catalog = ProductCatalogSelector.getCatalog("development")
      
      // Then it should be the development catalog
      assert(catalog == DevelopmentCatalog)
      assert(catalog.allProducts.nonEmpty)
      assert(catalog.allProducts.contains(DevelopmentProductCatalog.PDF_REPORT_EN_15544_2023))
    }

    test("ProductCatalogSelector - return staging catalog") {
      // When requesting staging catalog
      val catalog = ProductCatalogSelector.getCatalog("staging")
      
      // Then it should be the staging catalog
      assert(catalog == StagingCatalog)
      assert(catalog.allProducts.nonEmpty)
      assert(catalog.allProducts.contains(StagingProductCatalog.PDF_REPORT_EN_15544_2023))
    }

    test("ProductCatalogSelector - return production catalog") {
      // When requesting production catalog
      val catalog = ProductCatalogSelector.getCatalog("production")
      
      // Then it should be the production catalog
      assert(catalog == ProductionCatalog)
      assert(catalog.allProducts.nonEmpty)
      assert(catalog.allProducts.contains(ProductionProductCatalog.PDF_REPORT_EN_15544_2023))
    }

    test("ProductCatalogSelector - throw exception for invalid catalog type") {
      // When requesting an invalid catalog type
      // Then it should throw IllegalArgumentException
      val thrown = try {
        ProductCatalogSelector.getCatalog("invalid")
        false
      } catch {
        case _: IllegalArgumentException => true
        case _: Throwable => false
      }
      assert(thrown)
    }

    test("Product catalogs - have valid i18n keys for all products") {
      // Given all product catalogs
      val allCatalogs = List(
        ("development", DevelopmentCatalog),
        ("staging", StagingCatalog),
        ("production", ProductionCatalog)
      )
      
      // Load i18n data for English and French using given instances
      val enData = I18N_PaymentsShared(using Locales.en)
      val frData = I18N_PaymentsShared(using Locales.fr)
      
      allCatalogs.foreach { case (catalogName, catalog) =>
        catalog.allProducts.foreach { product =>
          // Extract the key path from the nameKey (e.g., "products.test.name" -> "test")
          val productKey = product.nameKey.split("\\.").dropRight(1).last
          
          // Verify English and French translations exist
          productKey match {
            case "test" =>
              assert(enData.products.test.name.nonEmpty)
              assert(enData.products.test.description.nonEmpty)
              assert(frData.products.test.name.nonEmpty)
              assert(frData.products.test.description.nonEmpty)
            case "pdf_report_EN_15544_2023" =>
              assert(enData.products.pdf_report_EN_15544_2023.name.nonEmpty)
              assert(enData.products.pdf_report_EN_15544_2023.description.nonEmpty)
              assert(frData.products.pdf_report_EN_15544_2023.name.nonEmpty)
              assert(frData.products.pdf_report_EN_15544_2023.description.nonEmpty)
            case other =>
              assert(false) // Unknown product key
          }
        }
      }
    }

    test("Product catalogs - consistent product IDs between staging and production") {
      // Given staging and production catalogs
      val stagingProduct = StagingProductCatalog.PDF_REPORT_EN_15544_2023
      val productionProduct = ProductionProductCatalog.PDF_REPORT_EN_15544_2023
      
      // Then staging and production should use the same product ID
      assert(stagingProduct.id == productionProduct.id)
      
      // But staging should have a lower price for testing
      assert(stagingProduct.price < productionProduct.price)
    }

    test("Product catalogs - all products with valid prices and currencies") {
      // Given all catalogs
      val allCatalogs = List(DevelopmentCatalog, StagingCatalog, ProductionCatalog)
      
      allCatalogs.foreach { catalog =>
        catalog.allProducts.foreach { product =>
          // Then all products should have positive prices
          assert(product.price > BigDecimal(0))
          
          // And valid currencies
          assert(Currency.fromString(product.currency).isDefined)
          
          // And be active by default
          assert(product.active == true)
        }
      }
    }

    test("Product catalogs - unique product IDs within each catalog") {
      // Given all catalogs
      val allCatalogs = List(
        ("development", DevelopmentCatalog),
        ("staging", StagingCatalog),
        ("production", ProductionCatalog)
      )
      
      allCatalogs.foreach { case (catalogName, catalog) =>
        // Then all product IDs should be unique within the catalog
        val ids = catalog.allProducts.map(_.id)
        assert(ids.distinct.size == ids.size)
      }
    }

    test("Product catalog integration - sync all development products to database") {
      val result = (for {
        conn <- setupTestDatabase
        given Conn = conn
        productRepo <- ProductRepository.create[IO]
        
        // When syncing all development products
        products <- IO.traverse(DevelopmentCatalog.allProducts)(productRepo.upsert)
        
        // Then all products should be in database
        retrieved <- IO.traverse(products.map(_.id))(productRepo.findById)
      } yield {
        assert(products.size == DevelopmentCatalog.allProducts.size)
        assert(retrieved.flatten.size == DevelopmentCatalog.allProducts.size)
        
        // Verify each product was stored correctly
        products.zip(DevelopmentCatalog.allProducts).foreach { case (stored, original) =>
          assert(stored.id == original.id)
          assert(stored.price == original.price)
          assert(stored.currency.toString == original.currency)
        }
      }).unsafeRunSync()
    }

    test("Product catalog integration - sync all staging products to database") {
      val result = (for {
        conn <- setupTestDatabase
        given Conn = conn
        productRepo <- ProductRepository.create[IO]
        
        // When syncing all staging products
        products <- IO.traverse(StagingCatalog.allProducts)(productRepo.upsert)
        
        // Then all products should be in database
        retrieved <- IO.traverse(products.map(_.id))(productRepo.findById)
      } yield {
        assert(products.size == StagingCatalog.allProducts.size)
        assert(retrieved.flatten.size == StagingCatalog.allProducts.size)
      }).unsafeRunSync()
    }

    test("Product catalog integration - sync all production products to database") {
      val result = (for {
        conn <- setupTestDatabase
        given Conn = conn
        productRepo <- ProductRepository.create[IO]
        
        // When syncing all production products
        products <- IO.traverse(ProductionCatalog.allProducts)(productRepo.upsert)
        
        // Then all products should be in database
        retrieved <- IO.traverse(products.map(_.id))(productRepo.findById)
      } yield {
        assert(products.size == ProductionCatalog.allProducts.size)
        assert(retrieved.flatten.size == ProductionCatalog.allProducts.size)
      }).unsafeRunSync()
    }
  }
}