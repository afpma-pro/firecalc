/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.services

import com.raquo.airstream.state.Var

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.IDBCursorWithValue
import org.scalajs.dom.IDBDatabase
import org.scalajs.dom.IDBObjectStore
import org.scalajs.dom.IDBTransactionMode
import org.scalajs.dom.IDBVersionChangeEvent

/**
 * IndexedDB-backed key-value store for catalog image data URIs.
 *
 * Image key format: `{yamlKey}:{uniqueKey}` — e.g., `door_15a_fireboxes:MY_FIREBOX_REF`.
 *
 * All public methods return `Future[Unit]` that always resolves successfully (errors are logged as warnings).
 */
object CatalogImageStore:

    private val DB_NAME    = "firecalc_catalog_images"
    private val DB_VERSION = 1
    private val STORE_NAME = "images"

    /** Reactive map: imageKey -> dataURI. For synchronous Laminar signal lookups. */
    val imagesVar: Var[Map[String, String]] = Var(Map.empty)

    /** Load all images from IndexedDB into imagesVar. Called at startup. */
    def loadAll(): Future[Unit] =
        try
            openDB()
                .flatMap { db =>
                    val promise = Promise[Unit]()
                    try
                        val tx    = db.transaction(STORE_NAME, IDBTransactionMode.readonly)
                        val store = tx.objectStore(STORE_NAME)
                        val acc   = mutable.Map.empty[String, String]

                        val cursorReq = store.openCursor()

                        cursorReq.onsuccess = { event =>
                            val cursor = event.target.result.asInstanceOf[IDBCursorWithValue[IDBObjectStore]]
                            if cursor != null then
                                acc +=         (cursor.key.toString -> cursor.value.toString)
                                cursor.continue(                                            )
                            else
                                // Cursor exhausted — all entries accumulated
                                imagesVar.set     (acc.toMap)
                                db.close          (         )
                                promise.trySuccess(()       )
                        }

                        cursorReq.onerror = { _ =>
                            dom.console.warn  ("CatalogImageStore: cursor error during loadAll")
                            db.close          (                                                )
                            promise.trySuccess(()                                              )
                        }

                        tx.onerror = { _ =>
                            dom.console.warn  ("CatalogImageStore: transaction error during loadAll")
                            db.close          (                                                     )
                            promise.trySuccess(()                                                   )
                        }
                    catch
                        case e: Throwable =>
                            dom.console.warn  (s"CatalogImageStore: loadAll transaction setup failed: ${e.getMessage}")
                            db.close          (                                                                       )
                            promise.trySuccess(()                                                                     )

                    promise.future
                }
                .recover { case e =>
                    dom.console.warn(s"CatalogImageStore: loadAll failed: ${e.getMessage}")
                }
        catch
            case e: Throwable =>
                dom.console.warn (s"CatalogImageStore: loadAll failed: ${e.getMessage}")
                Future.successful(()                                                   )

    /** Store images in IndexedDB and update imagesVar. Called on catalog import. */
    def putAll(images: Map[String, String]): Future[Unit] =
        if images.isEmpty then return Future.successful(())
        try
            openDB()
                .flatMap { db =>
                    val promise = Promise[Unit]()
                    try
                        val tx    = db.transaction(STORE_NAME, IDBTransactionMode.readwrite)
                        val store = tx.objectStore(STORE_NAME)

                        // All puts must happen synchronously within the transaction
                        images.foreach { (key, value) =>
                            store.put(value.asInstanceOf[js.Any], key.asInstanceOf[js.Any])
                        }

                        tx.oncomplete = { _ =>
                            imagesVar.update  (_ ++ images)
                            db.close          (           )
                            promise.trySuccess(()         )
                        }

                        tx.onerror = { _ =>
                            dom.console.warn  ("CatalogImageStore: transaction error during putAll")
                            db.close          (                                                    )
                            promise.trySuccess(()                                                  )
                        }
                    catch
                        case e: Throwable =>
                            dom.console.warn  (s"CatalogImageStore: putAll transaction setup failed: ${e.getMessage}")
                            db.close          (                                                                      )
                            promise.trySuccess(()                                                                    )

                    promise.future
                }
                .recover { case e =>
                    dom.console.warn(s"CatalogImageStore: putAll failed: ${e.getMessage}")
                }
        catch
            case e: Throwable =>
                dom.console.warn (s"CatalogImageStore: putAll failed: ${e.getMessage}")
                Future.successful(()                                                  )

    /** Clear all images from IndexedDB and reset imagesVar. Called on "Clear all". */
    def clear(): Future[Unit] =
        // Reset reactive state immediately (synchronous)
        imagesVar.set(Map.empty)
        try
            openDB()
                .flatMap { db =>
                    val promise = Promise[Unit]()
                    try
                        val tx    = db.transaction(STORE_NAME, IDBTransactionMode.readwrite)
                        val store = tx.objectStore(STORE_NAME)

                        val clearReq = store.clear()

                        clearReq.onerror = { _ =>
                            dom.console.warn("CatalogImageStore: clear request failed")
                        }

                        tx.oncomplete = { _ =>
                            db.close          (  )
                            promise.trySuccess(())
                        }

                        tx.onerror = { event =>
                            dom.console.warn  ("CatalogImageStore: transaction error during clear")
                            db.close          (                                                   )
                            promise.trySuccess(()                                                 )
                        }
                    catch
                        case e: Throwable =>
                            dom.console.warn  (s"CatalogImageStore: clear transaction setup failed: ${e.getMessage}")
                            db.close          (                                                                     )
                            promise.trySuccess(()                                                                   )

                    promise.future
                }
                .recover { case e =>
                    dom.console.warn(s"CatalogImageStore: clear failed: ${e.getMessage}")
                }
        catch
            case e: Throwable =>
                dom.console.warn (s"CatalogImageStore: clear failed: ${e.getMessage}")
                Future.successful(()                                                 )

    /** Open the IndexedDB database. Creates the object store on first use. */
    private def openDB(): Future[IDBDatabase] =
        val promise = Promise[IDBDatabase]()

        val factory = dom.window.indexedDB
            .getOrElse(throw new RuntimeException("IndexedDB not available"))

        val request = factory.open(DB_NAME, DB_VERSION)

        request.onupgradeneeded = { (event: IDBVersionChangeEvent) =>
            val db = event.target.result
            if !db.objectStoreNames.contains(STORE_NAME) then db.createObjectStore(STORE_NAME)
        }

        request.onsuccess = { event =>
            promise.trySuccess(event.target.result)
        }

        request.onerror = { _ =>
            promise.tryFailure(new RuntimeException(s"Failed to open IndexedDB '$DB_NAME'"))
        }

        promise.future
