package com.consullo.terminal.capture;

import com.consullo.terminal.core.TerminalCore;
import com.consullo.terminal.core.jediterm.JediTermCore;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Golden transcript tests.
 *
 * <p>
 * Fixtures are stored as hex-encoded byte streams in .ansi files to ensure that
 * ESC (0x1B) and other control bytes are represented unambiguously in source
 * control and copy/paste workflows.
 * </p>
 */
public final class GoldenTranscriptTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoldenTranscriptTest.class);

  @Test
  public void spinnerChurnIsSuppressed() throws Exception {
    runFixture("fixtures/spinner.ansi", "fixtures/spinner.txt");
  }

  @Test
  public void progressChurnIsSuppressed() throws Exception {
    runFixture("fixtures/progress.ansi", "fixtures/progress.txt");
  }

  @Test
  public void alternateScreenIsSuppressedButExitTextIsCaptured() throws Exception {
    runFixture("fixtures/alt-screen.ansi", "fixtures/alt-screen.txt");
  }

  private static void runFixture(String ansiHexResource, String expectedResource) throws Exception {
    LOGGER.info("runFixture: starting {}", ansiHexResource);
    // Use 1-row terminal so all content scrolls into history immediately
    TerminalCore core = new JediTermCore(120, 1, 10_000);

    // volatileRowCount=0 since we have 1-row terminal, stabilityWindowMillis=0 for instant tests
    CaptureEngineConfig config = new CaptureEngineConfig(0, 0L, true);
    CaptureEngine capture = new CaptureEngine(config, new DefaultChurnFilterPolicy());

    List<TranscriptEvent> events = new ArrayList<>();

    // Feed fixture bytes all at once to ensure escape sequences are parsed correctly.
    byte[] data = loadHexResource(ansiHexResource);
    LOGGER.info("runFixture: loaded {} bytes from {}", data.length, ansiHexResource);

    core.feed(data, 0, data.length);
    events.addAll(capture.onDamage(core, core.snapshot(), null));

    LOGGER.info("runFixture: finished feeding, {} events", events.size());

    // Final drain
    events.addAll(capture.onDamage(core, core.snapshot(), null));

    String actual = join(events);
    String expected = loadTextResource(expectedResource);

    LOGGER.info("runFixture: comparing actual ({} chars) vs expected ({} chars)", actual.length(), expected.length());
    assertThat(actual).isEqualTo(expected);
  }

  private static String join(List<TranscriptEvent> events) {
    StringBuilder sb = new StringBuilder();
    for (TranscriptEvent e : events) {
      if (e == null) {
        continue;
      }
      if (e.type() == TranscriptEvent.Type.APPEND) {
        sb.append(e.text());
      }
    }
    return sb.toString();
  }

  private static byte[] loadHexResource(String path) throws Exception {
    InputStream is = GoldenTranscriptTest.class.getClassLoader().getResourceAsStream(path);
    if (is == null) {
      throw new IllegalStateException("Missing resource: " + path);
    }
    String hex = readAll(is);

    // Remove whitespace without regex.
    StringBuilder cleaned = new StringBuilder();
    for (int i = 0; i < hex.length(); i++) {
      char c = hex.charAt(i);
      if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
        continue;
      }
      cleaned.append(c);
    }

    if ((cleaned.length() % 2) != 0) {
      throw new IllegalStateException("Hex length must be even for resource: " + path);
    }

    byte[] out = new byte[cleaned.length() / 2];
    int j = 0;
    for (int i = 0; i < cleaned.length(); i += 2) {
      int hi = hexVal(cleaned.charAt(i));
      int lo = hexVal(cleaned.charAt(i + 1));
      out[j++] = (byte) ((hi << 4) | lo);
    }
    return out;
  }

  private static int hexVal(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return (c - 'a') + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return (c - 'A') + 10;
    }
    throw new IllegalArgumentException("Invalid hex char: " + c);
  }

  private static String loadTextResource(String path) throws Exception {
    InputStream is = GoldenTranscriptTest.class.getClassLoader().getResourceAsStream(path);
    if (is == null) {
      throw new IllegalStateException("Missing resource: " + path);
    }
    return readAll(is);
  }

  private static String readAll(InputStream is) throws Exception {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line);
        sb.append("\n");
      }
      return sb.toString();
    }
  }
}
