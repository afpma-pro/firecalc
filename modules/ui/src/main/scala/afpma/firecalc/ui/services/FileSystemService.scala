/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import scala.scalajs.js
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import org.scalajs.dom
import org.scalajs.dom.URL
import afpma.firecalc.ui.electron.ElectronAPI
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import io.taig.babel.Locale

/**
 * Unified file system service that works in both browser and Electron environments.
 * 
 * - In Electron: Uses IPC API for native file dialogs and file system access
 * - In Browser: Uses standard web APIs (FileReader, Blob download, file input)
 */
object FileSystemService:

  /**
   * Check if running in Electron environment
   */
  def isElectron: Boolean = ElectronAPI.isAvailable

  /**
   * Save file with content.
   * 
   * - In Electron: Opens native save dialog, then writes file
   * - In Browser: Triggers browser download
   * 
   * @param defaultFileName Suggested filename
   * @param content File content to save
   * @return Future with Either error message or success
   */
  def saveFile(defaultFileName: String, content: String)(using Locale): Future[Either[String, Unit]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    
    if isElectron then
      saveFileElectron(defaultFileName, content)
    else
      Future.successful(saveFileBrowser(defaultFileName, content))

  /**
   * Open file and read its content.
   * 
   * - In Electron: Opens native file dialog, then reads file
   * - In Browser: Triggers file input (caller must provide input element)
   * 
   * Note: For browser, this returns immediately with Right(None).
   * The caller must use `readFileFromInput` with a file input element.
   * 
   * @return Future with Either error message or optional file content and name
   */
  def openFile()(using Locale): Future[Either[String, Option[(String, String)]]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    
    if isElectron then
      openFileElectron()
    else
      // In browser, file opening requires user interaction with file input
      Future.successful(Right(None))

  /**
   * Read file content from browser file input element.
   * This is the browser-specific way to read files.
   * 
   * @param file The File object from input element
   * @return Future with Either error message or tuple of (content, filename)
   */
  def readFileFromInput(file: dom.File)(using Locale): Future[Either[String, (String, String)]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    
    val promise = scala.concurrent.Promise[Either[String, (String, String)]]()
    
    val reader = new dom.FileReader()
    
    reader.onload = { _ =>
      try
        val content = reader.result.asInstanceOf[String]
        promise.success(Right((content, file.name)))
      catch
        case ex: Exception =>
          promise.success(Left(I18N_UI.errors.failed_to_read_file.apply(ex.getMessage)))
    }
    
    reader.onerror = { _ =>
      promise.success(Left(I18N_UI.errors.failed_to_read_file.apply("")))
    }
    
    reader.readAsText(file)
    promise.future

  // ===== Private Electron implementations =====

  private def saveFileElectron(defaultFileName: String, content: String)(using locale: Locale): Future[Either[String, Unit]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    
    for
      dialogResult <- ElectronAPI.saveFileDialog(defaultFileName)(using locale)
      writeResult <- dialogResult match
        case Left(error) => Future.successful(Left(error))
        case Right(None) => Future.successful(Right(())) // User cancelled
        case Right(Some(filePath)) =>
          ElectronAPI.writeFile(filePath, content)(using locale)
    yield writeResult

  private def openFileElectron()(using locale: Locale): Future[Either[String, Option[(String, String)]]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    
    for
      dialogResult <- ElectronAPI.openFileDialog()(using locale)
      readResult <- dialogResult match
        case Left(error) => Future.successful(Left(error))
        case Right(None) => Future.successful(Right(None)) // User cancelled
        case Right(Some(filePath)) =>
          ElectronAPI.readFile(filePath)(using locale).map {
            case Left(error) => Left(error)
            case Right(content) =>
              // Extract filename from path
              val fileName = filePath.split("/").last
              Right(Some((content, fileName)))
          }
    yield readResult

  // ===== Private Browser implementations =====

  private def saveFileBrowser(fileName: String, content: String)(using Locale): Either[String, Unit] =
    try
      // Create Blob with content
      val blob = new dom.Blob(
        js.Array(content),
        new dom.BlobPropertyBag {
          `type` = "text/yaml;charset=utf-8"
        }
      )
      
      // Create download link
      val url = URL.createObjectURL(blob)
      val link = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
      link.href = url
      link.download = fileName
      link.style.display = "none"
      
      // Trigger download
      dom.document.body.appendChild(link)
      link.click()
      dom.document.body.removeChild(link)
      
      // Clean up
      dom.window.setTimeout(() => URL.revokeObjectURL(url), 100)
      
      Right(())
    catch
      case ex: Exception =>
        Left(I18N_UI.errors.failed_to_save_file.apply(ex.getMessage))