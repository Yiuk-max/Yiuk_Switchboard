package com.yiuk.switchboard.client.gui;

/** Fixed positions derived from the vanilla 1.21.1 multiplayer and pause-menu layouts. */
public final class RelayButtonPlacement {
    private RelayButtonPlacement() {}

    public record Point(int x, int y) {}

    public static Point multiplayer(int screenWidth, int screenHeight) {
        return new Point((screenWidth + 308) / 2 + 8, screenHeight - 54);
    }

    public static Point host(int optionsX, int optionsY, int optionsWidth, int optionsHeight) {
        return new Point(optionsX, optionsY + optionsHeight + 4);
    }
}
