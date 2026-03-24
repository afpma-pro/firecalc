# Plan: Reorganize TagTreeMenu Categories Across All PipePanel Subtypes

## Context

The menu categories in PipePanel subtypes have two problems:
1. **Illogical grouping**: Position/direction properties mixed with material/shape in a flat list
2. **Inconsistency**: PipePanel_13384_Thermal has sub-groups while FluePipePanel and FlowOnly are flat

Goal: Split Properties into logical sub-groups, applied consistently across all 3 panels.

## Target Menu Structures

### FluePipePanel (EN 15544)
```
Shortcut: "Start a new pipe" → Material + InnerShape  (unchanged)
Properties:
  Position & Direction:  SetInitialPosition, SetInitialDirection, SetFinalPosition
  Material / Roughness:  SetMaterial, SetRoughness
  SetInnerShape          (standalone leaf)
Geometry:               (unchanged)
```

### PipePanel_13384_Thermal (Chimney + Connector)
```
Shortcut: "Start a new pipe" → PipeLocation + Material + InnerShape + Layer  (unchanged)
Properties:
  Position & Direction:  SetInitialPosition, SetInitialDirection, SetFinalPosition
  Material / Roughness:  SetMaterial, SetRoughness
  SetPipeLocation        (standalone leaf)
  Dimensions:            SetInnerShape, SetOuterShape, SetThickness
  Layers:                SetLayer, SetLayers
  SetAirSpaceAfterLayers (standalone leaf)
Geometry:               (unchanged)
Catalogs:               (unchanged)
```

### PipePanel_13384_FlowOnly (Air Intake)
```
Shortcut: "Start a new pipe" → Material + InnerShape  (unchanged)
Properties:
  Position & Direction:  SetInitialPosition, SetInitialDirection, SetFinalPosition
  Material / Roughness:  SetMaterial, SetRoughness
  SetInnerShape          (standalone leaf)
Geometry:               (unchanged)
```

## Steps

### 1. Add I18N keys (4 files)

**`modules/i18n/src/main/resources/i18n/en.conf`** — add inside `set_prop {}` block (after `_geometric_properties`):
```
_position_and_direction       = "Position & Direction"
_material_and_roughness       = "Material / Roughness"
```

**`modules/i18n/src/main/resources/i18n/fr.conf`** — add inside `set_prop {}` block:
```
_position_and_direction       = "Position & Direction"
_material_and_roughness       = "Matériau / Rugosité"
```

**`modules/i18n/src/main/scala/afpma/firecalc/i18n/I18nData.scala`** — add 2 fields to `case class SetProp` (after `_geometric_properties`):
```scala
_position_and_direction      : String,
_material_and_roughness      : String,
```

### 2. Restructure FluePipePanel `prop_elements`

```scala
lazy val prop_elements = TagTreeMenu.Group(
    txt  = I18N.set_prop._self,
    next = List(
        TagTreeMenu.Group(
            txt  = I18N.set_prop._position_and_direction,
            next = List(
                TagTreeMenu.Leaf[SetInitialPosition],
                TagTreeMenu.Leaf[SetInitialDirection],
                TagTreeMenu.Leaf[SetFinalPosition]
            )
        ),
        TagTreeMenu.Group(
            txt  = I18N.set_prop._material_and_roughness,
            next = List(
                TagTreeMenu.Leaf[SetMaterial],
                TagTreeMenu.Leaf[SetRoughness]
            )
        ),
        TagTreeMenu.Leaf[SetInnerShape]
    )
)
```

### 3. Restructure PipePanel_13384_Thermal `prop_elements`

```scala
lazy val prop_elements = TagTreeMenu.Group(
    txt  = I18N.set_prop._self,
    next = List(
        TagTreeMenu.Group(
            txt  = I18N.set_prop._position_and_direction,
            next = List(
                TagTreeMenu.Leaf[SetInitialPosition],
                TagTreeMenu.Leaf[SetInitialDirection],
                TagTreeMenu.Leaf[SetFinalPosition]
            )
        ),
        TagTreeMenu.Group(
            txt  = I18N.set_prop._material_and_roughness,
            next = List(
                TagTreeMenu.Leaf[SetMaterial],
                TagTreeMenu.Leaf[SetRoughness]
            )
        ),
        TagTreeMenu.Leaf[SetPipeLocation],
        prop_elements_geom,
        prop_elements_insulation,
        TagTreeMenu.Leaf[SetAirSpaceAfterLayers]
    )
)
```

`prop_elements_geom` (Dimensions) and `prop_elements_insulation` (Layers) stay as-is.

### 4. Restructure PipePanel_13384_FlowOnly `prop_elements`

```scala
lazy val prop_elements = TagTreeMenu.Group(
    txt  = I18N.set_prop._self,
    next = List(
        TagTreeMenu.Group(
            txt  = I18N.set_prop._position_and_direction,
            next = List(
                TagTreeMenu.Leaf[SetInitialPosition],
                TagTreeMenu.Leaf[SetInitialDirection],
                TagTreeMenu.Leaf[SetFinalPosition]
            )
        ),
        TagTreeMenu.Group(
            txt  = I18N.set_prop._material_and_roughness,
            next = List(
                TagTreeMenu.Leaf[SetMaterial],
                TagTreeMenu.Leaf[SetRoughness]
            )
        ),
        TagTreeMenu.Leaf[SetInnerShape]
    )
)
```

## Files Modified

| File | Change |
|------|--------|
| `modules/i18n/src/main/resources/i18n/en.conf` | +2 keys in `set_prop` |
| `modules/i18n/src/main/resources/i18n/fr.conf` | +2 keys in `set_prop` |
| `modules/i18n/src/main/scala/afpma/firecalc/i18n/I18nData.scala` | +2 fields in `SetProp` |
| `modules/ui/.../panels/FluePipePanel.scala` | Rewrite `prop_elements` |
| `modules/ui/.../panels/PipePanel_13384_Thermal.scala` | Rewrite `prop_elements` |
| `modules/ui/.../panels/PipePanel_13384_FlowOnly.scala` | Rewrite `prop_elements` |

No changes to: `PipePanel.scala`, `TagTreeMenuComponent.scala`, concrete subclasses.

## Verification

1. Compile `i18n` module (cross-compiled) — verify new keys resolve
2. Compile `ui` module — verify all 3 panels compile
3. Manual UI test: verify the new sub-group structure in each pipe panel
