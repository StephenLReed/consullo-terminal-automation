package com.consullo.terminal.pty;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for spawning a PTY-attached process.
 *
 * @param command command and arguments (e.g., ["claude", "code"])
 * @param workingDirectory working directory for the spawned process
 * @param environment environment variables to add/override (may be null)
 * @param initialColumns initial PTY columns
 * @param initialRows initial PTY rows
 * @since 1.0
 */
public record PtyProcessConfig(
    List<String> command,
    Path workingDirectory,
    java.util.Map<String, String> environment,
    int initialColumns,
    int initialRows) {
}
