package com.consullo.terminal.core;

import java.time.Instant;

/**
 * Immutable snapshot of terminal state needed by capture and orchestration
 * logic.
 *
 * <p>
 * This is intentionally minimal. The capture engine should prefer scrollback
 * for committed output, but it also needs to know when the terminal is in
 * alternate-screen mode to suppress repaint churn.
 * </p>
 */
public final class TerminalSnapshot {

  private final Instant timestamp;
  private final int cols;
  private final int rows;
  private final boolean alternateScreen;

  private TerminalSnapshot(Builder b) {
    this.timestamp = b.timestamp;
    this.cols = b.cols;
    this.rows = b.rows;
    this.alternateScreen = b.alternateScreen;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public int getCols() {
    return cols;
  }

  public int getRows() {
    return rows;
  }

  public boolean isAlternateScreen() {
    return alternateScreen;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private Instant timestamp;
    private int cols;
    private int rows;
    private boolean alternateScreen;

    private Builder() {
    }

    public Builder timestamp(Instant ts) {
      this.timestamp = ts;
      return this;
    }

    public Builder cols(int cols) {
      this.cols = cols;
      return this;
    }

    public Builder rows(int rows) {
      this.rows = rows;
      return this;
    }

    public Builder alternateScreen(boolean alternateScreen) {
      this.alternateScreen = alternateScreen;
      return this;
    }

    public TerminalSnapshot build() {
      if (timestamp == null) {
        timestamp = Instant.now();
      }
      if (cols <= 0 || rows <= 0) {
        throw new IllegalArgumentException("cols/rows must be positive.");
      }
      return new TerminalSnapshot(this);
    }
  }
}
