package com.consullo.terminal.core;

import java.util.List;

/**
 * Immutable snapshot of terminal state intended for capture decisions and UI rendering.
 *
 * <p>This snapshot is a simplified representation:
 * - A list of visible rows as plain text
 * - Cursor position
 * - Whether the alternate screen buffer is active
 *
 * <p>The reference capture engine operates on plain-text rows and scrollback deltas, and does not require
 * detailed cell attributes. If future use cases require attributes (colors, styles, hyperlinks), extend this
 * object to include a cell grid representation.
 *
 * @param rows visible terminal rows, in top-to-bottom order
 * @param cursorRow cursor row (0-based)
 * @param cursorColumn cursor column (0-based)
 * @param alternateScreen true when the terminal is currently in the alternate screen buffer
 * @since 1.0
 */
public record TerminalSnapshot(
    List<String> rows,
    int cursorRow,
    int cursorColumn,
    boolean alternateScreen) {
}
