package com.consullo.terminal.pty;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PTY controller implemented with pty4j.
 *
 * <p>This controller spawns a PTY-attached subprocess and exposes streams suitable for:
 * - writing user input bytes (including ESC)
 * - reading rendered output bytes
 *
 * @since 1.0
 */
public final class PtyProcessControllerPty4j implements PtyProcessController {

  private static final Logger LOGGER = LoggerFactory.getLogger(PtyProcessControllerPty4j.class);

  private final PtyProcess process;
  private final CompletableFuture<Integer> exitFuture;

  /**
   * Spawns a PTY-attached process.
   *
   * @param config process configuration (command, working directory, environment, initial size)
   * @throws Exception if process cannot be started
   */
  public PtyProcessControllerPty4j(final PtyProcessConfig config) throws Exception {
    Validate.notNull(config, "config must not be null");
    Validate.notNull(config.command(), "command must not be null");
    Validate.isTrue(!config.command().isEmpty(), "command must not be empty");
    Validate.notNull(config.workingDirectory(), "workingDirectory must not be null");
    Validate.isTrue(config.initialColumns() > 0, "initialColumns must be positive");
    Validate.isTrue(config.initialRows() > 0, "initialRows must be positive");

    final Path workDir = config.workingDirectory();
    final String[] cmd = config.command().toArray(new String[0]);

    final Map<String, String> env = new HashMap<>(16);
    if (config.environment() != null) {
      env.putAll(config.environment());
    }

    final PtyProcessBuilder builder = new PtyProcessBuilder(cmd);
    builder.setDirectory(workDir.toString());
    builder.setEnvironment(env);

    // pty4j will initialize the PTY size based on the process's TTY settings where supported.
    this.process = builder.start();

    this.exitFuture = new CompletableFuture<>();
    startExitMonitorThread();
  }

  @Override
  public InputStream getPtyOutput() throws Exception {
    return this.process.getInputStream();
  }

  @Override
  public OutputStream getPtyInput() throws Exception {
    return this.process.getOutputStream();
  }

  @Override
  public void resize(final int columns, final int rows) throws Exception {
    Validate.isTrue(columns > 0, "columns must be positive");
    Validate.isTrue(rows > 0, "rows must be positive");

    try {
      this.process.setWinSize(new WinSize(columns, rows));
    } catch (final Exception e) {
      LOGGER.warn("PTY resize failed: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Override
  public CompletableFuture<Integer> onExit() throws Exception {
    return this.exitFuture;
  }

  @Override
  public int pid() throws Exception {
    return (int) this.process.pid();
  }

  @Override
  public boolean isAlive() throws Exception {
    return this.process.isAlive();
  }

  @Override
  public void close() throws Exception {
    try {
      this.process.destroy();
    } finally {
      // Best-effort: ensure the exit future completes.
      if (!this.exitFuture.isDone()) {
        this.exitFuture.complete(this.process.exitValue());
      }
    }
  }

  /**
   * Starts a monitor thread that completes the exit future when the subprocess terminates.
   *
   * @throws Exception if thread cannot be started
   */
  protected void startExitMonitorThread() throws Exception {
    final Thread monitor = new Thread(() -> {
      try {
        final int code = this.process.waitFor();
        this.exitFuture.complete(code);
      } catch (final Exception e) {
        this.exitFuture.completeExceptionally(e);
      }
    }, "PtyProcessExitMonitor");
    monitor.setDaemon(true);
    monitor.start();
  }
}
