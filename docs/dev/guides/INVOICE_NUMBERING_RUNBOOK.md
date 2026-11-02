<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
-->

# Invoice Numbering Runbook

> **Related docs**: the payments config block (where `counter-start` lives) is documented in [STAGING.md](STAGING.md) and [PRODUCTION.md](PRODUCTION.md); the webhook-to-status mapping in [GOCARDLESS.md](GOCARDLESS.md).

For the developer or on-call person who hit a refused start (`INVOICE NUMBERING GUARD: refusing to start`) or is about to point the payments backend at a different database file. Everything on this page is read-only until a repair section says otherwise; outside the bounded config repair described under *The three repairs*, the service never rewrites the counter on its own.

## What happens at boot

The payments backend, on every start, reads the invoice-numbering state of the single database file it was given and prints it — `=== invoice numbering state (boot) ===`, with the resolved db path, the counter row, the highest issued sequence number, orders numbered/unnumbered by status, and the configured prefix/width — whether or not the file then passes. It then checks the file against itself: the counter row, the issued numbers, and the string the next mint would produce. A contradiction is fatal in every environment, with no opt-out: the process exits before the HTTP port binds, refusing to start rather than mint a number this database already contains.

The page below: what the states mean, how to inspect a file without starting anything, and the repair for each of the three conditions the guard distinguishes.

## Which order states hold an invoice number

The invoice counter is advanced only by the transition to `Processing`, driven by the GoCardless `("payments","submitted")` event. That event is the point of no return: the payment has been sent to the banks and can no longer be cancelled by us, so an invoice is owed from that moment on. Every other status maps to exactly one GoCardless event:

| GoCardless payment state | matched webhook action | `OrderStatus` | invoice number | why |
|---|---|---|---|---|
| pending customer approval | none | `Pending` | **not owed** | GoCardless has not received the debit yet |
| pending submission | `created` (stores the payment id only); `pending_submission` is unhandled | `Pending` | **not owed** | not sent to the banks, still cancellable by us — nothing has been consumed |
| **submitted** | `("payments","submitted")` | `Processing` | **owed — the only mint point** | the customer has committed and the order cannot be walked back by us |
| confirmed | `("payments","confirmed")` | `Confirmed` | owed, already held from `Processing` | the customer's bank confirms collection |
| paid out | `("payments","paid_out")` | `PaidOut` | owed, already held | funds transferred to our account; a charge-back has no handler, so the order stays `PaidOut` and keeps its number |
| failed | `("payments","failed")` | `Failed` | **not owed** | routinely numberless: the report is generated before the number is minted and the report-failure handlers stop there; an order that reached `submitted` first keeps whatever number it holds |
| cancelled | `("payments","cancelled")` | `Cancelled` | **not owed** | cancelled before the charge date; a payment GoCardless created and cancelled without ever submitting consumed nothing |

Reading only this table: an unnumbered `Pending`, `Failed` or `Cancelled` order is **normal**, not a fault — do not "fix" an unpaid order by hand-assigning it a number. An unnumbered `Processing`, `Confirmed` or `PaidOut` order is a fault. Nothing consumes a number before `submitted`, and nothing ever clears a number, so an order that reached `submitted` and later failed keeps its number and leaves no hole in the sequence.

## Inspect a file without starting anything

The three questions the guard cares about, answered read-only against any payments database file:

```bash
sqlite3 -header "file:/path/to/firecalc-payments.db?mode=ro" <<'SQL'
-- 1. the counter row exactly as the file holds it
SELECT id, currentNumber, startingNumber FROM InvoiceCounter;
-- 2. highest sequence already issued: read the trailing digit run of the
--    stored strings, never a cut of a configured width
WITH issued(sequence) AS (
    SELECT CAST(substr(invoiceNumber, LENGTH(RTRIM(invoiceNumber, '0123456789')) + 1) AS INTEGER)
    FROM ProductOrder
    WHERE invoiceNumber IS NOT NULL AND invoiceNumber GLOB '*[0-9]'
)
SELECT IFNULL(MAX(sequence), 'none (no number has been issued)') AS highest_issued FROM issued;
-- 3. orders numbered / unnumbered by status
SELECT status,
       SUM(invoiceNumber IS NOT NULL) AS numbered,
       SUM(invoiceNumber IS NULL)     AS unnumbered
FROM ProductOrder
GROUP BY status
ORDER BY status;
SQL
```

