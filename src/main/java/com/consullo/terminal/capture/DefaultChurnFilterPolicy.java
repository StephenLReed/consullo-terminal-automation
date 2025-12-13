package com.consullo.terminal.capture;

import java.util.List;
import org.apache.commons.lang3.Validate;

/**
 * Default churn suppression policy.
 *
 * <p>Heuristics (intentionally conservative):
 * - Suppress very short spinner-like tokens ("|", "/", "-", "\\", "*", "…") and common "thinking" phrases.
 * - Suppress progress-like lines that contain '%' and multiple repeated characters ('=', '-', '#') in a bracket.
 *
 * <p>No regex is used. The policy is designed to be replaced or extended for Claude-specific UI strings.
 *
 * @since 1.0
 */
public final class DefaultChurnFilterPolicy implements ChurnFilterPolicy {

  @Override
  public boolean shouldSuppressRow(final String rowText, final List<String> recentSamples) throws Exception {
    Validate.notNull(rowText, "rowText must not be null");
    Validate.notNull(recentSamples, "recentSamples must not be null");

    final String trimmed = rowText.trim();
    if (trimmed.isEmpty()) {
      return false;
    }

    if (isSpinnerToken(trimmed)) {
      return true;
    }

    if (containsThinkingPhrase(trimmed)) {
      return true;
    }

    if (looksLikeProgressBar(trimmed)) {
      return true;
    }

    // If the row changes frequently among short variants, suppress.
    if (recentSamples.size() >= 6 && trimmed.length() <= 40) {
      int distinct = 0;
      String last = null;
      for (final String s : recentSamples) {
        if (last == null || !last.equals(s)) {
          distinct++;
          last = s;
        }
      }
      if (distinct >= 5) {
        return true;
      }
    }

    return false;
  }

  /**
   * Detects minimal spinner tokens.
   *
   * @param trimmed trimmed text
   * @return true if spinner-like token
   * @throws Exception if input is null
   */
  protected static boolean isSpinnerToken(final String trimmed) throws Exception {
    Validate.notNull(trimmed, "trimmed must not be null");

    if (trimmed.length() == 1) {
      final char c = trimmed.charAt(0);
      return c == '|' || c == '/' || c == '-' || c == '\\' || c == '*' || c == '.';
    }

    if (trimmed.length() <= 3) {
      // e.g., "...", "..", "..."
      boolean allDots = true;
      for (int i = 0; i < trimmed.length(); i++) {
        if (trimmed.charAt(i) != '.') {
          allDots = false;
          break;
        }
      }
      if (allDots) {
        return true;
      }
    }

    return false;
  }

  /**
   * Detects common "thinking" phrases without regex.
   *
   * @param trimmed trimmed text
   * @return true if phrase indicates transient status
   * @throws Exception if input is null
   */
  protected static boolean containsThinkingPhrase(final String trimmed) throws Exception {
    Validate.notNull(trimmed, "trimmed must not be null");

    final String lower = trimmed.toLowerCase();
    return lower.contains("thinking") || lower.contains("working") || lower.contains("loading")
        || lower.contains("waiting") || lower.contains("analyzing");
  }

  /**
   * Detects progress bar-like rows using simple checks.
   *
   * @param trimmed trimmed text
   * @return true if it resembles a progress bar
   * @throws Exception if input is null
   */
  protected static boolean looksLikeProgressBar(final String trimmed) throws Exception {
    Validate.notNull(trimmed, "trimmed must not be null");

    if (!trimmed.contains("%")) {
      return false;
    }

    final int open = trimmed.indexOf('[');
    final int close = trimmed.indexOf(']');
    if (open >= 0 && close > open) {
      final String inside = trimmed.substring(open + 1, close);
      int barChars = 0;
      for (int i = 0; i < inside.length(); i++) {
        final char c = inside.charAt(i);
        if (c == '=' || c == '-' || c == '#' || c == '>' || c == ' ') {
          barChars++;
        }
      }
      return barChars >= inside.length() - 1 && inside.length() >= 10;
    }

    return false;
  }
}
