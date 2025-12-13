# Architecture

## Modules (single-module starter)
This starter keeps everything in one Maven module to reduce friction. The package structure mirrors the planned module split:

- `com.consullo.terminal.pty.*` — PTY + process lifecycle + resize + I/O threads
- `com.consullo.terminal.core.*` — terminal core abstraction (JediTerm adapter) and snapshot/scrollback adapters
- `com.consullo.terminal.capture.*` — churn-suppressing transcript capture engine (pure functions + state records)
- `com.consullo.terminal.driver.*` — higher-level session wrapper for Claude Code (send prompt, ESC, script steps)
- `com.consullo.terminal.ui.*` — Swing view scaffold (rendering hooks, selection/clipboard placeholders)
- `com.consullo.terminal.demo.*` — deterministic demo tool + harness for manual verification

## Threading Model

### Output path
1. `PtyProcessController` reads bytes from PTY output on a dedicated I/O thread.
2. Bytes are forwarded to a single-thread executor that owns **all terminal-core mutations**.
3. The terminal core applies ANSI/VT updates and emits `DamageEvent`s.
4. `CaptureEngine` executes on the same single-thread executor and emits transcript events.
5. UI repaint requests are marshaled to the Swing EDT (if enabled).

### Input path
- Key strokes are encoded to bytes and written to PTY input via `PtyProcessController.sendBytes(...)`.

## Key Extension Points
- `TerminalCore` — swap JediTerm for another terminal engine if desired
- `ChurnFilterPolicy` — customize spinner/progress suppression without changing the core algorithm
- `TranscriptSink` — publish to JSONL/MongoDB/event bus, etc.
