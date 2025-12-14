package com.consullo.terminal.capture;

import com.consullo.terminal.core.ScrollbackView;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.TerminalSnapshot;
import com.consullo.terminal.core.events.DamageEvent;
import com.consullo.terminal.core.events.DamageListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the capture engine using deterministic in-memory scrollback and snapshots.
 *
 * @since 1.0
 */
public class CaptureEngineTest {

  @Test
  @DisplayName("Should emit newly committed scrollback lines as append events")
  void onDamage_ScrollbackDelta_EmitsAppendEvents() throws Exception {
    final CaptureEngineConfig config = new CaptureEngineConfig(2, 350L, true);
    final CaptureEngine engine = new CaptureEngine(config, new DefaultChurnFilterPolicy());

    final List<String> lines = new ArrayList<>(4);
    lines.add("a");
    lines.add("b");
    final ScrollbackView scrollback = new FixedScrollback(lines);

    // Create mock terminal core
    TerminalCore mockCore = mock(TerminalCore.class);
    when(mockCore.scrollback()).thenReturn(scrollback);

    TerminalSnapshot snapshot = TerminalSnapshot.builder()
            .timestamp(Instant.now())
            .cols(80)
            .rows(24)
            .alternateScreen(false)
            .build();
    DamageEvent damage = DamageEvent.createFullRedraw();

    List<TranscriptEvent> events = engine.onDamage(mockCore, snapshot, damage);

    assertThat(events.size()).isEqualTo(2);
    assertThat(events.get(0).type()).isEqualTo(TranscriptEvent.Type.APPEND);
    assertThat(events.get(0).text()).isEqualTo("a\n");
    assertThat(events.get(1).text()).isEqualTo("b\n");
  }

  /**
   * Fixed scrollback implementation for tests.
   * All lines are treated as history lines, no screen content.
   */
  private static final class FixedScrollback implements ScrollbackView {

    private final List<String> lines;

    private FixedScrollback(final List<String> lines) {
      this.lines = lines;
    }

    @Override
    public int historyLineCount() {
      return this.lines.size();
    }

    @Override
    public int screenRowCount() {
      return 0;
    }

    @Override
    public List<String> readHistoryLines(final int startInclusive, final int endExclusive) {
      final List<String> out = new ArrayList<>(endExclusive - startInclusive);
      for (int i = startInclusive; i < endExclusive && i < lines.size(); i++) {
        out.add(this.lines.get(i));
      }
      return out;
    }

    @Override
    public List<String> readScreenLines(final int startInclusive, final int endExclusive) {
      return List.of();
    }
  }
}
