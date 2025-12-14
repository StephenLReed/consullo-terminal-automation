package com.consullo.terminal.core.jediterm;

import com.jediterm.core.Color;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.TerminalSelection;

/**
 * Headless terminal display stub for use without a GUI.
 *
 * <p>All display operations are no-ops. This allows JediTerminal to function
 * in a headless environment for transcript capture.
 */
public final class HeadlessTerminalDisplay implements TerminalDisplay {

  private String windowTitle = "";

  @Override
  public void setCursor(int x, int y) {
    // no-op
  }

  @Override
  public void setCursorShape(CursorShape cursorShape) {
    // no-op
  }

  @Override
  public void beep() {
    // no-op
  }

  @Override
  public void onResize(TermSize termSize, RequestOrigin origin) {
    // no-op
  }

  @Override
  public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
    // no-op
  }

  @Override
  public void setCursorVisible(boolean visible) {
    // no-op
  }

  @Override
  public void useAlternateScreenBuffer(boolean enabled) {
    // no-op
  }

  @Override
  public String getWindowTitle() {
    return windowTitle;
  }

  @Override
  public void setWindowTitle(String title) {
    this.windowTitle = title != null ? title : "";
  }

  @Override
  public TerminalSelection getSelection() {
    return null;
  }

  @Override
  public void terminalMouseModeSet(MouseMode mode) {
    // no-op
  }

  @Override
  public void setMouseFormat(MouseFormat format) {
    // no-op
  }

  @Override
  public boolean ambiguousCharsAreDoubleWidth() {
    return false;
  }

  @Override
  public void setBracketedPasteMode(boolean enabled) {
    // no-op
  }

  @Override
  public Color getWindowForeground() {
    return null;
  }

  @Override
  public Color getWindowBackground() {
    return null;
  }
}
