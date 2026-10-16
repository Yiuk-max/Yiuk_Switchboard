package com.yiuk.switchboard.client;

import com.yiuk.switchboard.SwitchboardMod;
import com.yiuk.switchboard.client.gui.RelayLoginScreen;
import com.yiuk.switchboard.client.gui.RelayHostCreateScreen;
import com.yiuk.switchboard.client.gui.HostStatusScreen;
import com.yiuk.switchboard.client.host.HostRoom;
import com.yiuk.switchboard.client.host.HostRoomState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = SwitchboardMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        Minecraft minecraft = Minecraft.getInstance();
        if (screen instanceof JoinMultiplayerScreen) {
            int[] bounds = relayButtonBounds(screen);
            event.addListener(Button.builder(
                    net.minecraft.network.chat.Component.translatable("yiukswitchboard.button.player"),
                    button -> minecraft.setScreen(new RelayLoginScreen(screen)))
                    .bounds(bounds[0], bounds[1], bounds[2], 20).build());
        } else if (screen instanceof PauseScreen && minecraft.getSingleplayerServer() != null) {
            int[] bounds = relayButtonBounds(screen);
            event.addListener(Button.builder(
                    net.minecraft.network.chat.Component.translatable("yiukswitchboard.button.host"),
                    button -> {
                        HostRoom room = HostRoomState.get();
                        if (room != null && !room.isClosed()) minecraft.setScreen(new HostStatusScreen(screen, room));
                        else minecraft.setScreen(new RelayHostCreateScreen(screen));
                    })
                    .bounds(bounds[0], bounds[1], bounds[2], 20).build());
        }
    }

    private static int[] relayButtonBounds(net.minecraft.client.gui.screens.Screen screen) {
        int buttonWidth = Math.min(132, Math.max(90, screen.width - 16));
        int[][] candidates = {
                {8, screen.height - 28},
                {screen.width - buttonWidth - 8, screen.height - 28},
                {8, screen.height - 52},
                {screen.width - buttonWidth - 8, screen.height - 52},
                {8, 8}
        };
        for (int[] candidate : candidates) {
            int x = candidate[0];
            int y = candidate[1];
            if (x < 0 || y < 0 || x + buttonWidth > screen.width || y + 20 > screen.height) continue;
            boolean overlaps = screen.children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .anyMatch(widget -> x < widget.getX() + widget.getWidth() + 4
                            && x + buttonWidth + 4 > widget.getX()
                            && y < widget.getY() + widget.getHeight() + 4
                            && y + 24 > widget.getY());
            if (!overlaps) return new int[]{x, y, buttonWidth};
        }
        return new int[]{Math.max(0, screen.width - buttonWidth - 8), 8, buttonWidth};
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        HostRoom room = HostRoomState.get();
        if (room != null && Minecraft.getInstance().getSingleplayerServer() == null) room.close();
    }
}
