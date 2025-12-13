package com.consullo.terminal.capture;

import com.consullo.terminal.core.ScrollbackView;
import com.consullo.terminal.core.TerminalSnapshot;
import com.consullo.terminal.core.events.DamageEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the capture engine using deterministic in-memory scrollback and snapshots.
 *
 * @since 1.0
 */
public class CaptureEngineTest {

  @Test
  @DisplayName("Should emit newly committed scrollback lines as append events")
  void processDamage_ScrollbackDelta_EmitsAppendEvents() throws Exception {
    final CaptureEngineState state = CaptureEngine.createInitialState();
    final CaptureEngineConfig config = new CaptureEngineConfig(2, 350L, true);

    final List<String> lines = new ArrayList<>(4);
    lines.add("a");
    lines.add("b");
    final ScrollbackView scrollback = new FixedScrollback(lines);

    final TerminalSnapshot snapshot = new TerminalSnapshot(Collections.singletonList(""), 0, 0, false);
    final DamageEvent damage = new DamageEvent(Instant.now(), 0, 1, false);

    final JSONObject result = CaptureEngine.processDamage(state, config, new DefaultChurnFilterPolicy(), damage, snapshot, scrollback);
    final JSONArray events = result.getJSONArray("events");

    assertThat(events.size()).isEqualTo(2);
    final JSONObject e0 = (JSONObject) events.get(0);
    final JSONObject e1 = (JSONObject) events.get(1);
    assertThat(e0.getString("type")).isEqualTo("append");
    assertThat(e0.getString("text")).isEqualTo("a\n");
    assertThat(e1.getString("text")).isEqualTo("b\n");
  }

  /**
   * Fixed scrollback implementation for tests.
   */
  private static final class FixedScrollback implements ScrollbackView {

    private final List<String> lines;

    private FixedScrollback(final List<String> lines) {
      this.lines = lines;
    }

    @Override
    public int lineCount() throws Exception {
      return this.lines.size();
    }

    @Override
    public List<String> readLines(final int startInclusive, final int endExclusive) throws Exception {
      final List<String> out = new ArrayList<>(endExclusive - startInclusive);
      for (int i = startInclusive; i < endExclusive; i++) {
        out.add(this.lines.get(i));
      }
      return out;
    }
  }
}
