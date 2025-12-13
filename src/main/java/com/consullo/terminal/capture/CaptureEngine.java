package com.consullo.terminal.capture;

import com.consullo.terminal.core.ScrollbackView;
import com.consullo.terminal.core.TerminalSnapshot;
import com.consullo.terminal.core.events.DamageEvent;
import com.consullo.util.DateTimeUtils;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Capture engine that emits append-only transcript events while suppressing redraw churn.
 *
 * <p>The engine operates deterministically using:
 * 1) Scrollback deltas (primary signal, low-noise)
 * 2) Screen stability in the non-volatile region (secondary signal)
 *
 * <p>The output is returned as a JSON object to align with Consullo integration patterns.
 *
 * @since 1.0
 */
public final class CaptureEngine {

  private CaptureEngine() {
  }

  /**
   * Creates an initial capture state.
   *
   * @return initial state
   * @throws Exception if creation fails
   */
  public static CaptureEngineState createInitialState() throws Exception {
    final Map<Integer, Long> rowDigests = new HashMap<>(64);
    final Map<Integer, Instant> rowLastChanged = new HashMap<>(64);
    return new CaptureEngineState(0, 0L, rowDigests, rowLastChanged);
  }

  /**
   * Processes a damage event and emits transcript events while returning updated capture state.
   *
   * <p>Returned JSON fields:
   * - `state`: updated state encoded as JSON
   * - `events`: JSONArray of transcript events
   *
   * @param state capture engine state
   * @param config capture engine configuration
   * @param churnPolicy churn suppression policy (non-null)
   * @param damageEvent damage event
   * @param snapshot terminal snapshot
   * @param scrollback scrollback view
   * @return JSONObject containing updated state and emitted events
   * @throws Exception if processing fails
   */
  public static JSONObject processDamage(
      final CaptureEngineState state,
      final CaptureEngineConfig config,
      final ChurnFilterPolicy churnPolicy,
      final DamageEvent damageEvent,
      final TerminalSnapshot snapshot,
      final ScrollbackView scrollback) throws Exception {

    Validate.notNull(state, "state must not be null");
    Validate.notNull(config, "config must not be null");
    Validate.notNull(churnPolicy, "churnPolicy must not be null");
    Validate.notNull(damageEvent, "damageEvent must not be null");
    Validate.notNull(snapshot, "snapshot must not be null");
    Validate.notNull(scrollback, "scrollback must not be null");

    final JSONArray events = new JSONArray();
    CaptureEngineState updated = state;

    // PHASE 1: Epoch handling for full redraw signals
    if (damageEvent.fullRedraw()) {
      updated = new CaptureEngineState(updated.lastEmittedScrollbackLineIndex(), updated.epoch() + 1L,
          updated.rowDigests(), updated.rowLastChangedUtc());
    }

    // PHASE 2: Emit scrollback deltas (primary signal)
    final JSONObject scrollbackResult = emitScrollbackDeltas(updated, scrollback);
    updated = decodeState(scrollbackResult.getJSONObject("state"));
    final JSONArray scrollEvents = scrollbackResult.getJSONArray("events");
    events.addAll(scrollEvents);

    // PHASE 3: Emit stable screen lines (secondary signal)
    final JSONObject screenResult = emitStableScreenLines(updated, config, churnPolicy, snapshot);
    updated = decodeState(screenResult.getJSONObject("state"));
    final JSONArray screenEvents = screenResult.getJSONArray("events");
    events.addAll(screenEvents);

    final JSONObject result = new JSONObject(2);
    result.put("state", encodeState(updated));
    result.put("events", events);
    return result;
  }

  /**
   * Emits newly-committed scrollback lines since the last emitted index.
   *
   * @param state current state
   * @param scrollback scrollback view
   * @return JSONObject with updated state and events
   * @throws Exception if processing fails
   */
  protected static JSONObject emitScrollbackDeltas(
      final CaptureEngineState state,
      final ScrollbackView scrollback) throws Exception {

    Validate.notNull(state, "state must not be null");
    Validate.notNull(scrollback, "scrollback must not be null");

    final int count = scrollback.lineCount();
    final int start = state.lastEmittedScrollbackLineIndex();

    final JSONArray events = new JSONArray();
    int nextIndex = start;

    if (count > start) {
      final List<String> newLines = scrollback.readLines(start, count);
      for (final String line : newLines) {
        final String normalized = rightTrim(line);
        if (!normalized.isEmpty()) {
          events.add(createAppendEvent(normalized + "\n", "SCROLLBACK"));
        } else {
          events.add(createAppendEvent("\n", "SCROLLBACK"));
        }
        nextIndex++;
      }
    }

    final CaptureEngineState updated = new CaptureEngineState(nextIndex, state.epoch(), state.rowDigests(), state.rowLastChangedUtc());

    final JSONObject result = new JSONObject(2);
    result.put("state", encodeState(updated));
    result.put("events", events);
    return result;
  }

