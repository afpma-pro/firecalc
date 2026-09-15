/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.v7.AddFlowOnlyPipeElement_15544_V4 as FDElem15
import afpma.firecalc.dto.v7.AddThermalPipeElement_13384_V4 as TDElem13
import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4
import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*
import afpma.firecalc.dto.common.PipeShape.Circle

import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.*
import afpma.firecalc.engine.models.geometry.ChainEditDispatcher.PropagationStrategy.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.AzimuthDirection.*
import afpma.firecalc.domain.InclinationDirection
import afpma.firecalc.domain.InclinationDirection.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.units.Vec3

/**
 * Unit tests for ChainEditDispatcher — detectEdit, apply strategies, policy, and
 * downstreamPinCount.
 *
 * Fixture strategy: all slots use FlueSlot(Seq[FlowOnlyPipeDescr_15544_V4]) plus
 * wrapper initialFrame for the frame seed, and AddSharpeAngle_0_to_180 for
 * direction-change elements. The chain always ends with a ChimneySlot so the
 * chimney scope boundary is exercised correctly.
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
    val adUp   : AbsoluteDirection = AbsoluteDirection(None, Up)

    private val rearInitialFrame = Some(PipeFrame.initial(Vec3.Rear))

    // A FlueSlot element that is a direction-change (pinned)
    def bend(angleDeg: Double, pin: Option[AbsoluteDirection]): FlowOnlyPipeDescr_15544_V4 =
        FDElem15.AddSharpeAngle_0_to_180("b", angleDeg.degrees, pin)

    /** A FlueSlot split/merge element — also a 90° direction change (see `IsDirectionChange`). */
    def mergeFlow(pin: Option[AbsoluteDirection]): FlowOnlyPipeDescr_15544_V4 =
        FDElem15.MergeTwoFlowsIntoSingleWith90DegTurn("m", pin, Circle(0.18.meters))

    // A FlueSlot element that is NOT a direction change (plain section)
    def sectionNamed(name: String): FlowOnlyPipeDescr_15544_V4 =
        FDElem15.AddSectionSlopped(name, 1.0.meters)

    def section(): FlowOnlyPipeDescr_15544_V4 =
        sectionNamed("s")

    /** A ChimneySlot/ConnectorSlot direction-change element (thermal variant). */
    def chimneyBend(angleDeg: Double, pin: Option[AbsoluteDirection]): ThermalPipeDescr_13384_V4 =
        TDElem13.AddSharpeAngle_0_to_90("cb", angleDeg.degrees, pin)

    /** Minimal chain: one FlueSlot + one ChimneySlot */
    def simpleFlue(elems: FlowOnlyPipeDescr_15544_V4*): Seq[PostFireboxPipeDescrSlot_V7] =
        Seq(
            FlueSlot   (elems.toSeq),
            ChimneySlot(Seq.empty  )
        )

    /** Chain with one FlueSlot + one ChimneySlot populated with thermal elements. */
    def flueAndChimney(
        flueElems   : Seq[FlowOnlyPipeDescr_15544_V4],
        chimneyElems: Seq[ThermalPipeDescr_13384_V4]
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        Seq(FlueSlot(flueElems), ChimneySlot(chimneyElems))

    /** Chain with two FlueSlots + ChimneySlot, giving cross-slot coord coverage */
    def twoSlotChain(
        flue0Elems: Seq[FlowOnlyPipeDescr_15544_V4],
        flue1Elems: Seq[FlowOnlyPipeDescr_15544_V4]
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        Seq(
            FlueSlot   (flue0Elems),
            FlueSlot   (flue1Elems),
            ChimneySlot(Seq.empty )
        )

    // ── detectEdit tests ─────────────────────────────────────────────────

    "ChainEditDispatcher.detectEdit" should "return None when slots are identical (no change)" in {
        val slots = simpleFlue(
            bend(90.0, Some(adRight))
        )
        ChainEditDispatcher.detectEdit(slots, slots) shouldBe None
    }

    it should "return None on length mismatch" in {
        val s1 = simpleFlue(bend(90.0, Some(adRight)))
        val s2 = Seq(FlueSlot(Seq.empty), ChimneySlot(Seq.empty))
        ChainEditDispatcher.detectEdit(s1, s2) shouldBe None
    }

    it should "detect AngleEdit on a pinned element" in {
        val coord = ChainCoord(slotIdx = 0, elemIdx = 0)
        val old   = simpleFlue(bend(90.0, Some(adRight)))
        val upd   = simpleFlue(bend(45.0, Some(adRight)))
        ChainEditDispatcher.detectEdit(old, upd) shouldBe Some(
            AngleEdit(coord, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        )
    }

    it should "return None for angle change on unpinned element (absDir = None)" in {
        val old = simpleFlue(bend(90.0, None))
        val upd = simpleFlue(bend(45.0, None))
        ChainEditDispatcher.detectEdit(old, upd) shouldBe None
    }

    it should "return None for a pure reorder of two direction-change elements (up/down chevron swap)" in {
        // Regression: swapping two direction-change elements used to be reported as a DirectionEdit
        // at the first differing index (both sides of that index are direction changes, both turning
        // 90°), and the dispatcher then spliced the moved element over the pre-edit chain there —
        // destroying the element that sat at it. This is the reported case: an "Angle vif" moved up
        // over a "Fusionner en 1 flux", which the scan conflates because a merge is a 90° direction
        // change too. Both pins come from the reporter's project.
        val old     = simpleFlue(
            mergeFlow(Some(adRight)), // the merge the user moves down with
            bend(90.0, Some(adUp)) // the angle the user moves up   (e.g. "vers remontée")
        )
        val swapped = simpleFlue(
            bend(90.0, Some(adUp)),
            mergeFlow(Some(adRight))
        )
        ChainEditDispatcher.detectEdit(old, swapped) shouldBe None
    }

    it should "return None for a pure reorder of two same-kind direction-change elements" in {
        val old     = simpleFlue(
            bend(90.0, Some(adLeft)),
            bend(90.0, Some(adDown))
        )
        val swapped = simpleFlue(
            bend(90.0, Some(adDown)),
            bend(90.0, Some(adLeft))
        )
        ChainEditDispatcher.detectEdit(old, swapped) shouldBe None
    }

    it should "return None for a reorder that differs in kind, pin and angle at once" in {
        // Same elements, permuted: no positional pairing can turn this into an element edit.
        val old     = simpleFlue(
            bend(45.0, Some(adLeft)),
            mergeFlow(Some(adRight)),
            bend(90.0, None        )
        )
        val rotated = simpleFlue(
            mergeFlow(Some(adRight)),
            bend(90.0, None        ),
            bend(45.0, Some(adLeft))
        )
        ChainEditDispatcher.detectEdit(old, rotated) shouldBe None
    }

    it should "return None for a reorder of duplicate-valued elements" in {
        // The value-equal copies make the sequences differ positionally while the multiset does not.
        val old     = simpleFlue(
            bend(90.0, Some(adLeft)),
            bend(90.0, Some(adLeft)),
            mergeFlow(Some(adRight))
        )
        val swapped = simpleFlue(
            bend(90.0, Some(adLeft)),
            mergeFlow(Some(adRight)),
            bend(90.0, Some(adLeft))
        )
        ChainEditDispatcher.detectEdit(old, swapped) shouldBe None
    }

    it should "return None when a slot move swaps two whole flue slots" in {
        // A slot move is a reorder one level up: the up/down chevrons move a whole slot, and both
        // sequences hold the same slots, so there is no element edit to attribute. Before the guard
        // this returned a cross-paired DirectionEdit naming slot B's pin as the edit's old value and
        // slot A's as the new one, and `apply` then rotated the chain by the delta between those two
        // unrelated elements.
        val old     = twoSlotChain(Seq(section(), bend(90.0, Some(adDown))), Seq(section(), bend(90.0, Some(adLeft))))
        val swapped = Seq(old(1), old(0), old(2))
        ChainEditDispatcher.detectEdit(old, swapped) shouldBe None
    }

    it should "return None when a slot move swaps flue slots of different lengths" in {
        // Lengths differ (4 vs 1 elements), so before the guard the longer slot looked like a
        // descriptor-level insertion into the shorter one.
        val old     = twoSlotChain(
            Seq(section(), bend(90.0, Some(adDown)), section(), bend(90.0, Some(adLeft))),
            Seq(sectionNamed("short"))
        )
        val swapped = Seq(old(1), old(0), old(2))
        ChainEditDispatcher.detectEdit(old, swapped) shouldBe None
    }

    it should "still report an angle edit made inside one slot of a multi-slot chain" in {
        // Only slots that the content matching leaves over are scanned; an element edit within a slot
        // changes that slot's value, so it is a leftover and detection proceeds as before.
        val old = twoSlotChain(Seq(section(), bend(90.0, Some(adRight))), Seq(section(), bend(90.0, Some(adLeft))))
        val nw  = twoSlotChain(Seq(section(), bend(45.0, Some(adRight))), Seq(section(), bend(90.0, Some(adLeft))))
        ChainEditDispatcher.detectEdit(old, nw) shouldBe Some(
            AngleEdit(ChainCoord(0, 1), oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        )
    }

    it should "attribute an edit that rides in the same window as a slot move to the moved slot" in {
        // One chevron click plus one angle keystroke inside the same debounce window: the slots are
        // swapped AND slot A's angle is now 45°. Slots are paired by content, so the leftover old slot
        // is A itself and the leftover new index is where A now sits: the edit is measured against A's
        // own 90°/Down. Pairing by position instead read the edit as B's pin (`Left`) at index 0 and
        // overwrote B through the dispatcher's rewrite.
        val old = twoSlotChain(Seq(section(), bend(90.0, Some(adDown))), Seq(section(), bend(90.0, Some(adLeft))))
        val nw  = Seq(old(1), FlueSlot(Seq(section(), bend(45.0, Some(adDown)))), ChimneySlot(Seq.empty))
        // The coordinate is read off the moved slot's NEW position (`newDescr.indexOf`), so its bend —
        // the second descriptor element — lands at elemIdx 1.
        ChainEditDispatcher.detectEdit(old, nw) shouldBe Some(
            AngleEdit(ChainCoord(1, 1), oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adDown)
        )
    }

    it should "still attribute the first edit when two slots differ at once" in {
        // Two slots changed at once, each against its own pre-edit values: the correspondence between
        // leftovers is a guess, and pairing them in order keeps the previous behaviour — report the
        // first edit in ascending new index rather than silently dropping both.
        val old = twoSlotChain(Seq(section(), bend(90.0, Some(adRight))), Seq(section(), bend(90.0, Some(adLeft))))
        val nw  = twoSlotChain(Seq(section(), bend(45.0, Some(adRight))), Seq(section(), bend(45.0, Some(adLeft))))
        ChainEditDispatcher.detectEdit(old, nw) shouldBe Some(
            AngleEdit(ChainCoord(0, 1), oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        )
    }

    it should "return None when the window replaced two direction-change values at once" in {
        // Attribution would be a guess, so nothing is reported — the user's sequence stands.
        val old = simpleFlue(bend(90.0, Some(adRight)), mergeFlow(Some(adRight)))
        val nw  = simpleFlue(bend(45.0, Some(adLeft)), mergeFlow(Some(adFront)))
        ChainEditDispatcher.detectEdit(old, nw) shouldBe None
    }

    it should "still report the angle edit when the window also carries a change to another element" in {
        // A keystroke can land in the same debounce window as a rename or a move. Only direction-change
        // elements are the dispatcher's business, so the angle edit is still attributable.
        val old = simpleFlue(sectionNamed("A"), bend(90.0, Some(adRight)))
        val nw  = simpleFlue(sectionNamed("B"), bend(45.0, Some(adRight)))
        ChainEditDispatcher.detectEdit(old, nw) shouldBe Some(
            AngleEdit(ChainCoord(0, 1), oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        )
    }

    it should "propagate that edit without reverting the rename riding with it" in {
        val old  = simpleFlue(sectionNamed("A"), bend(90.0, Some(adRight)), bend(90.0, Some(adRear)))
        val nw   = simpleFlue(sectionNamed("B"), bend(45.0, Some(adRight)), bend(90.0, Some(adRear)))
        val edit = ChainEditDispatcher.detectEdit(old, nw).getOrElse(fail("expected the angle edit"))

        val result = ChainEditDispatcher(old, nw, edit, RigidRotation, initialFrame = rearInitialFrame)
        val descr  = result(0).asInstanceOf[FlueSlot].descr

        descr should contain(sectionNamed("B")) // the rename survives the rewrite
        descr should not contain sectionNamed("A") // the baseline's copy is not resurrected
        descr(2) should not equal old(0).asInstanceOf[FlueSlot].descr(2) // downstream pin rotated
    }

    it should "pair a move-plus-edit snapshot with the edited element's own old values" in {
        // The reported pair again, but with the moved element's angle also changed in the same window.
        // The edit must be measured against the element's *own* pre-edit pin (`Up`), never against the
        // merge the move displaced (`Right`) — that cross-pairing is what fed the absDir recomputation
        // and overwrote the merge.
        val old = simpleFlue(mergeFlow(Some(adRight)), bend(90.0, Some(adUp)))
        val nw  = simpleFlue(bend(45.0, Some(adUp)), mergeFlow(Some(adRight)))

        ChainEditDispatcher.detectEdit(old, nw) shouldBe Some(
            AngleEdit(ChainCoord(0, 0), oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adUp)
        )
    }

    it should "keep every element when a move rides along with an element edit" in {
        val old  = simpleFlue(mergeFlow(Some(adRight)), bend(90.0, Some(adUp)))
        val nw   = simpleFlue(bend(45.0, Some(adUp)), mergeFlow(Some(adRight)))
        val edit = ChainEditDispatcher.detectEdit(old, nw).getOrElse(fail("expected the angle edit"))

        val result = ChainEditDispatcher(old, nw, edit, RigidRotation, initialFrame = rearInitialFrame)
        val descr  = result(0).asInstanceOf[FlueSlot].descr

        descr should have size 2
        descr(1) shouldBe mergeFlow(Some(adRight)) // the displaced merge is untouched
        descr.head match
            case b: FDElem15.AddSharpeAngle_0_to_180 =>
                val userAngle = bend(45.0, None).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180].angle
                b.angle shouldBe userAngle // the user's 45°, not the 90° it had before the move
                b.absDir should not be None // re-derived by the AngleEdit pose contract
            case other => fail(s"expected the edited angle, got $other")
    }

    it should "not revert the elements a snapshot carries alongside the edit" in {
        // The snapshot is authoritative: anything the user's sequence holds besides the edited element
        // must survive the rewrite instead of being rebuilt from the baseline.
        val edit = AngleEdit(ChainCoord(0, 1), oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        val pre  = simpleFlue(sectionNamed("stale"), bend(90.0, Some(adRight)))
        val nw   = simpleFlue(sectionNamed("user"), bend(45.0, Some(adRight)))

        val result = ChainEditDispatcher(pre, nw, edit, RigidRotation, initialFrame = rearInitialFrame)

        result(0).asInstanceOf[FlueSlot].descr should contain(sectionNamed("user"))
        result(0).asInstanceOf[FlueSlot].descr should not contain sectionNamed("stale")
    }

    it should "detect DirectionEdit when only absDir changes" in {
        val coord = ChainCoord(slotIdx = 0, elemIdx = 0)
        val old   = simpleFlue(bend(90.0, Some(adRight)))
        val upd   = simpleFlue(bend(90.0, Some(adFront)))
        ChainEditDispatcher.detectEdit(old, upd) shouldBe Some(
            DirectionEdit(coord, oldAbsDir = Some(adRight), newAbsDir = Some(adFront))
        )
    }

    it should "return AngleEdit (not DirectionEdit) when both angle and absDir change on same element" in {
        val old = simpleFlue(bend(90.0, Some(adRight)))
        val upd = simpleFlue(bend(45.0, Some(adFront)))
        ChainEditDispatcher.detectEdit(old, upd) match
            case Some(_: AngleEdit) => succeed
            case other              => fail(s"Expected AngleEdit, got $other")
    }

    it should "detect descriptor-level insertion when multiple elements are inserted together" in {
        val old = simpleFlue(
            sectionNamed("before"),
            sectionNamed("after" )
        )
        val upd = simpleFlue(
            sectionNamed("before"           ),
            bend        (45.0, Some(adRight)),
            sectionNamed("middle"           ),
            sectionNamed("after"            )
        )

        ChainEditDispatcher.detectEdit(old, upd) shouldBe Some(
            InsertEdit(ChainCoord(slotIdx = 0, elemIdx = 1), deflectionDeg = 45.0, InsertKind.DescriptorLevel)
        )
    }

    it should "return None when an appended slot contains no direction change" in {
        val old = simpleFlue(bend(90.0, Some(adRight)))
        val upd = old :+ ConnectorSlot(Seq.empty)
        ChainEditDispatcher.detectEdit(old, upd) shouldBe None
    }

    // ── apply + AngleEdit tests ─────────────────────────────────────────

    // Base chain: bend90(pinned Right) + bend90(pinned Front)
    def baseChain3(): Seq[PostFireboxPipeDescrSlot_V7] =
        simpleFlue(
            bend(90.0, Some(adRight)),
            bend(90.0, Some(adFront)),
            bend(90.0, Some(adRear) )
        )

    // Same chain but with the first bend's angle changed to 45°
    def editedChain3(newAngle: Double = 45.0): Seq[PostFireboxPipeDescrSlot_V7] =
        simpleFlue(
            bend(newAngle, Some(adRight)),
            bend(90.0, Some(adFront)    ),
            bend(90.0, Some(adRear)     )
        )

    val coord0_1: ChainCoord = ChainCoord(slotIdx = 0, elemIdx = 0)

    def angleEdit90to45(): AngleEdit =
        AngleEdit(coord0_1, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)

    "ChainEditDispatcher.apply" should
        "RigidRotation: downstream pinned elements' absDirs are rotated" in {
            val pre    = baseChain3()
            val newS   = editedChain3(45.0)
            val edit   = angleEdit90to45()
            val result = ChainEditDispatcher(pre, newS, edit, RigidRotation, initialFrame = rearInitialFrame)

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
            flueElems    = Seq(bend(90.0, Some(adRight))),
            chimneyElems = Seq(chimneyBend(45.0, Some(adFront)))
        )
        val newS   = flueAndChimney(
            flueElems    = Seq(bend(45.0, Some(adRight))),
            chimneyElems = Seq(chimneyBend(45.0, Some(adFront)))
        )
        val edit   = AngleEdit(coord0_1, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)
        val result = ChainEditDispatcher(pre, newS, edit, RigidRotation, initialFrame = rearInitialFrame)

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
            FlueSlot   (Seq(bend(90.0, Some(adRight))       )),
            NoFlueSlot,
            ChimneySlot(Seq(chimneyBend(45.0, Some(adFront))))
        )
        val newS = Seq(
            FlueSlot   (Seq(bend(45.0, Some(adRight))       )),
            NoFlueSlot,
            ChimneySlot(Seq(chimneyBend(45.0, Some(adFront))))
        )
        val edit = AngleEdit(coord0_1, oldAngleDeg = 90.0, newAngleDeg = 45.0, oldAbsDir = adRight)

        val result = ChainEditDispatcher(pre, newS, edit, RigidRotation, initialFrame = rearInitialFrame)

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
            // Consistent snapshot: pre and new differ at the edit site only. The pre-edit pin (`Right`)
            // is evaluated against the *user's* incoming frame, so the pin change really does turn the
            // chain's outgoing direction (Right -> Front) and the downstream pin must follow.
            val coord  = ChainCoord(slotIdx = 0, elemIdx = 1)
            val pre    = simpleFlue(
                bend(90.0, Some(adFront)),
                bend(90.0, Some(adRight)),
                bend(90.0, Some(adRear) )
            )
            val newS   = simpleFlue(
                bend(90.0, Some(adFront)),
                bend(90.0, Some(adFront)),
                bend(90.0, Some(adRear) )
            )
            val edit   = DirectionEdit(coord, oldAbsDir = Some(adRight), newAbsDir = Some(adFront))
            val result = ChainEditDispatcher(pre, newS, edit, RigidRotation, initialFrame = rearInitialFrame)

            val preD    = pre(0).asInstanceOf[FlueSlot].descr
            val resultD = result(0).asInstanceOf[FlueSlot].descr
            // Edited element is the one the user wrote
            resultD(1) shouldBe newS(0).asInstanceOf[FlueSlot].descr(1)
            // The downstream pinned element is rigidly rotated: its old pin (`Rear`) cannot survive a
            // 90° turn of the outgoing direction.
            resultD(2) should not equal preD(2)
        }

    it should "DirectionEdit: downstream pins are left alone when the outgoing direction is unchanged" in {
        // Unpinned in both states: nothing turns, so nothing downstream moves.
        val coord  = ChainCoord(slotIdx = 0, elemIdx = 0)
        val pre    = simpleFlue(bend(90.0, None), bend(90.0, Some(adRear)))
        val newS   = simpleFlue(bend(90.0, None), bend(90.0, Some(adRear)))
        val edit   = DirectionEdit(coord, oldAbsDir = None, newAbsDir = None)
        val result = ChainEditDispatcher(pre, newS, edit, RigidRotation, initialFrame = rearInitialFrame)

        result shouldBe newS
    }

    // ── Idempotency ───────────────────────────────────────────────────

    "ChainEditDispatcher.apply" should "be idempotent under RigidRotation" in {
        val pre  = baseChain3()
        val newS = editedChain3(45.0)
        val edit = angleEdit90to45()
        val r1   = ChainEditDispatcher(pre, newS, edit, RigidRotation, initialFrame = rearInitialFrame)
        val r2   = ChainEditDispatcher(pre, newS, edit, RigidRotation, initialFrame = rearInitialFrame)
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
            // Chain: bend0(pinned), section, bend1(pinned), bend2(unpinned)
            val slots = simpleFlue(
                bend   (90.0, Some(adRight)), // idx=0 pinned  ← coord here
                section(                   ), // idx=1 not a bend
                bend   (90.0, Some(adFront)), // idx=2 pinned  ← downstream
                bend   (45.0, None         )  // idx=3 unpinned
            )
            val coord = ChainCoord(slotIdx = 0, elemIdx = 0)
            // idx=2 is pinned, idx=3 is unpinned → count = 1
            ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 1
        }

    it should "return 0 when the coord is in an empty ChimneySlot" in {
        // Chimney is part of the downstream walk now (unified rule), but with no pins the count is 0.
        val slots = simpleFlue(
            bend(90.0, Some(adRight))
        )
        val coord = ChainCoord(slotIdx = 1, elemIdx = 0) // ChimneySlot is idx=1
        ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 0
    }

    it should "count pinned direction-changes inside the ChimneySlot" in {
        val slots = flueAndChimney(
            flueElems    = Seq(bend(90.0, Some(adRight))),
            chimneyElems = Seq(chimneyBend(45.0, Some(adFront)), chimneyBend(30.0, None))
        )
        // coord at FlueSlot idx 1 → downstream includes chimney pin idx=0 (pinned) + idx=1 (unpinned) = 1.
        val coord = ChainCoord(slotIdx = 0, elemIdx = 1)
        ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 1
    }

    it should "count pins across multiple flue slots" in {
        val slots = twoSlotChain(
            flue0Elems = Seq(bend(90.0, Some(adRight))),
            flue1Elems = Seq(bend(90.0, Some(adFront)), bend(45.0, None))
        )
        // coord in slot 0, elem 1 → downstream is all of slot 1: bend(pinned) + bend(unpinned) = 1
        val coord = ChainCoord(slotIdx = 0, elemIdx = 1)
        ChainEditDispatcher.downstreamPinCount(slots, coord) shouldBe 1
    }

    // ── rotateAllPins — initial-direction full-chain rotation ──

    "ChainEditDispatcher.rotateAllPins" should "rotate all downstream pins by the direction delta" in {
        val slots  = flueAndChimney(
            Seq(
                bend(90.0, Some(adRight)), // elem 0
                bend(90.0, Some(adRear) )  // elem 1
            ),
            Seq(chimneyBend(45.0, Some(adDown)))
        )
        val result = ChainEditDispatcher.rotateAllPins(slots, Vec3.Rear, Vec3.Front)

        result match
            case Seq(FlueSlot(descr), ChimneySlot(_)) =>
                val bend0 = descr(0).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
                val bend1 = descr(1).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
                bend0.absDir shouldBe Some(absDir(Left, Horizontal) )
                bend1.absDir shouldBe Some(absDir(Front, Horizontal))
            case _                                    => fail("slot structure changed")
    }

    it should "rotate element 0 in slot 0 (lb=-1, nothing skipped)" in {
        val slots  = simpleFlue(bend(90.0, Some(adRight)))
        val result = ChainEditDispatcher.rotateAllPins(slots, Vec3.Front, Vec3.Rear)
        result match
            case Seq(FlueSlot(descr), _) =>
                val bend0 = descr(0).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
                bend0.absDir shouldBe Some(absDir(Left, Horizontal))
            case _                       => fail("slot structure changed")
    }

    it should "return slots unchanged when direction delta is zero" in {
        val slots = simpleFlue(bend(90.0, Some(adRight)))
        ChainEditDispatcher.rotateAllPins(slots, Vec3.Front, Vec3.Front) shouldBe slots
    }

    it should "handle pure inclination change (horizontal to up)" in {
        val slots  = simpleFlue(bend(90.0, Some(adFront)))
        val result = ChainEditDispatcher.rotateAllPins(slots, Vec3.Front, Vec3.Up)
        result match
            case Seq(FlueSlot(descr), _) =>
                val bend0 = descr(0).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
                bend0.absDir.get.inclination should not be Horizontal
            case _                       => fail("slot structure changed")
    }

    it should "not modify unpinned elements" in {
        val slots  = simpleFlue(
            bend(90.0, None         ), // unpinned
            bend(45.0, Some(adRight))  // pinned
        )
        val result = ChainEditDispatcher.rotateAllPins(slots, Vec3.Front, Vec3.Rear)
        result match
            case Seq(FlueSlot(descr), _) =>
                val unpinned = descr(0).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
                val pinned   = descr(1).asInstanceOf[FDElem15.AddSharpeAngle_0_to_180]
                unpinned.absDir shouldBe None
                pinned.absDir should not be None
            case _                       => fail("slot structure changed")
    }

    it should "rotate chimney pins when they exist" in {
        val slots  = flueAndChimney(
            Seq(bend(90.0, Some(adRight))      ),
            Seq(chimneyBend(45.0, Some(adDown)))
        )
        val result = ChainEditDispatcher.rotateAllPins(slots, Vec3.Front, Vec3.Rear)
        result match
            case Seq(FlueSlot(_), ChimneySlot(chimneyDescr)) =>
                val chimBend = chimneyDescr(0).asInstanceOf[TDElem13.AddSharpeAngle_0_to_90]
                chimBend.absDir shouldBe defined
            case _                                           => fail("slot structure changed")
    }
end ChainEditDispatcherSuite
