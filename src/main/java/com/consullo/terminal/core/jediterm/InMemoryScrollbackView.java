package com.consullo.terminal.core.jediterm;

import com.consullo.terminal.core.ScrollbackView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.Validate;

/**
 * Simple in-memory scrollback adapter used by the starter implementation.
 *
 * <p>This implementation treats all lines as history lines with no screen content,
 * suitable for testing and simple use cases where screen tracking is not needed.</p>
 *
 * @since 1.0
 */
public final class InMemoryScrollbackView implements ScrollbackView {

  private final List<String> scrollback;

  /**
   * Creates a new view over the provided scrollback list.
   *
   * @param scrollback backing scrollback list
   */
  public InMemoryScrollbackView(final List<String> scrollback) {
    Validate.notNull(scrollback, "scrollback must not be null");
    this.scrollback = scrollback;
  }

  @Override
  public int historyLineCount() {
    return this.scrollback.size();
  }

  @Override
  public int screenRowCount() {
    return 0; // In-memory view has no screen concept
  }

  @Override
  public List<String> readHistoryLines(final int startInclusive, final int endExclusive) {
    if (startInclusive < 0) {
      throw new IllegalArgumentException("startInclusive must be non-negative");
    }
    if (endExclusive < startInclusive) {
      throw new IllegalArgumentException("endExclusive must be >= startInclusive");
    }

    int end = Math.min(endExclusive, this.scrollback.size());
    if (startInclusive >= end) {
      return Collections.emptyList();
    }

    final List<String> result = new ArrayList<>(end - startInclusive);
    for (int i = startInclusive; i < end; i++) {
      result.add(this.scrollback.get(i));
    }

    return Collections.unmodifiableList(result);
  }

  @Override
  public List<String> readScreenLines(final int startInclusive, final int endExclusive) {
    return Collections.emptyList(); // In-memory view has no screen concept
  }
}
