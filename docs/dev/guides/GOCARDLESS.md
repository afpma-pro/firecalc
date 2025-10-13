<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

./gc login

./gc list mandates

./gc listen --forward http://localhost:8181/v1/webhooks/gocardless

./gc trigger --help


./gc trigger payment_submitted
./gc trigger payment_confirmed
./gc trigger payment_paid_out

french_iban_for_testing.md

FR1420041010050500013M02606