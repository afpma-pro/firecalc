<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# FireCalc Engine Module

Core calculation engine for masonry heater design compliant with EN 13384 and EN 15544 European standards.

## 📚 Documentation

### Technical Documentation

- **[ENGINE.md](ENGINE.md)** - Engine development guide (compilation, testing, publishing)
- **[Homologation.md](Homologation.md)** - Validation and homologation notes
- **[EN13384_INTERPRETATION.md](EN13384_INTERPRETATION.md)** - Implementation notes and standard interpretations

### Standards References

This engine implements:
- **EN 13384** - Chimneys - Thermal and fluid dynamic calculation methods
- **EN 15544** - Chimneys - Test methods for system chimneys

## 🏗️ Architecture

**Technology Stack:**
- Scala 3 (pure functional programming)
- Cats (FP abstractions)
- Coulomb (compile-time unit checking)
- ScalaTest (testing)

**Key Features:**
- Pure functional core (no side effects)
- Type-safe physical units
- Algebraic data types (ADTs)
- Cross-compiled for JVM and JavaScript

## 🚀 Quick Start

### Compilation

```bash
# Compile for JVM
sbt engine/compile

# Compile for JavaScript
sbt engineJS/compile

# Watch mode
sbt ~engine/compile
```

### Running

```bash
# Run engine
sbt engine/run

# Watch and run
sbt ~engine/run
```

### Testing

```bash
# Run all tests
sbt engine/test

# Run specific test suite
sbt "engine-kernel/testOnly *InterpolationSuite"
sbt "engine-15544-strict/testOnly *strict_p1_decouverte_Suite"
sbt "engine-15544-mce/testOnly *mce_p1_decouverte_Suite"
sbt "engine-15544-strict/testOnly *velocity_limits_Suite"
sbt "engine-15544-strict/testOnly *ThermalResistance_Suite"
sbt "engine-15544-strict/testOnly *cas_types_15544_v20241001_Suite"
sbt "engine-13384-strict/testOnly *cas_types_13384_C2_Suite"
sbt "engine-13384-strict/testOnly *cas_types_13384_C16_Suite"
```

### Publishing

```bash
# Publish to local Maven repository
sbt engine/publishLocal
```

## 🔧 Development

### Code Quality

```bash
# Format code
sbt "engine / scalafmt"

# Fix linting issues
sbt "engine / scalafix"
sbt "engine / scalafix RemoveUnused"
```

engine depends on:
├── engine-kernel  # Pure calculation foundation
├── domain         # Shared domain types
├── dto            # Data transfer objects
├── units          # Physical units system
└── i18n           # Internationalization

## 📖 Related Documentation

### Project-Wide
- [Project Architecture](../../ARCHITECTURE.md) - System overview
- [Developer Guide](../../docs/dev/README.md) - Development documentation

### Related Modules
- [DTO Module](../dto/) - Data models
- [Units Module](../units/) - Physical units
- [UI Module](../ui/) - Frontend (uses engine via Scala.js)
- [FDIM Module](../fdim/) - Fluid dynamics test cases
- [EN15544 Labo Module](../engine-15544-labo/) - Labo-mode calculation engine

## 🧪 Test Modules

- **[fdim/](../fdim/)** - Fluid dynamics exercises and EN 15544 test cases
- **[labo/](../labo/)** - Laboratory configurations and experimental cases

## 🔍 Key Concepts

### Pure Functional Design
All calculations are pure functions with no side effects, enabling:
- Reliable testing
- Easy reasoning about code
- Safe parallelization
- Predictable behavior

### Type-Safe Units
Coulomb library provides compile-time dimensional analysis:
```scala
val temp: Quantity[Temperature] = 300.withUnit[Kelvin]
val length: Quantity[Length] = 5.withUnit[Meter]
// temp + length  // Compile error! Cannot add different dimensions
```

### Standards Compliance
- Implements EN 13384 thermal and fluid dynamic calculations
- Supports EN 15544 strict and MCE (Maximum Credible Event) modes
- Documented interpretations and assumptions (see [EN13384_INTERPRETATION.md](EN13384_INTERPRETATION.md))

## ⚠️ Important Notes

### Standard Interpretations

During implementation, several points required interpretation or clarification. These are documented in:
- [EN13384_INTERPRETATION.md](EN13384_INTERPRETATION.md) - Potential divergences from standard
- [Homologation.md](Homologation.md) - Validation requirements

Look for `// __INTERPRETATION__` comments in the code for specific implementation decisions.

Note: While this README lives in `modules/engine/`, the actual `// __INTERPRETATION__` markers are distributed across source files in `engine-kernel` (2 source files) and `engine-13384-strict` (5 source files), not in the engine module itself.

### Testing Strategy


- **Unit tests**: Core calculation functions
- **Integration tests**: Complete calculation workflows  
- **Standard compliance tests**: Reference cases from EN standards
- **Property-based tests**: ScalaCheck for invariants

## 📝 Contributing

When contributing to the engine:

1. Follow Scala best development practices
2. Maintain pure functional style
3. Add tests for new functionality
4. Document standard interpretations
5. Update relevant documentation

## 📄 License

See [LICENSE](../../LICENSE) - AGPL-3.0-or-later