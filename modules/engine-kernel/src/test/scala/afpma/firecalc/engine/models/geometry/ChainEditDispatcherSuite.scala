/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_15544_V3 as FDElem15
import afpma.firecalc.dto.v4.AddThermalPipeElement_13384_V3 as TDElem13
import afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3
import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3 as FDProp15
import afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*

import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.*
import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.PropagationStrategy.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.AzimuthDirection.*
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.domain.InclinationDirection.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

/**
 * Unit tests for ChainEditDispatcher — detectEdit, apply strategies, policy, and
 * downstreamPinCount.
 *
 * Fixture strategy: all slots use FlueSlot(Seq[FlowOnlyPipeDescr_15544_V3]) with
 * SetInitialDirection to seed the frame and AddSharpeAngle_0_to_180 for direction-change
 * elements. The chain always ends with a ChimneySlot so the chimney scope boundary is
 * exercised correctly.
 */
class ChainEditDispatcherSuite extends AnyFlatSpec with Matchers:

    // ── Helpers ──────────────────────────────────────────────────────────

    def absDir(az: AzimuthDirection, incl: InclinationDirection): AbsoluteDirection =
        AbsoluteDirection(az, incl)

    val adRight: AbsoluteDirection = absDir(Right, Horizontal)
    val adFront: AbsoluteDirection = absDir(Front, Horizontal)
    val adLeft : AbsoluteDirection = absDir(Left, Horizontal)
    val adRear : AbsoluteDirection = absDir(Rear, Horizontal)
    val adDown : AbsoluteDirection = AbsoluteDirection(None, Down)

    // A FlueSlot element that is a direction-change (pinned)
    def bend(angleDeg: Double, pin: Option[AbsoluteDirection]): FlowOnlyPipeDescr_15544_V3 =
        FDElem15.AddSharpeAngle_0_to_180("b", angleDeg.degrees, pin)

    // A FlueSlot element that is NOT a direction change (plain section)
    def sectionNamed(name: String): FlowOnlyPipeDescr_15544_V3 =
        FDElem15.AddSectionSlopped(name, 1.0.meters)

    def section(): FlowOnlyPipeDescr_15544_V3 =
        sectionNamed("s")

    def initDir(az: AzimuthDirection, incl: InclinationDirection): FlowOnlyPipeDescr_15544_V3 =
        FDProp15.SetInitialDirection(az, incl)

    /** A ChimneySlot/ConnectorSlot direction-change element (thermal variant). */
    def chimneyBend(angleDeg: Double, pin: Option[AbsoluteDirection]): ThermalPipeDescr_13384_V3 =
        TDElem13.AddSharpeAngle_0_to_90("cb", angleDeg.degrees, pin)

    /** Minimal chain: one FlueSlot + one ChimneySlot */
    def simpleFlue(elems: FlowOnlyPipeDescr_15544_V3*): Seq[PostFireboxPipeDescrSlot] =
        Seq(
            FlueSlot   (elems.toSeq),
            ChimneySlot(Seq.empty  )
        )

    /** Chain with one FlueSlot + one ChimneySlot populated with thermal elements. */
    def flueAndChimney(
        flueElems   : Seq[FlowOnlyPipeDescr_15544_V3],
        chimneyElems: Seq[ThermalPipeDescr_13384_V3]
    ): Seq[PostFireboxPipeDescrSlot] =
        Seq(FlueSlot(flueElems), ChimneySlot(chimneyElems))

    /** Chain with two FlueSlots + ChimneySlot, giving cross-slot coord coverage */
    def twoSlotChain(
        flue0Elems: Seq[FlowOnlyPipeDescr_15544_V3],
        flue1Elems: Seq[FlowOnlyPipeDescr_15544_V3]
    ): Seq[PostFireboxPipeDescrSlot] =
        Seq(
            FlueSlot   (flue0Elems),
            FlueSlot   (flue1Elems),
            ChimneySlot(Seq.empty )
        )

    // ── detectEdit tests ─────────────────────────────────────────────────

    "ChainEditDispatcher.detectEdit" should "return None when slots are identical (no change)" in {
        val slots = simpleFlue(
            initDir(Rear, Horizontal   ),
            bend   (90.0, Some(adRight))
        )
        ChainEditDispatcher.detectEdit(slots, slots) shouldBe None
    }

    it should "return None on length mismatch" in {
        val s1 = simpleFlue(initDir(Rear, Horizontal), bend(90.0, Some(adRight)))
        val s2 = Seq(FlueSlot(Seq(initDir(Rear, Horizontal))), ChimneySlot(Seq.empty))
        ChainEditDispatcher.detectEdit(s1, s2) shouldBe None
    }

    it should "detect AngleEdit on a pinned element" in {
        val coord = ChainCoord(slotIdx = 0, elemIdx = 1)
        val old   = simpleFlue(initDir(Rear, Horizontal), bend(90.0, Some(adRight)))
        val upd   = simpleFlue(initDir(Rear, Horizontal), bend(45.0, Some(adRight)))
        ChainEditDispatcher.detectEdit(old, upd) shouldBe Some(
            AngleEdit(coord, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        )
    }

    it should "return None for angle change on unpinned element (absDir = None)" in {
        val old = simpleFlue(initDir(Rear, Horizontal), bend(90.0, None))
        val upd = simpleFlue(initDir(Rear, Horizontal), bend(45.0, None))
        ChainEditDispatcher.detectEdit(old, upd) shouldBe None
    }

    it should "detect DirectionEdit when only absDir changes" in {
        val coord = ChainCoord(slotIdx = 0, elemIdx = 1)
        val old   = simpleFlue(initDir(Rear, Horizontal), bend(90.0, Some(adRight)))
        val upd   = simpleFlue(initDir(Rear, Horizontal), bend(90.0, Some(adFront)))
        ChainEditDispatcher.detectEdit(old, upd) shouldBe Some(
            DirectionEdit(coord, oldAbsDir = Some(adRight), newAbsDir = Some(adFront))
        )
    }

    it should "return AngleEdit (not DirectionEdit) when both angle and absDir change on same element" in {
        val old = simpleFlue(initDir(Rear, Horizontal), bend(90.0, Some(adRight)))
        val upd = simpleFlue(initDir(Rear, Horizontal), bend(45.0, Some(adFront)))
        ChainEditDispatcher.detectEdit(old, upd) match
            case Some(_: AngleEdit) => succeed
            case other              => fail(s"Expected AngleEdit, got $other")
    }

    it should "detect descriptor-level insertion when multiple elements are inserted together" in {
        val old = simpleFlue(
            initDir     (Rear, Horizontal),
            sectionNamed("before"        ),
            sectionNamed("after"         )
        )
        val upd = simpleFlue(
            initDir     (Rear, Horizontal   ),
            sectionNamed("before"           ),
            bend        (45.0, Some(adRight)),
            sectionNamed("middle"           ),
            sectionNamed("after"            )
        )

        ChainEditDispatcher.detectEdit(old, upd) shouldBe Some(
            InsertEdit(ChainCoord(slotIdx = 0, elemIdx = 2), deflectionDeg = 45.0, InsertKind.DescriptorLevel)
        )
    }

    it should "return None when an appended slot contains no direction change" in {
        val old = simpleFlue(initDir(Rear, Horizontal), bend(90.0, Some(adRight)))
        val upd = old :+ ConnectorSlot(Seq.empty)
        ChainEditDispatcher.detectEdit(old, upd) shouldBe None
    }

    // ── apply + AngleEdit tests ─────────────────────────────────────────

    // Base chain: SetInitialDirection(Rear) + bend90(pinned Right) + bend90(pinned Front)
    def baseChain3(): Seq[PostFireboxPipeDescrSlot] =
        simpleFlue(
            initDir(Rear, Horizontal   ),
            bend   (90.0, Some(adRight)),
            bend   (90.0, Some(adFront))
        )

    // Same chain but with the first bend's angle changed to 45°
    def editedChain3(newAngle: Double = 45.0): Seq[PostFireboxPipeDescrSlot] =
        simpleFlue(
            initDir(Rear, Horizontal       ),
            bend   (newAngle, Some(adRight)),
            bend   (90.0, Some(adFront)    )
        )

    val coord0_1: ChainCoord = ChainCoord(slotIdx = 0, elemIdx = 1)

    def angleEdit90to45(): AngleEdit =
        AngleEdit(coord0_1, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)

    "ChainEditDispatcher.apply" should
        "RigidRotation: downstream pinned elements' absDirs are rotated" in {
            val pre    = baseChain3()
            val newS   = editedChain3(45.0)
            val edit   = angleEdit90to45()
            val result = ChainEditDispatcher(pre, newS, edit, RigidRotation)

            result.length shouldBe pre.length
            // Result has same number of elements in each slot
            val preDescr    = pre(0).asInstanceOf[FlueSlot].descr
            val resultDescr = result(0).asInstanceOf[FlueSlot].descr
            resultDescr.length shouldBe preDescr.length
            // The downstream pinned element (idx=2) should have had its absDir rotated
            // (not equal to the pre-edit value, since the outgoing direction changed)
            resultDescr(2) should not equal preDescr(2)
        }

    it should "RigidRotation: chimney pins are rotated (unified with flue/connector)" in {
        // Flue carries the edit site at idx=1; chimney carries a pinned bend that used to be Front.
        // After rigid-rotating on the 90°→45° flue edit, the chimney pin's absDir must differ from
        // the pre-edit value — confirming rotation propagates through the chimney boundary.
        val pre    = flueAndChimney(
            flueElems    = Seq(initDir(Rear, Horizontal), bend(90.0, Some(adRight))),
            chimneyElems = Seq(chimneyBend(45.0, Some(adFront)))
        )
        val newS   = flueAndChimney(
            flueElems    = Seq(initDir(Rear, Horizontal), bend(45.0, Some(adRight))),
            chimneyElems = Seq(chimneyBend(45.0, Some(adFront)))
        )
        val edit   = AngleEdit(coord0_1, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        val result = ChainEditDispatcher(pre, newS, edit, RigidRotation)

        result.length shouldBe pre.length
        result(1) match
            case ChimneySlot(d) =>
                d.length shouldBe 1
                d(0) should not equal pre(1).asInstanceOf[ChimneySlot].descr(0)
            case _              => fail("Expected ChimneySlot at idx 1")
    }

    it should "use supplied initial frame when preserving a first-slot angle edit" in {
        val pre  = simpleFlue(bend(90.0, Some(adDown)))
        val newS = simpleFlue(bend(45.0, Some(adDown)))
        val edit = ChainEditDispatcher
            .detectEdit(pre, newS)
            .getOrElse(fail("detectEdit failed to spot the angle change"))

        val result = ChainEditDispatcher(
            pre,
            newS,
            edit,
            RigidRotation,
            initialFrame = Some(PipeFrame.initial(Vec3.Right))
        )

        val resultDescr = result(0).asInstanceOf[FlueSlot].descr
        val resultBend  = resultDescr(0).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
        val abs         = resultBend.absDir.getOrElse(fail("absDir missing"))

        abs.azimuth shouldBe Some(Right)
        abs.inclination match
            case InclinationDirection.Custom(angle) => angle.value.shouldBe(-45.0 +- 0.5)
            case other                              => fail(s"expected custom -45° inclination, got $other")
    }

    it should "rotate chimney pins through a NoFlueSlot gap" in {
        val pre  = Seq(
            FlueSlot   (Seq(initDir(Rear, Horizontal), bend(90.0, Some(adRight)))),
            NoFlueSlot,
            ChimneySlot(Seq(chimneyBend(45.0, Some(adFront)))                    )
        )
        val newS = Seq(
            FlueSlot   (Seq(initDir(Rear, Horizontal), bend(45.0, Some(adRight)))),
            NoFlueSlot,
            ChimneySlot(Seq(chimneyBend(45.0, Some(adFront)))                    )
        )
        val edit = AngleEdit(coord0_1, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)

        val result = ChainEditDispatcher(pre, newS, edit, RigidRotation)

        result(1) shouldBe NoFlueSlot
        result(2) match
            case ChimneySlot(d) =>
                d.length shouldBe 1
                d(0) should not equal pre(2).asInstanceOf[ChimneySlot].descr(0)
            case _              => fail("Expected ChimneySlot at idx 2")
    }

    it should "return raw new slots when descriptor insertion has no incoming frame" in {
        val pre  = simpleFlue(
            sectionNamed("before"),
            sectionNamed("after" )
        )
        val newS = simpleFlue(
            sectionNamed("before"  ),
            bend        (45.0, None),
            sectionNamed("after"   )
        )
        val edit = ChainEditDispatcher
            .detectEdit(pre, newS)
            .getOrElse(fail("detectEdit failed to spot the insertion"))

        val result = ChainEditDispatcher(pre, newS, edit, RigidRotation)

        result shouldBe newS
        val inserted = result(0).asInstanceOf[FlueSlot].descr(1).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
        inserted.absDir shouldBe None
    }

    // ── apply + DirectionEdit tests ─────────────────────────────────────

    "ChainEditDispatcher.apply" should
        "DirectionEdit + RigidRotation: downstream pinned elements rotate" in {
            val coord  = ChainCoord(slotIdx = 0, elemIdx = 1)
            val pre    = simpleFlue(
                initDir(Rear, Horizontal   ),
                bend   (90.0, Some(adRight)),
                bend   (90.0, Some(adFront))
            )
            val newS   = simpleFlue(
                initDir(Rear, Horizontal   ),
                bend   (90.0, Some(adLeft) ),
                bend   (90.0, Some(adFront))
            )
            val edit   = DirectionEdit(coord, oldAbsDir = Some(adRight), newAbsDir = Some(adLeft))
            val result = ChainEditDispatcher(pre, newS, edit, RigidRotation)

            // The downstream pinned element (idx=2) should have been rigidly rotated
            val preD    = pre(0).asInstanceOf[FlueSlot].descr
            val resultD = result(0).asInstanceOf[FlueSlot].descr
            // Edited element is patched
            resultD(1) shouldBe newS(0).asInstanceOf[FlueSlot].descr(1)
            // Downstream is rotated (not equal to pre)
            resultD(2) should not equal preD(2)
        }

    // ── Idempotency ───────────────────────────────────────────────────

    "ChainEditDispatcher.apply" should "be idempotent under RigidRotation" in {
        val pre  = baseChain3()
        val newS = editedChain3(45.0)
        val edit = angleEdit90to45()
        val r1   = ChainEditDispatcher(pre, newS, edit, RigidRotation)
        val r2   = ChainEditDispatcher(pre, newS, edit, RigidRotation)
        r1 shouldBe r2
    }

    // ── Policy tests ──────────────────────────────────────────────────

    "ChainEditDispatcher.defaultStrategy" should "always return RigidRotation" in {
        val ae = angleEdit90to45()
        val de = DirectionEdit(coord0_1, Some(adRight), Some(adLeft))
        ChainEditDispatcher.defaultStrategy(ae) shouldBe RigidRotation
        ChainEditDispatcher.defaultStrategy(de) shouldBe RigidRotation
    }

    "ChainEditDispatcher.alternatives" should "return List(RigidRotation) for any edit" in {
        val ae = angleEdit90to45()
        val de = DirectionEdit(coord0_1, Some(adRight), Some(adLeft))
        ChainEditDispatcher.alternatives(ae) shouldBe List(RigidRotation)
        ChainEditDispatcher.alternatives(de) shouldBe List(RigidRotation)
    }

    // ── downstreamPinCount ────────────────────────────────────────────

    "ChainEditDispatcher.downstreamPinCount" should
        "count pinned direction-change elements strictly downstream of coord" in {
            // Chain: initDir, bend0(pinned), section, bend1(pinned), bend2(unpinned)
            val slots = simpleFlue(
                initDir(Rear, Horizontal   ),
                bend   (90.0, Some(adRight)), // idx=1 pinned  ← coord here
                section(                   ), // idx=2 not a bend
                bend   (90.0, Some(adFront)), // idx=3 pinned  ← downstream
                bend   (45.0, None         )  // idx=4 unpinned
            )
            val coord = ChainCoord(slotIdx = 0, elemIdx = 1)
            // idx=3 is pinned, idx=4 is unpinned → count = 1
            ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 1
        }

    it should "return 0 when the coord is in an empty ChimneySlot" in {
        // Chimney is part of the downstream walk now (unified rule), but with no pins the count is 0.
        val slots = simpleFlue(
            initDir(Rear, Horizontal   ),
            bend   (90.0, Some(adRight))
        )
        val coord = ChainCoord(slotIdx = 1, elemIdx = 0) // ChimneySlot is idx=1
        ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 0
    }

    it should "count pinned direction-changes inside the ChimneySlot" in {
        val slots = flueAndChimney(
            flueElems    = Seq(initDir(Rear, Horizontal), bend(90.0, Some(adRight))),
            chimneyElems = Seq(chimneyBend(45.0, Some(adFront)), chimneyBend(30.0, None))
        )
        // coord at FlueSlot idx 1 → downstream includes chimney pin idx=0 (pinned) + idx=1 (unpinned) = 1.
        val coord = ChainCoord(slotIdx = 0, elemIdx = 1)
        ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 1
    }

    it should "count pins across multiple flue slots" in {
        val slots = twoSlotChain(
            flue0Elems = Seq(initDir(Rear, Horizontal), bend(90.0, Some(adRight))),
            flue1Elems = Seq(bend(90.0, Some(adFront)), bend(45.0, None))
        )
        // coord in slot 0, elem 1 → downstream is all of slot 1: bend(pinned) + bend(unpinned) = 1
        val coord = ChainCoord(slotIdx = 0, elemIdx = 1)
        ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 1
    }

end ChainEditDispatcherSuite
