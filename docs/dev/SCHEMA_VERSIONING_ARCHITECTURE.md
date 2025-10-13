<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Schema Versioning Architecture

**Date**: October 9, 2025  
**Status**: ✅ ACTIVE - Production Architecture

## Table of Contents

- [Overview](#overview)
- [Architecture Principles](#architecture-principles)
- [Versioned Components](#versioned-components)
- [Version Opaque Types](#version-opaque-types)
- [Migration Infrastructure](#migration-infrastructure)
- [Creating New Versions](#creating-new-versions)
- [Module Boundaries](#module-boundaries)
- [Data Flow](#data-flow)
- [Best Practices](#best-practices)

---

## Overview

FireCalc implements a **unified versioned schema architecture** for managing application state evolution across multiple independent components. Each component can version independently while maintaining type safety through opaque version types and Chimney transformers.

### Key Benefits

✅ **Independent Evolution** - Components version separately (e.g., ClientProjectData V3 + BillingInfo V2 + FireCalcYAML V1)  
✅ **Type Safety** - Opaque types prevent version mixing  
✅ **Migration Safety** - Chimney transformers provide compile-time guarantees  
✅ **Explicit Versioning** - All schemas have explicit version fields  
✅ **Module Boundaries** - Clear separation between dto, ui, and payments-shared modules  
✅ **Backward Compatibility** - Migration paths handle old data gracefully

---

## Architecture Principles

### 1. Independent Versioning

Each component maintains its own version number and evolution path:

```scala
AppStateSchema_V1 {
    version: AppStateSchema_Version = 1
    engine_state: FireCalcYAML_V1 { version: FireCalc_Version = 1 }
    sensitive_data: ClientProjectData_V1 { version: ClientProjectData_Version = 1 }
    billing_data: BillingInfo_V1 { version: BillingInfo_Version = 1 }
}
```

### 2. Opaque Types for Version Safety

Each version type is an opaque wrapper around `Int` to prevent accidental mixing:

```scala
opaque type ClientProjectData_Version = Int
opaque type BillingInfo_Version = Int
opaque type AppStateSchema_Version = Int
opaque type FireCalc_Version = Int
```

### 3. Module Separation

- **`dto` module** - Engine state only ([`FireCalcYAML_V1`](../../modules/dto/src/main/scala/afpma/firecalc/dto/v1/FireCalcYAML_V1.scala))
- **`ui` module** - Client state ([`AppStateSchema_V1`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/AppStateSchema_V1.scala), [`ClientProjectData_V1`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/ClientProjectData_V1.scala), [`BillingInfo_V1`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/BillingInfo_V1.scala))
- **`payments-shared` module** - Payment API contracts ([`CustomerInfo_V1`](../../modules/payments-shared/src/main/scala/afpma/firecalc/payments/shared/api/v1.scala))

---

## Versioned Components

### 1. AppStateSchema_V1 (UI Module)

**Location**: [`modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/AppStateSchema_V1.scala`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/AppStateSchema_V1.scala)

**Purpose**: Unified container for all client-side state, persisted atomically in localStorage.

```scala
final case class AppStateSchema_V1(
    version: AppStateSchema_Version = AppStateSchema_Version(1),
    
    /** Engine state - sent to backend for PDF generation */
    engine_state: FireCalcYAML_V1,
    
    /** Sensitive data - NEVER sent to backend */
    sensitive_data: ClientProjectData_V1,
    
    /** Billing data - transforms to CustomerInfo_V1 for payment API */
    billing_data: BillingInfo_V1
)
```

**Version Type**: [`AppStateSchema_Version`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/common/AppStateSchema_Version.scala)

**Migrations**: When creating V2, define transformer in a new `AppStateSchema_V1_to_V2` migration object

### 2. FireCalcYAML_V1 (DTO Module)

**Location**: [`modules/dto/src/main/scala/afpma/firecalc/dto/v1/FireCalcYAML_V1.scala`](../../modules/dto/src/main/scala/afpma/firecalc/dto/v1/FireCalcYAML_V1.scala)

**Purpose**: Technical project data sent to backend for PDF generation. Contains NO sensitive customer information.

```scala
final case class FireCalcYAML_V1(
    version: FireCalc_Version = FireCalc_Version(1),
    locale: Locale,
    display_units: DisplayUnits,
    standard_or_computation_method: StandardOrComputationMethod,
    project_description: ProjectDescr,  // reference, date, country only
    local_conditions: LocalConditions,
    stove_params: StoveParams,
    air_intake_descr: Seq[IncrDescr_13384],
    firebox: Firebox,
    flue_pipe_descr: Seq[IncrDescr_15544],
    connector_pipe_descr: Seq[IncrDescr_13384],
    chimney_pipe_descr: Seq[IncrDescr_13384],
)
```

**Version Type**: [`FireCalc_Version`](../../modules/dto/src/main/scala/afpma/firecalc/dto/common/FireCalc_Version.scala)

**Security**: This data is safe to send to backend - no personal customer information

### 3. ClientProjectData_V1 (UI Module)

**Location**: [`modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/ClientProjectData_V1.scala`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/ClientProjectData_V1.scala)

**Purpose**: Sensitive customer information stored ONLY client-side in localStorage.

```scala
final case class ClientProjectData_V1(
    version: ClientProjectData_Version = ClientProjectData_Version(1),
    customer: Customer,
    billing_address: Address,
    project_address: Address
)
```

**Version Type**: [`ClientProjectData_Version`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/common/ClientProjectData_Version.scala)

**Migrations**: [`ClientProjectDataMigrations`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/migrations/ClientProjectDataMigrations.scala)

**Security**: 🔒 **NEVER sent to backend** - stays in browser localStorage only

### 4. BillingInfo_V1 (UI Module)

**Location**: [`modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/BillingInfo_V1.scala`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/BillingInfo_V1.scala)

**Purpose**: UI billing form data, stored client-side and transformed to [`CustomerInfo_V1`](../../modules/payments-shared/src/main/scala/afpma/firecalc/payments/shared/api/v1.scala) when calling payment APIs.

```scala
case class BillingInfo_V1(
  version: BillingInfo_Version = BillingInfo_Version(1),
  customer_type: BillableCustomerType,
  given_name: Option[GivenName],
  family_name: Option[FamilyName],
  company_name: Option[CompanyName],
  address_line1: String,
  address_line2: Option[String],
  address_line3: Option[String],
  city: String,
  region: Option[String],
  postal_code: String,
  country_code: BillableCountry,
  email: String,
  phone_number: Option[String],
)
```

**Version Type**: [`BillingInfo_Version`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/common/BillingInfo_Version.scala)

**Migrations**: [`BillingInfoMigrations`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/migrations/BillingInfoMigrations.scala)

**Data Flow**: UI form → BillingInfo_V1 → (transform) → CustomerInfo_V1 → Payment API

### 5. CustomerInfo_V1 (Payments-Shared Module)

**Location**: [`modules/payments-shared/src/main/scala/afpma/firecalc/payments/shared/api/v1.scala`](../../modules/payments-shared/src/main/scala/afpma/firecalc/payments/shared/api/v1.scala)

**Purpose**: Payment backend API contract. Transformed from [`BillingInfo_V1`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v1/BillingInfo_V1.scala) when making payment requests.

```scala
case class CustomerInfo_V1(
    email: String,
    customerType: CustomerType,
    language: BackendCompatibleLanguage,
    givenName: Option[String],
    familyName: Option[String],
    companyName: Option[String],
    addressLine1: Option[String],
    addressLine2: Option[String],
    addressLine3: Option[String],
    city: Option[String],
    region: Option[String],
    postalCode: Option[String],
    countryCode: Option[CountryCode_ISO_3166_1_ALPHA_2],
    phoneNumber: Option[String],
)
```

**Migrations**: [`CustomerInfoMigrations`](../../modules/payments-shared/src/main/scala/afpma/firecalc/payments/shared/api/migrations/CustomerInfoMigrations.scala)

**Module Boundary**: Shared between UI and payment backend for API contracts

---

## Version Opaque Types

Each versioned component has its own opaque type to ensure type safety and prevent accidental version mixing.

### Structure

All version types follow this pattern:

```scala
// Definition
opaque type ComponentName_Version = Int

object ComponentName_Version:
    def apply(v: Int): ComponentName_Version = v
    
    extension (v: ComponentName_Version)
        def toInt: Int = v
        
    given Ordering[ComponentName_Version] = Ordering.by(_.toInt)
    
    // Circe JSON serialization
    given Encoder[ComponentName_Version] = Encoder[Int].contramap(_.toInt)
    given Decoder[ComponentName_Version] = Decoder[Int].map(ComponentName_Version.apply)
```

### Existing Version Types

| Type | Location | Purpose |
|------|----------|---------|
| [`AppStateSchema_Version`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/common/AppStateSchema_Version.scala) | `ui/models/schema/common/` | AppStateSchema container version |
| [`FireCalc_Version`](../../modules/dto/src/main/scala/afpma/firecalc/dto/common/FireCalc_Version.scala) | `dto/common/` | FireCalcYAML engine state version |
| [`ClientProjectData_Version`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/common/ClientProjectData_Version.scala) | `ui/models/schema/common/` | Sensitive data version |
| [`BillingInfo_Version`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/common/BillingInfo_Version.scala) | `ui/models/schema/common/` | Billing form data version |

### Benefits

✅ **Type Safety** - Prevents accidentally using wrong version type  
✅ **Explicit Intent** - Code clearly shows which version is being used  
✅ **Compiler Guarantees** - Mismatched versions caught at compile time  
✅ **Clean Serialization** - Circe encoders/decoders in companion objects

---

## Migration Infrastructure

### Chimney Transformers

We use [Chimney](https://scalalandio.github.io/chimney/) for type-safe schema migrations:

```scala
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

given Transformer[ComponentName_V1, ComponentName_V2] = 
    Transformer.define[ComponentName_V1, ComponentName_V2]
        .withFieldConst(_.new_field, defaultValue)
        .withFieldRenamed(_.old_name, _.new_name)
        .buildTransformer
```

### Migration Objects

Each component has a migration object:

| Component | Migration Object | Location |
|-----------|-----------------|----------|
| ClientProjectData | [`ClientProjectDataMigrations`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/migrations/ClientProjectDataMigrations.scala) | `ui/models/schema/migrations/` |
| BillingInfo | [`BillingInfoMigrations`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/migrations/BillingInfoMigrations.scala) | `ui/models/schema/migrations/` |
| CustomerInfo | [`CustomerInfoMigrations`](../../modules/payments-shared/src/main/scala/afpma/firecalc/payments/shared/api/migrations/CustomerInfoMigrations.scala) | `payments-shared/api/migrations/` |

### Migration Pattern

```scala
object ComponentNameMigrations:

    // V1 → V2 transformer
    given Transformer[ComponentName_V1, ComponentName_V2] = 
        Transformer.define[ComponentName_V1, ComponentName_V2]
            .withFieldConst(_.new_field, defaultValue)
            .buildTransformer
    
    // V2 → V3 transformer
    given Transformer[ComponentName_V2, ComponentName_V3] = 
        Transformer.define[ComponentName_V2, ComponentName_V3]
            .withFieldRenamed(_.old_name, _.new_name)
            .buildTransformer
    
    // Migrate to latest version
    def migrateToLatest(data: ComponentName_V1): ComponentName_V3 = 
        data
            .transformInto[ComponentName_V2]  // V1 → V2
            .transformInto[ComponentName_V3]  // V2 → V3
```

---

## Creating New Versions

### Step-by-Step Guide

#### 1. Create New Version File

```bash
# For ClientProjectData V2
touch modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/v2/ClientProjectData_V2.scala
```

#### 2. Define New Schema

```scala
package afpma.firecalc.ui.models.schema.v2

import afpma.firecalc.dto.common.{Customer, Address}
import afpma.firecalc.ui.models.schema.common.ClientProjectData_Version
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto

final case class ClientProjectData_V2(
    version: ClientProjectData_Version = ClientProjectData_Version(2),  // ⬅️ Update version
    customer: Customer,
    billing_address: Address,
    project_address: Address,
    secondary_contact: Option[Customer] = None  // ⬅️ New field with default
)

object ClientProjectData_V2:
    given Encoder[ClientProjectData_V2] = semiauto.deriveEncoder[ClientProjectData_V2]
    given Decoder[ClientProjectData_V2] = semiauto.deriveDecoder[ClientProjectData_V2]
```

#### 3. Add Migration Transformer

In [`ClientProjectDataMigrations.scala`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/migrations/ClientProjectDataMigrations.scala):

```scala
import afpma.firecalc.ui.models.schema.v2.ClientProjectData_V2

given Transformer[ClientProjectData_V1, ClientProjectData_V2] = 
    Transformer.define[ClientProjectData_V1, ClientProjectData_V2]
        .withFieldConst(_.version, ClientProjectData_Version(2))
        .withFieldConst(_.secondary_contact, None)  // Default for existing data
        .buildTransformer

def migrateToLatest(data: ClientProjectData_V1): ClientProjectData_V2 = 
    data.transformInto[ClientProjectData_V2]
```

#### 4. Update Type Alias

In [`ClientProjectData.scala`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/ClientProjectData.scala):

```scala
import afpma.firecalc.ui.models.schema.v2.*

// Update to point to V2
type ClientProjectData = ClientProjectData_V2

object ClientProjectData:
    val LATEST_VERSION: Int = 2  // ⬅️ Update version number
    export ClientProjectData_V2.{given, *}
```

#### 5. Update Form Instances (UI Module only)

For UI components with forms (e.g., [`BillingInfoUI.scala`](../../modules/ui/src/main/scala/afpma/firecalc/ui/models/BillingInfoUI.scala)):

```scala
// Add form instances for new fields
given Defaultable[Option[Customer]] = Defaultable(None)
given ValidateVar[Option[Customer]] = ValidateVar.make(_ => VNelString.validUnit)
given DaisyUIVerticalForm[Option[Customer]] = // ... form derivation
```

#### 6. Test Migration

```scala
// In test suite
test("ClientProjectData V1 → V2 migration") {
  val v1 = ClientProjectData_V1(
    customer = Customer.empty,
    billing_address = Address.empty_butInFrance,
    project_address = Address.empty_butInFrance
  )
  
  val v2 = v1.transformInto[ClientProjectData_V2]
  
  assert(v2.version.toInt == 2)
  assert(v2.secondary_contact.isEmpty)
}
```

---

## Module Boundaries

### Module Separation Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Module (Scala.js)                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ AppStateSchema_V1                                    │    │
│  │  ├─ engine_state: FireCalcYAML_V1 (from dto module) │    │
│  │  ├─ sensitive_data: ClientProjectData_V1            │    │
│  │  └─ billing_data: BillingInfo_V1                    │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                             │
                             ├─────────────────────────────────┐
                             │                                  │
                             ▼                                  ▼
┌──────────────────────────────────┐    ┌──────────────────────────────────┐
│      DTO Module (JVM/JS)         │    │  Payments-Shared Module (JVM/JS) │
│  ┌────────────────────────────┐  │    │  ┌────────────────────────────┐  │
│  │ FireCalcYAML_V1            │  │    │  │ CustomerInfo_V1            │  │
│  │ (engine state only)        │  │    │  │ (payment API contract)     │  │
│  └────────────────────────────┘  │    │  └────────────────────────────┘  │
└──────────────────────────────────┘    └──────────────────────────────────┘
              │                                        ▲
              │                                        │
              ▼                                        │
┌──────────────────────────────────┐                  │
│   Backend (PDF Generation)       │                  │
│   Receives: FireCalcYAML_V1      │                  │
│   (NO sensitive data)            │                  │
└──────────────────────────────────┘                  │
                                                       │
                                    ┌──────────────────────────────────┐
                                    │   Payment Backend                 │
                                    │   Receives: CustomerInfo_V1       │
                                    │   (transformed from BillingInfo)  │
                                    └──────────────────────────────────┘
```

### Module Responsibilities

| Module | Contains | Purpose | Sent To |
|--------|----------|---------|---------|
| **dto** | `FireCalcYAML_V1` | Technical project data | PDF backend ✅ |
| **ui** | `AppStateSchema_V1`<br>`ClientProjectData_V1`<br>`BillingInfo_V1` | Client state<br>Sensitive data<br>Billing forms | localStorage only<br>Never sent 🔒<br>Payment backend (as CustomerInfo_V1) |
| **payments-shared** | `CustomerInfo_V1` | Payment API contract | Payment backend ✅ |

---

## Data Flow

### 1. Client-Side Storage (localStorage)

```scala
// In Frontend.scala
val writeStorageVarSubscription = appStateSchemaVar
    .signal.changes.distinct
    .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS) 
    --> appStateSchemaWebStorageVar.writer
```

**Stored in localStorage**:
```json
{
  "version": 1,
  "engine_state": { "version": 1, /* FireCalcYAML_V1 */ },
  "sensitive_data": { "version": 1, /* ClientProjectData_V1 */ },
  "billing_data": { "version": 1, /* BillingInfo_V1 */ }
}
```

### 2. PDF Generation Request

**Sent to Backend**:
```scala
// Only FireCalcYAML_V1 is sent
POST /api/generate-pdf
{
  "version": 1,
  "locale": "fr",
  "project_description": {
    "reference": "PROJECT-123",
    "date": "2025-10-09",
    "country": "France"
    // ✅ NO customer, billing_address, project_address
  },
  // ... technical data only
}
```

### 3. Payment Request

**Transformation Flow**:
```scala
BillingInfo_V1 
  → BillingInfoWithLanguage (add language)
  → CustomerInfo_V1 (transform via Chimney)
  → Payment Backend API
```

**Sent to Payment Backend**:
```scala
POST /api/purchase/create-intent
{
  "customer": {  // CustomerInfo_V1
    "email": "customer@example.com",
    "customerType": "Individual",
    "language": "fr",
    // ... address fields
  }
}
```

---

## Best Practices

### 1. Version Field Management

✅ **DO**: Always include version field as first parameter with default value
```scala
case class Schema_V1(
    version: Schema_Version = Schema_Version(1),  // ⬅️ First field, default value
    // ... other fields
)
```

❌ **DON'T**: Omit version field or place it elsewhere
```scala
case class Schema_V1(
    data: String,
    version: Schema_Version  // ❌ Not first, no default
)
```

### 2. Migration Safety

✅ **DO**: Always provide default values for new fields
```scala
given Transformer[Schema_V1, Schema_V2] = 
    Transformer.define[Schema_V1, Schema_V2]
        .withFieldConst(_.new_optional_field, None)  // ⬅️ Safe default
        .buildTransformer
```

❌ **DON'T**: Add required fields without defaults
```scala
case class Schema_V2(
    existing: String,
    required_new_field: String  // ❌ How to migrate existing data?
)
```

### 3. Type Alias Updates

✅ **DO**: Update `LATEST_VERSION` constant when creating new versions
```scala
object Schema:
    val LATEST_VERSION: Int = 2  // ⬅️ Keep in sync
    type Schema = Schema_V2
```

❌ **DON'T**: Forget to update version constant
```scala
object Schema:
    val LATEST_VERSION: Int = 1  // ❌ Out of sync
    type Schema = Schema_V2
```

### 4. Circe Encoders/Decoders

✅ **DO**: Define encoders/decoders in companion object
```scala
object Schema_V1:
    given Encoder[Schema_V1] = semiauto.deriveEncoder[Schema_V1]
    given Decoder[Schema_V1] = semiauto.deriveDecoder[Schema_V1]
```

✅ **DO**: Define version type encoders/decoders in version type companion
```scala
object Schema_Version:
    given Encoder[Schema_Version] = Encoder[Int].contramap(_.toInt)
    given Decoder[Schema_Version] = Decoder[Int].map(Schema_Version.apply)
```

### 5. Breaking Changes

When making breaking changes:

1. **Create V2 alongside V1** (don't modify V1)
2. **Add migration transformer** in Migrations object
3. **Update type alias** to point to V2
4. **Test migration path** with real data
5. **Update `LATEST_VERSION`** constant
6. **Keep V1 code** for reference and potential rollback

### 6. Testing Migrations

Always test:
- ✅ V1 → V2 transformation works
- ✅ Default values are correct
- ✅ JSON serialization/deserialization
- ✅ Version field updates correctly
- ✅ No data loss during migration

```scala
test("Schema V1 → V2 migration preserves data") {
  val v1 = Schema_V1(/* ... */)
  val v2 = v1.transformInto[Schema_V2]
  
  assert(v2.version.toInt == 2)
  // Verify all V1 fields transferred correctly
  // Verify new fields have correct defaults
}
```

---

## Related Documentation

- **[Architecture Overview](../../ARCHITECTURE.md)** - High-level system design
- **[UI Module Config](../../modules/ui/CONFIG.md)** - Frontend configuration

---

## Future Evolution Examples

### Example: Adding Tax Information to BillingInfo

**1. Create BillingInfo_V2**:
```scala
case class BillingInfo_V2(
    version: BillingInfo_Version = BillingInfo_Version(2),
    // ... all V1 fields ...
    tax_id: Option[TaxId] = None,  // New field
    vat_number: Option[VATNumber] = None  // New field
)
```

**2. Add Migration**:
```scala
// In BillingInfoMigrations.scala
given Transformer[BillingInfo_V1, BillingInfo_V2] = 
    Transformer.define[BillingInfo_V1, BillingInfo_V2]
        .withFieldConst(_.version, BillingInfo_Version(2))
        .withFieldConst(_.tax_id, None)
        .withFieldConst(_.vat_number, None)
        .buildTransformer
```

**3. Update CustomerInfo API** (if needed):
```scala
// In payments-shared/api/v2.scala
case class CustomerInfo_V2(
    // ... all V1 fields ...
    taxId: Option[String],
    vatNumber: Option[String]
)

// Add transformer from BillingInfo_V2 to CustomerInfo_V2
```

---

## Maintenance Checklist

When creating a new version:

- [ ] Create `ComponentName_VX.scala` file
- [ ] Add version field as first parameter with default value
- [ ] Define Circe encoders/decoders in companion object
- [ ] Create migration transformer from V(X-1) to VX
- [ ] Update `migrateToLatest()` in Migrations object
- [ ] Update type alias to point to VX
- [ ] Update `LATEST_VERSION` constant
- [ ] Add form instances (if UI component)
- [ ] Write migration tests
- [ ] Update documentation
- [ ] Verify all modules compile
- [ ] Test with real data migration

---

**Questions or Issues?** See [Developer Guide](./README.md) or review [Architecture Documentation](../../ARCHITECTURE.md)