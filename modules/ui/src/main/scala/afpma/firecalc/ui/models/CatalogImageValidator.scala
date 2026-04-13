/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

object CatalogImageValidator:

    val MAX_DECODED_BYTES_PER_IMAGE: Long = 200L * 1024       // 200 KB
    val MAX_TOTAL_DECODED_BYTES    : Long = 50L * 1024 * 1024 // 50 MB

    private val Base64Marker = ";base64,"

    /** Validate a single image URI: must be data:image/SUBTYPE;base64,... and <= 200 KB decoded. */
    def validate(uri: String): Either[String, String] =
        if !uri.startsWith("data:image/") then Left("Image URI must start with data:image/")
        else
            val markerIndex = uri.indexOf(Base64Marker)
            if markerIndex < 0 then Left("Image URI must use ;base64 encoding")
            else
                val b64Start  = markerIndex + Base64Marker.length
                val b64Length = uri.length - b64Start
                if b64Length <= 0 then Left("Image URI has empty base64 payload")
                else
                    val paddingChars   = countPadding(uri)
                    val estimatedBytes = b64Length.toLong * 3L / 4L - paddingChars
                    if estimatedBytes > MAX_DECODED_BYTES_PER_IMAGE then
                        Left  (
                            f"Image exceeds per-image limit: ~${estimatedBytes / 1024}%d KB > ${MAX_DECODED_BYTES_PER_IMAGE / 1024}%d KB"
                        )
                    else Right(uri)

    /**
     * Validate a batch of (imageKey, uri) pairs with per-image and aggregate (50 MB) limits.
     * Returns (validImages: Map[key -> uri], warnings: List[String]). Skips images that fail validation or exceed
     * aggregate limit.
     */
    def validateBatch(images: Seq[(String, String)]): (Map[String, String], List[String]) =
        val validBuilder    = Map.newBuilder[String, String]
        val warningsBuilder = List.newBuilder[String]
        var totalBytes      = 0L

        images.foreach { (key, uri) =>
            validate(uri) match
                case Left(reason)    =>
                    warningsBuilder += s"[$key] Skipped: $reason"
                case Right(validUri) =>
                    val estBytes = estimateDecodedSize(validUri)
                    if totalBytes + estBytes > MAX_TOTAL_DECODED_BYTES then
                        warningsBuilder += s"[$key] Skipped: aggregate size limit exceeded" +
                            f" (~${(totalBytes + estBytes) / 1024 / 1024}%d MB > ${MAX_TOTAL_DECODED_BYTES / 1024 / 1024}%d MB)"
                    else
                        totalBytes += estBytes
                        validBuilder += key -> validUri
        }

        (validBuilder.result(), warningsBuilder.result())

    private def estimateDecodedSize(uri: String): Long =
        val b64Start  = uri.indexOf(Base64Marker) + Base64Marker.length
        val b64Length = uri.length - b64Start
        val padding   = countPadding(uri)
        b64Length.toLong * 3L / 4L - padding

    private def countPadding(uri: String): Long =
        var count = 0L
        if uri.nonEmpty && uri.charAt(uri.length - 1) == '=' then
            count += 1
            if uri.length > 1 && uri.charAt(uri.length - 2) == '=' then count += 1
        count
