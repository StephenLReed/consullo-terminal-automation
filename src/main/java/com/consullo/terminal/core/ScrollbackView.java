package com.consullo.terminal.core;

import java.util.List;

/**
 * Read-only view over terminal scrollback.
 *
 * <p>The capture engine uses this interface to emit committed output while avoiding redraw churn.
 *
 * @since 1.0
 */
public interface ScrollbackView {

  /**
   * Returns the number of scrollback lines currently available.
   *
   * @return line count
   * @throws Exception if scrollback cannot be queried
   */
  int lineCount() throws Exception;

  /**
   * Returns the scrollback lines for the specified range [startInclusive, endExclusive).
   *
   * <p>Implementations should return plain text without ANSI escapes.
   *
   * @param startInclusive start line index inclusive
   * @param endExclusive end line index exclusive
   * @return list of lines
   * @throws Exception if scrollback cannot be accessed
   */
  List<String> readLines(final int startInclusive, final int endExclusive) throws Exception;
}
