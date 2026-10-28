# Smoke-path element labels are installer-facing English; internal code names may diverge

The three smoke-path elements — the masonry gas channel inside the heater body (what L_Z measures), the connecting flue between heater and chimney, and the chimney — carried three mutually contradictory English namings: code identifiers (`FluePipeT`, `ConnectorPipeT`, `ChimneyPipeT`), EN UI strings (`pipe_type.connector` = "Chimney connector", `pipe_type.channel` = "Channels", `pipe_type.chimney` = "Chimney"), and descriptive prose. A review of this conflict could reasonably land on either side: the code names are internally consistent and align with the FR strings and L_Z wording; the EN labels align with installer-facing usage. A domain-modeling session in 2026-10 produced opposite answers from two careful readers, which is the evidence that the choice must be recorded.

We decided the **current EN UI labels are the canonical English vocabulary** for these elements ("chimney connector" for the masonry channel, "channel" for the connecting flue, "chimney" for the chimney), and that code identifiers deliberately diverge from them. `FluePipeT` naming the chimney connector is not a bug, and the i18n strings must not be "corrected" to match the code.

## Considered options

- **Canonize the code names (Flue / Connector / Chimney) and fix the EN strings** — rejected: installer-facing wording is what the customer and the installer size against; internal naming consistency is recoverable in code, a mislabelled UI is not.
- **Canonize the EN translation of the standards' terms (e.g. "flue connector")** — rejected: the in-repo EN strings don't use them consistently anyway, and neither side is a standards body bound by that translation.

## Consequences

- `modules/engine/GLOSSARY.md` carries the three entries with `_Avoid_` lists; topology prose reads "terminal channel", "ends with a chimney connector".
- Any future unification must rename the code side (`FluePipeT` → the canonical term), never the EN strings; a PR that "fixes" `pipe_type.connector`/`pipe_type.channel` to match the enum names reverts this decision.
- No wire/DTO change: the divergence is confined to internal Scala names vs localised display strings.
