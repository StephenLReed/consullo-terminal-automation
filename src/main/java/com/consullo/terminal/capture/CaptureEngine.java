package com.consullo.terminal.capture;

import com.consullo.terminal.core.ScrollbackView;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.TerminalSnapshot;
import com.consullo.terminal.core.events.DamageEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts terminal state updates into churn-suppressed transcript events.
 *
 * <p>
 * Strategy:
 * <ul>
 * <li>Emit new scrollback/history lines immediately (high signal, committed content).</li>
 * <li>Emit screen lines only after stable for stabilityWindowMillis (lower signal, volatile).</li>
 * <li>Skip volatile bottom rows (spinner/progress region).</li>
 * <li>Suppress screen emissions in alternate screen mode if configured.</li>
 * <li>Deduplicate to avoid re-emitting identical content.</li>
 * </ul>
 * </p>
 */
public final class CaptureEngine {

  private final CaptureEngineConfig config;
  private final ChurnFilterPolicy churnPolicy;

  private int lastEmittedScrollbackIndex;

  // Screen stability tracking: row index -> state
  private final Map<Integer, ScreenRowState> screenRowStates = new HashMap<>();

  // Deduplication: track content hashes of already-emitted lines
  private final Set<Integer> emittedContentHashes = new HashSet<>();

  /**
   * Tracks the state of a single screen row for stability detection.
   */
  private static final class ScreenRowState {
    String content;
    long firstSeenMillis;
    boolean emitted;

    ScreenRowState(String content, long firstSeenMillis) {
      this.content = content;
      this.firstSeenMillis = firstSeenMillis;
      this.emitted = false;
    }
  }

  public CaptureEngine(CaptureEngineConfig config, ChurnFilterPolicy churnPolicy) {
    if (config == null || churnPolicy == null) {
      throw new IllegalArgumentException("config/churnPolicy must not be null.");
    }
    this.config = config;
    this.churnPolicy = churnPolicy;
    this.lastEmittedScrollbackIndex = 0;
  }

  /**
   * Handle a terminal damage notification and produce transcript events.
   *
   * @param core terminal core
   * @param snapshot snapshot
   * @param event event
   * @return transcript events
   */
  public List<TranscriptEvent> onDamage(TerminalCore core, TerminalSnapshot snapshot, DamageEvent event) throws Exception {
    if (core == null) {
      throw new IllegalArgumentException("core must not be null.");
    }

    List<TranscriptEvent> out = new ArrayList<>();
    long now = System.currentTimeMillis();

    // Step A: emit history/scrollback deltas immediately
    ScrollbackView sb = core.scrollback();
    int historyCount = sb.historyLineCount();

    if (lastEmittedScrollbackIndex < 0) {
      lastEmittedScrollbackIndex = 0;
    }
    if (lastEmittedScrollbackIndex > historyCount) {
      lastEmittedScrollbackIndex = historyCount;
    }

    List<String> newHistoryLines = sb.readHistoryLines(lastEmittedScrollbackIndex, historyCount);
    for (String line : newHistoryLines) {
      if (line == null) {
        continue;
      }
      String normalized = normalizeLine(line);

      // Skip blank lines - they're padding from the screen buffer, not real content
      if (normalized.isEmpty()) {
        continue;
      }

      // Check churn filter
      boolean suppress = churnPolicy.shouldSuppressRow(normalized, List.of());
      if (suppress) {
        continue;
      }

      // Deduplicate
      int hash = normalized.hashCode();
      if (emittedContentHashes.contains(hash)) {
        continue;
      }
      emittedContentHashes.add(hash);

      out.add(TranscriptEvent.append(normalized + "\n", Instant.now(), TranscriptEvent.Source.SCROLLBACK));
    }
    lastEmittedScrollbackIndex = historyCount;

    // Step B: screen stability tracking
    // Skip if alternate screen mode and configured to suppress
    boolean inAltScreen = snapshot != null && snapshot.isAlternateScreen();
    if (inAltScreen && config.suppressAlternateScreen()) {
      // Clear screen state when entering alt screen
      screenRowStates.clear();
      return out;
    }

    int screenRows = sb.screenRowCount();
    int stableRowLimit = screenRows - config.volatileRowCount();
    if (stableRowLimit < 0) {
      stableRowLimit = 0;
    }

    // Read screen lines (excluding volatile bottom rows)
    List<String> screenLines = sb.readScreenLines(0, stableRowLimit);

    // Update screen row states and check for stable rows
    for (int row = 0; row < screenLines.size(); row++) {
      String content = normalizeLine(screenLines.get(row));

      ScreenRowState state = screenRowStates.get(row);
      if (state == null) {
        // First time seeing this row
        screenRowStates.put(row, new ScreenRowState(content, now));
      } else if (!content.equals(state.content)) {
        // Content changed, reset timestamp
        state.content = content;
        state.firstSeenMillis = now;
        state.emitted = false;
      } else if (!state.emitted) {
        // Content unchanged, check if stable long enough
        long elapsed = now - state.firstSeenMillis;
        if (elapsed >= config.stabilityWindowMillis()) {
          // Row is stable, consider emitting
          if (!content.isEmpty()) {
            boolean suppress = churnPolicy.shouldSuppressRow(content, List.of());
            if (!suppress) {
              // Deduplicate
              int hash = content.hashCode();
              if (!emittedContentHashes.contains(hash)) {
                emittedContentHashes.add(hash);
                out.add(TranscriptEvent.append(content + "\n", Instant.now(), TranscriptEvent.Source.SCREEN_STABLE));
              }
            }
          }
          state.emitted = true;
        }
      }
    }

    // Clean up stale row states if screen shrank
    screenRowStates.keySet().removeIf(row -> row >= screenRows);

    return out;
  }

  private static String normalizeLine(String s) {
    // Trim whitespace from both ends (including NUL chars which terminals use for empty cells)
    int start = 0;
    int end = s.length();

    // Left-trim
    while (start < end) {
      char c = s.charAt(start);
      if (c == ' ' || c == '\0' || c == '\t') {
        start++;
      } else {
        break;
      }
    }

    // Right-trim
    while (end > start) {
      char c = s.charAt(end - 1);
      if (c == ' ' || c == '\0' || c == '\t') {
        end--;
      } else {
        break;
      }
    }

    if (start == 0 && end == s.length()) {
      return s;
    }
    return s.substring(start, end);
  }
}
