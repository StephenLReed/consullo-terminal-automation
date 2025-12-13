package com.consullo.terminal.demo;

import com.consullo.terminal.capture.CaptureEngineConfig;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.jediterm.JediTermCore;
import com.consullo.terminal.driver.ClaudeSession;
import com.consullo.terminal.pty.PtyProcessConfig;
import com.consullo.terminal.pty.PtyProcessController;
import com.consullo.terminal.pty.PtyProcessControllerPty4j;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONObject;
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
    String javaHome = System.getProperty("java.home");
    String javaBin = javaHome + "/bin/java";
    String classPath = System.getProperty("java.class.path");

    List<String> cmd = new ArrayList<>();
    cmd.add(javaBin);
    cmd.add("--enable-native-access=ALL-UNNAMED"); // optional but silences the JNA warning
    cmd.add("-cp");
    cmd.add(classPath);
    cmd.add(TerminalFixtureApp.class.getName());

    final Path workDir = Path.of(".").toAbsolutePath().normalize();

    final PtyProcessConfig config = new PtyProcessConfig(cmd, workDir, null, 120, 40);
    final PtyProcessController pty = new PtyProcessControllerPty4j(config);

    final TerminalCore core = new JediTermCore(120, 40);
    final CaptureEngineConfig captureConfig = new CaptureEngineConfig(2, 350L, true);

    try (final ClaudeSession session = new ClaudeSession(pty, core, captureConfig)) {
      LOGGER.info("Started demo PTY PID={}", pty.pid());

      int emptyPolls = 0;
      while (emptyPolls < 3) {
        final JSONObject event = session.pollTranscriptEvent(500, TimeUnit.MILLISECONDS);
        if (event != null) {
          System.out.print(event.getString("text"));
          emptyPolls = 0;
        } else if (!session.isAlive()) {
          emptyPolls++;
        }
      }
      LOGGER.info("Demo completed, process exited");
    }
  }
}
