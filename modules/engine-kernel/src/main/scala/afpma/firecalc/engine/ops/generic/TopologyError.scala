/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

/**
 * Validation errors for the post-firebox topology grammar.
 *
 * The grammar:
 * {{{
 *   PostFireboxChain := FLUE_PIPE_REGION  CONNECTOR_PIPE  CHIMNEY_PIPE
 *   FLUE_PIPE_REGION := (FluePipeT | ConnectorPipeT)*  FluePipeT  |  ε
 *   CONNECTOR_PIPE   := ConnectorPipeT  |  noop
 *   CHIMNEY_PIPE     := ChimneyPipeT  (always exactly one, always last)
 * }}}
 */
enum TopologyError:
    /** Rule 1: last slot must be ChimneyPipeT */
    case MissingChimney

    /** Rule 3: no FluePipeT may appear after the CONNECTOR_PIPE position */
    case FluePipeAfterConnector

    /** Rule 4: at most one ConnectorPipeT after the last FluePipeT */
    case MultipleConnectorsAfterFlue

    /** Rule 5: no ChimneyPipeT except the last slot */
    case ChimneyNotLast
