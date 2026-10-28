# Engine — EN 13384 / EN 15544 sizing & evaluation

The engine context: shared computation core plus the per-standard implementations that
size and validate masonry heaters against EN 13384 and EN 15544. Covers `engine-kernel`,
`engine`, `engine-13384-common/strict`, `engine-15544-common/strict/mce/labo`,
`engine-validation`, `fdim`, and the `firebox_calc_api` wire vocabulary.

## Structure

One context, one language core. The EN 13384 and EN 15544 sides share their vocabulary
(operating point, term/constraint, pipe elements, topology); the vocabularies fork at the
solver (`MecaFlu`) and the firebox description seam (ADR-0001), not at the language.
Terms that only one standard speaks are grouped under per-standard subheadings below.

**Language convention: English only.** Preferred terms and `_Avoid_` synonyms in this
glossary are English; localised UI strings are tracked in `modules/i18n/`, not here.

## Language

### Smoke-path elements

**Chimney connector**:
The masonry gas channel inside the heater body, carrying combustion products from the
firebox exit toward the channel; the run whose length L_Z (EN 15544 §3.12) measures.
Despite the name, it is not a connector — it is the heater's own flue passage.
_Avoid_: flue, channel (for this element)

**Channel**:
The connecting flue between the heater and the chimney; exists only in EN 13384
configurations — EN 15544 sizing never sees one.
_Avoid_: connector, connecting flue (as a term)

**Chimney**:
The permanent installation's gas-conveying duct discharging to the exterior; exists only
in EN 13384 configurations.

### Chain topology

**Head region**:
The prefix of a post-firebox chain lying before the terminal channel; it ends with a
chimney connector when non-empty, may be empty (the chain is then simply unspecified), or
may be a lone no-flue head.
_Avoid_: using it for the chimney-connector prefix the UI computes (a different concept)

**No-flue head**:
A head region stating explicitly — from the firebox's contribution — that this firebox has
no downstream flue runs: its gases discharge straight into the terminal channel or chimney.
Not the same statement as an empty head region, which leaves the chain unspecified.
_Avoid_: empty head (for this case)

**Chimney-connector prefix**:
The index span [0 … last chimney-connector slot] that the DTO/UI compute for head-region
membership. It ignores NoFlue slots trailing the last chimney connector, so it can be
smaller than the grammatical head region; only the head region carries normative meaning.
_Avoid_: head region

**Terminal channel**:
The mandatory single channel slot between the head region and the chimney; its
descriptor may be empty.

### Air-path elements

**Air intake**:
The opening or duct admitting outside air to the heater's room-side air system, at
ambient pressure. EN 13384 models it as a chain element fed at ambient pressure; EN 15544
never models the duct itself, constraining only its shape and size from the firebox side.
_Avoid_: air duct, combustion air duct

**Combustion air**:
The air conveyed to the firebox for combustion; modelled as the duct between the air
intake and the firebox.
_Avoid_: air duct

**Air space**:
The ventilated void around a chimney inside the masonry body (EN 13384 / DTU 24.1), with
optionally partially opened vents.
_Avoid_: air gap (for this feature)

**Air gap**:
The cavity between the heater surface and its facing (EN 15544 facing types).
_Avoid_: air space (for this feature)

**Air column**:
A combustion-air riser of the UZ37/Ecolabeled kind, carrying outside air up to the firebox
slots.
_Avoid_: air gap, air space (for this feature)

### Firebox

**Firebox**:
The physical combustion chamber of the heater, modelled as the gas-path element fed by the
air system and discharging into the chimney connector. The appliance as a whole is the
heater, never a "firebox".
_Avoid_: stove, heater (for this element)

**Firebox model**:
The physics-and-parameter hierarchy that determines how a firebox is sized: traditional,
single-tested, or certified-design (EN 15544 §4.2).
_Avoid_: firebox type, firebox class

**Firebox kind**:
The closed dispatch registry for fireboxes: six product kinds plus an open black-box kind
for anything computed remotely. Kind says which registry entry handles a firebox; model
says which physics applies. Kinds are named by their per-layer identifiers (code
constructor names, wire strings and YAML wrapper keys per ADR-0006, engine-source config
keys) — never by translated English names in specs, issues, or docs.
_Avoid_: firebox type, firebox class, prose translations of kind identifiers

#### Firebox geometry (EN 15544 §3)

**Firebox base area (A_BR)**:
The floor area enclosed by the firebox interior walls.
_Avoid_: "base" unqualified — a bare "Base" is a geometry coordinate, not this area

**Required firebox surface**:
The combustion-chamber surface the standard requires: 900 × m_B. Distinct from the
constructed surface below despite the shared symbol in the papers.
_Avoid_: O_BR unqualified

**Constructed firebox internal surface**:
The actual internal surface of the combustion chamber, measured as if the firebox door
were closed. Denominator of the glazing cap.
_Avoid_: O_BR unqualified

### Topology operations

**Split**:
Declaring one flue cross-section as several parallel flows.
_Avoid_: dividing, branching (for this)

**Merge**:
Bringing parallel flows back into a single cross-section.
_Avoid_: joining, combining (for this)

**Number of flows**:
The flow count active in a split segment. Flow-area conservation across split/merge is
enforced only where enabled — it is an optional constraint, not an invariant.

### Computation variants

**Strict variant**:
The normative EN 15544 path: sizing and validation computed from flow physics and the
EN 15544 §4 constraints only, with no thermal modelling of the firebox.

**MCE variant**:
An EN 15544 sizing run in which the firebox's thermal behaviour is estimated rather than
tested: the EN 13384 thermal solver is applied and efficiency is derived from burn-out
time via BS845. "MCE" is an opaque legacy label and is never expanded (decision
2026-10-28).
_Avoid_: expanding MCE, e.g. "Maximum Credible Event"

