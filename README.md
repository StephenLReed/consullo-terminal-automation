# Consullo Terminal Automation (Reference Architecture)

This project provides a PTY-driven terminal automation stack intended to drive interactive CLI/TUI tools (e.g., Claude Code)
while extracting a stable, append-only transcript that suppresses redraw churn (spinners, progress bars, status lines).

## Key Goals
- Spawn an interactive process attached to a PTY and send keystrokes (including ESC) programmatically.
- Parse ANSI/VT sequences using a mature terminal core and maintain a screen + scrollback model.
- Emit a transcript stream that focuses on *committed* output, ignoring transient redraws and animations.
- Provide a Swing-based reference view that can be evolved toward GNOME Terminal interaction patterns.

## Build
```bash
mvn -q -DskipTests=false test
```

## Run the demo (headless-friendly)
```bash
mvn -q -DskipTests=true exec:java -Dexec.mainClass=com.consullo.terminal.demo.TerminalAutomationDemo
```

See `docs/ARCHITECTURE.md` and `docs/CAPTURE_POLICY.md` for details.
