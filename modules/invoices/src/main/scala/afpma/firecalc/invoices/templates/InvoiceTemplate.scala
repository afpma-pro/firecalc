/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.templates

import afpma.firecalc.invoices.models.InvoiceData
import io.taig.babel.Locale

/**
 * Typeclass for rendering invoice data into Typst markup.
 * 
 * This allows for flexible and extensible invoice template implementations.
 * Users can provide custom implementations for different invoice layouts,
 * styling, and business requirements.
 * 
 * @tparam T The type of data to render (typically InvoiceData or a subset)
 */
trait InvoiceTemplate[T]:
  /**
   * Renders the given data into Typst markup string.
   * 
   * @param data The invoice data to render
   * @param locale The locale for internationalization
   * @return Typst markup string ready for PDF generation
   */
  def render(data: T)(using locale: Locale): String

  /**
   * Optional method to validate the data before rendering.
   * Returns None if valid, or Some(errorMessage) if invalid.
   */
  def validate(data: T): Option[String] = None

  /**
   * Combines this template with another template using a composition strategy.
   */
  def combine[U](other: InvoiceTemplate[U])(strategy: TemplateCompositionStrategy): InvoiceTemplate[(T, U)] =
    new CombinedTemplate(this, other, strategy)

object InvoiceTemplate:
  /**
   * Summons an implicit InvoiceTemplate instance.
   */
  def apply[T](using template: InvoiceTemplate[T]): InvoiceTemplate[T] = template

  /**
   * Creates a simple template from a function.
   */
  def fromFunction[T](f: T => Locale ?=> String): InvoiceTemplate[T] =
    new InvoiceTemplate[T]:
      def render(data: T)(using locale: Locale): String = f(data)

  /**
   * Creates a template with validation from functions.
   */
  def fromFunctions[T](
    renderFn: T => Locale ?=> String,
    validateFn: T => Option[String]
  ): InvoiceTemplate[T] =
    new InvoiceTemplate[T]:
      def render(data: T)(using locale: Locale): String = renderFn(data)
      override def validate(data: T): Option[String] = validateFn(data)

/**
 * Strategies for combining multiple templates.
 */
enum TemplateCompositionStrategy:
  case Vertical    // Stack templates vertically
  case Horizontal  // Place templates side by side
  case Overlay     // Overlay templates (second on top)
  case Custom(combiner: (String, String) => String) // Custom combination logic

/**
 * Implementation of combined templates.
 */
private class CombinedTemplate[T, U](
  first: InvoiceTemplate[T],
  second: InvoiceTemplate[U],
  strategy: TemplateCompositionStrategy
) extends InvoiceTemplate[(T, U)]:

  def render(data: (T, U))(using locale: Locale): String =
    val (firstData, secondData) = data
    val firstResult = first.render(firstData)
    val secondResult = second.render(secondData)
    
    strategy match
      case TemplateCompositionStrategy.Vertical =>
        s"$firstResult\n\n$secondResult"
      case TemplateCompositionStrategy.Horizontal =>
        s"#grid(columns: 2, [$firstResult], [$secondResult])"
      case TemplateCompositionStrategy.Overlay =>
        s"#place([$firstResult])\n#place([$secondResult])"
      case TemplateCompositionStrategy.Custom(combiner) =>
        combiner(firstResult, secondResult)

  override def validate(data: (T, U)): Option[String] =
    val (firstData, secondData) = data
    first.validate(firstData).orElse(second.validate(secondData))

/**
 * Template configuration for customizing appearance and behavior.
 */
case class TemplateConfig(
  // Typography
  fontSize: String = "10pt",
  fontFamily: String = "default",
  
  // Colors
  primaryColor: String = "#000000",
  secondaryColor: String = "#666666",
  accentColor: String = "#0066cc",
  
  // Layout
  marginTop: String = "30pt",
  marginBottom: String = "30pt",
  marginLeft: String = "30pt",
  marginRight: String = "30pt",
  
  // Table styling
  tableStroke: String = "0.4pt",
  tableFill: Option[String] = None,
  
  // Logo
  logoWidth: String = "60pt",
  logoPosition: LogoPosition = LogoPosition.TopLeft,
  
  // Language
  language: String = "en",
  
  // Custom CSS-like properties
  customProperties: Map[String, String] = Map.empty
)

enum LogoPosition:
  case TopLeft
  case TopRight
  case TopCenter
  case None

/**
 * Base trait for templates that support configuration.
 */
trait ConfigurableTemplate[T] extends InvoiceTemplate[T]:
  def config: TemplateConfig
  
  def withConfig(newConfig: TemplateConfig): ConfigurableTemplate[T]
  
  /**
   * Helper method to apply template configuration to Typst markup.
   */
  protected def applyConfig(content: String): String =
    val configSetup = generateConfigSetup()
    s"$configSetup\n\n$content"
  
  private def generateConfigSetup(): String =
    s"""
    |#set text(size: ${config.fontSize}, lang: "${config.language}")
    |#set page(margin: (
    |  top: ${config.marginTop},
    |  bottom: ${config.marginBottom}, 
    |  left: ${config.marginLeft},
    |  right: ${config.marginRight}
    |))
    |#set table(stroke: ${config.tableStroke})
    |""".stripMargin