**Labo variant**:
The MCE variant shaped for and exposed to the laboratory-testing workflow.

**Fluid-mechanics solver (MecaFlu)**:
The pressure-balance machinery that walks the gas path and produces the draft result. It
exists as two variants — thermal and flow-only — so "MecaFlu" names a family, not one
algorithm. There is likewise no single "the" firebox pressure: the value depends on the
variant and the operating point (ADR-0001).
_Avoid_: "the MecaFlu algorithm"

**Description seam**:
The boundary at which a firebox contributes either a description (which the engine solves)
or a dictated result (which the engine carries). Which side a firebox sits on is a
deployment decision, not a property of the firebox.

### Gas-path pressures

The four per-section pressure terms of the draft balance, named after the English texts of
EN 15544 and EN 13384 (decision 2026-10-28; code names go in _Avoid_ lists).

**Standing pressure (p_h)**:
The draught a column of hot flue gas generates over its height — the chimney effect, and
the only gain in the pressure balance. EN 15544 names it standing pressure and sums it as
the buoyancy term; EN 13384: theoretical draught available due to chimney effect.

**Friction resistance (p_Rs)**:
The pressure lost to friction along a duct's wall length, from roughness and flow regime.
EN 15544: resistance pressure due to friction (p_r); EN 13384 lumps it with form losses as
P_E — we keep the two apart because one comes from length, the other from declared fittings.
_Avoid_: static friction (code name; the loss is neither static nor dry friction)

**Direction-change resistance (p_u)**:
The pressure lost at bends, section changes and fittings captured by a declared flow-
resistance coefficient ζ — EN 15544: resistance pressure due to direction change (p_u).
The one term that can be unknown rather than zero: a section with no declared ζ supports no
claim, it does not prove absence of loss.
_Avoid_: dynamic friction (code name)

**Velocity-change pressure (p_Rg)**:
The pressure difference caused by the flue-gas velocity changing — section changes, flow
splits and merges (EN 13384 P_G). Signed: it can be a gain, not only a loss.
_Avoid_: vChange friction (code name)

**Pressure balance**:
At a cross-section of the gas path: accumulated standing pressure minus the accumulated
resistances (friction, velocity change, direction change). The appliance's pressure
requirement is a demand on this balance — EN 15544's required delivery pressure is
Σ p_u + Σ p_r − Σ p_h over the chain, from the combustion chamber to the last flue pipe.
_Avoid_: "available draft" without saying at which cross-section and operating point

### Evaluation

**Operating point**:
The canonical pairing of a draft condition and a load quantity at which the appliance is
evaluated; sizing runs a four-point grid.
_Avoid_: pass (retired outright by ADR-0009)

**Draft condition**:
The chimney-draft state an operating point evaluates: the minimum-draft state (with maximum
positive pressure) or the maximum-draft state (with minimum positive pressure).

**Load quantity**:
The fuel-charge level of an operating point: nominal (the declared maximum charge, m_B) or
reduced (m_B_min). "Nominal" qualifies only the load axis; any other use of the word must
be spelled out for what it actually refers to.

**Dry flue-gas composition (at nominal load)**:
The combustion-gas fractions, moisture removed, belonging to the appliance operating at
nominal load. Always named with its qualifier — it is not an intrinsic property of a
firebox, and any default value for it is a convention, not a measurement.
_Avoid_: nominal composition, nominal CO2

**Effective load mass**:
An operating point's load mass shared across the number of gas flows of the section that
cools it (ADR-0008).

### Constraints and findings

**Constraint**:
A requirement on a physical term, carrying the clause or product rule it comes from. Every
constraint states which terms it reads and what bounds or predicate holds.

**Described-only constraint**:
A constraint reported for display of the norm but never judged on that path — its verdict
is Satisfied by construction.
_Avoid_: reading Satisfied from a described-only constraint as conformity

**Evaluated constraint**:
A constraint whose check actually runs on that path; only evaluated constraints can produce
a violation.

**Verdict**:
The judgement on one constraint at one operating point: satisfied or violated.

**Constraint fold**:
The single reduction by which per-term constraint algebra becomes the verdict list for a
run. It is the one place a term becomes a verdict; described-only and evaluated
constraints both go through it.
_Avoid_: using "fold" for list-reduction talk inside the constraint pipeline

**Severity (blocking / advisory)**:
A property of the rule, not of the violation: a blocking violation rejects the computation;
an advisory violation is reported without rejection. Rules whose only documentary basis is
the UZ37 worksheets may be advisory; standards-cited rules are blocking until the standard
owner re-grades them. The desktop path grades every cited constraint as blocking; only the
HTTP calc path honours advisory (decision of 2026-10-24, asymmetry accepted).

### Conformance and cross-validation

**Cross-validation**:
Byte-for-byte comparison of the engine against an independent reference implementation of
the standard. A fixture is cross-validated (golden) or it is not — there is no partial
status.
_Avoid_: "validation" unqualified for this

**Golden fixture**:
A worked standard case cross-validated against an independent reference implementation.
Self-generated expectations prove nothing: an agent-produced fixture cannot confer golden
status.

**Reference case (cas type)**:
A complete worked example of a standard used as a cross-validation fixture.

**Type testing / tested type**:
The standard's conformity sense: a firebox type proven by test, as for the `SingleTested`
kind. Distinct from cross-validation above.
_Avoid_: validation test

**Certification**:
The authority-side approval process for a product on the market. Supporting documents are
interpretation notes (Homologation.md, EN13384_INTERPRETATION.md).
_Avoid_: homologation (in English prose), validation (for this)
