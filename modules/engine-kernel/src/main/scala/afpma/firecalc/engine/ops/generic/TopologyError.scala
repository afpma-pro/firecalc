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
 *   PostFireboxChain   := HEAD_REGION  TERMINAL_CONNECTOR  CHIMNEY
 *   HEAD_REGION        := EMPTY | non-empty sequence of Flue/Connector ending with FluePipe
 *   TERMINAL_CONNECTOR := ConnectorPipeT   (mandatory slot; descriptor may be empty)
 *   CHIMNEY            := ChimneyPipeT     (mandatory, exactly one, last)
 * }}}
 *
 * Retained rules:
 *   - HEAD_REGION may be empty (EN 13384-only pipelines + legacy V6 YAML).
 *   - HEAD_REGION may start with either FluePipe or ConnectorPipe.
 *   - HEAD_REGION may contain any arrangement of Flue/Connector (no alternation
 *     constraint — two adjacent Flues or two adjacent Connectors are legal).
 *   - HEAD_REGION, if non-empty, must end with a FluePipe (preserves the fixed
 *     terminal-connector distinction — that slot is non-editable).
 */
enum TopologyError:
    /** The last slot must be ChimneyPipeT (or the slot vector is empty). */
    case MissingChimney

    /** A ChimneyPipeT appears somewhere other than the last slot. */
    case ChimneyNotLast

    /** The terminal connector slot between HEAD_REGION and CHIMNEY is missing. */
    case MissingTerminalConnector

    /**
     * HEAD_REGION ends with a ConnectorPipeT — it must end with a FluePipeT
     * (otherwise the connector would duplicate the terminal connector slot).
     */
    case HeadRegionEndsWithConnector
