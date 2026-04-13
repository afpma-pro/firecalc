# PRD: Friendly Direction Badge

**Feature**: `DirectionBadgeComponent` — a dedicated, reusable badge showing the final pipe direction after a direction change or along a straight section.

**Location**: Replace inline `directionBadge` in `PipePanel.scala` with a standalone component.

---

## 1. Requirements

### 1.1 Display

| Context | What is shown |
|---|---|
| Final direction is cardinal (Front, Right, Up, Down, Rear, Left) | `dir.` label + cardinal name, e.g. **dir. Right** |
| Final direction is cardinal + elevation (Tier 2) | `dir.` label + cardinal + arrow elevation, e.g. **dir. Right ↑30°** |
| Final direction is fully custom (Tier 3) | `dir.` label + arrow notation: **dir. ↻45° ↑30°** (azimuth with ↻, elevation with ↑/↓) |
| Non-cardinal with roll angle visible | Additional `badge-outline badge-xs` showing roll, e.g. **roll 120°** |

- Badge uses DaisyUI `badge badge-ghost badge-sm font-mono` classes
- `dir.` prefix is an i18n-translated label (short for "direction"), styled `text-xs opacity-60`
- Placed to the right of the element title using flex layout

### 1.2 Tooltip (DaisyUITooltip)

Shown on hover for **all** badges. Content is **dynamic + convention**:

```
Direction: Right ↑30°              ← actual computed direction
Azimuth 90° · Elevation 30°       ← numeric values
Roll 90° from reference            ← only when roll is defined
───────────────────────────────
Roll 0° bends toward Up            ← convention explanation
(horizontal pipe convention)        ← varies by current direction
```

Convention line adapts:
- If current direction is vertical (Up): "Roll 0° bends toward Rear"
- If current direction is horizontal: "Roll 0° bends toward Up"
- If current direction is Down: "Roll 0° bends toward Rear"

All tooltip text is i18n-translated.

### 1.3 Editable Mode (Click-to-set)

When displayed next to a `DirectionChange` (i.e. `previousDirection` is provided):

- Badge shows a small chevron `▾` indicator
- Clicking opens a **dropdown menu** listing reachable cardinal directions with their roll angles
- Each item shows: `{roll}° {CardinalName}` with the current selection highlighted
- Unreachable cardinals (e.g. Up/Down for 90° bend from horizontal) show `—` and are disabled
- Selecting an item sets the roll angle on the direction change (bisync via Laminar `Var`)

When displayed next to a `StraightSection` (no `previousDirection`): badge is read-only, no chevron, no dropdown.

### 1.4 Component API

```scala
case class DirectionBadgeComponent(
    absDirection: Signal[Option[Vec3]],
    previousDirection: Signal[Option[Vec3]],  // None for straight sections
    frameBefore: Signal[Option[PipeFrame]],   // For computing reachable cardinals
    rollVar: Option[Var[Option[Angle]]],      // Bisync target; None = read-only
)
```

- `absDirection`: The computed direction after the element
- `previousDirection`: Direction before the element (enables editable mode when `Some`)
- `frameBefore`: The PipeFrame before this element (needed for `reachableCardinals` and tooltip convention)
- `rollVar`: When provided, enables click-to-set. Bidirectional binding to the direction change's roll angle.

Exposes `lazy val node: HtmlElement` (following the DaisyUITooltip pattern).

### 1.5 i18n Keys

New keys to add to `I18nData_UI`:

| Key path | FR | EN |
|---|---|---|
| `direction_badge.label` | `dir.` | `dir.` |
| `direction_badge.tooltip_direction` | `Direction :` | `Direction:` |
| `direction_badge.tooltip_azimuth_elevation` | `Azimut %s° · Élévation %s°` | `Azimuth %s° · Elevation %s°` |
| `direction_badge.tooltip_roll` | `Roulis %s° depuis la référence` | `Roll %s° from reference` |
| `direction_badge.tooltip_convention_up` | `Roulis 0° dévie vers l'Arrière` | `Roll 0° bends toward Rear` |
| `direction_badge.tooltip_convention_horizontal` | `Roulis 0° dévie vers le Haut` | `Roll 0° bends toward Up` |
| `direction_badge.tooltip_convention_down` | `Roulis 0° dévie vers l'Arrière` | `Roll 0° bends toward Rear` |

