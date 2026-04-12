/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.ui.models.schema.AppStateSchema
import afpma.firecalc.ui.models.schema.AppStateSchema.given

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var

import io.circe.Json
import io.circe.syntax.*

final class UndoManager(maxDepth: Int = 1000):

    // Past states — most recent is last
    private val undoStack = Var[Vector[AppStateSchema]](Vector.empty)

    // Future states (populated by undo, cleared on new edit)
    private val redoStack = Var[Vector[AppStateSchema]](Vector.empty)

    // Guard: prevents undo/redo restores from being re-captured as new snapshots
    private var isRestoring: Boolean = false

    // Last known state + JSON — pushSnapshot pushes the PREVIOUS state to the undo stack
    // when a new (different) state arrives. This avoids the bug where the current state
    // sits on top of the stack, causing a no-op first undo.
    private var lastKnownState: Option[AppStateSchema] = None
    private var lastKnownJson: Option[Json]            = None

    // --- Public Signals for UI binding ---

    val canUndo: Signal[Boolean] = undoStack.signal.map(_.nonEmpty)
    val canRedo: Signal[Boolean] = redoStack.signal.map(_.nonEmpty)

    val cannotUndo: Signal[Boolean] = canUndo.map(!_)
    val cannotRedo: Signal[Boolean] = canRedo.map(!_)

    // --- Public Methods ---

    /** Called by the debounced observer when appStateSchemaVar changes.
      * Pushes the PREVIOUS known state to the undo stack (not the current one).
      * First call just records the baseline; subsequent calls push the diff. */
    def pushSnapshot(current: AppStateSchema): Unit =
        if !isRestoring then
            val currentJson = current.asJson
            if !lastKnownJson.contains(currentJson) then
                lastKnownState.foreach { prev =>
                    val stack   = undoStack.now()
                    val trimmed = if stack.size >= maxDepth then stack.drop(1) else stack
                    undoStack.set(trimmed :+ prev)
                    redoStack.set(Vector.empty)
                }
                lastKnownState = Some(current)
                lastKnownJson  = Some(currentJson)

    /** Undo: pop from undo stack, push current to redo, return the state to restore.
      * Returns None if nothing to undo. */
    def undo(currentState: => AppStateSchema): Option[AppStateSchema] =
        val stack = undoStack.now()
        if stack.isEmpty then None
        else
            val previous     = stack.last
            val current      = currentState
            val previousJson = previous.asJson
            undoStack.set(stack.init)
            redoStack.update(_ :+ current)
            lastKnownState = Some(previous)
            lastKnownJson  = Some(previousJson)
            Some(previous)

    /** Redo: pop from redo stack, push current to undo, return the state to restore.
      * Returns None if nothing to redo. */
    def redo(currentState: => AppStateSchema): Option[AppStateSchema] =
        val stack = redoStack.now()
        if stack.isEmpty then None
        else
            val next    = stack.last
            val current = currentState
            val nextJson = next.asJson
            redoStack.set(stack.init)
            undoStack.update(_ :+ current)
            lastKnownState = Some(next)
            lastKnownJson  = Some(nextJson)
            Some(next)

    /** Clear both stacks. Called on project load. */
    def reset(): Unit =
        undoStack.set(Vector.empty)
        redoStack.set(Vector.empty)
        lastKnownState = None
        lastKnownJson  = None

    /** Set/unset the restoring guard. Callers bracket state restoration with this. */
    def withRestoring[A](f: => A): A =
        isRestoring = true
        try f
        finally isRestoring = false
