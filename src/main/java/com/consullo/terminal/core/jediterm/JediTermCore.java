package com.consullo.terminal.core.jediterm;

import com.consullo.terminal.core.ScrollbackView;
import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.TerminalSnapshot;
import com.consullo.terminal.core.events.DamageEvent;
import com.consullo.terminal.core.events.DamageListener;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TerminalDataStream;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link TerminalCore} implementation backed by JediTerm.
 *
 * <p>
 * This integrates:
 * <ul>
 * <li>ANSI/VT parsing: {@link JediEmulator}</li>
 * <li>Terminal state + cursor: {@link JediTerminal}</li>
 * <li>Scrollback/history: {@link TerminalTextBuffer} (negative indices are
 * history)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Notes:
 * <ul>
 * <li>Carriage return (\r) rewrites are interpreted by the emulator into the
 * buffer.</li>
 * <li>Alternate screen transitions are tracked via terminal modes and buffer
 * behavior.</li>
 * </ul>
 * </p>
 */
public final class JediTermCore implements TerminalCore {

  private static final Logger LOGGER = LoggerFactory.getLogger(JediTermCore.class);

  private final Object lock = new Object();

  private final ByteQueueTerminalDataStream dataStream;
  private final TerminalTextBuffer textBuffer;
  private final StyleState styleState;
  private final JediTerminal terminal;
  private final JediEmulator emulator;

  private final List<DamageListener> listeners = new ArrayList<>();

  private int cols;
  private int rows;

  // Minimal mode tracking. JediTerm internally tracks modes; we also expose a simple snapshot.
  private volatile boolean alternateScreen;

  /**
   * Creates a terminal core with given screen size and scrollback capacity.
   *
   * @param cols screen columns
   * @param rows screen rows
   * @param maxHistoryLines max scrollback lines to retain (conservative
   * default: 50_000)
   */
  public JediTermCore(int cols, int rows, int maxHistoryLines) {
    if (cols <= 0 || rows <= 0) {
      throw new IllegalArgumentException("cols/rows must be positive.");
    }
    if (maxHistoryLines <= 0) {
      throw new IllegalArgumentException("maxHistoryLines must be positive.");
    }

    this.cols = cols;
    this.rows = rows;

    this.dataStream = new ByteQueueTerminalDataStream();

    // TerminalTextBuffer manages screen + history.
    this.styleState = new StyleState();
    this.textBuffer = new TerminalTextBuffer(cols, rows, styleState, maxHistoryLines);

    // JediTerminal applies control sequences into the display + text buffer.
    // JediTerm 3.57 requires a TerminalDisplay; we use a headless stub.
    this.terminal = new JediTerminal(new HeadlessTerminalDisplay(), textBuffer, styleState);

    // Emulator pulls from dataStream and drives the terminal.
    TerminalDataStream tds = dataStream;
    Terminal t = terminal;
    this.emulator = new JediEmulator(tds, t);
  }

  @Override
  public void feed(byte[] data, int off, int len) {
    if (data == null) {
      throw new IllegalArgumentException("data must not be null.");
    }
    if (len <= 0) {
      return;
    }

    // Append bytes for the emulator to consume.
    LOGGER.debug("feed: appending {} bytes", len);
    dataStream.appendBytes(data, off, len);

    // Reset EOF flag so emulator can process new data.
    // JediEmulator sets myEof=true when TerminalDataStream.EOF is thrown.
    emulator.resetEof();

    // Let the emulator drive iteration - it handles escape sequences internally.
    // The emulator will set myEof=true when the data stream throws EOF, causing hasNext() to return false.
    boolean anyChange = false;
    int processed = 0;

    synchronized (lock) {
      while (emulator.hasNext()) {
        try {
          emulator.next();
          anyChange = true;
          processed++;
        } catch (IOException e) {
          LOGGER.debug("feed: IOException after {} iterations", processed);
          break;
        } catch (RuntimeException e) {
          LOGGER.warn("feed: RuntimeException after {} iterations: {}", processed, e.getMessage());
          break;
        }
      }
    }

    LOGGER.debug("feed: processed {} iterations, anyChange={}", processed, anyChange);
    if (anyChange) {
      fireDamageEvent();
    }
  }

  @Override
  public TerminalSnapshot snapshot() {
    synchronized (lock) {
      // We expose a minimal snapshot. The capture engine should prefer scrollback for committed output.
      return TerminalSnapshot.builder()
              .timestamp(Instant.now())
              .cols(cols)
              .rows(rows)
              .alternateScreen(alternateScreen)
              .build();
    }
  }

  @Override
  public ScrollbackView scrollback() {
    return new JediTermScrollbackView(textBuffer);
  }

  @Override
  public void addDamageListener(DamageListener l) {
    if (l == null) {
      throw new IllegalArgumentException("listener must not be null.");
    }
    synchronized (lock) {
      listeners.add(l);
    }
  }

  @Override
  public void resize(int cols, int rows) {
    if (cols <= 0 || rows <= 0) {
      throw new IllegalArgumentException("cols/rows must be positive.");
    }
    synchronized (lock) {
      this.cols = cols;
      this.rows = rows;
      TermSize newSize = new TermSize(cols, rows);
      textBuffer.resize(newSize, null, null);
      terminal.resize(newSize, RequestOrigin.Remote);
    }
    fireDamageEvent();
  }

