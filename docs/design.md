# Consullo Terminal Automation - Design Document

## Overview

Consullo Terminal Automation is a Java library for programmatically driving interactive CLI/TUI applications—specifically Claude Code—while capturing a clean, append-only transcript that filters out terminal noise such as spinners, progress bars, and screen redraws.

### Problem Statement

Claude Code is a powerful CLI tool, but integrating it into automated or semi-automated workflows presents challenges:

1. **Terminal Complexity**: Claude Code uses ANSI/VT escape sequences for rich terminal output including cursor positioning, colors, and alternate screen buffers.

2. **Visual Noise**: The CLI displays spinners, progress indicators, and status lines that are useful for humans but pollute transcripts for programmatic consumption.

3. **Keystroke Injection**: Sending prompts and control keys (especially ESC for interruption) requires proper PTY handling, not simple stdin piping.

4. **Multi-turn Interaction**: Conducting conversations requires parsing Claude's responses to determine when it's ready for the next prompt.

### Solution

This library provides a PTY-attached wrapper that:
- Spawns Claude Code (or any CLI) in a pseudo-terminal
- Parses all terminal output through a mature VT emulator (JediTerm)
- Maintains scrollback history and screen state
- Emits a filtered transcript stream that captures committed output while suppressing transient animations

## Target Use Case

**Interactive Wrapper**: A human-supervised tool that wraps Claude Code with additional capabilities, enabling multi-turn programmatic conversations where follow-up prompts are sent based on Claude's responses.

Example workflow:
```
1. Java process spawns Claude Code via PTY
2. Sends initial prompt: "Explain the authentication flow in this codebase"
3. Captures Claude's response (filtered of spinners/progress)
4. Based on response, sends follow-up: "Now refactor the login function"
5. Captures response, sends ESC if needed to interrupt
6. Human reviews transcript and intervenes as needed
```

## Architecture

### Component Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                     ClaudeSession (Driver)                      │
│  High-level API: sendPrompt(), sendEscape(), transcriptQueue()  │
└─────────────────────────────────────────────────────────────────┘
                                │
                ┌───────────────┼───────────────────────┐
                ▼               ▼                       ▼
┌──────────────────────┐  ┌───────────────────┐  ┌─────────────────────┐
│ PtyProcessController │  │  TerminalCore     │  │   CaptureEngine     │
│   (pty4j wrapper)    │  │ (JediTerm adapter)│  │ (churn filtering)   │
└──────────────────────┘  └───────────────────┘  └─────────────────────┘
        │                       │                       │
        │                       ▼                       │
        │              ┌─────────────────┐              │
        │              │ ScrollbackView  │◄─────────────┘
        │              │  (line buffer)  │
        │              └─────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Operating System PTY                         │
│              (pseudo-terminal master/slave pair)                │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Claude Code Process                        │
│                   (or any interactive CLI)                      │
└─────────────────────────────────────────────────────────────────┘
```

### Package Structure

| Package | Responsibility |
|---------|----------------|
| `com.consullo.terminal.pty` | PTY lifecycle, process spawn, resize, I/O streams |
| `com.consullo.terminal.core` | Terminal state abstraction, snapshot, scrollback interface |
| `com.consullo.terminal.core.jediterm` | JediTerm integration: VT parsing, text buffer, headless display |
| `com.consullo.terminal.core.events` | Damage events signaling terminal state changes |
| `com.consullo.terminal.capture` | Churn-suppressed transcript extraction |
| `com.consullo.terminal.driver` | High-level session API for Claude Code interaction |
| `com.consullo.terminal.demo` | Demo applications and test fixtures |

## Core Components

### PtyProcessController

Abstracts PTY management using pty4j:
- Spawns subprocess attached to PTY master/slave
- Provides input stream (process stdout) and output stream (process stdin)
- Handles terminal resize (SIGWINCH)
- Monitors process exit

### TerminalCore (JediTermCore)

Wraps JediTerm's terminal emulator for headless operation:
- **JediEmulator**: Parses ANSI/VT byte sequences
- **JediTerminal**: Maintains cursor position, screen attributes
- **TerminalTextBuffer**: Manages screen content and scrollback history
- **HeadlessTerminalDisplay**: Stub display for non-GUI operation

Key method: `feed(byte[] data, int off, int len)` - processes raw PTY output through the emulator.

### ScrollbackView

Read-only interface to terminal history and screen content:
- `historyLineCount()`: Count of lines scrolled off screen (committed)
- `screenRowCount()`: Count of visible screen rows
- `readHistoryLines(start, end)`: Extract history line range as plain text
- `readScreenLines(start, end)`: Extract screen row range as plain text
- Legacy methods `lineCount()` and `readLines()` for backwards compatibility

History lines are the primary signal source—they've scrolled off screen and are "committed". Screen lines are secondary and require stability tracking before emission.

### CaptureEngine

Converts terminal damage events into transcript events using a dual-signal capture strategy:

**Signal 1 - History Lines (Immediate)**: Emit new scrollback/history lines as they appear. Lines that enter history have been "committed" by the terminal—they're no longer subject to in-place rewriting. This is the high-signal, reliable source.

**Signal 2 - Screen Lines (Stability-Based)**: Track screen content and emit lines only after they remain unchanged for `stabilityWindowMillis`. This captures content that hasn't scrolled yet but is stable enough to be meaningful. Volatile bottom rows (configurable via `volatileRowCount`) are skipped as they typically contain status/spinner content.

**Deduplication**: Content hashes prevent re-emitting identical lines across both signals.

**Whitespace Normalization**: Lines are trimmed on both left and right (including NUL characters used for empty terminal cells).

**Churn Filtering**: The `ChurnFilterPolicy` interface allows suppression of:
- Spinner patterns (`|`, `/`, `-`, `\`, braille characters)
- Progress bars (`[=====>   ]`, `45%`)
- Status prefixes (`Loading...`, `Thinking...`)

**Alternate Screen Handling**: When the terminal enters alternate screen mode (e.g., for full-screen editors), screen-derived output is suppressed since it represents volatile UI rather than committed content.

### TranscriptEvent

Immutable record representing captured output:
```java
public record TranscriptEvent(
    Type type,           // APPEND
    String text,         // The captured text
    Instant timestamp,   // When captured
    Source source        // SCROLLBACK (immediate history) or SCREEN_STABLE (stability-tracked screen)
) {}
```

### ClaudeSession

High-level API for driving Claude Code:

```java
// Create session
ClaudeSession session = ClaudeSessionFactory.create(
    new String[]{"claude"},  // command
    workDir,
    env,
    120, 30  // cols, rows
);

