# Integration Guide

## Driving Claude Code
`ClaudeSession` provides:
- `sendPrompt(prompt, appendNewline)` — send a user prompt
- `sendEscape()` — send ESC key
- `sendText(text)` — send raw text
- `subscribeTranscript(...)` — receive append-only transcript events as JSON

## Transcript Output Format
Transcript events are JSON objects:
- `type`: currently `"append"`
- `text`: appended text (typically includes trailing newline)
- `meta`: metadata including `timestampUtc`, `source`, and optional tags

This format is designed to be persisted via Consullo JSON utilities and forwarded to agents/A2A pipelines.
