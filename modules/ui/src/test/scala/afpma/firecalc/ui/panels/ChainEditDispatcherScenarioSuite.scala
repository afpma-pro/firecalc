/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_15544_V3.AddSharpeAngle_0_to_180
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*

import afpma.firecalc.engine.models.geometry.ChainEditDispatcher
import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.PropagationStrategy.*

import afpma.firecalc.ui.models.EngineState

import coulomb.policy.standard.given

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * End-to-end scenario tests for the ChainEditDispatcher.
 *
 * Each scenario starts from `EngineState.example_projet_15544` and applies a user-level
 * edit (angle change), then calls `ChainEditDispatcher` with `RigidRotation` strategy
 * and asserts the resulting `absDir` values on downstream elements.
 *
 * These replace `PostFireboxChainRotationScenarioSuite` — same fixture data, same expected
 * outputs, new dispatcher API.
 */
class ChainEditDispatcherScenarioSuite extends AnyFreeSpec with Matchers:

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Drive the dispatcher end-to-end against a raw user edit using RigidRotation.
     *
     *  1. `detectEdit(oldSlots, rawNewSlots)` — locate the edited element.
     *  2. `ChainEditDispatcher(oldSlots, rawNewSlots, edit, RigidRotation)` — apply strategy.
     */
    private def simulateAngleEditAndRotate(
        oldSlots   : Seq[PostFireboxPipeDescrSlot],
        rawNewSlots: Seq[PostFireboxPipeDescrSlot]
    ): Seq[PostFireboxPipeDescrSlot] =
        val edit = ChainEditDispatcher
            .detectEdit(oldSlots, rawNewSlots)
            .getOrElse(fail("detectEdit failed to spot the angle change"))
        ChainEditDispatcher(oldSlots, rawNewSlots, edit, RigidRotation)

    /** Find a direction-change element in a FlueSlot by its display name. */
    private def findFlueBend(slot: PostFireboxPipeDescrSlot, name: String): AddSharpeAngle_0_to_180 =
        slot match
            case FlueSlot(descr) =>
                descr
                    .collectFirst {
                        case a: AddSharpeAngle_0_to_180 if a.name == name => a
                    }
                    .getOrElse(fail(s"'$name' not found in FlueSlot"))
            case other           =>
                fail(s"expected FlueSlot, got ${other.getClass.getSimpleName}")

    /** Extract the inclination angle in degrees — regardless of discrete vs Custom case. */
    private def inclinationDeg(incl: InclinationDirection): Double = incl match
        case InclinationDirection.Up         => 90.0
        case InclinationDirection.Down       => -90.0
        case InclinationDirection.Horizontal => 0.0
        case InclinationDirection.Custom(a)  => a.toUnit[Degree].value

    // ── Scenario 1: angle edit on "virage avant descente" (90° → 45°) ──

    "scenario 1: change 'virage avant descente' angle from 90° to 45°" - {

        val oldSlots = EngineState.example_projet_15544.post_firebox_pipes

        val (flueSlotIdx, flueDescr) = oldSlots.zipWithIndex
            .collectFirst { case (FlueSlot(d), i) =>
                (i, d)
            }
            .getOrElse(fail("no FlueSlot in example project"))

        val elemIdx = flueDescr.indexWhere {
            case a: AddSharpeAngle_0_to_180 if a.name == "virage avant descente" => true
            case _ => false
        }
        require(elemIdx >= 0, "'virage avant descente' not found in FlueSlot")

        val editedDescr = flueDescr.updated(
            elemIdx,
            flueDescr(elemIdx) match
                case a: AddSharpeAngle_0_to_180 => a.copy(angle = 45.degrees)
                case other => other
        )
        val rawNewSlots = oldSlots.updated(flueSlotIdx, FlueSlot(editedDescr))

        val finalSlots = simulateAngleEditAndRotate(oldSlots, rawNewSlots)

        "'arrière banc' direction should be (Right, inclination ≈ -45°)" in {
            // 'arrière banc' is a section — its direction is the outgoing direction of the
            // preceding bend, 'virage avant banc arrière'. We check that bend's absDir.
            val bend = findFlueBend(finalSlots(flueSlotIdx), "virage avant banc arrière")
            val abs  = bend.absDir.getOrElse(fail("absDir missing"))
            withClue(s"full absDir = $abs:"):
                abs.azimuth.shouldBe                    (Some(AzimuthDirection.Right))
                inclinationDeg(abs.inclination).shouldBe(-45.0 +- 0.5                )
        }

        "'remontée' direction should be (Right, inclination ≈ +45°)" in {
            // 'remontée' is a section — its direction is the outgoing direction of the
            // preceding bend, 'virage avant remontée'. We check that bend's absDir.
            val bend = findFlueBend(finalSlots(flueSlotIdx), "virage avant remontée")
            val abs  = bend.absDir.getOrElse(fail("absDir missing"))
            withClue(s"full absDir = $abs:"):
                abs.azimuth.shouldBe                    (Some(AzimuthDirection.Right))
                inclinationDeg(abs.inclination).shouldBe(45.0 +- 0.5                 )
        }
    }

end ChainEditDispatcherScenarioSuite
