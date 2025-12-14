package com.consullo.terminal.core.events;

import java.time.Instant;

/**
 * Represents a terminal-state change event that can be used to trigger partial repaint and transcript capture.
 *
 * <p>The reference implementation uses a coarse damage model (changed row ranges). This is sufficient for capture
 * and for a Swing view scaffold. If higher-fidelity repaint is required, extend this to include rectangles.
 *
 * @param timestampUtc event timestamp in UTC
 * @param changedRowStart first changed row (inclusive)
 * @param changedRowEnd last changed row (exclusive)
 * @param fullRedraw true when the change represents a full screen redraw (e.g., clear screen)
 * @since 1.0
 */
public record DamageEvent(
    Instant timestampUtc,
    int changedRowStart,
    int changedRowEnd,
    boolean fullRedraw) {

  /**
   * Creates a full-redraw damage event.
   *
   * @return full redraw event
   */
  public static DamageEvent createFullRedraw() {
    return new DamageEvent(Instant.now(), 0, Integer.MAX_VALUE, true);
  }

  /**
   * Creates a partial damage event for specified row range.
   *
   * @param startRow first changed row (inclusive)
   * @param endRow last changed row (exclusive)
   * @return partial damage event
   */
  public static DamageEvent rows(int startRow, int endRow) {
    return new DamageEvent(Instant.now(), startRow, endRow, false);
  }
}
