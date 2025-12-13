# Capture Policy

The capture engine produces a stable, append-only transcript while suppressing redraw churn.

## Primary Signal: Scrollback Delta
Whenever the terminal core exposes scrollback lines, the engine emits all *new* lines beyond the last emitted index.
This avoids most animation noise because spinners and progress bars typically rewrite the current line rather than
committing new lines.

## Secondary Signal: Screen Stability
Some TUIs render output in-place without committing to scrollback. For those cases, the engine:
- Treats the last N rows as volatile (default: 2)
- Tracks row text digests for the non-volatile region
- Emits a row only after it remains unchanged for a stability window (default: 350ms)

## Alternate Screen Handling
When the terminal switches into the alternate screen buffer (common for full-screen TUIs), screen-derived emissions
are suppressed by default. Scrollback deltas still emit if the underlying program commits lines.

## Churn Suppression Heuristics
The default policy suppresses volatile updates that:
- Change too frequently (updates/sec threshold)
- Look like spinners/progress indicators based on simple string heuristics (no regex)

These heuristics are intentionally conservative and configurable.
