# Contributed L_N: section-wise flue-gas decay in EN 15544

EN 15544 §4.8.3 gives a single closed-form cooling law, `t(L) = t_burnout · exp(−0.83·L/L_N)`, with `L_N = factor·√m_B` (§4.3.2) — written for a one-flow flue pipe at nominal charge. Our pipe model supports split/merge topologies and a reduced-load operating point, and the closed form left both unhandled: splitting a flow changed no temperature (ηWN ≡ ηWmin ≡ non-split), making bifurcation thermally pointless and over-estimating outlet temperature (`.scratch/lz-and-efficiency-when-split-flow/`).

We decided to evaluate the decay **per section** against a **contributed L_N** built from the section's **effective load mass** — the operating point's load mass (`m_B` nominal / `m_B_min` reduced) divided by the section's number of flows — as a product of per-section exponentials. The no-arg `L_Z_calculated` stays defined at nominal `m_B` for design-time checks (`L_Z_min`, required delivery pressure, UI), since those are §4.3.3/§4.10 constraints, not a temperature path.

## Considered options

- **Keep global L_N** — status quo; rejected: the bug, not a model.
- **Re-run the whole computation at `m_B/nf`** for split projects — rejected: breaks mass conservation across merge, changes firebox sizing/flow mechanics that are already `nf`-correct, and isn't what the standard models.
- **Section-wise contributed L_N (chosen)** — collapses exactly (product of exponentials) to the closed form for unsplit paths at any load, so all non-split results — including every cas_types golden (no golden fixture uses a Split) — are provably unchanged at T1; only load-awareness (ηWmin) shifts goldens, gated on human re-cross-validation.

## Consequences

- An additive `L_Z_calculated(mass)` overload + the `OpPointLoads` domain object (operating-point load policy: `effective_load_mass(op, nf)`) enter the public engine API; the private repo sees a pin bump, no edit.
- An additive `t_fluepipe(L_Z, lzCalculated)` overload joins the same public API, exposing the §4.8.3 closed form to the operating-point `L_Z_calculated`.
- Reduced-load outputs change for every project ⇒ golden re-baseline is a human gate (see `.scratch/.../issues/02-load-aware-lz.md`).
- `t_fluepipe_mean`'s closed form is valid only for unsplit nominal spans; documented as such (no live callers).
