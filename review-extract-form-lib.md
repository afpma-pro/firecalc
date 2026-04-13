# Review Report — extract-form-lib

Date: 2026-04-08
Reviewer: GPT-5.4 via OpenRouter
Branch reviewed: `feat/extract-form-lib`
Range reviewed: roughly `ad44bae..HEAD`

## Executive summary

The extraction is substantial and mostly successful:
- the old UI form abstraction was moved into dedicated modules,
- the UI was migrated to the new `Form[A] + FormRenderer` design,
- old `formgen/` and form-specific `daisyui` wrappers were deleted,
- `ui/compile` and `ui/fastLinkJS` now succeed,
- most tests pass.

Overall assessment: good architectural direction with real payoff, but there are a few design and maintainability issues worth addressing before considering this fully settled.

## What looks good

1. Clear architectural improvement
- Separating core / derivation / coulomb / daisyui is cleaner than the old monolithic UI-owned form stack.
- Moving renderer choice to render-site via `FormRenderer` is a better abstraction boundary.
- Immutable `FormConfig` and elimination of mutable overwrite state are improvements.

2. Migration completeness
- The old `formgen/` package and `DaisyUIVerticalForm` / `DaisyUIHorizontalForm` files were actually removed.
- UI call sites were migrated instead of papering over with compatibility shims only.
- `ui/compile` and `ui/fastLinkJS` succeeding is a strong signal that the migration is real.

3. Practical compatibility work
- Adding migration helpers like `eitherAsSelectWithOptions`, `optionOfEither`, `splitViaMatchingOnly`, and selection helpers made the migration feasible without rewriting all domain-specific form code.
- The non-private `_SplitViaMatchingHelper` fix is correct and demonstrates proper understanding of Scala 3 inline visibility constraints.

4. Validation/i18n abstraction
- `FormMessages` is a reasonable abstraction for moving validation out of UI-i18n coupling.
- `ValidateVarCommonInstances` was adapted in a useful way.

## Main review findings

### 1. There is still problematic coupling between new form modules and old UI internals
Severity: medium

Evidence:
- `modules/laminar-form-derivation/.../FormDerivation.scala` imports:
  - `afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues`
- `modules/ui/src/main/scala/afpma/firecalc/ui/instances/VerticalFormCommonInstances.scala` and `HorizontalFormCommonInstances.scala` still import old UI `DaisyUIInputs` members such as:
  - `afpma.firecalc.ui.daisyui.DaisyUIInputs.FieldsetLabelAndContent`
  - `afpma.firecalc.ui.daisyui.DaisyUIInputs.SelectAndOptionsOnly`
- `modules/ui/src/main/scala/afpma/firecalc/ui/utils/dualqtyd.scala` depends on old UI `DaisyUIInputs.NumberInputWithUnitsAndFloatingLabelAndTooltipValidation`.

Why this matters:
- The new form library is not fully self-contained yet.
- Some old UI daisyui code is still effectively part of the form stack, but it lives in the UI module rather than the extracted modules.
- This creates a fuzzy boundary: the extraction is conceptually complete, but operationally some rendering widgets still live in the old UI layer.

Recommendation:
- Either:
  1. finish moving the missing rendering primitives/widgets into `laminar-form-daisyui`, or
  2. explicitly document that `laminar-form-daisyui` only owns the generic renderer shell while certain advanced widgets remain app-specific in `ui/daisyui`.
- If choosing (2), rename or document those pieces so the boundary is intentional, not accidental.

### 2. `forSelectionWithDefaultValue_usingSelectInput` has a suspicious unused parameter
Severity: medium

Evidence:
- In `FormDerivation.scala`, method:
  - `forSelectionWithDefaultValue_usingSelectInput[A, T](..., getId: A => String)`
- Implementation does not use `getId` at all.

Why this matters:
- This is usually a migration smell: the old API required explicit ids, the new implementation silently switched to renderer-driven `Show`-based selection.
- That can change behavior if `Show[A]` is not a stable/unique identifier.
- Call sites still pass `getId`, which implies reviewers/readers assume it matters.

Recommendation:
- Either remove `getId` from the API and update call sites, or
- actually use it in selection rendering.
- At minimum, document why `getId` is ignored and whether `Show[A]` uniqueness is required.