How to read the results:

- **Counter** — exactly one row, `currentNumber` not null, and it must equal the highest issued sequence. `startingNumber` is the recorded sequence anchor; it is never rewritten. A database with no issued numbers must sit one below that start.
- **Sequence extraction** — the query takes the trailing digit run of each stored number, exactly like the guard's own `InvoiceNumberingState.sequenceNumber`, and deliberately does NOT hard-code this environment's prefix or width: those are environment-specific presentation (staging uses `FCALC-STG-[YYYY]-` with 4 digits, production `FCALC-[YYYY]-`), the year inside a prefix is not a series boundary, and a number written under a past width must still be judged by its actual value. One global sequence, trailing digits.
- **By status** — an unnumbered `Processing`/`Confirmed`/`PaidOut` row is the fault condition; everything else in the count is normal by the table above.

### Worked against the copies in the repository root

The current staging copy, and a rewound copy you make with (the rewind is what a file from an older point in time looks like when its counter has been dragged back below numbers it already holds):

```bash
sqlite3 "file:firecalc-payments-staging-20261014-1846.db?mode=ro" ".backup /tmp/rewound.db"
sqlite3 /tmp/rewound.db "UPDATE InvoiceCounter SET currentNumber = 1;"
```

actual output on `firecalc-payments-staging.db` (current):

```text
id|currentNumber|startingNumber
1|1|1
highest_issued
1
status|numbered|unnumbered
Processing|1|0
```

counter 1, highest issued 1 — consistent, this boots.

actual output on `/tmp/rewound.db`:

```text
id|currentNumber|startingNumber
1|1|1
highest_issued
6
status|numbered|unnumbered
PaidOut|6|0
Pending|0|3
```

counter 1, highest issued 6 — the counter is behind the numbers already issued. The three `Pending` rows are normal: they never owed a number. The single configuration value that would boot this file is `invoice.counter-start = 6` — see the first repair below; the guard prints exactly that value.

## The fatal block, word for word

When a start is refused, after the state block the startup prints (this is the real block for `/tmp/rewound.db` under the staging configuration, date-dependent lines as printed 2026-11-02):

```text
=== INVOICE NUMBERING GUARD: refusing to start ===
the invoice counter in this database contradicts the invoice numbers it already holds;
refusing to start rather than mint a number this database already contains.
db path              : /tmp/rewound.db
counter current      : 1
highest issued       : 6 (held as: FCALC-STG-2026-0006)
next mint would give : FCALC-STG-2025-0002 (previous year), FCALC-STG-2026-0002 (this year), FCALC-STG-2027-0002 (next year)
findings:
  [2] the counter is at 1 but sequence numbers up to 6 are already issued; the next mint would repeat an issued sequence
      repair option: set invoice.counter-start to 6 — the only value that can boot this database (it equals the highest sequence number already issued); the service verifies it at startup, raises the counter to it and logs the raise
      repair option: inspect and, if needed, edit the database file named above
  [7] the next invoice number this service would mint, FCALC-STG-2026-0002, is already held by order 07d7274d-87b9-429a-9e5a-7d8f43786ff1
```

The findings are tagged with the clause they violate; the three repairable conditions are `[2]`, `[3a]` and `[8]`, each below. For everything else the block can report — `[1]` no/ambiguous/null counter row, `[3b]` two orders holding the same number string, `[4]` a number below the recorded start, `[5]` an empty database whose counter is not one below the start, `[6]` a number carrying another environment's prefix, `[7]` a next-mint collision — the file contradicts itself and the block says: `operator repair : inspect and, if needed, edit the database file named above; this service never rewrites the counter on its own.`

## The three repairs

### 1. The counter is behind the numbers already issued

*Applies when:* the block reports `[2] the counter is at X but sequence numbers up to Y are already issued; the next mint would repeat an issued sequence`. The inspection query shows the same thing: `currentNumber` below `highest_issued`. This is what an older-but-self-consistent file, or a counter dragged back, looks like.

