package com.consullo.terminal.core.events;

/**
 * Listener interface for terminal damage events.
 *
 * @since 1.0
 */
public interface DamageListener {

  /**
   * Called when the terminal state changes.
   *
   * @param damageEvent damage event describing the changed region
   * @throws Exception if listener processing fails
   */
  void onDamage(final DamageEvent damageEvent) throws Exception;
}
