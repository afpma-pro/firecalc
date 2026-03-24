# Checklist — Angle Spécifique Catalog

## Catalog infrastructure
- [ ] Load a `.fcalc-db` file WITHOUT `angle_presets` section — no crash, no regressions
- [ ] Open Catalog Manager — "Angle/Bend Presets" label does NOT appear when no angle presets loaded
- [ ] Load a `.fcalc-db` file WITH `angle_presets` section — Catalog Manager shows entry count
- [ ] Clear all catalogs — angle presets also cleared

## UI — Select button
- [ ] Add `AddAngleAdjustable` from direction change menu
- [ ] "Select from catalog" button appears to the right of the direction badge
- [ ] Direction input still appears next to the Select button
- [ ] Direction badge still works (click to set final direction)
- [ ] Click "Select from catalog" with no catalog loaded — dialog opens, empty, no crash
- [ ] Click "Select from catalog" with catalog loaded — dialog shows entries, search works, image preview works
- [ ] Select an entry — name, angle, zeta updated; absDir unchanged
- [ ] Select again — values overwrite correctly
- [ ] Add a second `AddAngleAdjustable` — each has its own working Select button + direction input

## XLSX — Template generation
- [ ] Run `generate-templates <dir>` — `angle-presets-template.xlsx` created
- [ ] Open template — bilingual headers, example row, freeze pane, image comment

## XLSX — Import
- [ ] Fill template with test data (reference, angle °, zeta, optional image)
- [ ] Run `import angle-presets <input.xlsx> <output.fcalc-db>`
- [ ] Load output `.fcalc-db` in app — entries appear in catalog manager and Select dialog

## Locale
- [ ] EN: "Angle/Bend Presets" in Catalog Manager, "Select from catalog" on button
- [ ] FR: "Catalogue angles/coudes", "Sélectionner depuis le catalogue"
