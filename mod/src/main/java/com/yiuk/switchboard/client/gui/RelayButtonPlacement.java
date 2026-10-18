package com.yiuk.switchboard.client.gui;

/** Positions derived from vanilla anchors so added controls follow the active screen layout. */
public final class RelayButtonPlacement {
    private RelayButtonPlacement() {}

    public record Point(int x, int y) {}

    public static Point multiplayer(int screenWidth, int screenHeight) {
        return new Point((screenWidth + 308) / 2 + 8, screenHeight - 54);
    }

    public static Point host(int anchorX, int anchorY, int anchorHeight) {
        return new Point(anchorX, anchorY + anchorHeight + 4);
    }
}
