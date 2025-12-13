package com.consullo.terminal.driver;

import com.consullo.terminal.capture.CaptureEngine;
import com.consullo.terminal.capture.CaptureEngineConfig;
import com.consullo.terminal.capture.CaptureEngineState;
import com.consullo.terminal.capture.DefaultChurnFilterPolicy;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.events.DamageEvent;
import com.consullo.terminal.core.events.DamageListener;
import com.consullo.terminal.pty.PtyProcessController;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Validate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Session wrapper that ties together:
 * - PTY process controller
 * - Terminal core
 * - Capture engine
 * - Transcript event queue
 *
 * <p>This class is intentionally small and pragmatic. It is safe to evolve into a richer API while keeping:
 * - terminal-core mutations serialized on a single executor
 * - transcript emission deterministic and testable
 *
 * @since 1.0
 */
public final class ClaudeSession implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeSession.class);

  private final PtyProcessController pty;
  private final TerminalCore core;
  private final ExecutorService terminalExecutor;
  private final BlockingQueue<JSONObject> transcriptEvents;

  private final CaptureEngineConfig captureConfig;
  private CaptureEngineState captureState;

  private final Deque<String> volatileSamples;

  /**
   * Creates a new session.
   *
   * @param pty PTY process controller
   * @param core terminal core
   * @param captureConfig capture configuration
   * @throws Exception if inputs are invalid
   */
  public ClaudeSession(
      final PtyProcessController pty,
      final TerminalCore core,
      final CaptureEngineConfig captureConfig) throws Exception {

    Validate.notNull(pty, "pty must not be null");
    Validate.notNull(core, "core must not be null");
    Validate.notNull(captureConfig, "captureConfig must not be null");

    this.pty = pty;
    this.core = core;
    this.captureConfig = captureConfig;

    this.terminalExecutor = Executors.newSingleThreadExecutor(r -> {
      final Thread t = new Thread(r, "TerminalCoreExecutor");
      t.setDaemon(true);
      return t;
    });

    this.transcriptEvents = new ArrayBlockingQueue<>(8192);

    this.captureState = CaptureEngine.createInitialState();
    this.volatileSamples = new ArrayDeque<>(16);

    this.core.addDamageListener(new InternalDamageListener());

    startPtyReadLoop();
  }

  /**
   * Sends a prompt to the terminal, optionally appending a newline.
   *
   * @param prompt prompt text to send
   * @param appendNewline if true, append '\n'
   * @throws Exception if sending fails
   */
  public void sendPrompt(final String prompt, final boolean appendNewline) throws Exception {
    Validate.notNull(prompt, "prompt must not be null");
    final StringBuilder outBuilder = new StringBuilder(prompt.length() + 2);
    outBuilder.append(prompt);
    if (appendNewline) {
      outBuilder.append('\n');
    }
    sendText(outBuilder.toString());
  }

  /**
   * Sends raw text to the PTY input using UTF-8 encoding.
   *
   * @param text text to send
   * @throws Exception if sending fails
   */
  public void sendText(final String text) throws Exception {
    Validate.notNull(text, "text must not be null");
    final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    sendBytes(bytes);
  }

  /**
   * Sends the ESC key to the PTY input.
   *
   * @throws Exception if sending fails
   */
  public void sendEscape() throws Exception {
    sendBytes(new byte[] { 0x1B });
  }

  /**
   * Retrieves the next transcript event from the internal queue (blocking).
   *
   * @return transcript event JSON
   * @throws Exception if interrupted or queue access fails
   */
  public JSONObject takeTranscriptEvent() throws Exception {
    try {
      return this.transcriptEvents.take();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Exception("Interrupted while waiting for transcript event", e);
    }
  }

  /**
   * Polls for the next transcript event with a timeout.
   *
   * @param timeout maximum time to wait
   * @param unit time unit of the timeout
   * @return transcript event JSON, or null if timeout elapsed
   * @throws Exception if interrupted
   */
  public JSONObject pollTranscriptEvent(final long timeout, final TimeUnit unit) throws Exception {
    Validate.notNull(unit, "unit must not be null");
    try {
      return this.transcriptEvents.poll(timeout, unit);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Exception("Interrupted while polling for transcript event", e);
    }
  }

  /**
   * Returns whether the underlying PTY process is still alive.
   *
   * @return true if process is running
   * @throws Exception if status check fails
   */
  public boolean isAlive() throws Exception {
    return this.pty.isAlive();
  }

  /**
   * Returns the terminal core for UI attachment or advanced diagnostics.
   *
   * @return core
   * @throws Exception never thrown, but retained for uniform Consullo signatures
   */
  public TerminalCore getCore() throws Exception {
    return this.core;
  }

  @Override
  public void close() throws Exception {
    try {
      this.terminalExecutor.shutdownNow();
    } finally {
      this.pty.close();
    }
  }

  /**
   * Starts a PTY read loop that forwards bytes into the terminal executor.
   *
   * @throws Exception if PTY streams cannot be obtained
   */
  protected void startPtyReadLoop() throws Exception {
    final InputStream in = this.pty.getPtyOutput();

    final Thread reader = new Thread(() -> {
      final byte[] buffer = new byte[8192];
      while (true) {
        try {
          final int n = in.read(buffer);
          if (n < 0) {
            return;
          }
          final byte[] copy = new byte[n];
          System.arraycopy(buffer, 0, copy, 0, n);

          this.terminalExecutor.execute(() -> {
            try {
              this.core.feed(copy, 0, copy.length);
            } catch (final Exception e) {
              LOGGER.error("Terminal feed failed: {}", e.getMessage(), e);
            }
          });

        } catch (final Exception e) {
          LOGGER.warn("PTY read loop terminated: {}", e.getMessage(), e);
          return;
        }
      }
    }, "PtyReadLoop");
    reader.setDaemon(true);
    reader.start();
  }

  /**
   * Writes bytes to the PTY input.
   *
   * @param bytes bytes to write
   * @throws Exception if write fails
   */
  protected void sendBytes(final byte[] bytes) throws Exception {
    Validate.notNull(bytes, "bytes must not be null");
    final OutputStream out = this.pty.getPtyInput();
    out.write(bytes);
    out.flush();
  }

  /**
   * Damage listener that runs on the terminal core executor thread.
   */
  private final class InternalDamageListener implements DamageListener {

    @Override
    public void onDamage(final DamageEvent damageEvent) throws Exception {
      final JSONObject result = CaptureEngine.processDamage(
          ClaudeSession.this.captureState,
          ClaudeSession.this.captureConfig,
          new DefaultChurnFilterPolicy(),
          damageEvent,
          ClaudeSession.this.core.snapshot(),
          ClaudeSession.this.core.scrollback());

      ClaudeSession.this.captureState = CaptureEngine.decodeState(result.getJSONObject("state"));

      final JSONArray events = result.getJSONArray("events");
      for (final Object o : events) {
        final JSONObject e = (JSONObject) o;
        offerTranscriptEvent(e);
      }
    }

    /**
     * Offers a transcript event to the queue.
     *
     * @param event event
     * @throws Exception if event is null
     */
    protected void offerTranscriptEvent(final JSONObject event) throws Exception {
      Validate.notNull(event, "event must not be null");

      // Best effort: if the queue is full, drop oldest.
      if (!ClaudeSession.this.transcriptEvents.offer(event)) {
        ClaudeSession.this.transcriptEvents.poll();
        ClaudeSession.this.transcriptEvents.offer(event);
      }
    }
  }
}
