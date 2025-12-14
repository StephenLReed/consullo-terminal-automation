package com.consullo.terminal.demo;

import com.consullo.terminal.capture.CaptureEngine;
import com.consullo.terminal.capture.CaptureEngineConfig;
import com.consullo.terminal.capture.DefaultChurnFilterPolicy;
import com.consullo.terminal.capture.TranscriptEvent;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.jediterm.JediTermCore;
import com.consullo.terminal.driver.ClaudeSession;
import com.consullo.terminal.pty.PtyProcessConfig;
import com.consullo.terminal.pty.PtyProcessControllerPty4j;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal demo that spawns a deterministic test subprocess and prints the
 * churn-suppressed transcript to stdout.
 *
 * <p>
 * For initial verification, this demo runs {@link TerminalFixtureApp} which
 * emits: - normal lines - a spinner-like volatile line (simulated) - a
 * progress-like volatile line (simulated)
 *
 * @since 1.0
 */
public final class TerminalAutomationDemo {

  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalAutomationDemo.class);

  private TerminalAutomationDemo() {
  }

  /**
   * Demo entry point.
   *
   * @param args args
   * @throws Exception if demo fails
   */
  public static void main(final String[] args) throws Exception {
    // Use a simple shell script to test terminal capture instead of spawning Java
    List<String> cmd = new ArrayList<>();
    cmd.add("/bin/bash");
    cmd.add("-c");
    cmd.add("echo 'fixture: start'; echo 'fixture: line 1'; echo 'fixture: line 2'; " +
            "for i in 1 2 3; do printf '\\rspinner |'; sleep 0.05; printf '\\rspinner /'; sleep 0.05; done; " +
            "echo; echo 'fixture: after spinner'; echo 'fixture: done'");

    final Path workDir = Path.of(".").toAbsolutePath().normalize();

    // Use small terminal height (3 rows) so content scrolls into history quickly
    final PtyProcessConfig config = new PtyProcessConfig(cmd, workDir, null, 120, 3);
    final PtyProcessControllerPty4j pty = new PtyProcessControllerPty4j(config);

    final TerminalCore core = new JediTermCore(120, 3, 50_000);
    // volatileRowCount=1 to skip bottom status row, stabilityWindowMillis=100 for faster screen emission
    final CaptureEngineConfig captureConfig = new CaptureEngineConfig(1, 100L, true);
    final CaptureEngine captureEngine = new CaptureEngine(captureConfig, new DefaultChurnFilterPolicy());

    try (final ClaudeSession session = ClaudeSession.create(pty, core, captureEngine)) {
      LOGGER.info("Started demo PTY PID={}", pty.pid());

      System.out.println("=== Captured Transcript ===");
      int emptyPolls = 0;
      int totalEvents = 0;
      while (emptyPolls < 5) {
        TranscriptEvent event = session.transcriptQueue().poll(200, TimeUnit.MILLISECONDS);
        if (event != null) {
          System.out.print(event.text());
          totalEvents++;
          emptyPolls = 0;
        } else if (!pty.isAlive()) {
          emptyPolls++;
        }
      }
      System.out.println("=== End Transcript (" + totalEvents + " events) ===");
      LOGGER.info("Demo completed");
    }
  }
}
