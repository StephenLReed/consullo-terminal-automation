package com.consullo.terminal.core;

import java.util.List;

/**
 * Read-only view over terminal history and screen content.
 *
 * <p>The capture engine uses this interface to emit committed output while avoiding redraw churn.
 * History lines are scrolled-off content (stable). Screen lines are current display (may be volatile).
 *
 * @since 1.0
 */
public interface ScrollbackView {

  /**
   * Returns the number of history lines (scrolled off screen).
   *
   * @return history line count
   */
  int historyLineCount();

  /**
   * Returns the number of visible screen rows.
   *
   * @return screen row count
   */
  int screenRowCount();

  /**
   * Returns history lines for the specified range [startInclusive, endExclusive).
   * Index 0 is the oldest history line.
   *
   * @param startInclusive start line index inclusive
   * @param endExclusive end line index exclusive
   * @return list of lines (plain text, right-trimmed)
   */
  List<String> readHistoryLines(int startInclusive, int endExclusive);

  /**
   * Returns screen lines for the specified range [startInclusive, endExclusive).
   * Index 0 is the top screen row.
   *
   * @param startInclusive start row index inclusive
   * @param endExclusive end row index exclusive
   * @return list of lines (plain text, right-trimmed)
   */
  List<String> readScreenLines(int startInclusive, int endExclusive);

  /**
   * Legacy method for backwards compatibility.
   * Returns total line count (history + screen).
   */
  default int lineCount() {
    return historyLineCount() + screenRowCount();
  }

  /**
   * Legacy method for backwards compatibility.
   * Reads lines from combined history + screen view.
   */
  default List<String> readLines(int startInclusive, int endExclusive) {
    return readHistoryLines(startInclusive, endExclusive);
  }
}
