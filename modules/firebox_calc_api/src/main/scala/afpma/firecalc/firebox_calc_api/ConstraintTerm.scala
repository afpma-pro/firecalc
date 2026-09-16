/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/**
 * Identifier for a specific constraint term. `wire` is the normative OpenAPI
 * enum value (`ConstraintTerm` in `openapi/firebox-calc-api.yaml`) and differs
 * from the Scala case name for the physics symbols (`MB` → `m_B`).
 *
 * Wire-stable API contract. Any shape change requires a matching OpenAPI document change in the same commit.
 */
enum ConstraintTerm(val wire: String):
    case MB                    extends ConstraintTerm("m_B")
    case MBMin                 extends ConstraintTerm("m_B_min")
    case GlassArea             extends ConstraintTerm("glassArea")
    case FireboxBase           extends ConstraintTerm("fireboxBase")
    case H_BR                  extends ConstraintTerm("H_BR")
    case Lambda                extends ConstraintTerm("lambda")
    case HeightOfLowestOpening extends ConstraintTerm("heightOfLowestOpening")
    case GlassSurfaceRatio     extends ConstraintTerm("glassSurfaceRatio")
    case AirIntakePipeShape    extends ConstraintTerm("airIntakePipeShape")
    case NominalLoadInRange    extends ConstraintTerm("nominalLoadInRange")
    case Custom                extends ConstraintTerm("custom")
