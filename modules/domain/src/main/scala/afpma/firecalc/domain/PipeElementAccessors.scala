/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import afpma.firecalc.units.coulombutils.*

/**
 * Typed accessor for direction-change elements.
 * Extends the marker trait so consumers can match on either the accessor or the marker.
 * DTOs mix this in to expose `angle` and `absDir` without reflection.
 */
trait HasDirectionChangeData extends IsDirectionChange:
    def dcAngle : Angle
    def dcAbsDir: Option[AbsoluteDirection] = None

/**
 * Typed accessor for split/merge elements.
 * `n_flows` from `SetsNumberOfFlows` already distinguishes split (2) vs merge (1).
 */
trait HasSplitMergeData extends IsSplitMergeTurn with SetsNumberOfFlows:
    def smName               : String
    def smAbsDir             : Option[AbsoluteDirection] = None
    def smNewInnerShape      : PipeShape
    def smSymmetryPlaneAbsDir: Option[AbsoluteDirection] = None

/**
 * Typed accessor for length-bearing section elements.
 * Extracts the pipe length along the current frame direction.
 */
trait HasSectionLength extends IsLengthBearingPipeElement:
    def sectionLength: Length

/** Typed accessor for sections that also specify elevation gain explicitly. */
trait HasSectionElevation extends HasSectionLength:
    def sectionElevationGain: Length

/** Typed accessor for elements that set the inner shape. */
trait HasInnerShapeValue extends SetsInnerShape:
    def innerShapeValue: PipeShape
