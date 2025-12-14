package com.consullo.terminal.driver;

import com.consullo.terminal.capture.CaptureEngine;
import com.consullo.terminal.capture.TranscriptEvent;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.TerminalSnapshot;
import com.consullo.terminal.core.events.DamageEvent;
import com.consullo.terminal.core.events.DamageListener;
import com.consullo.terminal.pty.PtyProcessControllerPty4j;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents a single interactive CLI session.
 *
 * <p>
 * Owns:
 * <ul>
 * <li>PTY process controller</li>
 * <li>Terminal core (ANSI/VT parsing + scrollback)</li>
 * <li>Capture engine (churn-suppressed transcript)</li>
 * </ul>
 * </p>
 */
public final class ClaudeSession implements AutoCloseable {

  private final PtyProcessControllerPty4j pty;
  private final TerminalCore core;
  private final CaptureEngine captureEngine;

  private final BlockingQueue<TranscriptEvent> transcriptQueue = new LinkedBlockingQueue<>();

  private ClaudeSession(PtyProcessControllerPty4j pty, TerminalCore core, CaptureEngine captureEngine) {
    this.pty = pty;
    this.core = core;
    this.captureEngine = captureEngine;
  }

  public static ClaudeSession create(PtyProcessControllerPty4j pty, TerminalCore core, CaptureEngine captureEngine) {
    if (pty == null || core == null || captureEngine == null) {
      throw new IllegalArgumentException("pty/core/captureEngine must not be null.");
    }

    ClaudeSession s = new ClaudeSession(pty, core, captureEngine);

    // Wire terminal damage → capture.
    try {
      core.addDamageListener(new DamageListener() {
        @Override
        public void onDamage(TerminalSnapshot snapshot, DamageEvent event) {
          try {
            for (TranscriptEvent te : s.captureEngine.onDamage(core, snapshot, event)) {
              s.transcriptQueue.add(te);
            }
          } catch (Exception e) {
            // Log and continue
          }
        }
      });

      // Start PTY read loop
      s.startPtyReadLoop();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize session", e);
    }

    return s;
  }

  public TerminalCore core() {
    return core;
  }

  public BlockingQueue<TranscriptEvent> transcriptQueue() {
    return transcriptQueue;
  }

  public void sendText(String s) {
    if (s == null) {
      throw new IllegalArgumentException("s must not be null.");
    }
    writeBytes(s.getBytes(StandardCharsets.UTF_8));
  }

  public void sendPrompt(String prompt) {
    if (prompt == null) {
      throw new IllegalArgumentException("prompt must not be null.");
    }
    // Normalize: prompt + newline.
    sendText(prompt);
    sendText("\n");
  }

  public void sendEscape() {
    // ESC = 0x1B
    writeBytes(new byte[]{0x1B});
  }

  private void writeBytes(byte[] bytes) {
    try {
      pty.getPtyInput().write(bytes);
      pty.getPtyInput().flush();
    } catch (Exception e) {
      throw new IllegalStateException("Failed writing to PTY.", e);
    }
  }

  private void startPtyReadLoop() throws Exception {
    InputStream in = pty.getPtyOutput();
    Thread reader = new Thread(() -> {
      byte[] buffer = new byte[8192];
      while (true) {
        try {
          int n = in.read(buffer);
          if (n < 0) {
            return;
          }
          byte[] copy = new byte[n];
          System.arraycopy(buffer, 0, copy, 0, n);
          core.feed(copy, 0, copy.length);
        } catch (Exception e) {
          return;
        }
      }
    }, "PtyReadLoop");
    reader.setDaemon(true);
    reader.start();
  }

  @Override
  public void close() {
    try {
      pty.close();
    } catch (Exception e) {
      // ignore
    }
  }
}
