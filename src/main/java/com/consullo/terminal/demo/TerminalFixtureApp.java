package com.consullo.terminal.demo;

import java.io.PrintStream;

/**
 * Deterministic terminal output fixture used for integration testing and demo verification.
 *
 * <p>This app emits:
 * 1) A few committed lines (newline terminated)
 * 2) Volatile spinner updates (carriage-return rewrite)
 * 3) A committed final line
 *
 * <p>It does not require ANSI parsing for the starter, but the patterns match common TUI behavior.
 *
 * @since 1.0
 */
public final class TerminalFixtureApp {

  private TerminalFixtureApp() {
  }

  /**
   * Entry point.
   *
   * @param args args
   * @throws Exception if sleep is interrupted
   */
  public static void main(final String[] args) throws Exception {
    final PrintStream out = System.out;

    out.println("fixture: start");
    out.println("fixture: line 1");
    out.println("fixture: line 2");

    final char[] spinner = new char[] { '|', '/', '-', '\\' };
    for (int i = 0; i < 40; i++) {
      final char c = spinner[i % spinner.length];
      out.print("\rspinner " + c);
      out.flush();
      Thread.sleep(20L);
    }
    out.print("\r");
    out.println("fixture: after spinner");

    for (int p = 0; p <= 100; p += 5) {
      out.print("\r[==========          ] " + p + "%");
      out.flush();
      Thread.sleep(15L);
    }
    out.print("\r");
    out.println("fixture: done");

    out.flush();
  }
}
