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
 *   HEAD_REGION        := alternating Flue/Connector (MAY BE EMPTY; if non-empty, must end with FluePipe)
 *   TERMINAL_CONNECTOR := ConnectorPipeT   (mandatory slot; descriptor may be empty)
 *   CHIMNEY            := ChimneyPipeT     (mandatory, exactly one, last)
 * }}}
 *
 * Derived rules:
 *   - HEAD_REGION may be empty (EN 13384-only pipelines + legacy V6 YAML).
 *   - HEAD_REGION may start with either FluePipe or ConnectorPipe.
 *   - No two consecutive pipes of the same type inside HEAD_REGION.
 *   - HEAD_REGION, if non-empty, must end with a FluePipe (cannot end with a
 *     ConnectorPipe, which would duplicate the terminal connector and violate
 *     the "no consecutive same type" rule across the HEAD↔TERMINAL boundary).
 */
enum TopologyError:
    /** The last slot must be ChimneyPipeT (or the slot vector is empty). */
    case MissingChimney

    /** A ChimneyPipeT appears somewhere other than the last slot. */
    case ChimneyNotLast

    /** The terminal connector slot between HEAD_REGION and CHIMNEY is missing. */
    case MissingTerminalConnector

    /** Two consecutive pipes of the same type appear inside HEAD_REGION. */
    case ConsecutiveSamePipeTypeInHead

    /**
     * HEAD_REGION ends with a ConnectorPipeT — it must end with a FluePipeT
     * (otherwise the connector would duplicate the terminal connector slot).
     */
    case HeadRegionEndsWithConnector
