# WS1 Implementation Summary: Extract `domain` Module from `dto`

## Result: PASS

All compilation (JVM+JS) and tests pass.

| Metric | Value |
|--------|-------|
| Files created | 9 (7 domain types + 2 re-export files) |
| Files deleted | 4 (moved from dto) |
| Files modified | 3 (build.sbt, ProjectDescr.scala, EmissionsValues.scala) |
| Compilation | Pass (domain, dto, engine_kernel, engine — JVM+JS) |
| Tests | Pass (dto 74, engine_kernel 178, engine 18) |

## Files Created

### Domain types (`modules/domain/src/main/scala/afpma/firecalc/domain/`)

| File | Origin | Content |
|------|--------|---------|
| `PipeShape.scala` | dto/common/PipeShape.scala | ADT with Circle/Square/Rectangle, Coulomb lengths, `@Transl` |
| `flows.scala` | dto/common/flows.scala | Opaque type `NbOfFlows` |
| `Country.scala` | extracted from dto/common/ProjectDescr.scala | Enum (~25 countries), `derives Show` |
| `AbsoluteDirection.scala` | dto/v4/AbsoluteDirection.scala | `AzimuthDirection`, `InclinationDirection`, `AbsoluteDirection` |
| `TypeOfAppliance.scala` | dto/v4/TypeOfAppliance.scala | Enum with `ShowUsingLocale` instance |
| `PolluantName.scala` | extracted from dto/v4/EmissionsValues.scala | Enum (CO, Dust, OGC, NOx) with `ShowUsingLocale` |
| `TestReport.scala` | extracted from dto/v4/EmissionsValues.scala | Case class with `@Transl` |

### Re-export files (backward compatibility)

| File | Re-exports |
|------|------------|
| `dto/common/domain_reexports.scala` | PipeShape, NbOfFlows, Country |
| `dto/v4/domain_reexports.scala` | AzimuthDirection, InclinationDirection, AbsoluteDirection, TypeOfAppliance, PolluantName, TestReport |

## Files Deleted

- `modules/dto/.../dto/common/PipeShape.scala`
- `modules/dto/.../dto/common/flows.scala`
- `modules/dto/.../dto/v4/AbsoluteDirection.scala`
- `modules/dto/.../dto/v4/TypeOfAppliance.scala`

## Files Modified

- **`build.sbt`** — added `domain` crossProject (depends on `units`, `i18n`, `kittens`), added to root aggregate, added as dependency of `dto` and `engine_kernel`
- **`dto/common/ProjectDescr.scala`** — removed Country enum definition, added `import afpma.firecalc.domain.Country`
- **`dto/v4/EmissionsValues.scala`** — removed PolluantName + TestReport definitions, added `import afpma.firecalc.domain.{PolluantName, TestReport}`

## Deviations from Plan

1. **`all.scala` barrel file dropped** — bare `export TypeName` at package level is invalid Scala 3. Unnecessary since all types share `afpma.firecalc.domain` package (`import afpma.firecalc.domain.*` works natively).
2. **Added `kittens` dependency to domain module** — needed for `Country derives Show` (enum derivation).
3. **Cleaned unused imports** in ProjectDescr.scala (`cats.derived.*`) and EmissionsValues.scala (`I18N`) to eliminate compiler warnings.

## Dependency Graph (after WS1)

```
domain (NEW) → units, i18n
  ↑
dto → utils, i18n, units, domain
  ↑
engine-kernel → i18n, units, dto, domain
```

Zero import changes in engine-kernel or any downstream module — re-exports handle backward compatibility.
