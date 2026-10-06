/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.OutsideAirLocationInHeater

import afpma.firecalc.i18n.LocalizedString

import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxContribution
import afpma.firecalc.engine.alg.en15544.FireboxKind
import afpma.firecalc.engine.alg.en15544.RemoteFireboxData
import afpma.firecalc.engine.api.WireConversions
import afpma.firecalc.engine.biblio.kov.firebox_emissions.Standing_Standard_Burning_Firebox
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.en15544.firebox.{AFPMA_PRSE, TraditionalFirebox}
import afpma.firecalc.engine.models.en15544.std.Firebox_15544

import cats.data.Validated

import coulomb.syntax.withUnit

import afpma.firecalc.firebox_calc_api.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Phase 2 IP-gate acceptance (ticket 12, checklist items 6 and 7): the
 * invariants the strict engine's registry/conversion/remote seam must hold for
 * the extraction to stay behaviour-preserving and cast-free.
 *
 * Complements — does not duplicate — `RemoteRegistrySuite` (which covers the
 * remote-BUILT registry: 6 entries, totality, order preservation, remote spy)
 * and `RemoteConstraintReportingSuite` (honest per-term reporting). This suite
 * pins the STATIC `FireboxEngineRegistry_Strict` itself, the DTO→model
 * conversion, the unsealed-model seam, the remote-backed swap contract, and
 * the wire-kind bijection.
 *
 * Runs on both platforms: the registry, conversion, and carriers are shared
 * (ticket 16), so every assertion pins the same behaviour on the JVM and
 * Scala.js — the two proprietary kinds are served by the generic remote-only
 * carriers on both.
 */
