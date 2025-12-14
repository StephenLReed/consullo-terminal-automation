package com.consullo.terminal.capture;

import java.time.Instant;

/**
 * Transcript event emitted by the capture engine.
 */
public final class TranscriptEvent {

  public enum Type {
    APPEND
  }

  public enum Source {
    SCROLLBACK,
    SCREEN_STABLE
  }

  private final Type type;
  private final String text;
  private final Instant timestamp;
  private final Source source;

  private TranscriptEvent(Type type, String text, Instant timestamp, Source source) {
    this.type = type;
    this.text = text;
    this.timestamp = timestamp;
    this.source = source;
  }

  public static TranscriptEvent append(String text, Instant ts, Source src) {
    if (text == null || ts == null || src == null) {
      throw new IllegalArgumentException("text/ts/src must not be null.");
    }
    return new TranscriptEvent(Type.APPEND, text, ts, src);
  }

  public Type type() {
    return type;
  }

  public String text() {
    return text;
  }

  public Instant timestamp() {
    return timestamp;
  }

  public Source source() {
    return source;
  }
}
