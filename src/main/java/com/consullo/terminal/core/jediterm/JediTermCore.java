package com.consullo.terminal.core.jediterm;

import com.consullo.terminal.core.ScrollbackView;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.TerminalSnapshot;
import com.consullo.terminal.core.events.DamageEvent;
import com.consullo.terminal.core.events.DamageListener;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal JediTerm-backed {@link TerminalCore} adapter.
 *
 * <p>This class intentionally keeps the JediTerm integration surface area small in the starter.
 * The current implementation:
 * - Maintains a very lightweight plain-text "screen" model for demo/testing
 * - Emits damage events on every feed
 *
 * <p>For a production integration, replace the lightweight model with a true JediTerm screen + scrollback
 * adapter (TerminalTextBuffer, TerminalModel, etc.) while preserving this interface.
 *
 * @since 1.0
 */
public final class JediTermCore implements TerminalCore {

  private static final Logger LOGGER = LoggerFactory.getLogger(JediTermCore.class);

  private final List<DamageListener> listeners;
  private final List<String> screenRows;
  private final List<String> scrollback;
  private int columns;
  private int rows;
  private boolean alternateScreen;

  /**
   * Creates a new core with a specified initial size.
   *
   * @param columns initial columns
   * @param rows initial rows
   * @throws Exception if parameters are invalid
   */
  public JediTermCore(final int columns, final int rows) throws Exception {
    Validate.isTrue(columns > 0, "columns must be positive");
    Validate.isTrue(rows > 0, "rows must be positive");

    this.columns = columns;
    this.rows = rows;
    this.listeners = new ArrayList<>(4);
    this.screenRows = new ArrayList<>(rows);
    this.scrollback = new ArrayList<>(256);

    for (int i = 0; i < rows; i++) {
      this.screenRows.add("");
    }
  }

  @Override
  public void feed(final byte[] data, final int offset, final int length) throws Exception {
    Validate.notNull(data, "data must not be null");
    Validate.isTrue(offset >= 0, "offset must be non-negative");
    Validate.isTrue(length >= 0, "length must be non-negative");
    Validate.isTrue(offset + length <= data.length, "offset+length must be within array bounds");

    // NOTE: This starter uses a simplified feed that treats the output as UTF-8 text with newlines.
    // Production: replace with true JediTerm parsing and screen model updates.
    final String text = new String(data, offset, length, StandardCharsets.UTF_8);

    applyTextToModel(text);

    publishDamage(new DamageEvent(Instant.now(), 0, this.rows, false));
  }

  @Override
  public TerminalSnapshot snapshot() throws Exception {
    final List<String> rowsCopy = new ArrayList<>(this.screenRows.size());
    rowsCopy.addAll(this.screenRows);
    return new TerminalSnapshot(Collections.unmodifiableList(rowsCopy), 0, 0, this.alternateScreen);
  }

  @Override
  public ScrollbackView scrollback() throws Exception {
    return new InMemoryScrollbackView(this.scrollback);
  }

  @Override
  public void resize(final int columns, final int rows) throws Exception {
    Validate.isTrue(columns > 0, "columns must be positive");
    Validate.isTrue(rows > 0, "rows must be positive");

    this.columns = columns;
    this.rows = rows;

    while (this.screenRows.size() < rows) {
      this.screenRows.add("");
    }
    while (this.screenRows.size() > rows) {
      this.screenRows.remove(this.screenRows.size() - 1);
    }

    publishDamage(new DamageEvent(Instant.now(), 0, rows, true));
  }

  @Override
  public void addDamageListener(final DamageListener listener) throws Exception {
    Validate.notNull(listener, "listener must not be null");
    this.listeners.add(listener);
  }

  @Override
  public void publishDamage(final DamageEvent damageEvent) throws Exception {
    Validate.notNull(damageEvent, "damageEvent must not be null");

    for (final DamageListener listener : this.listeners) {
      try {
        listener.onDamage(damageEvent);
      } catch (final Exception e) {
        LOGGER.warn("Damage listener failed: {}", e.getMessage(), e);
      }
    }
  }

  /**
   * Applies new text to the simplified model.
   *
   * <p>This method approximates terminal behavior:
   * - Splits on newline
   * - Appends complete lines to scrollback
   * - Keeps the last partial line on the last screen row
   *
   * @param text new output text
   * @throws Exception if text is null
   */
  protected void applyTextToModel(final String text) throws Exception {
    Validate.notNull(text, "text must not be null");

    final StringBuilder bufferBuilder = new StringBuilder(text.length());
    bufferBuilder.append(text);

    final String accumulated = bufferBuilder.toString();
    int start = 0;

    for (int i = 0; i < accumulated.length(); i++) {
      final char c = accumulated.charAt(i);
      if (c == '\n') {
        final String line = accumulated.substring(start, i);
        this.scrollback.add(line);
        start = i + 1;
      }
    }

    final String tail = accumulated.substring(start);

    // Keep the tail on the last row as a simple "current line"
    if (!this.screenRows.isEmpty()) {
      this.screenRows.set(this.screenRows.size() - 1, tail);
    }
  }
}
