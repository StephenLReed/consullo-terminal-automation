package com.consullo.terminal.capture;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable state for the capture engine.
 *
 * @param lastEmittedScrollbackLineIndex last scrollback line index emitted to transcript
 * @param epoch logical epoch incremented on full redraw and resize events
 * @param rowDigests row digest map for stability tracking in the non-volatile region
 * @param rowLastChangedUtc last-change timestamps for each tracked row
 * @since 1.0
 */
public record CaptureEngineState(
    int lastEmittedScrollbackLineIndex,
    long epoch,
    Map<Integer, Long> rowDigests,
    Map<Integer, Instant> rowLastChangedUtc) {
}
