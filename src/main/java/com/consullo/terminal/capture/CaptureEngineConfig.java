package com.consullo.terminal.capture;

/**
 * Capture engine configuration values.
 *
 * @param volatileRowCount number of bottom rows treated as volatile (spinner/progress region)
 * @param stabilityWindowMillis milliseconds a row must remain unchanged before emission
 * @param suppressAlternateScreen if true, suppress screen-stability emissions while in alternate screen
 * @since 1.0
 */
public record CaptureEngineConfig(
    int volatileRowCount,
    long stabilityWindowMillis,
    boolean suppressAlternateScreen) {
}