  private void fireDamageEvent() {
    DamageEvent ev = DamageEvent.createFullRedraw();
    List<DamageListener> copy;
    synchronized (lock) {
      copy = new ArrayList<>(listeners);
    }
    TerminalSnapshot snap = snapshot();
    for (DamageListener dl : copy) {
      try {
        dl.onDamage(snap, ev);
      } catch (RuntimeException ignored) {
        // keep system alive
      }
    }
  }

  private void updateAlternateScreenHeuristic(char lastChar) {
    // JediTerm internally handles DECSET/DECRST modes. In a full integration, you can subscribe to mode changes.
    // As a conservative approximation, we toggle when we see the common sequences:
    // ESC [ ? 1 0 4 9 h  (enter alt screen)
    // ESC [ ? 1 0 4 9 l  (exit alt screen)
    // This implementation avoids regex; we detect by a simple rolling window.
    // For correctness, CaptureEngine also treats repeated repainting of the last lines as volatile.
    // NOTE: The real terminal may use ?47h/?1047h etc; add those if needed.
    // We keep a tiny finite-state machine.
    altFsmStep(lastChar);
  }

  private int altState = 0; // state for detecting ESC[?1049h/l
  private boolean altSeenDigits1049;

  private void altFsmStep(char ch) {
    int v = ((int) ch) & 0xFF;

    if (altState == 0) {
      if (v == 0x1B) { // ESC
        altState = 1;
        altSeenDigits1049 = false;
      }
      return;
    }

    if (altState == 1) {
      if (ch == '[') {
        altState = 2;
        return;
      }
      altState = 0;
      return;
    }

    if (altState == 2) {
      if (ch == '?') {
        altState = 3;
        return;
      }
      altState = 0;
      return;
    }

    if (altState == 3) {
      // read digits; recognize 1049 specifically
      if (ch >= '0' && ch <= '9') {
        // We track by building a short digits string without allocations:
        // Accept sequences "...1049..."
        // Implement: shift in last 4 digits.
        shiftDigit(ch);
        return;
      }

      if (ch == 'h') {
        if (altSeenDigits1049) {
          alternateScreen = true;
        }
        altState = 0;
        return;
      }

      if (ch == 'l') {
        if (altSeenDigits1049) {
          alternateScreen = false;
        }
        altState = 0;
        return;
      }

      // any other terminator resets
      altState = 0;
    }
  }

  private int last4Digits = 0;

  private void shiftDigit(char ch) {
    int d = ch - '0';
    last4Digits = ((last4Digits % 1000) * 10) + d;
    if (last4Digits == 1049) {
      altSeenDigits1049 = true;
    }
  }

  /**
   * Scrollback view adapter that provides access to both history and screen content
   * from {@link TerminalTextBuffer}.
   *
   * <p>History lines are truly committed (scrolled off screen) and stable.
   * Screen lines are the current display and may be volatile.</p>
   */
  private static final class JediTermScrollbackView implements ScrollbackView {

    private final TerminalTextBuffer buf;

    private JediTermScrollbackView(TerminalTextBuffer buf) {
      this.buf = buf;
    }

    @Override
    public int historyLineCount() {
      return buf.getHistoryLinesCount();
    }

    @Override
    public int screenRowCount() {
      return buf.getHeight();
    }

    @Override
    public List<String> readHistoryLines(int startInclusive, int endExclusive) {
      int history = buf.getHistoryLinesCount();

      List<String> result = new ArrayList<>();
      for (int index = startInclusive; index < endExclusive && index < history; index++) {
        if (index < 0) {
          continue;
        }
        // Map 0..history-1 to negative buffer indices: -history .. -1
        int bufIndex = index - history;
        TerminalLine line = buf.getLine(bufIndex);
        result.add(terminalLineToPlainText(line));
      }
      return result;
    }

    @Override
    public List<String> readScreenLines(int startInclusive, int endExclusive) {
      int screenHeight = buf.getHeight();

      List<String> result = new ArrayList<>();
      for (int index = startInclusive; index < endExclusive && index < screenHeight; index++) {
        if (index < 0) {
          continue;
        }
        // Screen lines use 0-based positive indices
        TerminalLine line = buf.getLine(index);
        result.add(terminalLineToPlainText(line));
      }
      return result;
    }

    private static String terminalLineToPlainText(TerminalLine line) {
      if (line == null) {
        return "";
      }
      StringBuilder sb = new StringBuilder();
      List<TerminalLine.TextEntry> entries = line.getEntries();
      if (entries == null) {
        return "";
      }
      for (TerminalLine.TextEntry e : entries) {
        if (e == null) {
          continue;
        }
        CharSequence cs = e.getText();
        if (cs != null) {
          sb.append(cs);
        }
      }
      // Right-trim whitespace (including NUL chars which terminals use for empty cells)
      int n = sb.length();
      while (n > 0) {
        char c = sb.charAt(n - 1);
        if (c == ' ' || c == '\0' || c == '\t') {
          n--;
        } else {
          break;
        }
      }
      if (n != sb.length()) {
        sb.setLength(n);
      }
      return sb.toString();
    }
  }
}
