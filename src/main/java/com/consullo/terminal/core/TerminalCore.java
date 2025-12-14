package com.consullo.terminal.core;

import com.consullo.terminal.core.events.DamageEvent;
import com.consullo.terminal.core.events.DamageListener;

/**
 * Terminal core abstraction that converts raw PTY bytes into a maintained terminal state (screen + scrollback).
 *
 * <p>This interface exists to isolate the capture and automation layers from a specific terminal emulation
 * implementation. The reference implementation uses JediTerm, but alternative engines (e.g., libvterm via JNI)
 * can be substituted by providing another {@link TerminalCore} implementation.
 *
 * @since 1.0
 */
public interface TerminalCore {

  /**
   * Feeds raw bytes received from the PTY into the terminal parser/state machine.
   *
   * <p>Callers must ensure that all invocations are serialized on a single thread to avoid inconsistent
   * terminal state and race conditions in damage event emission.
   *
   * @param data raw bytes read from the PTY output stream
   * @param offset offset into the data array
   * @param length number of bytes to read from the array
   * @throws Exception if terminal parsing fails
   */
  void feed(final byte[] data, final int offset, final int length) throws Exception;

  /**
   * Returns an immutable snapshot of the current terminal state suitable for capture decisions and UI rendering.
   *
   * @return immutable terminal snapshot
   * @throws Exception if snapshot construction fails
   */
  TerminalSnapshot snapshot() throws Exception;

  /**
   * Returns a view over the scrollback buffer. This view is expected to be efficient for incremental reads.
   *
   * @return scrollback view
   * @throws Exception if scrollback access fails
   */
  ScrollbackView scrollback() throws Exception;

  /**
   * Resizes the terminal and underlying PTY row/column settings.
   *
   * @param columns number of columns in the terminal
   * @param rows number of rows in the terminal
   * @throws Exception if resize fails
   */
  void resize(final int columns, final int rows) throws Exception;

  /**
   * Adds a listener that will be invoked when the terminal state changes.
   *
   * @param listener listener to add
   * @throws Exception if listener cannot be added
   */
  void addDamageListener(final DamageListener listener) throws Exception;
}