  /**
   * Emits stable screen lines from the non-volatile region using a debounce window.
   *
   * @param state current state
   * @param config capture configuration
   * @param churnPolicy churn suppression policy
   * @param snapshot terminal snapshot
   * @return JSONObject with updated state and events
   * @throws Exception if processing fails
   */
  protected static JSONObject emitStableScreenLines(
      final CaptureEngineState state,
      final CaptureEngineConfig config,
      final ChurnFilterPolicy churnPolicy,
      final TerminalSnapshot snapshot) throws Exception {

    Validate.notNull(state, "state must not be null");
    Validate.notNull(config, "config must not be null");
    Validate.notNull(churnPolicy, "churnPolicy must not be null");
    Validate.notNull(snapshot, "snapshot must not be null");

    final JSONArray events = new JSONArray();

    if (snapshot.alternateScreen() && config.suppressAlternateScreen()) {
      final JSONObject result = new JSONObject(2);
      result.put("state", encodeState(state));
      result.put("events", events);
      return result;
    }

    final List<String> rows = snapshot.rows();
    final int totalRows = rows.size();
    final int volatileCount = Math.max(0, config.volatileRowCount());
    final int stableEndExclusive = Math.max(0, totalRows - volatileCount);

    final Map<Integer, Long> digests = state.rowDigests();
    final Map<Integer, Instant> lastChanged = state.rowLastChangedUtc();

    final Instant now = DateTimeUtils.utcNow();

    // Store recent samples for the volatile region to allow churnPolicy evaluation.
    final Map<Integer, Deque<String>> recentVolatileSamples = new HashMap<>(volatileCount * 2);

    // PHASE 1: Update digests for stable region and record last-changed times
    for (int rowIndex = 0; rowIndex < stableEndExclusive; rowIndex++) {
      final String rowText = rightTrim(rows.get(rowIndex));
      final int rowKey = createRowKey(state.epoch(), rowIndex);
      final long digest = computeDigest(rowText);

      final Long previous = digests.get(rowKey);
      if (previous == null || previous.longValue() != digest) {
        digests.put(rowKey, digest);
        lastChanged.put(rowKey, now);
      }
    }

    // PHASE 2: Track volatile samples (do not emit; used only for suppression)
    for (int rowIndex = stableEndExclusive; rowIndex < totalRows; rowIndex++) {
      final String rowText = rightTrim(rows.get(rowIndex));
      final int rowKey = createRowKey(state.epoch(), rowIndex);
      final Deque<String> q = recentVolatileSamples.computeIfAbsent(rowKey, k -> new ArrayDeque<>(12));
      if (q.size() >= 10) {
        q.removeFirst();
      }
      q.addLast(rowText);
    }

    // PHASE 3: Emit stable rows that have not changed for stabilityWindowMillis
    for (int rowIndex = 0; rowIndex < stableEndExclusive; rowIndex++) {
      final String rowText = rightTrim(rows.get(rowIndex));
      if (rowText.isEmpty()) {
        continue;
      }

      final int rowKey = createRowKey(state.epoch(), rowIndex);
      final Instant changedAt = lastChanged.get(rowKey);
      if (changedAt == null) {
        continue;
      }

      final long ageMillis = now.toEpochMilli() - changedAt.toEpochMilli();
      if (ageMillis < config.stabilityWindowMillis()) {
        continue;
      }

      // Do not emit if churn policy says suppress (rare for stable region, but safe)
      final List<String> samples = new ArrayList<>(0);
      if (churnPolicy.shouldSuppressRow(rowText, samples)) {
        continue;
      }

      events.add(createAppendEvent(rowText + "\n", "SCREEN_STABLE"));

      // Mark as emitted by moving lastChanged forward so we don't repeatedly emit.
      lastChanged.put(rowKey, now);
    }

    final CaptureEngineState updated = new CaptureEngineState(
        state.lastEmittedScrollbackLineIndex(),
        state.epoch(),
        digests,
        lastChanged);

    final JSONObject result = new JSONObject(2);
    result.put("state", encodeState(updated));
    result.put("events", events);
    return result;
  }