### 3. `TypeOfAppliance` derivation fallback is brittle
Severity: medium

Evidence:
- In `VerticalFormCommonInstances.scala`, around the `Firebox.SingleTested` handling, a local manual `given DF[TypeOfAppliance]` was introduced using:
  - `Show.fromToString`
  - `Defaultable(TypeOfAppliance.WoodLogs)`
  - `ValidateVar.valid`
  - `FormDerivation.forEnumOrSumTypeLike_UsingShowAsId(...)`

Why this matters:
- This fixes compilation, but it is a local patch for a missing global instance.
- If other files derive forms that transitively require `TypeOfAppliance`, they may hit the same issue or behave inconsistently.
- `Show.fromToString` may not align with i18n or the intended domain display.

Recommendation:
- Move the `TypeOfAppliance` form instance to the appropriate common instance module so it is globally available and intentional.
- Prefer a domain-specific `Show` if one exists rather than `toString`.

### 4. `dualqtyd.scala` still has some rough edges and technical debt
Severity: medium

Evidence:
- Uses mixed imports from both new form modules and old UI daisyui widgets.
- Contains manual form implementations with duplicated vertical/horizontal logic.
- Current implementation still carries migration-era noise, e.g. alias import:
  - `import afpma.laminar.form.daisyui.{DaisyUIInputs as _, *}`
- Recent bug fix was required because anonymous `new Form[...]` still used old member names (`defaultable_instance`, `validate_var`) and wrong render signature.

Why this matters:
- This file is central to unit-aware forms and seems especially fragile.
- It already caused the final compile failure after the larger migration was “done”, which is a warning sign.

Recommendation:
- Refactor `dualqtyd.scala` next:
  - extract the common vertical/horizontal internals,
  - remove migration alias imports,
  - prefer renderer-driven composition where possible,
  - add focused tests around dual-unit form behavior.

### 5. Review of test outcome: not all tests passed
Severity: low/medium

Observed:
- `ui/compile`: passes
- `ui/fastLinkJS`: passes
- test output indicates:
  - engine tests passing,
  - several `payments` tests failing (`payments / Test / test`).

Interpretation:
- The session notes say those are pre-existing payment integration failures. That may be true, but the branch still does not leave the repo in a fully green `sbt test` state.

Recommendation:
- In the PR description / merge notes, explicitly state:
  - exact failing test names,
  - whether they also fail on base branch,
  - whether they are unrelated to the form extraction.
- Ideally confirm with a base-branch comparison instead of relying on prior session memory.

### 6. The migration introduced several compatibility helpers that may deserve explicit deprecation status
Severity: low

Evidence:
- `eitherAsSelectWithOptions`
- `optionOfEither`
- `splitViaMatchingOnly`
- coulomb convenience methods like `forQtyD`, `forTempD`, etc.

Why this matters:
- Some are good permanent API; others look like migration adapters.
- Without documentation, future maintainers won’t know which are core abstractions and which are transitional shims.

Recommendation:
- Classify them:
  - permanent API surface,
  - or transitional compatibility layer.
- If transitional, add comments / TODOs / deprecation path.

## Specific noteworthy code observations

1. `FormDerivation._SplitViaMatchingHelper`
- Final fix to make it non-private is correct.
- Good catch; private inline helper objects are a known Scala 3 pitfall.

2. `ValidateVarCommonInstances`
- The added `FormMessages` given is reasonable.
- However, it hardcodes the i18n binding inside the validation instance class. That is practical, but it keeps the UI-level dependency here rather than at composition root.

3. Old `ui/daisyui/DaisyUIInputs.scala`
- It now imports `afpma.laminar.form.*`, which is a good cleanup.
- But the file still mixes old UI helpers (`Component`, `OptionalField`, `formatPrecise`) and new form abstractions. Again, acceptable pragmatically, but not a fully clean separation.

## Risk assessment

Low-to-medium risk overall.

Why not low?
- Large migration touching many instance files and render sites.
- Heavy reliance on Scala 3 givens/derivation means small scope/import changes can break unrelated derivations.
- Some extracted-vs-old boundaries remain blurry.

