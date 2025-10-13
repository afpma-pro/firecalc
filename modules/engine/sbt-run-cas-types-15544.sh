#!/bin/bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal


sbt -error "engine/testOnly *cas_types_15544_C1_Suite" > "modules/engine/validation/cas_types_15544/current/01 - Colonne ascendante.afpma.txt"
sbt -error "engine/testOnly *cas_types_15544_C2_Suite" > "modules/engine/validation/cas_types_15544/current/02 - Kachelofen.afpma.txt"
sbt -error "engine/testOnly *cas_types_15544_C3_Suite" > "modules/engine/validation/cas_types_15544/current/03 - Cas pratique.afpma.txt"