*Repair:* set `invoice.counter-start` (the `invoice { counter-start = … }` key in the environment's payments config) to the value the block printed — the highest sequence already issued. Exactly one value can ever boot this file. At startup the configured value is verified against the same checks before anything is written: if it passes, the counter is raised to it and the raise is logged (`Invoice counter raised from X to Y: the value came from configuration (invoice.counter-start) and was verified against this database's issued invoice numbers before writing` — the boot log is the only trace; the counter row keeps no audit trail). If it fails — anything lower fails as this same rewind finding, anything higher opens a hole and fails as the gap finding — nothing is written and the block adds: `repair attempt : the configuration asked for invoice.counter-start = Z; the value failed the checks below; nothing was written — the database still holds X`. Keep the key in step with the file afterwards: the value is required at config load and is evaluated at every start — when it equals the counter the file holds, nothing is attempted; when it differs, that value is judged by the same checks, and on a self-consistent file only the value the file itself holds passes (below it lands as this same rewind finding, above it as the gap finding). A config left behind by a database that has since issued numbers does NOT make the next start fatal: the file is internally consistent at whatever its counter now holds, so the service boots and prints one warning line naming the configured value and the database's counter — the config value was not applied because the database is consistent. Set the key to the value the warning names so the next start is silent. The refused-value framing shown above appears in the fatal block only when the file itself contradicts itself; there, set the key to the bootable value the block prints. The alternative repair, offered in the same block: inspect and, if needed, edit the database file named above.

### 2. A hole in the sequence

*Applies when:* the block reports `[3a] no order holds sequence N, yet the counter is at C`. The counter is ahead of a number that was never assigned — typically a mint that died between incrementing and assigning. The block names the unnumbered order that appears to have been owed the number (oldest candidate by creation time, masked email) and prints both repairs with the exact number string that was due:

> `sequence N appears owed to order <id> (<status>, customer <masked>)` …
> `repair option: assign the invoice number <string> to order <id>`
> `repair option: bring the counter back from C to N-1 so the invoice number <string> can be minted again`

*Repair:* choose one — this is a judgement call, never do both blindly. Either the report for that number was owed and never generated (assign the named number to the named order), or the number was minted and the report was abandoned (bring the counter back so the number can be minted again — either by editing the file, or through the same `invoice.counter-start` key set to `N-1`: the boot applies it only if the file is then self-consistent, and refuses with nothing written otherwise). The guard's own caution, which the block prints: *neither repair is safe before deciding which situation applies — was a report owed this number and never generated, or was the number minted and the report abandoned?*

### 3. An order owes a number while the sequence is intact

*Applies when:* the block reports `[8] order <id> is <status> and holds no invoice number …` together with `the issued sequence itself is undamaged — nothing is missing from it; this order simply never received its number`. A `Confirmed` order here means the startup numbering pass was attempted and failed for it; a `Processing` order can never repair itself (no transition re-enters `Processing`) — it is money on its way with no invoice, and the block says how long it has been sitting, measured from when the order entered that state.

*Repair:* assign the next number — the block names the exact string (`repair option: assign the next invoice number <string> to order <id>`) — but only after confirming the order really is owed one (the block's own caution), i.e. it actually reached `submitted` per the table above.

> **Warning:** assigning a number to a stuck `Processing` order gives the order an invoice number, but it does NOT produce the report and email that were never generated. The numbering gap is closed; the customer still has nothing in their inbox.

## Copy rule: a live payments database is never plain-copied

The operational payments databases run in WAL mode: the newest transactions can live in the `-wal` sidecar rather than in the `.db` file itself. A plain `cp` of the `.db` file can silently lose them. Always copy with SQLite's own backup command:

```bash
sqlite3 "file:/path/to/live.db?mode=ro" ".backup /path/to/copy.db"
```

The read-only URI source cannot write to the live file. Never `cp`, never `rsync` of a single `.db` file, for any copy that must be complete.

## Worked example: the b24 → b25 cutover

A deployment cutover handed the payments backend an older database file — one that was internally self-consistent: its counter equalled its own highest issued number, it had no holes and no duplicates, so every check that can inspect a file passes on it. The service started happily and re-issued numbers real customers already held: `FCALC-STG-2026-0002` and `-0003` were each issued twice, to different orders, across the cutover.

No application-side check can see this. A check only ever sees one file, and "this file is older than the file you meant to start" is not a property of either file alone. That residual risk was accepted knowingly: the guard exists to make the other direction impossible — a file that contradicts *itself* can no longer mint duplicates from inside its own start — and to make the wrong-file case visible to a human: the state block is printed on every boot, and the inspection query above answers the same questions before the service ever touches the file. Choosing which file to point the service at stays a human job; this page is how you do it.
