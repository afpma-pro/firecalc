#  | Name | Type | Length | Length Formula | Shape | Shape Formula | Roughness | Elev. Gain | Extra
---|------|------|--------|----------------|-------|---------------|-----------|------------|------
| 0 | vers centre chambre de détente | StraightSection | 5.50 cm | W / 2 | ◯ 62.8 cm | ◯  perimeterWetted | 3 mm | 0.00 cm |  |
| 1 | angle vif 90° | AngleVif (sharp angle) | — | — | ◯ 62.8 cm | ◯  perimeterWetted | — | — | angle=90 ° |
| 2 | section geometry change | SectionGeometryChange | — | — | ◯ 62.8 cm → ▭ 121.5 cm x 11 cm | — | — | — |  |
| 3 | vers colonnes d'air | StraightSection | 34.75 cm | (2 * A / 2 + 2 * B / 2) / 4 + D1 + S / 2 | ▭ 121.5 cm x 11 cm | 2·air_columns_total_width_side_wall + air_columns_total_width_rear_wall  ×  W | 3 mm | 0.00 cm |  |
| 4 | virage au pied des colonnes d'air | AngleVif (sharp angle) | — | — | ▭ 121.5 cm x 11 cm | — | — | — | angle=90 ° |
| 5 | section geometry change | SectionGeometryChange | — | — | ▭ 121.5 cm x 11 cm → ▯ 121.5 cm x 3.5 cm | — | — | — |  |
| 6 | remontée dans les colonnes d'air | StraightSection | 29.53 cm | W / 2 + FLOOR_THICKNESS + Y * 2 | ▯ 121.5 cm x 3.5 cm | 2·air_columns_total_width_side_wall + air_columns_total_width_rear_wall  ×  S | 3 mm | 29.53 cm |  |
| 7 | virage 90° avant injecteur | AngleVif (sharp angle) | — | — | ▯ 121.5 cm x 3.5 cm | — | — | — | angle=90 ° |
| 8 | section geometry change | SectionGeometryChange | — | — | ▯ 121.5 cm x 3.5 cm → ▭ 532 cm x 0.8 cm | — | — | — |  |
| 9 | injecteurs | StraightSection | 7.75 cm | D1 + S / 2 | ▭ 532 cm x 0.8 cm | (8·Ls + 4·Lr + Lt - 12·E)  ×  Z | 3 mm | 0.00 cm |  |