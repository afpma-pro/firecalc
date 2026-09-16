<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
-->

# Impact of `co2_dry_nominal` on Computed Quantities

## Summary

`co2_dry_nominal` (dry CO₂ concentration of flue gases, default 7.05%) is a hardcoded value referenced by the EN 15544 standard. Changing it affects the **reported flue gas composition** and the **EN 13384 condensation temperature** $T_{sp}$, but **has no impact on EN 15544 compliance calculations** because the 15544 standard uses a fixed 45°C safety threshold independent of flue gas composition. Its effect on all other quantities — flue temperatures, gas density, and pressure requirements — is negligible (< 1°C, < 0.5 Pa).

## How `co2_dry_nominal` Flows Through the Calculations

### EN 13384 Condensation Temperature ($T_{sp}$)

$T_{sp}$ is derived from $\sigma_{H2O}$ and barometric pressure (altitude, outside temperature). For wood fuel, $T_{sp}$ equals the water dew point.

**Effective chain**: $\sigma_{CO2} \rightarrow \sigma_{H2O} \rightarrow p_D \rightarrow T_p \rightarrow T_{sp}$

Sensitivity: approximately **1.8°C per percentage point** of $\sigma_{CO2}$ for typical installations.

### EN 15544 Temperature Check — Fixed Threshold

EN 15544 §4.10.2 defines a **fixed** minimum chimney wall temperature of **45°C**. This threshold is **independent** of $\sigma_{CO2}$, $\sigma_{H2O}$, or the computed $T_{sp}$. The EN 13384 condensation check ($T_{iob} - T_{ig} \ge 0$) is a separate, optional overlay — not part of EN 15544 compliance.

## T_sp Sensitivity

| $\sigma_{CO2}$ | C1 (traditional) | C2 (traditional) | C3 (ecolabeled, 1500m) |
|-----------------|-------------------|-------------------|--------------------------|
| 7.05% | 41.8°C | 40.2°C | 38.3°C |
| 9.0% | **45.6°C** | **43.9°C** | 42.0°C |
| 10.0% | 47.3°C | 45.6°C | 43.6°C |

At 9.0%, traditional cases C1 and C2 land within ±2°C of the EN 15544 fixed threshold (45°C). C3 (ecolabeled, 1500m altitude) has a lower baseline $T_{sp}$ due to reduced barometric pressure at altitude — no single `co2_dry_nominal` aligns all three cases simultaneously.

## Downstream Effects

All downstream effects are negligible. Summary for 7.05% → 10% shift:

| Quantity | Shift | Significance |
|----------|-------|--------------|
| Flue segment temperatures | +0.1 to +0.6°C | Negligible |
| Flue gas density | −0.001 kg/m³ | Negligible |
| Pressure drop | +0.01 to +0.02 Pa | Negligible |
| $\Sigma p_h$ (ascensionnelle) | +0.02 Pa | Negligible |
| $\Sigma p_h - (\Sigma p_r + \Sigma p_u)$ | +0.02 Pa | Negligible |
| Efficiency ($\eta_{WN}$, $\eta_{Wmin}$, seasonal) | No change | EN 15544 uses internal fixed CO₂ constant |
| Mass flow rates ($\dot{m}$, $\dot{m}_B$) | No change | Derived from heat output, not CO₂ |
| Volumetric flow rates | No change | Function of mass flow and temperature |
| Emissions (CO, dust, COG, NOx) | No change | Manufacturer test data |
| Compliance status | No change | Fixed thresholds |

## Conclusion

The 7.05% default is an arbitrary value with no or very low bearing on EN 15544 calculation or duct sizing. It only influences the reported σ_CO₂ / σ_H₂O figures and the optional EN 13384 condensation margin.
