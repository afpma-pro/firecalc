# 0005 — Opaque Identity for Wire Pipe Converters

**Date:** 2026-07-31
**Status:** Accepted
**Commit:** 6f1ef3e915bf73a366b04082b952aa6ae6581a36

## Context

SPEC_02 invariant 9 prohibits casts, unchecked matching, or catch-all fallback in new dispatch or adaptation. The `RemoteFireboxPrefetch.convertResponse` method used `asInstanceOf` casts to convert `PipeFullDescrG[PipeElDescr]` (returned by `WireConversions.fromWirePipeFullDescr`) to module-specific types (`FireboxPipe_Module_15544.FullDescr`, `CombustionAirPipe_Module_15544.FullDescr`).

Six such casts existed across the codebase: two in `RemoteFireboxPrefetch`, two in `CalculationReportServiceImpl`, and two in `LocalRemoteEquivalenceSuite`.

## Decision

Add an opaque identity wrapper in `PipeDescrAlg` (where the opaque type `PipeFullDescr` is transparent) and module-specific converters in `WireConversions`:

```scala
// PipeDescrAlg.scala — opaque type is transparent here
def PipeFullDescr(descr: PipeFullDescrG[PipeElDescr]): PipeFullDescr = descr

// WireConversions.scala — module-specific converters
def fromWireFireboxPipeFullDescr(w: WirePipeFullDescr): FireboxPipe_Module_15544.FullDescr =
    PipeDescrAlg.PipeFullDescr(fromWirePipeFullDescr(w))
```

Callers pass the named converter explicitly. The original `fromWirePipeFullDescr` remains unchanged for existing callers.

## Consequences

- **Positive:** All six casts eliminated. Conversion is definitionally type-safe within the opaque type's owning scope; the compiler verifies exhaustiveness at all call sites. No runtime check, duplicated decoding, or additional allocation.
- **Positive:** Wire-protocol knowledge stays in `WireConversions`; engine model modules do not acquire wire dependencies.
- **Negative:** Adds methods to `PipeDescrAlg` and `WireConversions`. Mitigated by the methods being one-liners with clear names.
- **Considered:** Type-class converter (`FromWirePipeFullDescr[D]`). Rejected: two concrete types is not enough justification for a type-class battery; the explicit converter is more direct and boring.