### 1.6 Transition Display

**Final direction only** is shown in the badge. `previousDirection` is used internally for:
- Determining editable vs read-only mode
- Providing context in the tooltip (if useful later)

No `Previous → Final` arrow in the badge.

---

## 2. Visual Reference

See `tasks/mockups-direction-badge.html` for interactive HTML mockups covering:
- Cardinal badge display with `dir.` label
- Arrow notation for non-cardinal: `↻{az}° ↑{el}°`
- Tooltip layout (dynamic + convention)
- Dropdown click-to-set with reachable cardinals

**Chosen compact format**: Option B — Arrow notation (`↻45° ↑30°` / `↻182° ↓15°`)

---

## 3. Implementation Plan

### Phase 1: Component skeleton + display

1. **Create `DirectionBadgeComponent.scala`**
   - Location: `modules/ui/src/main/scala/afpma/firecalc/ui/components/DirectionBadgeComponent.scala`
   - Implement `case class` with the API from §1.4
   - Implement `lazy val node: HtmlElement` rendering the badge with:
     - `dir.` i18n label
     - Cardinal name OR arrow notation based on `Vec3.toDisplayString` tier
   - For arrow notation: add a new method `toCompactArrowString` on Vec3 or as a local helper

2. **Add i18n keys**
   - Add `DirectionBadge` case class in `I18nData_UI`
   - Add FR and EN translations in babel resource files

3. **Wire into PipePanel**
   - Replace inline `directionBadge` in `PipePanel.scala` with `DirectionBadgeComponent`
   - Pass `directionAfterByIdx`, `frameBeforeByIdx` signals from `PipePanel_13384_Thermal`
   - Pass `rollVar` from the direction change element (editable) or `None` (straight section)

### Phase 2: Tooltip

4. **Add DaisyUITooltip integration**
   - Wrap badge in `DaisyUITooltip`
   - Tooltip content: reactive `div` combining direction display, az/el values, roll, and convention
   - Convention text selected based on `frameBefore.direction` orientation (vertical vs horizontal)

### Phase 3: Click-to-set dropdown

5. **Implement dropdown menu**
   - Conditionally render chevron when `rollVar.isDefined`
   - On click: show DaisyUI `dropdown` with `reachableCardinals` from `frameBefore`
   - Each item: `{roll}° {cardinalName}` — clicking writes to `rollVar`
   - Highlight current selection

### Phase 4: Polish

6. **Test and verify**
   - Verify badge updates reactively when roll/angle changes
   - Verify dropdown sets roll correctly (bidirectional)
   - Verify tooltip shows correct convention per direction
   - Verify i18n works in both FR and EN
   - Compile check with Metals

---

## 4. Files to Create/Modify

| Action | File |
|---|---|
| **Create** | `modules/ui/src/main/scala/afpma/firecalc/ui/components/DirectionBadgeComponent.scala` |
| Modify | `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PipePanel.scala` — replace inline badge |
| Modify | `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PipePanel_13384_Thermal.scala` — wire signals |
| Modify | `modules/ui-i18n/src/main/scala/afpma/firecalc/ui/i18n/I18nData_UI.scala` — add keys |
| Modify | i18n babel resource files (FR, EN) — add translations |
| Possibly modify | `modules/engine/.../Vec3.scala` — add `toCompactArrowString` if placed there |

---

## 5. Open Questions (resolved)

| Question | Decision |
|---|---|
| Editable meaning | Both: reactive display + click-to-set via dropdown |
| Compact format | Arrow notation: `↻{az}° ↑{el}°` / `↻{az}° ↓{el}°` |
| Transition display | Final direction only; previous used internally |
| Tooltip content | Dynamic values + convention explanation |
| Edit UX | Dropdown menu (not inline buttons) |
