package com.consullo.terminal.pty;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal controller for a PTY-attached subprocess.
 *
 * <p>Implementations must provide:
 * - access to PTY input/output streams
 * - resize support
 * - exit monitoring
 *
 * @since 1.0
 */
public interface PtyProcessController extends AutoCloseable {

  InputStream getPtyOutput() throws Exception;

  OutputStream getPtyInput() throws Exception;

  void resize(final int columns, final int rows) throws Exception;

  CompletableFuture<Integer> onExit() throws Exception;

  int pid() throws Exception;

  boolean isAlive() throws Exception;

  @Override
  void close() throws Exception;
}
