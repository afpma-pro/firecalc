# Fix Floating-Point Approximation Issues in Direction Tracking

## Context

After the angleN2 → roll migration, direction tracking uses trigonometry (`PipeFrame.applyBend` with Rodrigues rotation) which introduces IEEE 754 floating-point noise.

## Issue 1: Near-zero elevation_gain

**Where**: `ElementFactory_15544_Instances.scala` — `flowOnlyStraightSection15544` factory, line ~92:
```scala
val finalElevGain = ctx.currentFrame match
    case Some(frame) => (len.value * frame.direction.z).m
    case None        => elev_gain
```

`frame.direction.z` should be exactly `0.0` for horizontal sections but is instead ~`4.3e-18` or `3.06e-17` after bends with `roll = ±90.degrees` (because `cos(π/2) ≈ 6.12e-17` in IEEE 754).

**Affected tests** (`Pipes_15544_IncrementalBuilder`, case 3):
- `straight-1-short` (index 2): `elevation_gain = 4.329780281177466E-18` instead of `0.0`
- `straight-2` (index 4): `elevation_gain = 3.061616997868383E-17` instead of `0.0`

**Also in** `Pipes_15544_IncrementalBuilder`, case 2 (line ~124):
- `elevation_gain = 4.329780281177466E-17` instead of `0.0`

## Issue 2: Near-round-number angleN2

**Where**: `ElementFactory_15544_Instances.scala` — `directionChange15544` factory, `computedAngleN2`:
```scala
val postBendFrame = frame.applyBend(...)
Some(dirBefore.angleTo(postBendFrame.direction).withUnit[Degree])
```

`Vec3.angleTo` uses `math.acos(dot / (norm * norm))` which can produce e.g. `89.99999999999999` instead of `90.0`.

**Affected test** (`Pipes_15544_IncrementalBuilder`, case 3):
- 2nd bend (index 3): `angleN2 = Some(89.99999999999999)` instead of `Some(90.0)`

## Possible fix strategies

- **Snap-to-zero**: Clamp `elevation_gain` to `0.0` when `|value| < 1e-12`
- **Snap-to-round**: Round `angleN2` to nearest 0.5° (or nearest integer degree) when within epsilon
- **Test tolerance**: Use `shouldEqual(x +- epsilon)` for fields subject to float noise (requires breaking structural equality assertions into field-by-field checks)