  /**
   * Creates a transcript append event.
   *
   * @param text appended text
   * @param source event source label
   * @return event object
   * @throws Exception if inputs are invalid
   */
  protected static JSONObject createAppendEvent(final String text, final String source) throws Exception {
    Validate.notNull(text, "text must not be null");
    Validate.notNull(source, "source must not be null");

    final JSONObject meta = new JSONObject(4);
    meta.put("timestampUtc", DateTimeUtils.toJsonString(DateTimeUtils.utcNow()));
    meta.put("source", source);

    final JSONObject event = new JSONObject(3);
    event.put("type", "append");
    event.put("text", text);
    event.put("meta", meta);
    return event;
  }

  /**
   * Encodes state as JSON to simplify persistence and event streaming.
   *
   * @param state state to encode
   * @return JSON state
   * @throws Exception if encoding fails
   */
  protected static JSONObject encodeState(final CaptureEngineState state) throws Exception {
    Validate.notNull(state, "state must not be null");

    final JSONObject json = new JSONObject(6);
    json.put("lastEmittedScrollbackLineIndex", state.lastEmittedScrollbackLineIndex());
    json.put("epoch", state.epoch());

    final JSONObject digests = new JSONObject(state.rowDigests().size());
    for (final Map.Entry<Integer, Long> e : state.rowDigests().entrySet()) {
      digests.put(String.valueOf(e.getKey()), e.getValue());
    }

    final JSONObject lastChanged = new JSONObject(state.rowLastChangedUtc().size());
    for (final Map.Entry<Integer, Instant> e : state.rowLastChangedUtc().entrySet()) {
      lastChanged.put(String.valueOf(e.getKey()), DateTimeUtils.toTimestamp(e.getValue()));
    }

    json.put("rowDigests", digests);
    json.put("rowLastChangedUtc", lastChanged);
    return json;
  }

  /**
   * Decodes JSON-encoded state.
   *
   * @param json JSON state
   * @return decoded state
   * @throws Exception if decoding fails
   */
  public static CaptureEngineState decodeState(final JSONObject json) throws Exception {
    Validate.notNull(json, "json must not be null");

    final int lastIndex = json.getInt("lastEmittedScrollbackLineIndex");
    final long epoch = json.getLong("epoch");

    final JSONObject digestsJson = json.getJSONObject("rowDigests");
    final JSONObject lastChangedJson = json.getJSONObject("rowLastChangedUtc");

    final Map<Integer, Long> digests = new HashMap<>(Math.max(16, digestsJson.size() * 2));
    final Map<Integer, Instant> lastChanged = new HashMap<>(Math.max(16, lastChangedJson.size() * 2));

    for (final Object keyObj : digestsJson.keySet()) {
      final String key = String.valueOf(keyObj);
      final int rowKey = Integer.parseInt(key);
      final long digest = digestsJson.getLong(key);
      digests.put(rowKey, digest);
    }

    for (final Object keyObj : lastChangedJson.keySet()) {
      final String key = String.valueOf(keyObj);
      final int rowKey = Integer.parseInt(key);
      final long ts = lastChangedJson.getLong(key);
      final Instant instant = DateTimeUtils.fromTimestamp(ts);
      lastChanged.put(rowKey, instant);
    }

    return new CaptureEngineState(lastIndex, epoch, digests, lastChanged);
  }

  /**
   * Creates a row key from the current epoch and row index.
   *
   * @param epoch epoch
   * @param rowIndex row index
   * @return row key integer
   * @throws Exception if rowIndex is negative
   */
  protected static int createRowKey(final long epoch, final int rowIndex) throws Exception {
    Validate.isTrue(rowIndex >= 0, "rowIndex must be non-negative");

    // Combine epoch and row index into a stable int key with low collision risk for typical sizes.
    final long value = (epoch << 16) ^ (long) rowIndex;
    return (int) (value ^ (value >>> 32));
  }

  /**
   * Computes a stable digest for a row using a simple non-cryptographic hash.
   *
   * @param text row text
   * @return digest
   * @throws Exception if text is null
   */
  protected static long computeDigest(final String text) throws Exception {
    Validate.notNull(text, "text must not be null");

    // FNV-1a 64-bit
    long hash = 0xcbf29ce484222325L;
    for (int i = 0; i < text.length(); i++) {
      hash ^= text.charAt(i);
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  /**
   * Right-trims a string without regex.
   *
   * @param s input string
   * @return right-trimmed string (never null)
   * @throws Exception if s is null
   */
  protected static String rightTrim(final String s) throws Exception {
    Validate.notNull(s, "s must not be null");

    int end = s.length();
    while (end > 0) {
      final char c = s.charAt(end - 1);
      if (c == ' ' || c == '\t' || c == '\r') {
        end--;
      } else {
        break;
      }
    }
    return s.substring(0, end);
  }
}