class Phase2InvariantAcceptanceSuite extends AnyFreeSpec with Matchers:

    // ── Minimal DTO fixtures (stub patterns per RemoteConstraintReportingSuite) ──

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

    private def stubEcolabeledDTO: Firebox.Ecolabeled =
        Firebox.Ecolabeled                    (
            heat_output_reduced                     = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            version                                 = Left("Version 1"),
            air_intake_shape                        = None,
            firebox_depth                           = 0.54.meters,
            firebox_width                           = 0.54.meters,
            firebox_height                          = 0.813.meters,
            height_of_first_row_of_air_injectors    = 0.10.meters,
            door_opening_width                      = 0.52.meters,
            glass_width                             = 0.50.meters,
            glass_height                            = 0.40.meters,
            ash_pit_height                          = 0.08.meters,
            air_manifold_height                     = 0.11.meters,
            firebox_floor_thickness                 = 0.08.meters,
            firebox_inner_wall_thickness            = 0.06.meters,
            firebox_outer_wall_thickness            = 0.06.meters,
            air_column_thickness                    = 0.035.meters,
            width_between_two_air_columns_sides     = 0.045.meters,
            width_between_two_air_columns_rear      = 0.045.meters,
            reinforcement_bars_offset_in_corners_R1 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R2 = 0.045.meters,
            reinforcement_bars_offset_in_corners_R3 = 0.045.meters,
            injector_height                         = 0.008.meters
        )

    private def stubTraditionalDTO: Firebox.Traditional =
        Firebox.Traditional                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            firebox_depth                         = 0.44.meters,
            firebox_width                         = 0.42.meters,
            firebox_height                        = 0.78.meters,
            height_of_lowest_opening              = 0.05.meters,
            pressure_loss_coefficient_from_door   = 0.3.unitless,
            total_air_intake_surface_area_on_door = 150.0.cm2,
            glass_width                           = 0.15.meters,
            glass_height                          = 0.20.meters
        )

    private def stubAfpmaPrseDTO: Firebox.AFPMA_PRSE =
        Firebox.AFPMA_PRSE                  (
            heat_output_reduced                   = HeatOutputReduced.NotDefined,
            outside_air_location_in_heater        = OutsideAirLocationInHeater.FromBottom,
            outside_air_conduit_shape             = PipeShape.Circle(10.0.cm),
            firebox_depth                         = 1.0.cm,
            firebox_width                         = 1.0.cm,
            firebox_height                        = 1.0.cm,
            height_of_first_row_of_air_injectors  = 1.0.cm,
            glass_width                           = 1.0.cm,
            glass_height                          = 1.0.cm,
            ash_pit_height                        = 1.0.cm,
            floor_thickness                       = 1.0.cm,
            combustion_air_manifold_height        = 1.0.cm,
            outside_air_inlet_lip                 = 1.0.cm,
            height_of_air_feed_to_columns         = 1.0.cm,
            number_of_air_columns_feeding_firebox = 1,
            number_of_air_columns_feeding_door    = 1
        )

    private def stubSingleTestedDTO: Firebox.SingleTested =
        Firebox.SingleTested                             (
            reference                              = "test",
            type_of_appliance                      = TypeOfAppliance.WoodLogs,
            test_standard                          = Firebox.TestStandard.EN_15250,
            firebox_depth                          = 1.0.cm,
            firebox_width                          = 1.0.cm,
            firebox_height                         = 1.0.cm,
            ash_pit_height                         = 1.0.cm,
            is_glass_surface_ratio_below_one_fifth = false,
            glass_area                             = 1.0.cm2,
            mean_firebox_temperature               = None,
            t_burnout                              = Some(20.0.degreesCelsius),
            efficiency_nominal                     = 0.8.percent,
            efficiency_reduced                     = None,
            heat_output_reduced                    = HeatOutputReduced.FromTypeTest(1.0.kW),
            minimum_fuel_mass                      = None,
            maximum_fuel_mass                      = 1.0.kg,
            air_fuel_ratio_nominal                 = 1.0.unitless,
            air_fuel_ratio_lowest                  = None,
            co2_dry_nominal                        = 0.1.percent,
            co2_dry_lowest                         = None,
            pellets_load_burn_duration             = None,
            emissions_values                       = stubEmis,
            image                                  = None
        )

    // Door15a DTO fields per modules/dto/src/main/scala/afpma/firecalc/dto/v5/Firebox_V4.scala —
    // Firebox_V4.Door15aFirebox_Catalog.
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
            pressure_loss_table_raw     = "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n10\t4\t3\t2\t1\n25\t22\t20\t18\t16",
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

    /** A `Firebox_15544` subtype declared INSIDE the defining compilation unit. */
    private def stubCustomForLab: Firebox_15544.Traditional.CustomForLab =
        Firebox_15544.Traditional.CustomForLab               (
            reference                = LocalizedString(_ => "phase2-invariant"),
            type_of_appliance        = TypeOfAppliance.WoodLogs,
            emissions_values         = Standing_Standard_Burning_Firebox,
            pn_reduced               = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
            dimensions               = Firebox_15544.Dimensions(
                base   = Firebox_15544.Dimensions.Base.Squared(
                    width = 39.0.cm,
                    depth = 55.0.cm
                ),
                height = 58.3.cm
            ),
            glass_area               = 200.0.cm2,
            height_of_lowest_opening = 5.0.cm
        )

    /** Marker remote data: non-empty verdict list + Without contributions. */
    private def remoteDataWith(verdicts: List[ConstraintVerdict]): RemoteFireboxData[
        FireboxPipe_Module_15544.FullDescr,
        CombustionAirPipe_Module_15544.FullDescr
    ] =
        RemoteFireboxData      (
            fireboxContribution       = Validated.valid(FireboxContribution.Without),
            combustionAirContribution = Validated.valid(FireboxContribution.Without),
            constraints               = verdicts,
            reportData                = FireboxReportData(
                fireboxDescription     = WireTable(header = None, colHeaders = None, rows = Nil),
                emissionsAndEfficiency = WireTable(header = None, colHeaders = None, rows = Nil),
                combustionAirPipeRows  = Nil,
                fireboxPipeRows        = Nil,
                ecolabeledOutputs      = None
            )
        )

    private def entryOf(kind: FireboxKind) =
        FireboxEngineRegistry_Strict.all.find(_.kind == kind).get

    "Invariant 7 — registry order is the established pre-extraction order" in {
        // The established order mirrors the retired `FireboxConstraintsResolver`'s
        // match-arm order (registry scaladoc): `withEngine` is `collectFirst`,
        // so overlapping `TypeTest`s resolve by position — reordering is a
        // behaviour change, not a style change. A regression that re-sorts this
        // list (e.g. alphabetically, or by enum-declaration order) must fail
        // here.
        FireboxEngineRegistry_Strict.all.map(_.kind) shouldBe List(
            FireboxKind.Ecolabeled,
            FireboxKind.Traditional,
            FireboxKind.AFPMA_PRSE,
            FireboxKind.SingleTested,
            FireboxKind.Door15aCatalog,
            FireboxKind.CustomForLab
        )
        // Totality: no kind dropped, none added (the registry's `checkTotality()`
        // locally block asserts the same at construction; pinning the size
        // against the shared taxonomy keeps a future closed-kind addition
        // visible here).
        FireboxEngineRegistry_Strict.all.size shouldBe FireboxKind.closed.size
    }

    "Invariant 7 — the registry order is identical on the JVM and Scala.js" in {
        // Ticket 16 made the strict registry shared (single source in
        // src/main), so the JVM and the browser register the SAME six entries
        // in the SAME order — the proprietary kinds through the generic
        // remote-only carriers on both platforms. The order proven above is
        // exactly a permutation covering `FireboxKind.closed`, with the two
        // proprietary kinds in their established first-and-fifth slots.
        val order = FireboxEngineRegistry_Strict.all.map(_.kind)
        order.toSet shouldBe FireboxKind.closed
        order.distinct shouldBe order
        order.indexOf(FireboxKind.Ecolabeled) shouldBe 0
        order.indexOf(FireboxKind.Door15aCatalog) shouldBe 4
    }

    "Invariants 1+2 — registration stays contravariant with ClassTag evidence" in {
        // (The ticket's "ClassTag evidence" is the `TypeTest` that replaced it —
        // see FireboxEngine.scala — FireboxEngineAny.toRemoteBacked: `TypeTest` supplies the runtime
        // evidence erasure destroys, where `ClassTag.unapply` could only test
        // the class component of a type.)
        //
        // One engine model per DTO subtype, built through the platform's own
        // conversion so each model has exactly the type its entry registers.
        val models: List[(FireboxKind, Firebox_15544)] = List(
            FireboxKind.Ecolabeled     -> FireboxModelConversion_Strict.toEngineModel(stubEcolabeledDTO),
            FireboxKind.Traditional    -> FireboxModelConversion_Strict.toEngineModel(stubTraditionalDTO),
            FireboxKind.AFPMA_PRSE     -> FireboxModelConversion_Strict.toEngineModel(stubAfpmaPrseDTO),
            FireboxKind.SingleTested   -> FireboxModelConversion_Strict.toEngineModel(stubSingleTestedDTO),
            FireboxKind.Door15aCatalog -> FireboxModelConversion_Strict.toEngineModel(stubDoor15aDTO)
        )

        models.foreach: (expectedKind, model) =>
            // Defined at its own firebox type ...
            entryOf(expectedKind).narrow(model).isDefined shouldBe true
            // ... and None at every OTHER registered firebox type — the full
            // 5×6 matrix (RemoteRegistrySuite checks only the Eco/Door pair):
            // the evidence is real, so each DTO subtype resolves to EXACTLY one
            // entry. Had `narrow` been an erased `isInstanceOf`, every entry
            // would accept every model and `collectFirst` would always pick the
            // first.
            FireboxEngineRegistry_Strict.all
                .filterNot(_.kind == expectedKind)
                .foreach: other =>
                    other.narrow(model).isEmpty shouldBe true
            // `engineFor` (the resolve-and-apply resolver) agrees.
            val resolved = FireboxEngineRegistry_Strict.engineFor(model)
            resolved.isRight shouldBe true
            resolved.toOption.get.engine.kind shouldBe expectedKind

        // The case RemoteRegistrySuite does NOT cover: resolution against the
        // STATIC strict registry (it exercises only the remote-built one).
        // Both entries are the generic remote-only carriers (ticket 16); kind
        // only, so the assertion holds on both platforms.
        entryOf(FireboxKind.Ecolabeled).kind shouldBe FireboxKind.Ecolabeled
        entryOf(FireboxKind.Door15aCatalog).kind shouldBe FireboxKind.Door15aCatalog
    }

    "Invariant 9 — DTO→model conversion is total and cast-free over the five sealed subtypes" in {
        // The compile-time exhaustiveness of the conversion is the primary
        // proof (JVM: the chimney `Transformer[Firebox, Firebox_15544]`
        // derivation over the sealed DTO with no catch-all; JS: the exhaustive
        // five-arm match in main-js FireboxModelConversion_Strict). This test
        // pins the mapping's behaviour: each DTO subtype lands on its expected
        // engine model. Adding a sixth DTO subtype without a conversion arm
        // breaks compilation; silently misrouting one breaks this test.
        FireboxModelConversion_Strict.toEngineModel(stubTraditionalDTO) shouldBe a[TraditionalFirebox]
        FireboxModelConversion_Strict.toEngineModel(stubAfpmaPrseDTO) shouldBe a[AFPMA_PRSE]
        FireboxModelConversion_Strict.toEngineModel(stubSingleTestedDTO) shouldBe a[Firebox_15544.SingleTested]

        // Ecolabeled and Door15a convert to the generic remote-only carriers
        // (ticket 16: shared, on both platforms). The kind's registry entry
        // carries exactly that FB type, so narrow-acceptance pins the mapping.
        val ecoModel = FireboxModelConversion_Strict.toEngineModel(stubEcolabeledDTO)
        ecoModel shouldBe a[Firebox_15544]
        entryOf(FireboxKind.Ecolabeled).narrow(ecoModel).isDefined shouldBe true

        val doorModel = FireboxModelConversion_Strict.toEngineModel(stubDoor15aDTO)
        doorModel shouldBe a[Firebox_15544]
        entryOf(FireboxKind.Door15aCatalog).narrow(doorModel).isDefined shouldBe true
    }

    "Invariant 8 — Firebox_15544 remains unsealed" in {
        // `Firebox_15544` is declared in
        // modules/engine-15544-common/src/main/scala/afpma/firecalc/engine/models/en15544/
        // std.scala — Firebox_15544 (trait + companion)
        // and is NOT sealed: the two proprietary kinds' public engine models
        // are the generic remote-only carriers, defined OUTSIDE that
        // compilation unit (`RemoteOnlyFireboxes_Strict`), yet are
        // `Firebox_15544`. (The proprietary Austrian models live privately.)
        //
        // Proof: the registry's Ecolabeled and Door15a entries carry FB types
        // that are not among the subtypes declared in the std.scala source file
        // (`SingleTested`, `Traditional.CustomForLab`, and the `Traditional` /
        // `CertifiedDesign` branch traits). The proprietary entries accept
        // models that every std.scala-subtype entry rejects, and reject a
        // std.scala-declared subtype in turn — disjoint FB types, all accepted
        // where `Firebox_15544` is required.
        val ecoModel  = FireboxModelConversion_Strict.toEngineModel(stubEcolabeledDTO)
        val doorModel = FireboxModelConversion_Strict.toEngineModel(stubDoor15aDTO)

        val ecoEntry  = entryOf(FireboxKind.Ecolabeled)
        val doorEntry = entryOf(FireboxKind.Door15aCatalog)
        ecoEntry.narrow(ecoModel).isDefined shouldBe true
        doorEntry.narrow(doorModel).isDefined shouldBe true

        // The entries whose FB IS declared in std.scala (SingleTested,
        // CustomForLab) reject the proprietary models ...
        val stdEntries = FireboxEngineRegistry_Strict.all.filter(e =>
            e.kind == FireboxKind.SingleTested || e.kind == FireboxKind.CustomForLab
        )
        stdEntries.foreach: e =>
            e.narrow(ecoModel).isEmpty shouldBe true
            e.narrow(doorModel).isEmpty shouldBe true

        // ... and the proprietary entries reject a std.scala-declared subtype.
        val customForLab = stubCustomForLab
        ecoEntry.narrow(customForLab).isEmpty shouldBe true
        doorEntry.narrow(customForLab).isEmpty shouldBe true

        // Both worlds are accepted where `Firebox_15544` is required — the
        // registry resolves an out-of-unit model exactly like an in-unit one.
        FireboxEngineRegistry_Strict.engineFor(ecoModel).isRight shouldBe true
        FireboxEngineRegistry_Strict.engineFor(customForLab).isRight shouldBe true
    }

    "Invariants 4+9 — remote-backed dispatch serves stored data without casts or recomputation" in {
        given Locale = Locale(Languages.En)

        val verdicts = List(
            ConstraintVerdict(
                ConstraintTerm.MB,
                ConstraintTerm.MB.toString,
                ConstraintDescriptor.Opaque,
                ConstraintOutcome.Satisfied
            ),
            ConstraintVerdict(
                ConstraintTerm.GlassArea,
                ConstraintTerm.GlassArea.toString,
                ConstraintDescriptor.Opaque,
                ConstraintOutcome.Violated("TestCode", "marker violation", blocking = true)
            )
        )
        val data     = remoteDataWith(verdicts)

        val ecoEntry = entryOf(FireboxKind.Ecolabeled)
        val swapped  = ecoEntry.toRemoteBacked(data)

        // Kind preserved, and the entry still narrows (FB + TypeTest carried
        // over verbatim — the swap changes only the engine that answers).
        swapped.kind shouldBe FireboxKind.Ecolabeled
        val ecoModel = FireboxModelConversion_Strict.toEngineModel(stubEcolabeledDTO)
        val narrowed = swapped.narrow(ecoModel)
        narrowed.isDefined shouldBe true
        val fb       = narrowed.get

        // The swapped engine returns EXACTLY the stored data ...
        swapped.instance.fireboxContribution(fb) shouldBe data.fireboxContribution
        swapped.instance.combustionAirContribution(fb) shouldBe data.combustionAirContribution
        val fctx = FireboxConstraintContext(mB = 0.0.kg, flow_rate = None, airIntakePipeShape = None)
        swapped.instance.constraints(fb, ctx = None, fctx = fctx) shouldBe verdicts

        // ... for ANY firebox argument: it ignores the argument (that is the
        // contract — stored data, no recomputation). A second, geometrically
        // different firebox of the same FB type gets identical answers.
        val ecoModel2 = FireboxModelConversion_Strict.toEngineModel(
            stubEcolabeledDTO.copy(firebox_depth = 0.99.meters, firebox_width = 0.31.meters)
        )
        val fb2       = swapped.narrow(ecoModel2).get
        swapped.instance.fireboxContribution(fb2) shouldBe data.fireboxContribution
        swapped.instance.combustionAirContribution(fb2) shouldBe data.combustionAirContribution
        swapped.instance.constraints(fb2, ctx = None, fctx = fctx) shouldBe verdicts

        // The per-term algebra absence is typed, not stubbed: a remote-backed
        // entry offers no per-term surface (nothing honest to fold).
        swapped.constraintsAlg shouldBe None
    }

    "Strict seam — the normative request carries WireStandardVariant.Strict and the kind mapping is a total bijection" in {
        // Built the way the UI/PDF builders do (cf.
        // modules/ui/src/main/scala/afpma/firecalc/ui/remote/RemoteFireboxPrefetch.scala —
        // RemoteFireboxPrefetch.prefetchFireboxCalc):
        // apiVersion v1, standard = WireStandardVariant.Strict.
        val ecoRequest = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireConversions.toWireFireboxKind(FireboxKind.Ecolabeled),
            firebox            = stubEcolabeledDTO,
            params             = Standard15544Params(m_B = None, n_min = None),
            airIntakePipeShape = None,
            locale             = "fr",
            standard           = WireStandardVariant.Strict
        )
        ecoRequest.standard shouldBe WireStandardVariant.Strict
        ecoRequest.kind shouldBe WireFireboxKind.Ecolabeled

        val doorRequest = FireboxCalcRequest(
            apiVersion         = ApiVersion.v1,
            kind               = WireConversions.toWireFireboxKind(FireboxKind.Door15aCatalog),
            firebox            = stubDoor15aDTO,
            params             = Standard15544Params(m_B = None, n_min = None),
            airIntakePipeShape = None,
            locale             = "fr",
            standard           = WireStandardVariant.Strict
        )
        doorRequest.standard shouldBe WireStandardVariant.Strict
        doorRequest.kind shouldBe WireFireboxKind.Door15aCatalog

        // The total bijection that keeps DTO kind and wire kind from ever
        // disagreeing (WireConversions.scala — toWireFireboxKind / fromWireFireboxKind): both round trips are the
        // identity. `BlackBox` is excluded on both sides by construction (open
        // family, no wire case). A dropped or renamed case on either side makes
        // one of these round trips fail — or the match non-exhaustive at compile
        // time.
        WireFireboxKind.values.foreach: w =>
            WireConversions.toWireFireboxKind(WireConversions.fromWireFireboxKind(w)) shouldBe w
        FireboxKind.closed.foreach    : k =>
            WireConversions.fromWireFireboxKind(WireConversions.toWireFireboxKind(k)) shouldBe k
    }

    "No MCE HTTP registration — the strict registry serves no MCE entry and the MCE module registers nothing HTTP-shaped" in {
        // Behavioural, registry-scoped: every entry's kind is one of the strict
        // taxonomy's closed kinds — the registry serves nothing outside it. The
        // MCE registry is a SEPARATE object over the thermal `_13384` descriptor
        // pair (modules/engine-15544-mce/.../FireboxEngineRegistry_MCE.scala),
        // which this registry's `FireboxEngineAny[FD, CD]` type cannot even
        // name — so no MCE entry can appear here.
        FireboxEngineRegistry_Strict.all.foreach: entry =>
            FireboxKind.closed should contain(entry.kind)
        FireboxEngineRegistry_Strict.all.map(_.kind).toSet shouldBe FireboxKind.closed

        // Evidence for the posture (recorded, not asserted on source text):
        // no MCE serving path; exactly one documented refusal site — the
        // private calc-core checkpoint refuses every non-strict standard with
        // HTTP 400 `unsupported_standard` (ErrorResponseEnvelope; the shared-
        // contract variant `FireboxCalcServerError.UnsupportedStandard` ships
        // in `firebox_calc_api`, ADR-0011). The public repository serves no
        // Austrian calculation at all: its remote-only carriers refuse
        // locally resolved Austrian kinds (ADR-0010) — a separate, locally
        // recorded refusal, not a serving path either.
        //   grep -rE "HttpRoutes|http4s|FireboxCalcRequest|WireStandardVariant|route" modules/engine-15544-mce
        //     → zero source hits: the MCE engine module registers nothing HTTP-shaped.
        //   grep -r "WireStandardVariant\.Mce" modules
        //     → zero source hits: no public code path constructs or serves an
        //     MCE-variant request; the enum case exists for the wire contract
        //     only, and the strict-only deployment refuses it at the seam.
        //   grep -rE "Mce|MCE|mce" <private repository>/modules/firebox_calc_server
        //     → no MCE serving path: the private calculation server wires
        //     exactly one engine, Strict (FireboxCalcRoutes builds the Strict
        //     service); the only standard-related code is the documented
        //     refusal site above.
        succeed
    }
