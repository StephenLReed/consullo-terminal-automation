package com.consullo.terminal.capture;

import java.util.List;

/**
 * Policy for suppressing churn in volatile UI regions (spinners, progress bars, status lines).
 *
 * <p>The default policy uses simple string heuristics and update-frequency signals. No regex is used.
 *
 * @since 1.0
 */
public interface ChurnFilterPolicy {

  /**
   * Returns true if the provided row text should be suppressed as churn/animation output.
   *
   * @param rowText row text (already right-trimmed)
   * @param recentSamples recent samples for this row (may be empty)
   * @return true if suppressed
   * @throws Exception if evaluation fails
   */
  boolean shouldSuppressRow(final String rowText, final List<String> recentSamples) throws Exception;
}
