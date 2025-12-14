package com.consullo.terminal.core.jediterm;

import com.jediterm.terminal.TerminalDataStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TerminalDataStream} implementation backed by an in-memory FIFO of
 * bytes.
 *
 * <p>
 * JediTerm's emulator consumes characters from {@link TerminalDataStream}. To
 * preserve exact byte values (including ESC=0x1B, CR=0x0D, etc.), this stream
 * maps each input byte to a char with the same numeric value in the range
 * 0..255 (ISO-8859-1 identity mapping).
 * </p>
 *
 * <p>
 * This implementation uses a non-blocking pattern: when the queue is empty,
 * {@link #getChar()} throws {@link TerminalDataStream.EOF} immediately rather
 * than blocking. This allows the emulator's {@code hasNext()} to return false
 * and the feed loop to exit, waiting for more data to arrive.
 * </p>
 */
public final class ByteQueueTerminalDataStream implements TerminalDataStream {

  private static final Logger LOGGER = LoggerFactory.getLogger(ByteQueueTerminalDataStream.class);

  private final Object lock = new Object();
  private final Deque<Integer> queue = new ArrayDeque<>();

  /**
   * Append bytes to the stream.
   *
   * @param data byte array
   * @param off offset
   * @param len length
   */
  public void appendBytes(byte[] data, int off, int len) {
    if (data == null) {
      throw new IllegalArgumentException("data must not be null.");
    }
    if (off < 0 || len < 0 || off + len > data.length) {
      throw new IllegalArgumentException("Invalid off/len.");
    }
    synchronized (lock) {
      for (int i = 0; i < len; i++) {
        int v = data[off + i] & 0xFF;
        queue.addLast(v);
      }
    }
  }

  @Override
  public char getChar() throws IOException {
    int v = takeNextByte();
    return (char) v;
  }

  @Override
  public void pushChar(char c) throws IOException {
    synchronized (lock) {
      queue.addFirst(((int) c) & 0xFF);
    }
  }

  @Override
  public String readNonControlCharacters(int maxChars) throws IOException {
    if (maxChars <= 0) {
      return "";
    }

    StringBuilder sb = new StringBuilder();

    synchronized (lock) {
      // Non-blocking: just read what's available
      while (sb.length() < maxChars && !queue.isEmpty()) {
        int v = queue.peekFirst();
        if (isControl(v)) {
          break;
        }
        queue.removeFirst();
        sb.append((char) v);
      }
    }

    return sb.toString();
  }

  private int takeNextByte() throws IOException {
    synchronized (lock) {
      if (queue.isEmpty()) {
        // Throw TerminalDataStream.EOF so JediEmulator sets myEof=true and hasNext() returns false
        throw new TerminalDataStream.EOF();
      }
      return queue.removeFirst();
    }
  }

  private static boolean isControl(int v) {
    // Standard C0 controls: 0x00..0x1F plus DEL 0x7F
    // This is intentionally conservative for readNonControlCharacters optimization.
    return (v >= 0 && v <= 0x1F) || (v == 0x7F);
  }

  @Override
  public void pushBackBuffer(char[] chars, int length) throws IOException {
    synchronized (lock) {
      // Push back in reverse order so they come out in original order
      for (int i = length - 1; i >= 0; i--) {
        queue.addFirst(((int) chars[i]) & 0xFF);
      }
    }
  }

  @Override
  public boolean isEmpty() {
    synchronized (lock) {
      return queue.isEmpty();
    }
  }
}
