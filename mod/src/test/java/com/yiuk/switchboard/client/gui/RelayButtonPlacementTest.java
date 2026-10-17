package com.yiuk.switchboard.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelayButtonPlacementTest {
    @Test
    void multiplayerButtonsStartAtRightOfVanillaFooterAndStackOnRows() {
        var first = RelayButtonPlacement.multiplayer(854, 480);
        assertEquals(589, first.x());
        assertEquals(426, first.y());
        assertEquals(450, first.y() + 24);
    }

    @Test
    void hostButtonUsesOptionsLeftColumnAndNextRow() {
        var host = RelayButtonPlacement.host(323, 300, 204, 20);
        assertEquals(323, host.x());
        assertEquals(324, host.y());
    }
}
