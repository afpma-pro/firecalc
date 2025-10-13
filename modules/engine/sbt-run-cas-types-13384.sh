#!/bin/bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal


sbt -error "engine/testOnly *cas_types_13384_C2_Suite" > "modules/engine/validation/cas_types_13384/current/C2.afpma.txt"
sbt -error "engine/testOnly *cas_types_13384_C16_Suite" > "modules/engine/validation/cas_types_13384/current/C16.afpma.txt"
