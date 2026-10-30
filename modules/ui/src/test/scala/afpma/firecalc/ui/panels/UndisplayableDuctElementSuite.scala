/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.instances.V7Instances.given

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import io.circe.Decoder
import io.circe.parser.decode
import io.circe.yaml.scalayaml.parser as yamlParser

import io.taig.babel.Locales

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Regression tests for the render decision on an undisplayable element kind: the
 * failure is named, never a degraded row.
 *
 * The committed fixture below is the v7 wire form of `AddSectionSloppedForceManualElevationGain`
 * — the shape a migrated or foreign project carries (no UI menu produces it; the {value, unit}
 * pair is the QtyD encoding used by project files). It is decoded through the real project
 * codecs, then fed to the exact render decision the three pipe panels' arms call
 * (`UndisplayableDuctElement.failure`; their `handleCase` arms pass the matched element and
 * `models.currentI18N`). The panel chains themselves cannot run in this environment (no DOM),
 * so this pins the verdict, not Laminar wiring: for such an element the panels throw the named,
 * translated `UndisplayableDuctElementError` — never `NotImplementedError` — and the message is
 * resolved per-locale through the typed layer, which is what keeps the global error dialog's
 * existing message-keyed suppression at one dialog per episode.
 */
class UndisplayableDuctElementSuite extends AnyFreeSpec with Matchers:

    // The committed fixture: valid v7 wire data, as found in migrated / foreign state.
    private val fixtureYaml =
        """- AddSectionSloppedForceManualElevationGain:
          |    name: "forced-elevation-gain section"
          |    length: { value: "1.50", unit: "meter" }
          |    elevation_gain: { value: "0.50", unit: "meter" }
          |""".stripMargin

    private def decodeElements[X: Decoder]: List[X] =
        yamlParser.parse(fixtureYaml) match
            case Left(err)   => fail(s"the committed fixture failed to parse: $err")
            case Right(json) =>
                decode[List[X]](json.noSpaces) match
                    case Left(err)  => fail(s"the committed fixture failed to decode: $err")
                    case Right(els) => els

    // Decoding the fixture through each pipe family's real element codec and narrowing to the
    // fixture's case is itself the proof that the element is valid wire data for that family;
    // a wrong kind or count fails the test.

    private def thermalElement(): AddThermalPipeElement_13384.AddSectionSloppedForceManualElevationGain =
        decodeElements[AddThermalPipeElement_13384] match
            case (el: AddThermalPipeElement_13384.AddSectionSloppedForceManualElevationGain) :: Nil => el
            case other                                                                              => fail(s"expected one thermal 13384 element from the fixture, got: $other")

    private def flowOnly13384Element(): AddFlowOnlyPipeElement_13384.AddSectionSloppedForceManualElevationGain =
        decodeElements[AddFlowOnlyPipeElement_13384] match
            case (el: AddFlowOnlyPipeElement_13384.AddSectionSloppedForceManualElevationGain) :: Nil => el
            case other                                                                               => fail(s"expected one flow-only 13384 element from the fixture, got: $other")

    private def flue15544Element(): AddFlowOnlyPipeElement_15544.AddSectionSloppedForceManualElevationGain =
        decodeElements[AddFlowOnlyPipeElement_15544] match
            case (el: AddFlowOnlyPipeElement_15544.AddSectionSloppedForceManualElevationGain) :: Nil => el
            case other                                                                               => fail(s"expected one flue 15544 element from the fixture, got: $other")

    "the fixture decodes as valid v7 wire data in every pipe family that can carry it" - {
        "thermal 13384" in {
            thermalElement().name shouldBe "forced-elevation-gain section"
        }
        "flow-only 13384" in {
            flowOnly13384Element().name shouldBe "forced-elevation-gain section"
        }
        "flue 15544" in {
            flue15544Element().name shouldBe "forced-elevation-gain section"
        }
    }

    "the render verdict for such an element is the named failure, never NotImplementedError" - {
        val enI18N = I18N_UI(using Locales.en)

        def verdictIsNamedAndTranslated(
            elem: UndisplayableDuctElement.SectionForceManualElevationGain
        ): Unit =
            val err = intercept[UndisplayableDuctElementError] {
                UndisplayableDuctElement.failure(elem)(using enI18N)
            }
            (err: Throwable) should not be a[NotImplementedError]
            // The old raw crash was NotImplementedError("an implementation is missing").
            err.getMessage should not be "an implementation is missing"
            err.getMessage shouldBe enI18N.errors.undisplayable_section_force_manual_elevation_gain
            // The message names the element kind in the user's language.
            err.getMessage should include("forced manual elevation gain")

        "thermal 13384 panel verdict" in {
            verdictIsNamedAndTranslated(thermalElement())
        }
        "flow-only 13384 panel verdict" in {
            verdictIsNamedAndTranslated(flowOnly13384Element())
        }
        "flue 15544 panel verdict" in {
            verdictIsNamedAndTranslated(flue15544Element())
        }

        "the message resolves through the translation layer per locale, not a hardcoded string" in {
            val frI18N = I18N_UI(using Locales.fr)
            val err    = intercept[UndisplayableDuctElementError] {
                UndisplayableDuctElement.failure(thermalElement())(using frI18N)
            }
            err.getMessage shouldBe frI18N.errors.undisplayable_section_force_manual_elevation_gain
            err.getMessage should include("élévation verticale")
            err.getMessage should not be enI18N.errors.undisplayable_section_force_manual_elevation_gain
        }
    }

end UndisplayableDuctElementSuite
