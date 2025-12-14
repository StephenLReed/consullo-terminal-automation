# CLAUDE.md - AI Assistant Context

This file provides context for AI assistants working with this codebase.

## Project Purpose

**Consullo Terminal Automation** is a Java library that drives the Claude Code CLI from an external Java process. It sends prompts and keystrokes (including ESC) to Claude Code via PTY and captures a clean transcript of output, filtering out terminal animations and screen redraws.

### Core Requirement

> Drive Claude Code CLI by sending prompts and ESC keystrokes from Java, capturing new text output while ignoring screen redraws and animations.

### Use Case

Interactive wrapper for human-supervised multi-turn conversations with Claude Code, where follow-up prompts are sent based on Claude's responses.

## Architecture Summary

```
ClaudeSession (high-level API)
    ├── PtyProcessControllerPty4j (PTY management via pty4j)
    ├── JediTermCore (ANSI/VT parsing via JediTerm 3.57)
    └── CaptureEngine (dual-signal capture with churn filtering)
```

**Key insight**: Dual-signal capture strategy:
1. **History lines** (immediate): When text scrolls off screen into history, it's "committed" and emitted immediately.
2. **Screen lines** (stability-tracked): Content that hasn't scrolled yet is tracked and emitted only after remaining unchanged for a configurable time window.

This naturally filters most in-place animations while capturing stable content that hasn't scrolled yet.

## Package Layout

| Package | Purpose |
|---------|---------|
| `com.consullo.terminal.pty` | PTY process spawn, I/O, resize |
| `com.consullo.terminal.core` | Terminal abstraction, snapshot, scrollback |
| `com.consullo.terminal.core.jediterm` | JediTerm adapter (emulator, buffer, headless display) |
| `com.consullo.terminal.core.events` | DamageEvent, DamageListener |
| `com.consullo.terminal.capture` | CaptureEngine, ChurnFilterPolicy, TranscriptEvent |
| `com.consullo.terminal.driver` | ClaudeSession, ClaudeSessionFactory |
| `com.consullo.terminal.demo` | Demo apps |

## Key Classes

| Class | Role |
|-------|------|
| `ClaudeSession` | Main entry point: sendPrompt(), sendEscape(), transcriptQueue() |
| `JediTermCore` | Wraps JediTerm for headless terminal emulation |
| `ByteQueueTerminalDataStream` | Non-blocking FIFO queue bridging PTY bytes to JediEmulator |
| `CaptureEngine` | Dual-signal capture: history + screen stability tracking, deduplication |
| `DefaultChurnFilterPolicy` | Suppresses spinners, progress bars, loading indicators |
| `ScrollbackView` | Interface for accessing history lines and screen content |

## Build and Test

```bash
# Build and test
mvn -q test

# Run demo
mvn -q -DskipTests exec:java -Dexec.mainClass=com.consullo.terminal.demo.TerminalAutomationDemo
```

## Known Issues

### Escape Sequence Chunking

When feeding data in small chunks, escape sequences that span chunk boundaries may be parsed incorrectly. The `ByteQueueTerminalDataStream` throws EOF when empty, causing the emulator to abort mid-sequence. **Workaround**: Feed data in larger chunks when possible.

### Test Fixtures

Golden transcript tests use hex-encoded `.ansi` files in `src/test/resources/fixtures/` to preserve exact byte sequences including ESC (0x1B). Tests use a 1-row terminal and `stabilityWindowMillis=0` for deterministic behavior.

## Coding Conventions

- No regex in hot paths (churn filtering uses manual string operations)
- Immutable records for data transfer (TranscriptEvent, TerminalSnapshot)
- Defensive null checks with explicit IllegalArgumentException
- SLF4J logging (logback-classic in test scope)
- Thread safety via synchronized blocks on dedicated lock objects

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `org.jetbrains.pty4j:pty4j:0.13.11` | Cross-platform PTY |
| `org.jetbrains.jediterm:jediterm-core:3.57` | Terminal emulator (from JetBrains Nexus) |
| `com.consullo:consullo-utilities` | Consullo shared utilities |

## Extension Points

- `ChurnFilterPolicy`: Customize what gets filtered from transcript
- `TerminalCore`: Swap JediTerm for another terminal emulator
- `PtyProcessController`: Alternative PTY implementations

## Common Tasks

### Add new churn filter pattern
Edit `DefaultChurnFilterPolicy.java`, add detection in `isLikelySpinnerLine()` or `isLikelyProgressLine()`.

### Debug terminal parsing
Enable DEBUG logging for `com.consullo.terminal` in `src/test/resources/logback-test.xml`.

### Add golden transcript test
1. Create hex-encoded `.ansi` fixture file
2. Create expected `.txt` output file (no trailing blank lines)
3. Add test method calling `runFixture("fixtures/name.ansi", "fixtures/name.txt")`
4. Use 1-row terminal and `stabilityWindowMillis=0` for deterministic results

### Adjust capture behavior
Edit `CaptureEngineConfig` parameters:
- `volatileRowCount`: Number of bottom screen rows to skip (status/spinner area)
- `stabilityWindowMillis`: Time screen content must be unchanged before emission
- `suppressAlternateScreen`: Whether to suppress output in alternate screen mode

## Related Documentation

- `docs/design.md` - Full design document
- `docs/ARCHITECTURE.md` - Module and threading overview
- `docs/CAPTURE_POLICY.md` - Churn suppression strategy
- `docs/INTEGRATION.md` - API usage guide
