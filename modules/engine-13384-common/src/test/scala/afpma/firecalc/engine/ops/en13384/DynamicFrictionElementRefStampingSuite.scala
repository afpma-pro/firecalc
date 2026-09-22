/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Application_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError.*

import cats.Show
import cats.data.ValidatedNel
import cats.data.Validated.Invalid
import cats.syntax.all.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Pins the element-reference stamping of [[MecaFlu_13384_DynamicFriction]]'s
 * section-handler convergence point: `handleDynamicFrictionResult` is the only
 * production site that knows WHICH element the out-of-bound errors were computed
 * for, so it stamps `curr`'s own identity (index × name) onto every unreferenced
 * [[SingularFlowResistanceCoeffError.HasElementRef]] error passing through it,
 * leaves the slot target untouched, never overwrites an already-carried
 * reference, and leaves non-referencable errors unchanged.
 *
 * The Valid path is intentionally not exercised: it computes real EN 13384
 * pressure formulas and needs a fully wired algorithm/density/velocity context;
 * stamping only happens on the Invalid path.
 */
class DynamicFrictionElementRefStampingSuite extends AnyFlatSpec with Matchers:

    case class TestShape(label: String)
    given Show[TestShape] = Show.show[TestShape](s => s"TestShape(${s.label})")

    /** The element being computed at the convergence point: #5, 'Coude 90°'. */
    private val currRef: ElementRef = ElementRef(PipeIdx(5), "Coude 90°")

    private object handler extends MecaFlu_13384_DynamicFriction[FlowOnlyPipeDescr_13384.PipeElDescr]:
        protected def en13384              : EN13384_1_A1_2019_Application_Alg                      = ???
        protected def density_for_pr_pu_pd : Density                                                = ???
        protected def velocity_for_pr_pu_pd: FlowVelocity                                           = ???
        protected def pReq                 : DraftCondition                                         = ???
        protected def curr                 : NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr] =
            NamedPipeElDescrG(
                PipeIdx                             (5                                                ),
                FluePipeT,
                "Coude 90°",
                FlowOnlyPipeDescr_13384.SplitMerge90(NbOfFlows(1), None, PipeShape.Circle(0.30.meters)),
                NbOfFlows                           (1                                                )
            )

        /** Direct drive of the convergence point (the handler members are protected). */
        def run(zetaResult: DynamicFrictionCoeffOp.Result): ValidatedNel[MecaFlu_Error, (Option[ζ], Pressure)] =
            handleDynamicFrictionResult(zetaResult, FluePipeT)

    private val slot3 = Some(SlotIndex.unsafe(3))

    private def valueOutOfBound(elRef: Option[ElementRef] = None): SingularFlowResistanceCoeffErrorI =
        ValueOutOfBound[TestShape](
            TestShape("AngleVifDe0A90"),
            FluePipeT,
            "gamma",
            100.0,
            0.0,
            90.0,
            elRef
        )(using summon[Show[TestShape]], slot3)

    private def ratioOutOfBound(elRef: Option[ElementRef] = None): SingularFlowResistanceCoeffErrorI =
        RadiusDiameterRatioOutOfBound[TestShape](
            TestShape("CoudeCourbe90"),
            FluePipeT,
            "R/Dh",
            3.3,
            0.0,
            2.0,
            elRef
        )(using summon[Show[TestShape]], slot3)

    private def firstError(res: ValidatedNel[MecaFlu_Error, (Option[ζ], Pressure)]): MecaFlu_Error =
        res match
            case Invalid(nel) => nel.head
            case _            => fail("expected an Invalid result")

    private def referencable(err: MecaFlu_Error): HasElementRef = err match
        case e: HasElementRef => e
        case other => fail(s"expected a HasElementRef error, got $other")

    "handleDynamicFrictionResult" should "stamp the current element onto an unreferenced ValueOutOfBound" in {
        referencable(firstError(handler.run(valueOutOfBound().invalidNel))).elRef shouldBe Some(currRef)
    }

    it should "stamp the current element onto an unreferenced RadiusDiameterRatioOutOfBound" in {
        referencable(firstError(handler.run(ratioOutOfBound().invalidNel))).elRef shouldBe Some(currRef)
    }

    it should "keep the slot target untouched while stamping" in {
        firstError                (handler.run(valueOutOfBound().invalidNel)).target shouldBe
            ErrorTarget.SlotTarget(SlotIndex.unsafe(3)                      )
    }

    it should "not overwrite an already-carried element reference" in {
        val carried = Some(ElementRef(PipeIdx(9), "Coude vif"))
        referencable(firstError(handler.run(ratioOutOfBound(carried).invalidNel))).elRef shouldBe carried
    }

    it should "return a non-referencable error unchanged" in {
        val err: SingularFlowResistanceCoeffErrorI =
            NoGivenRatio_Ld_Dh[TestShape](TestShape("CoudeCourbe90"), FluePipeT)(using
                summon[Show[TestShape]],
                slot3
            )
        firstError(handler.run(err.invalidNel)) shouldBe err
    }

end DynamicFrictionElementRefStampingSuite
