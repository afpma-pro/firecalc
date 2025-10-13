/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.ui.config.UIConfig
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import com.raquo.laminar.api.L.*
import com.raquo.airstream.core.{EventStream, Signal}
import com.raquo.airstream.web.FetchStream
import com.raquo.airstream.flatten.FlattenStrategy.allowFlatMap
import io.taig.babel.Locale
import org.scalajs.dom
import scala.concurrent.duration.*
import scala.scalajs.js
import scala.util.{Try, Success, Failure}

/**
 * Helper for checking connectivity to the internet and the payments backend API.
 *
 * This object provides a two-tier connectivity check:
 * 1. Internet connectivity via Cloudflare (1.1.1.1)
 * 2. Backend API connectivity via healthcheck endpoint
 */
object PaymentsBackendApiConnectivity:

  /** Cloudflare DNS endpoint for internet connectivity check */
  val INTERNET_CHECK_URL = "https://1.1.1.1/cdn-cgi/trace"
  
  /** Base URL for the healthcheck endpoint (loaded from environment configuration) */
  def HEALTHCHECK_URL: String = UIConfig.Endpoints.healthcheck

  /** Polling interval in milliseconds */
  val POLLING_INTERVAL_MS = 2000

  /**
   * Detects if the app is running in Electron environment.
   * Electron adds a global 'electronAPI' object via preload script.
   */
  def isElectronEnvironment: Boolean =
    try {
      scalajs.js.Dynamic.global.electronAPI.asInstanceOf[js.UndefOr[js.Object]].isDefined
    } catch {
      case _: Throwable => false
    }

  /**
   * Error types that can occur during connectivity checks
   */
  sealed trait ConnectivityError
  
  /** Network-level errors (connection failed, timeout, non-200 response, etc.) */
  case class NetworkError(message: String) extends ConnectivityError
  
  /** JSON validation errors (malformed or unexpected JSON structure) */
  case class JsonValidationError(message: String) extends ConnectivityError

  /**
   * Internet connectivity status
   */
  sealed trait InternetStatus
  case object InternetConnected extends InternetStatus
  type InternetConnected = InternetConnected.type
  case object InternetDisconnected extends InternetStatus
  type InternetDisconnected = InternetDisconnected.type
  case object InternetChecking extends InternetStatus
  type InternetChecking = InternetChecking.type

  /**
   * Backend connectivity status
   */
  sealed trait BackendStatus
  case object BackendConnected extends BackendStatus
  type BackendConnected = BackendConnected.type
  case class BackendDisconnected(error: ConnectivityError) extends BackendStatus
  case object BackendChecking extends BackendStatus
  type BackendChecking = BackendChecking.type
  case object BackendNotChecked extends BackendStatus
  type BackendNotChecked = BackendNotChecked.type

  /**
   * Combined connectivity result tracking both internet and backend
   */
  sealed trait ConnectivityResult
  case object CheckDisabled extends ConnectivityResult
  type CheckDisabled = CheckDisabled.type
  case class InternetCheckInProgress(internetStatus: InternetChecking) extends ConnectivityResult
  case class InternetOnly(internetStatus: InternetDisconnected) extends ConnectivityResult
  case class BackendCheckInProgress(internetStatus: InternetConnected, backendStatus: BackendChecking) extends ConnectivityResult
  case class PartialConnection(internetStatus: InternetConnected, backendStatus: BackendDisconnected) extends ConnectivityResult
  case class FullConnection(internetStatus: InternetConnected, backendStatus: BackendConnected) extends ConnectivityResult

  /**
   * Gets the browser's basic connectivity status using navigator.onLine.
   * This is a synchronous check that returns immediately.
   *
   * @return true if navigator reports being online, false otherwise
   */
  def isNavigatorOnline: Boolean =
    dom.window.navigator.onLine

  /**
   * Verifies actual internet access using Cloudflare's trace endpoint.
   * This performs a real network request to confirm connectivity.
   *
   * Note: This may fail if blocked by browser extensions (ad blockers, privacy tools).
   * The system gracefully falls back to backend-only check in such cases.
   *
   * @return EventStream emitting InternetConnected if successful, InternetDisconnected otherwise
   */
  def verifyInternetAccess()(using Locale): EventStream[InternetStatus] =
    FetchStream.get(INTERNET_CHECK_URL)
      .map(_ => InternetConnected: InternetStatus)
      .recoverToTry
      .map {
        case Success(status) => status
        case Failure(error) =>
          // Silently handle blocking by browser extensions - this is expected
          // The system will fall back to backend healthcheck for connectivity detection
          InternetDisconnected: InternetStatus
      }

  /**
   * Checks internet connectivity using a two-step process:
   * 1. Quick check using navigator.onLine (instant, no network request)
   * 2. If online, verify with Cloudflare fetch (actual network validation)
   *
   * Note: If Cloudflare is blocked by browser extensions, the system gracefully
   * falls back to using only the backend healthcheck for connectivity detection.
   *
   * @return EventStream emitting InternetConnected if both checks pass, InternetDisconnected otherwise
   */
  def checkInternetConnectivity()(using Locale): EventStream[InternetStatus] =
    if !isNavigatorOnline then
      // Quick path: navigator reports offline
      EventStream.fromValue(InternetDisconnected, emitOnce = true)
    else
      // Navigator reports online, attempt to verify with Cloudflare
      // (May be blocked by browser extensions - handled gracefully)
      verifyInternetAccess()

  /**
   * Performs a single health check request to the specified URL.
   *
   * Returns an EventStream that emits:
   * - Right(responseText) if the request succeeds with HTTP 200
   * - Left(NetworkError) if the request fails or returns non-200 status
   *
   * @param url The URL to check (defaults to the configured healthcheck endpoint)
   * @return EventStream emitting either an error or the response text
   */
  def checkHttpStatus200(url: String = UIConfig.Endpoints.healthcheck)(using Locale): EventStream[Either[NetworkError, String]] =
    FetchStream
      .get(url)
      .map(responseText => Right(responseText): Either[NetworkError, String])
      .recoverToTry
      .map {
        case Success(Right(text)) => Right(text)
        case Success(Left(err)) => Left(err)
        case Failure(error) => Left(NetworkError(I18N_UI.connectivity_status.network_request_failed.apply(error.getMessage)))
      }

  /**
   * Validates that the JSON response from the healthcheck endpoint
   * has the expected structure and contains valid data.
   * 
   * @param jsonString The raw JSON response as a string
   * @return Either[String, Boolean] - Left with error message if invalid, Right(true) if valid
   * 
   * TODO: Implement validation logic
   * Expected structure:
   * {
   *   "info": {
   *     "engine_version": "...",
   *     "payments_base_version": "...",
   *     "payments_version": "...",
   *     "ui_latest_version": "...",
   *     "latest_firecalc_yaml_file_version": "..."
   *   }
   * }
   */
  def validateHealthCheckJson(jsonString: String): Either[String, Boolean] =
    Right(true)

  /**
   * Performs a complete health check: HTTP request + JSON validation.
   *
   * @param url The URL to check (defaults to the configured healthcheck endpoint)
   * @return EventStream emitting the backend status
   */
  def performBackendHealthCheck(url: String = UIConfig.Endpoints.healthcheck)(using Locale): EventStream[BackendStatus] =
    checkHttpStatus200(url).map {
      case Left(networkError) =>
        BackendDisconnected(networkError)
      
      case Right(responseText) =>
        validateHealthCheckJson(responseText) match {
          case Left(validationError) =>
            BackendDisconnected(JsonValidationError(validationError))
          
          case Right(true) =>
            BackendConnected
          
          case Right(false) =>
            BackendDisconnected(JsonValidationError(I18N_UI.connectivity_status.validation_returned_false))
        }
    }

  /**
   * Performs a complete connectivity check: internet + backend.
   *
   * **Electron environment**: Checks both internet (navigator.onLine + Cloudflare) and backend
   * **Web browser**: Only checks backend to avoid browser extension blocking
   *
   * @param url The URL to check for backend (defaults to the configured healthcheck endpoint)
   * @return EventStream emitting the combined connectivity result
   */
  def performFullConnectivityCheck(url: String = UIConfig.Endpoints.healthcheck)(using Locale): EventStream[ConnectivityResult] =
    if isElectronEnvironment then
      // Electron: Full two-tier check (internet + backend)
      checkInternetConnectivity().flatMap {
        case InternetDisconnected =>
          EventStream.fromValue(InternetOnly(InternetDisconnected), emitOnce = true)
        
        case InternetConnected =>
          performBackendHealthCheck(url).map {
            case BackendConnected =>
              FullConnection(InternetConnected, BackendConnected)
            
            case BackendDisconnected(error) =>
              PartialConnection(InternetConnected, BackendDisconnected(error))
            
            case _ =>
              BackendCheckInProgress(InternetConnected, BackendChecking)
          }
        
        case InternetChecking =>
          EventStream.fromValue(InternetCheckInProgress(InternetChecking), emitOnce = true)
      }
    else
      // Web browser: Backend-only check (infer internet from backend response)
      // Avoids browser extension blocking of external connectivity checks
      performBackendHealthCheck(url).map {
        case BackendConnected =>
          // Backend reachable means internet is working
          FullConnection(InternetConnected, BackendConnected)
        
        case BackendDisconnected(NetworkError(msg)) =>
          // Network error - likely no internet
          InternetOnly(InternetDisconnected)
        
        case BackendDisconnected(JsonValidationError(msg)) =>
          // JSON error means we reached backend but response was invalid
          PartialConnection(InternetConnected, BackendDisconnected(JsonValidationError(msg)))
        
        case _ =>
          BackendCheckInProgress(InternetConnected, BackendChecking)
      }

  /**
   * Creates a polling EventStream that performs health checks at regular intervals.
   * 
   * The polling starts when the trigger signal emits true, and stops when it emits false.
   * While polling, it performs a health check every POLLING_INTERVAL_MS milliseconds.
   * 
   * If a network error occurs, the polling continues (retrying).
   * If a JSON validation error occurs, the polling stops and emits Disconnected.
   * 
   * @param pollingTrigger Signal that controls when to start (true) and stop (false) polling
   * @param url The URL to check (defaults to the configured healthcheck endpoint)
   * @return EventStream of connectivity results
   */
  def createPollingStream(
    pollingTrigger: Signal[Boolean],
    url: String = UIConfig.Endpoints.healthcheck
  )(using locale: Locale): EventStream[ConnectivityResult] =
    pollingTrigger.changes.flatMapSwitch { (shouldPoll: Boolean) =>
      if shouldPoll then
        // Create a var to track if we should stop polling
        var shouldContinuePolling = true

        scalajs.js.Dynamic.global.console.log("creating new polling stream")
        
        // Create the polling stream
        val pollingStream = EventStream
          .periodic(POLLING_INTERVAL_MS)
          .map(_ => ())
          .flatMapMerge { _ =>
            if shouldContinuePolling then
              performFullConnectivityCheck(url)(using locale).map { result =>
                // Stop polling on JSON validation errors
                result match {
                  case PartialConnection(_, BackendDisconnected(JsonValidationError(_))) =>
                    shouldContinuePolling = false
                  case _ => ()
                }
                result
              }
            else
              EventStream.empty
          }
        
        // Start with immediate check
        EventStream.merge(
          performFullConnectivityCheck(url)(using locale),
          pollingStream
        )
      else
        // When polling is disabled, emit a disconnected state
        EventStream.fromValue(
          CheckDisabled,
          emitOnce = true
        )
    }

  /**
   * Creates the main connectivity signal that can be used throughout the application.
   * 
   * This signal:
   * - Emits true when connected to a compatible API
   * - Emits false when disconnected or when there are errors
   * - Is controlled by a polling trigger signal
   * 
   * @param pollingTrigger Signal that controls when to start/stop polling
   * @param url The URL to check (defaults to the configured healthcheck endpoint)
   * @return Signal[Boolean] indicating connectivity status
   */
  def createConnectivitySignal(
    pollingTrigger: Signal[Boolean],
    url: String = UIConfig.Endpoints.healthcheck
  )(using Locale): Signal[Boolean] =
    createPollingStream(pollingTrigger, url)
      .map {
        case FullConnection(_, _) => true
        case _ => false
      }
      .startWith(false) // Start with disconnected state

  /**
   * Convenience method to create a connectivity signal with error details.
   * 
   * @param pollingTrigger Signal that controls when to start/stop polling
   * @param url The URL to check (defaults to the configured healthcheck endpoint)
   * @return Signal containing the full connectivity result
   */
  def createDetailedConnectivitySignal(
    pollingTrigger: Signal[Boolean],
    url: String = UIConfig.Endpoints.healthcheck
  )(using locale: Locale): Signal[ConnectivityResult] =
    createPollingStream(pollingTrigger, url)(using locale)
      .startWith(CheckDisabled)

end PaymentsBackendApiConnectivity
