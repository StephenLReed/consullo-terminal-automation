package com.consullo.terminal.driver;

import com.consullo.terminal.capture.CaptureEngine;
import com.consullo.terminal.capture.CaptureEngineConfig;
import com.consullo.terminal.capture.DefaultChurnFilterPolicy;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.jediterm.JediTermCore;
import com.consullo.terminal.pty.PtyProcessConfig;
import com.consullo.terminal.pty.PtyProcessControllerPty4j;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating Claude Code sessions with sensible defaults.
 *
 * <p>
 * This class intentionally centralizes the decision-making around:
 * <ul>
 * <li>TERM defaults (to enable a rich but common VT emulation contract)</li>
 * <li>Optional CI mode to reduce spinners/animations (if desired)</li>
 * <li>Working directory defaults</li>
 * </ul>
 * </p>
 */
public final class ClaudeSessionFactory {

  private ClaudeSessionFactory() {
  }

  /**
   * Create a Claude Code CLI session.
   *
   * @param claudeExecutablePath path to the Claude Code CLI executable (e.g.
   * "claude")
   * @param workingDirectory working dir (if null, uses current directory)
   * @param enableCiMode if true, sets CI=1 (many tools reduce animations)
   * @param cols terminal columns
   * @param rows terminal rows
   * @return session
   */
  public static ClaudeSession createClaudeCodeSession(
          String claudeExecutablePath,
          Path workingDirectory,
          boolean enableCiMode,
          int cols,
          int rows
  ) {

    if (claudeExecutablePath == null || claudeExecutablePath.isBlank()) {
      throw new IllegalArgumentException("claudeExecutablePath must not be blank.");
    }
    if (cols <= 0 || rows <= 0) {
      throw new IllegalArgumentException("cols/rows must be positive.");
    }

    Map<String, String> env = new LinkedHashMap<>();
    env.putAll(System.getenv());

    // Sensible defaults for terminal-aware CLIs.
    env.put("TERM", "xterm-256color");

    // Optional reduction of animations and spinners.
    if (enableCiMode) {
      env.put("CI", "1");
    }

    Path workDir = workingDirectory != null ? workingDirectory : Path.of(".").toAbsolutePath().normalize();

    PtyProcessConfig cfg = new PtyProcessConfig(
            java.util.List.of(claudeExecutablePath),
            workDir,
            env,
            cols,
            rows
    );

    try {
      PtyProcessControllerPty4j pty = new PtyProcessControllerPty4j(cfg);
      TerminalCore core = new JediTermCore(cols, rows, 50_000);
      CaptureEngineConfig captureConfig = new CaptureEngineConfig(2, 350L, true);
      CaptureEngine captureEngine = new CaptureEngine(captureConfig, new DefaultChurnFilterPolicy());

      return ClaudeSession.create(pty, core, captureEngine);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create Claude session", e);
    }
  }

  /**
   * Send a prompt and then an ESC keystroke.
   *
   * <p>
   * This is a common automation primitive for UIs that need a prompt
   * "committed" followed by an escape to cancel a mode, stop a spinner, dismiss
   * an overlay, etc.
   * </p>
   *
   * @param session session
   * @param prompt prompt text
   */
  public static void sendPromptThenEscape(ClaudeSession session, String prompt) {
    if (session == null) {
      throw new IllegalArgumentException("session must not be null.");
    }
    if (prompt == null) {
      throw new IllegalArgumentException("prompt must not be null.");
    }
    session.sendPrompt(prompt);
    session.sendEscape();
  }
}
