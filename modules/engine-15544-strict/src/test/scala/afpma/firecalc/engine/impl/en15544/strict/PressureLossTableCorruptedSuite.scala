/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.CombustionAirPipeT
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.PressureLossMustBeDefined
import afpma.firecalc.engine.standard.PressureLossTableCorrupted
import afpma.firecalc.engine.standard.PressureLossTableError
import afpma.firecalc.engine.standard.given
import afpma.firecalc.i18n.ShowUsingLocale

import cats.data.Validated

import coulomb.syntax.withUnit

import io.taig.babel.Locale
import io.taig.babel.Locales

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * The dedicated failure surface for an unreadable pressure-loss table
 * (`pressure_loss_table_raw`).
 *
 * A stored value that is not a canonical TSV table — the `;`/`|` storage form
 * reaching the engine without the dto decode law, a truncated or garbage
 * payload, a header with no usable column or with no data row — must be
 * reported as [[PressureLossTableCorrupted]] ("the table cannot be read"),
 * never as a [[PressureLossTableError]] interpolation miss ("the table is fine,
 * it just has no value here"). The precedence around it is unchanged: no
 * `load_size_nominal` still means [[PressureLossMustBeDefined]], a blank field
 * is still [[PressureLossTableError]], and a readable table whose (mB, SB)
 * target lies outside it is still [[PressureLossTableError]].
 */
class PressureLossTableCorruptedSuite extends AnyFreeSpec with Matchers:

    // ── Fixtures (stub pattern per RemoteRegistrySuite) ──────────────────

    private val canonicalTable: String =
        "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n10\t4\t3\t2\t1\n25\t22\t20\t18\t16"

    /** The stored form of [[canonicalTable]]: `;` fields, `|` rows, no tab, no newline. */
    private val storageFormTable: String =
        "mb_in_kg/sb_in_cm;1.6;2.4;3.2;4.0|10;4;3;2;1|25;22;20;18;16"

    private def stubEmis: EmissionsAndEfficiencyValues_DTO =
        EmissionsAndEfficiencyValues_DTO               (
            firebox_name                = "test",
            accredited_or_notified_body = "test",
            test_reports                = Nil,
            emissions_values            = EmissionValues_DTO(
                co   = TestEmissionValue_DTO(PolluantName.CO, None, "", 0.0.percent),
                dust = TestEmissionValue_DTO(PolluantName.Dust, None, "", 0.0.percent),
                ogc  = TestEmissionValue_DTO(PolluantName.OGC, None, "", 0.0.percent),
                nox  = TestEmissionValue_DTO(PolluantName.NOx, None, "", 0.0.percent)
            )
        )

    private def stubDoor15aDTO: Firebox.Door15aFirebox_Catalog =
        Firebox.Door15aFirebox_Catalog                  (
            reference                   = "test",
            firebox_depth               = 1.0.cm,
            firebox_width               = 1.0.cm,
            firebox_height              = 1.0.cm,
            load_size_nominal           = Some(10.0.kg),
            sb                          = 2.4.withUnit[Centimeter],
            sb_min                      = None,
            sb_max                      = None,
            mb_min                      = None,
            mb_max                      = None,
            pressure_loss_table_raw     = canonicalTable,
            expectedAirIntakePipeShapes = List(PipeShape.Circle(10.0.cm)),
            actualAirIntakePipeShape    = PipeShape.Circle(10.0.cm),
            co2_dry_nominal             = 0.1.percent,
            co2_dry_lowest              = None,
            emissions_values            = stubEmis,
            glass_area                  = 1.0.cm2,
            height_of_lowest_opening    = 1.0.cm,
            heat_output_reduced         = HeatOutputReduced.NotDefined,
            image                       = None
        )

    private def dtoWith(rawTable: String): Firebox.Door15aFirebox_Catalog =
        stubDoor15aDTO.copy(pressure_loss_table_raw = rawTable)

    /** The generic Door15a carrier's combustion-air contribution — the raise site. */
    private def combustionAir(dto: Firebox.Door15aFirebox_Catalog) =
        RemoteOnlyFireboxes_Strict.door15aCatalogRemoteOnlyEngine
            .combustionAirContribution(RemoteOnlyFireboxes_Strict.Door15aCatalogCarrier(dto))

    private def errorOf(dto: Firebox.Door15aFirebox_Catalog): IncrementalValidation_Error =
        combustionAir(dto) match
            case Validated.Invalid(errors) => errors.head
            case Validated.Valid(value)    => fail(s"expected an invalid combustion-air contribution, got $value")

    private def showEn(err: IncrementalValidation_Error): String =
        given Locale = Locales.en
        ShowUsingLocale[IncrementalValidation_Error].show(err)

    private def showFr(err: IncrementalValidation_Error): String =
        given Locale = Locales.fr
        ShowUsingLocale[IncrementalValidation_Error].show(err)

    "PressureLossTableCorrupted" - {

        "is reported for the ';'/'|' storage form reaching the engine undecoded" in {
            errorOf(dtoWith(storageFormTable)) shouldBe PressureLossTableCorrupted(CombustionAirPipeT)
        }

        "is reported for a garbage payload" in {
            errorOf(dtoWith("hello")) shouldBe PressureLossTableCorrupted(CombustionAirPipeT)
        }

        "is reported for a truncated payload with a header but no data row" in {
            errorOf(dtoWith("mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0")  ) shouldBe
                PressureLossTableCorrupted(CombustionAirPipeT)
            errorOf(dtoWith("mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n")) shouldBe
                PressureLossTableCorrupted(CombustionAirPipeT)
        }

        "is reported for a header row with no usable column" in {
            errorOf(dtoWith("mb_in_kg/sb_in_cm\t\t\n10\t4")) shouldBe PressureLossTableCorrupted(CombustionAirPipeT)
        }

        "renders the repair instruction in English" in {
            showEn(errorOf(dtoWith(storageFormTable))) shouldBe
                "The pressure loss table (pressure_loss_table_raw) is corrupted. Send the file to AFPMA " +
                "for repair, or re-import the firebox from the Catalog."
        }

        "renders the repair instruction in French" in {
            showFr(errorOf(dtoWith(storageFormTable))) shouldBe
                "La table de perte de charge (pressure_loss_table_raw) est corrompue. Envoyez le fichier à " +
                "l'AFPMA pour réparation, ou réimportez le foyer depuis le Catalogue."
        }
    }

    "A readable pressure-loss table" - {

        "keeps the canonical TSV table valid" in {
            combustionAir(dtoWith(canonicalTable)).isValid shouldBe true
        }

        "keeps PressureLossTableError when the (mB, SB) target is outside the table" in {
            errorOf(dtoWith(canonicalTable).copy(sb = 10.0.withUnit[Centimeter])) match
                case e: PressureLossTableError => e.sectionTyp shouldBe CombustionAirPipeT
                case other => fail(s"expected a readable-table interpolation error, got $other")
        }

        "keeps PressureLossTableError for a blank field with a load set (blank is not corruption)" in {
            errorOf(dtoWith("")) shouldBe a[PressureLossTableError]
        }
    }

    "A missing load_size_nominal" - {

        "keeps PressureLossMustBeDefined for a blank table" in {
            errorOf(dtoWith("").copy(load_size_nominal = None)) shouldBe PressureLossMustBeDefined(CombustionAirPipeT)
        }

        "keeps PressureLossMustBeDefined for a corrupted table" in {
            errorOf(dtoWith("hello").copy(load_size_nominal = None)) shouldBe
                PressureLossMustBeDefined(CombustionAirPipeT)
        }
    }

end PressureLossTableCorruptedSuite
