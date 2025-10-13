/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.electron

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.concurrent.Future
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import io.taig.babel.Locale

/**
 * Scala.js facade for Electron IPC API exposed via preload.js
 * 
 * This API is only available when running in Electron desktop app.
 * Check `ElectronAPI.isAvailable` before using.
 */
@js.native
trait ElectronAPIResult extends js.Object:
  val success: Boolean = js.native
  val canceled: js.UndefOr[Boolean] = js.native
  val error: js.UndefOr[String] = js.native

@js.native
trait FileReadResult extends ElectronAPIResult:
  val content: js.UndefOr[String] = js.native

@js.native
trait FileDialogResult extends ElectronAPIResult:
  val path: js.UndefOr[String] = js.native

@js.native
@JSGlobal("window.electronAPI")
object ElectronAPIImpl extends js.Object:
  // File operations
  def readFile(filePath: String): js.Promise[FileReadResult] = js.native
  def writeFile(filePath: String, content: String): js.Promise[ElectronAPIResult] = js.native
  
  // Directory operations
  def listDir(dirPath: String): js.Promise[js.Object] = js.native
  def createDir(dirPath: String): js.Promise[ElectronAPIResult] = js.native
  
  // File dialogs
  def openFileDialog(): js.Promise[FileDialogResult] = js.native
  def saveFileDialog(defaultFileName: String): js.Promise[FileDialogResult] = js.native
  
  // App information
  def getVersion(): js.Promise[String] = js.native
  def checkForUpdates(): js.Promise[ElectronAPIResult] = js.native
  
  // Event listeners
  def onFileOpened(callback: js.Function1[String, Unit]): js.Function0[Unit] = js.native
  def onFileSaveAs(callback: js.Function1[String, Unit]): js.Function0[Unit] = js.native

/**
 * Safe wrapper for ElectronAPI with availability checking
 */
object ElectronAPI:
  
  /**
   * Check if Electron API is available (i.e., running in Electron app)
   */
  def isAvailable: Boolean =
    try
      js.typeOf(js.Dynamic.global.window.electronAPI) != "undefined"
    catch
      case _: Throwable => false
  
  /**
   * Get the Electron API if available
   */
  def get: Option[ElectronAPIImpl.type] =
    if isAvailable then Some(ElectronAPIImpl)
    else None
  
  /**
   * Read file using Electron API
   */
  def readFile(filePath: String)(using Locale): Future[Either[String, String]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.scalajs.js.Thenable.Implicits._
    
    ElectronAPIImpl.readFile(filePath).toFuture.map { result =>
      if result.success then
        result.content.toOption match
          case Some(content) => Right(content)
          case None => Left(I18N_UI.errors.no_content_returned)
      else
        Left(result.error.getOrElse(I18N_UI.errors.unknown_error))
    }.recover {
      case ex: Throwable => Left(I18N_UI.errors.failed_to_read_file.apply(ex.getMessage))
    }
  
  /**
   * Write file using Electron API
   */
  def writeFile(filePath: String, content: String)(using Locale): Future[Either[String, Unit]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.scalajs.js.Thenable.Implicits._
    
    ElectronAPIImpl.writeFile(filePath, content).toFuture.map { result =>
      if result.success then Right(())
      else Left(result.error.getOrElse(I18N_UI.errors.unknown_error))
    }.recover {
      case ex: Throwable => Left(I18N_UI.errors.failed_to_write_file.apply(ex.getMessage))
    }
  
  /**
   * Open file dialog using Electron API
   */
  def openFileDialog()(using Locale): Future[Either[String, Option[String]]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.scalajs.js.Thenable.Implicits._
    
    ElectronAPIImpl.openFileDialog().toFuture.map { result =>
      if result.canceled.getOrElse(false) then
        Right(None)
      else if result.success then
        result.path.toOption match
          case Some(path) => Right(Some(path))
          case None => Left(I18N_UI.errors.no_path_returned)
      else
        Left(result.error.getOrElse(I18N_UI.errors.unknown_error))
    }.recover {
      case ex: Throwable => Left(I18N_UI.errors.failed_to_open_dialog.apply(ex.getMessage))
    }
  
  /**
   * Save file dialog using Electron API
   */
  def saveFileDialog(defaultFileName: String)(using Locale): Future[Either[String, Option[String]]] =
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.scalajs.js.Thenable.Implicits._
    
    ElectronAPIImpl.saveFileDialog(defaultFileName).toFuture.map { result =>
      if result.canceled.getOrElse(false) then
        Right(None)
      else if result.success then
        result.path.toOption match
          case Some(path) => Right(Some(path))
          case None => Left(I18N_UI.errors.no_path_returned)
      else
        Left(result.error.getOrElse(I18N_UI.errors.unknown_error))
    }.recover {
      case ex: Throwable => Left(I18N_UI.errors.failed_to_save_dialog.apply(ex.getMessage))
    }