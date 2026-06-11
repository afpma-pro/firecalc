/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4.AddSharpeAngle_0_to_180
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*

import afpma.firecalc.engine.models.geometry.ChainEditDispatcher
import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.PropagationStrategy.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.Vec3

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
     *  2. `ChainEditDispatcher(..., RigidRotation, initialFrame)` — apply strategy with the fixture's
     *     initial frame.
     */
    private def simulateAngleEditAndRotate(
        oldSlots    : Seq[PostFireboxPipeDescrSlot_V7],
        rawNewSlots : Seq[PostFireboxPipeDescrSlot_V7],
        initialFrame: Option[PipeFrame]
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        val edit = ChainEditDispatcher
            .detectEdit(oldSlots, rawNewSlots)
            .getOrElse(fail("detectEdit failed to spot the angle change"))
        ChainEditDispatcher(oldSlots, rawNewSlots, edit, RigidRotation, initialFrame)

    private def exampleInitialFrame: Option[PipeFrame] =
        val initialDir = EngineState.example_projet_15544.post_firebox_pipes.initialDirection
        val azDeg      = AzimuthDirection.toDegrees(initialDir.azimuth)
        val elDeg      = InclinationDirection.toDegrees(initialDir.inclination)
        Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))

    /** Find a direction-change element in a FlueSlot by its display name. */
    private def findFlueBend(slot: PostFireboxPipeDescrSlot_V7, name: String): AddSharpeAngle_0_to_180 =
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

        val oldPipes     = EngineState.example_projet_15544.post_firebox_pipes
        val oldSlots     = oldPipes.slots
        val initialFrame = exampleInitialFrame

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

        val finalSlots = simulateAngleEditAndRotate(oldSlots, rawNewSlots, initialFrame)

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

    // ── Scenario 2: descriptor-level insertion (Finding 5) ──

    "scenario 2: insert a 45° bend into existing FlueSlot" - {

        val oldPipes     = EngineState.example_projet_15544.post_firebox_pipes
        val oldSlots     = oldPipes.slots
        val initialFrame = exampleInitialFrame

        val (flueSlotIdx, flueDescr) = oldSlots.zipWithIndex
            .collectFirst { case (FlueSlot(d), i) =>
                (i, d)
            }
            .getOrElse(fail("no FlueSlot in example project"))

        // Insert a new bend after "sortie foyer" (index 0 = roughness, 1 = innerShape,
        // 2 = setInitialDirection, 3 = setInitialPosition, 4 = addSectionHorizontal "sortie foyer")
        val insertAt    = 5 // right after the first section
        val newBend     = AddSharpeAngle_0_to_180(
            name   = "virage ajouté",
            angle  = 45.degrees,
            absDir = None
        )
        val newDescr    = flueDescr.patch(insertAt, Seq(newBend), 0)
        val rawNewSlots = oldSlots.updated(flueSlotIdx, FlueSlot(newDescr))

        val edit = ChainEditDispatcher.detectEdit(oldSlots, rawNewSlots)
        edit.isDefined.shouldBe(true)

        "should detect InsertEdit with DescriptorLevel kind" in {
            val ie = edit.get.asInstanceOf[ChainEditDispatcher.InsertEdit]
            ie.kind `shouldBe` ChainEditDispatcher.InsertKind.DescriptorLevel
            ie.coord.slotIdx `shouldBe` flueSlotIdx
            ie.coord.elemIdx `shouldBe` insertAt
            ie.deflectionDeg `shouldBe` 45.0
        }

        "should set the expected absDir on inserted element after dispatch" in {
            val ie         = edit.get.asInstanceOf[ChainEditDispatcher.InsertEdit]
            val finalSlots = ChainEditDispatcher(oldSlots, rawNewSlots, ie, RigidRotation, initialFrame)
            val inserted   = finalSlots(flueSlotIdx) match
                case FlueSlot(d) => d(insertAt).asInstanceOf[AddSharpeAngle_0_to_180]
                case _           => fail("expected FlueSlot")
            val abs        = inserted.absDir.getOrElse(fail("absDir missing"))
            withClue(s"full absDir = $abs:"):
                abs.azimuth.shouldBe                    (Some(AzimuthDirection.Right))
                inclinationDeg(abs.inclination).shouldBe(-45.0 +- 0.5                )
        }
    }

    // ── Scenario 3: slot-level insertion (Finding 5) ──

    "scenario 3: append a new ThermalFlueSlot with a direction-change element" - {

        val oldPipes     = EngineState.example_projet_15544.post_firebox_pipes
        val oldSlots     = oldPipes.slots
        val initialFrame = exampleInitialFrame

        // Create a new ThermalFlueSlot with a bend using case classes directly.
        // Minimal set of elements — just enough for the detector to find the direction change.
        import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.*
        import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4.*
        import afpma.firecalc.dto.common.PipeLocation
        import afpma.firecalc.dto.common.AppendLayerDescr

        val newThermalSlot = ThermalFlueSlot {
            Seq[afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4](
                SetRoughness            (1.mm                      ),
                SetInnerShape(afpma.firecalc.domain.PipeShape.Circle(20.cm)),
                SetLayers    (
                    List(AppendLayerDescr.FromThermalResistanceUsingThickness(1.mm, SquareMeterKelvinPerWatt(0.44)))
                ),
                SetPipeLocation         (PipeLocation.HeatedArea   ),
                AddSectionVertical      ("section initiale", 100.mm),
                AddSharpeAngle_0_to_90  (
                    name   = "coude ajouté",
                    angle  = 90.degrees,
                    absDir = Some(AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Down))
                )
            )
        }
        val rawNewSlots    = oldSlots :+ newThermalSlot

        val edit = ChainEditDispatcher.detectEdit(oldSlots, rawNewSlots)
        edit.isDefined.shouldBe(true)

        "should detect InsertEdit with SlotLevel kind" in {
            val ie = edit.get.asInstanceOf[ChainEditDispatcher.InsertEdit]
            ie.kind `shouldBe` ChainEditDispatcher.InsertKind.SlotLevel
            ie.coord.slotIdx `shouldBe` oldSlots.length
            ie.deflectionDeg `shouldBe` 90.0
        }

        "should set absDir and rotate downstream after dispatch" in {
            val ie           = edit.get.asInstanceOf[ChainEditDispatcher.InsertEdit]
            val finalSlots   = ChainEditDispatcher(oldSlots, rawNewSlots, ie, RigidRotation, initialFrame)
            val insertedSlot = finalSlots(ie.coord.slotIdx) match
                case ThermalFlueSlot(d) => d
                case _                  => fail("expected ThermalFlueSlot")
            val bend         = insertedSlot(ie.coord.elemIdx).asInstanceOf[AddSharpeAngle_0_to_90]
            bend.absDir.isDefined `shouldBe` true
        }
    }

    // ── Scenario 4: structural mismatch returns None ──

    "scenario 4: structural mismatch (slot removed) returns None" - {

        val oldPipes = EngineState.example_projet_15544.post_firebox_pipes
        val oldSlots = oldPipes.slots
        val newSlots = oldSlots.init // remove last slot

        val edit = ChainEditDispatcher.detectEdit(oldSlots, newSlots)
        edit `shouldBe` None
    }

    "scenario 5: same-slot ordinal mismatch returns None" - {

        val oldPipes = EngineState.example_projet_15544.post_firebox_pipes
        val oldSlots = oldPipes.slots
        // Replace first slot with a different type (ordinal mismatch)
        val newSlots = oldSlots.updated(0, ConnectorSlot(Seq.empty)) :+ ConnectorSlot(Seq.empty)

        val edit = ChainEditDispatcher.detectEdit(oldSlots, newSlots)
        edit `shouldBe` None
    }

end ChainEditDispatcherScenarioSuite
