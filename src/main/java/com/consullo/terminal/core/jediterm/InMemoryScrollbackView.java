package com.consullo.terminal.core.jediterm;

import com.consullo.terminal.core.ScrollbackView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.Validate;

/**
 * Simple in-memory scrollback adapter used by the starter implementation.
 *
 * @since 1.0
 */
public final class InMemoryScrollbackView implements ScrollbackView {

  private final List<String> scrollback;

  /**
   * Creates a new view over the provided scrollback list.
   *
   * @param scrollback backing scrollback list
   * @throws Exception if scrollback is null
   */
  public InMemoryScrollbackView(final List<String> scrollback) throws Exception {
    Validate.notNull(scrollback, "scrollback must not be null");
    this.scrollback = scrollback;
  }

  @Override
  public int lineCount() throws Exception {
    return this.scrollback.size();
  }

  @Override
  public List<String> readLines(final int startInclusive, final int endExclusive) throws Exception {
    Validate.isTrue(startInclusive >= 0, "startInclusive must be non-negative");
    Validate.isTrue(endExclusive >= startInclusive, "endExclusive must be >= startInclusive");
    Validate.isTrue(endExclusive <= this.scrollback.size(), "endExclusive must be within bounds");

    final List<String> result = new ArrayList<>(endExclusive - startInclusive);
    for (int i = startInclusive; i < endExclusive; i++) {
      result.add(this.scrollback.get(i));
    }

    return Collections.unmodifiableList(result);
  }
}