Why not high?
- Compilation is working.
- fastLinkJS works.
- Most tests pass.
- Final cleanup removed the most dangerous source of split-brain APIs: the old formgen and old form wrappers.

## Suggested follow-up tasks

1. Move or explicitly bless remaining old UI DaisyUI widgets still required by forms
- especially `FieldsetLabelAndContent`, `SelectAndOptionsOnly`, and unit-input-related widgets.

2. Clean up `dualqtyd.scala`
- reduce duplication,
- document why it still uses UI-local widgets,
- add tests.

3. Normalize enum/select APIs
- decide whether ids come from `Show`, explicit `getId`, or both.
- make `forSelectionWithDefaultValue_usingSelectInput` consistent.

4. Add targeted tests for extracted form modules
- derivation of case classes and sealed traits,
- conditional forms,
- option-of-either/either select behavior,
- coulomb numeric forms,
- splitViaMatchingOnly,
- dual-unit form logic.

5. Confirm payment test failures are unrelated
- compare against base branch and document results.

## Payments Test Verification (D6)

Verified 2026-04-08: Payment test failures are **pre-existing and unrelated** to the form extraction.

Base branch (`ad44bae`) test results:
- Total: 110, Failed: 8, Passed: 102
- Failing tests:
  - `afpma.firecalc.payments.email.EmailServiceSpec`
  - `afpma.firecalc.payments.service.InvoiceNumberServiceTest`
  - `afpma.firecalc.payments.repository.ProductCatalogIntegrationTest`
  - `afpma.firecalc.payments.repository.InvoiceCounterRepositoryTest`

Feature branch (`feat/extract-form-lib`) test results:
- Total: 110, Failed: 5, Passed: 105
- Same test classes fail (fewer failures likely due to test ordering / environment)

These are integration tests requiring external services (email, database) and fail identically on both branches.

## Follow-up Resolution (2026-04-08, second pass)

### Finding #2 (getId unused) — DISMISSED
The reviewer incorrectly flagged `getId` as unused in `forSelectionWithDefaultValue_usingSelectInput`.
It IS used: passed to `renderer.selectWithCustomId(... getId = getId, getById = id => selectOptions.find(getId(_) == id).get)`.
`getId` provides stable HTML option values (e.g. `_.name`), distinct from `Show[A]` which provides display labels.
Added `@param getId` doc comment to clarify the distinction.

### Finding #3 (TypeOfAppliance) — ALREADY CORRECT
`TypeOfAppliance` has a proper i18n-aware `Show` via `ShowUsingLocale[TypeOfAppliance]` defined in its companion object (dto module).
Since `VerticalFormCommonInstances` takes `using Locale`, the `ShowUsingLocale` automatically resolves to `Show`.
The instance is in the right place — no move needed.

### Follow-up #1 (dualqtyd cleanup) — DONE
Removed ~45 lines of dead commented-out code (old encoder/decoder stubs, type aliases, class skeleton).
File went from 278 → 233 lines. Renderer leak was already fixed in C2.

### Follow-up #5 (API classification) — DONE
Added `API status: permanent` or `@deprecated` doc comments to all factory methods in FormDerivation:
- `splitViaMatchingOnly` — permanent (externally-discriminated sealed traits)
- `eitherFromOption` — permanent (replaces deprecated eitherAsSelectWithOptions)
- `forEnumOrSumTypeLike_UsingShowAsId` — permanent (primary enum/select pattern)
- `forSelectionWithDefaultValue_usingSelectInput` — permanent (material selectors with editable sub-values)
- `eitherAsSelectWithOptions` — already @deprecated("Use eitherFromOption instead")
- `optionOfEither` — already @deprecated("Use FormDerivation.derived[OptionOfEither[L, R]] directly")
- `forList_fromComponent` — already @deprecated("Use Form.makeFor instead")

## Overall verdict

The migration is substantially successful and in mergeable shape from an architecture/migration perspective, assuming the known payments test failures are unrelated.

My recommendation:
- Accept the branch after documenting the remaining non-critical technical debt listed above.
- Prefer a follow-up cleanup PR for the lingering boundary issues (`dualqtyd`, remaining old `DaisyUIInputs` dependencies, API normalization around selection helpers).
