package com.consullo.terminal.core.events;

import com.consullo.terminal.core.TerminalSnapshot;

/**
 * Listener interface for terminal damage events.
 *
 * @since 1.0
 */
public interface DamageListener {

  /**
   * Called when the terminal state changes.
   *
   * @param snapshot current terminal snapshot
   * @param damageEvent damage event describing the changed region
   */
  void onDamage(TerminalSnapshot snapshot, DamageEvent damageEvent);
}
