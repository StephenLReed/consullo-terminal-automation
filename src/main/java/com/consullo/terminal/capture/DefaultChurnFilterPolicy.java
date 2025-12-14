package com.consullo.terminal.capture;

import com.consullo.terminal.core.TerminalSnapshot;
import java.util.List;

/**
 * Default churn suppression heuristics.
 *
 * <p>
 * This policy is intentionally conservative: it suppresses common
 * animation/spinner/progress patterns, and suppresses screen-derived output
 * while the terminal is in alternate-screen mode.
 * </p>
 */
public final class DefaultChurnFilterPolicy implements ChurnFilterPolicy {

  public boolean shouldSuppressLine(TerminalSnapshot snapshot, String line, boolean fromVolatileRegion) {
    if (line == null) {
      return true;
    }

    if (snapshot != null && snapshot.isAlternateScreen()) {
      // Default policy: do not emit screen-stable lines in alternate screen.
      // Scrollback lines may still be emitted by the capture engine.
      if (fromVolatileRegion) {
        return true;
      }
    }

    String s = rightTrim(line);
    if (s.isEmpty()) {
      return false;
    }

    // Suppress very short spinner-like lines.
    if (isLikelySpinnerLine(s)) {
      return true;
    }

    // Suppress typical progress bars and percent updates.
    if (isLikelyProgressLine(s)) {
      return true;
    }

    return false;
  }

  private static String rightTrim(String s) {
    // Right-trim whitespace (including NUL chars which terminals use for empty cells)
    int n = s.length();
    while (n > 0) {
      char c = s.charAt(n - 1);
      if (c == ' ' || c == '\0' || c == '\t') {
        n--;
      } else {
        break;
      }
    }
    if (n == s.length()) {
      return s;
    }
    return s.substring(0, n);
  }

  private static boolean isLikelySpinnerLine(String s) {
    // Common patterns: "Working |", "|", "-", "\", "..."
    // Avoid regex; use simple checks.
    int n = s.length();
    if (n == 1) {
      return isSpinnerGlyph(s.charAt(0));
    }
    // "..." is a common loading indicator
    if (n <= 3 && allDots(s)) {
      return true;
    }
    if (n == 2 && s.charAt(0) == ' ' && isSpinnerGlyph(s.charAt(1))) {
      return true;
    }
    if (n >= 3) {
      // Many spinners update the last char.
      char last = s.charAt(n - 1);
      if (isSpinnerGlyph(last)) {
        // If the rest is mostly letters/spaces, treat as spinner.
        int letters = 0;
        int spaces = 0;
        for (int i = 0; i < n - 1; i++) {
          char c = s.charAt(i);
          if (c == ' ') {
            spaces++;
          } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
            letters++;
          }
        }
        if (letters > 0 && spaces >= 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isSpinnerGlyph(char c) {
    // ASCII spinners
    if (c == '|' || c == '/' || c == '\\' || c == '-') {
      return true;
    }
    // Braille spinners are common; include a conservative range check.
    // Braille patterns: U+2800..U+28FF
    return (c >= 0x2800 && c <= 0x28FF);
  }

  private static boolean isLikelyProgressLine(String s) {
    // Percent: "... 12%" at end
    if (endsWithPercent(s)) {
      return true;
    }
    // Bracketed bar: "[=====>   ]" optionally with percent
    if (containsChar(s, '[') && containsChar(s, ']')) {
      // If it contains many '=' or '-' it's likely a progress bar.
      int eq = countChar(s, '=');
      int dash = countChar(s, '-');
      if (eq + dash >= 3) {
        return true;
      }
    }
    // Status prefixes
    if (startsWithIgnoreCase(s, "loading") || startsWithIgnoreCase(s, "thinking") || startsWithIgnoreCase(s, "working")) {
      if (endsWithDots(s)) {
        return true;
      }
    }
    return false;
  }

  private static boolean endsWithPercent(String s) {
    int n = s.length();
    if (n < 2) {
      return false;
    }
    if (s.charAt(n - 1) != '%') {
      return false;
    }
    // Ensure at least one digit before '%'
    char prev = s.charAt(n - 2);
    if (prev < '0' || prev > '9') {
      return false;
    }
    return true;
  }

  private static boolean endsWithDots(String s) {
    int n = s.length();
    if (n < 3) {
      return false;
    }
    return s.charAt(n - 1) == '.' && s.charAt(n - 2) == '.' && s.charAt(n - 3) == '.';
  }

  private static boolean startsWithIgnoreCase(String s, String prefix) {
    if (s.length() < prefix.length()) {
      return false;
    }
    for (int i = 0; i < prefix.length(); i++) {
      char a = s.charAt(i);
      char b = prefix.charAt(i);
      if (toLower(a) != toLower(b)) {
        return false;
      }
    }
    return true;
  }

  private static char toLower(char c) {
    if (c >= 'A' && c <= 'Z') {
      return (char) (c - 'A' + 'a');
    }
    return c;
  }

  private static boolean containsChar(String s, char c) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) {
        return true;
      }
    }
    return false;
  }

  private static int countChar(String s, char c) {
    int x = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) {
        x++;
      }
    }
    return x;
  }

  private static boolean allDots(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) != '.') {
        return false;
      }
    }
    return s.length() > 0;
  }

  @Override
  public boolean shouldSuppressRow(String rowText, List<String> recentSamples) throws Exception {
    if (rowText == null) {
      return true;
    }

    String s = rightTrim(rowText);
    if (s.isEmpty()) {
      return false;
    }

    // Suppress very short spinner-like lines.
    if (isLikelySpinnerLine(s)) {
      return true;
    }

    // Suppress typical progress bars and percent updates.
    if (isLikelyProgressLine(s)) {
      return true;
    }

    return false;
  }
}