// Send prompt
session.sendPrompt("Explain this code");

// Poll for output
TranscriptEvent event = session.transcriptQueue().poll(5, TimeUnit.SECONDS);

// Send ESC to interrupt
session.sendEscape();

// Cleanup
session.close();
```

## Data Flow

### Output Path (Claude Code → Transcript)

```
Claude Code writes to PTY slave stdout
            │
            ▼
PTY master readable → PtyProcessController reads bytes
            │
            ▼
TerminalCore.feed(bytes) → JediEmulator parses VT sequences
            │
            ▼
JediTerminal updates screen/scrollback buffer
            │
            ▼
DamageEvent fired → CaptureEngine.onDamage()
            │
            ▼
New scrollback lines extracted → ChurnFilterPolicy applied
            │
            ▼
TranscriptEvent emitted → BlockingQueue<TranscriptEvent>
            │
            ▼
Consumer polls transcript events
```

### Input Path (Keystrokes → Claude Code)

```
ClaudeSession.sendPrompt("text") or sendEscape()
            │
            ▼
Bytes written to PTY master stdin
            │
            ▼
PTY slave stdin → Claude Code reads input
```

## Threading Model

| Thread | Responsibility |
|--------|----------------|
| PtyReadLoop (daemon) | Reads PTY output, calls `TerminalCore.feed()` |
| Terminal Core Thread | Processes VT sequences, updates buffer (synchronized) |
| Capture Engine | Runs on terminal thread, emits to BlockingQueue |
| Consumer Thread | Polls `transcriptQueue()` for events |

All terminal state mutations occur under a single lock to ensure consistency.

## Configuration

### CaptureEngineConfig

| Parameter | Default | Description |
|-----------|---------|-------------|
| `volatileRowCount` | 2 | Bottom N rows treated as volatile (status lines) |
| `stabilityWindowMs` | 350 | Time a row must be unchanged before emission |
| `suppressAlternateScreen` | true | Suppress screen output in alt-screen mode |

### ChurnFilterPolicy

The `DefaultChurnFilterPolicy` implements heuristics without regex:
- Single-character spinner glyphs
- Braille pattern range (U+2800..U+28FF)
- Lines ending with `N%`
- Lines containing progress bar patterns `[===...]`
- Lines starting with loading/thinking prefixes

## Extension Points

| Interface | Purpose |
|-----------|---------|
| `TerminalCore` | Swap JediTerm for another terminal emulator |
| `ChurnFilterPolicy` | Custom suppression rules |
| `PtyProcessController` | Alternative PTY implementations |

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| pty4j | 0.13.11 | Cross-platform PTY support |
| jediterm-core | 3.57 | Terminal emulator (IntelliJ's terminal) |
| slf4j + logback | - | Logging |
| JUnit 5 + AssertJ | - | Testing |

## Known Limitations

1. **Alternate Screen Detection**: Currently uses heuristic FSM to detect ESC[?1049h/l. May miss some alternate screen modes.

2. **Escape Sequence Chunking**: When feeding data in small chunks, escape sequences that span chunk boundaries may be parsed incorrectly. Feed data in larger chunks when possible to avoid this.

3. **Single Session Focus**: Designed for one Claude Code session per `ClaudeSession` instance.

4. **Stability Window Timing**: Screen stability detection requires real time to pass. In unit tests, use `stabilityWindowMillis=0` for instant emission.

## Future Enhancements

1. **Response Boundary Detection**: Detect when Claude has finished responding (e.g., prompt reappears) to enable fully automated multi-turn conversations.

2. **Tool Use Interception**: Parse Claude's tool use requests from the transcript to enable programmatic approval/denial.

3. **Session State Serialization**: Save/restore session state for long-running workflows.

4. **Structured Output Parsing**: Extract code blocks, file paths, and other structured content from transcript.

5. **Escape Sequence Buffering**: Buffer incomplete escape sequences across chunk boundaries to handle arbitrary data chunking.
