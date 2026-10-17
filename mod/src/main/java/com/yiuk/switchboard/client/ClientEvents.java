package com.yiuk.switchboard.client;

import com.yiuk.switchboard.SwitchboardMod;
import com.yiuk.switchboard.client.gui.RelayHostCreateScreen;
import com.yiuk.switchboard.client.gui.RelayServerAddScreen;
import com.yiuk.switchboard.client.gui.HostStatusScreen;
import com.yiuk.switchboard.client.gui.SavedRelayServerEntry;
import com.yiuk.switchboard.client.gui.RelayButtonPlacement;
import com.yiuk.switchboard.client.host.HostRoom;
import com.yiuk.switchboard.client.host.HostRoomState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.PauseScreen;
import com.yiuk.switchboard.config.RelayServerStore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = SwitchboardMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static long lastRelayEntryCheck;
    private ClientEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        Minecraft minecraft = Minecraft.getInstance();
        if (screen instanceof JoinMultiplayerScreen joinScreen) {
            // Vanilla's multiplayer controls occupy a 308px-wide, two-row group at the bottom.
            // Stack two standard 100px buttons immediately to its right.
            var position = RelayButtonPlacement.multiplayer(screen.width, screen.height);
            int x = position.x();
            int y = position.y();
            if (x + 100 <= screen.width - 4) {
                event.addListener(Button.builder(
                        net.minecraft.network.chat.Component.translatable("yiukswitchboard.button.add_relay_server"),
                        button -> minecraft.setScreen(new RelayServerAddScreen(screen)))
                        .bounds(x, y, 100, 20).build());
                event.addListener(Button.builder(
                        net.minecraft.network.chat.Component.translatable("yiukswitchboard.button.connect_relay_server"),
                        button -> connectSelectedRelay(joinScreen))
                        .bounds(x, y + 24, 100, 20).build());
            }
            ensureRelayEntries(joinScreen);
        } else if (screen instanceof PauseScreen && minecraft.getSingleplayerServer() != null) {
            var options = screen.children().stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .filter(widget -> widget.getMessage().getString().equals(
                            net.minecraft.network.chat.Component.translatable("menu.options").getString()))
                    .findFirst();
            if (options.isPresent()) {
                AbstractWidget anchor = options.get();
                var position = RelayButtonPlacement.host(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight());
                // Insert a real grid row. Move all existing vanilla and mod widgets below it
                // together so the new host button never shares the mods-list row.
                int insertedRowY = position.y();
                for (var child : screen.children()) {
                    if (child instanceof AbstractWidget widget && widget.getY() >= insertedRowY) {
                        widget.setY(widget.getY() + anchor.getHeight() + 4);
                    }
                }
                event.addListener(Button.builder(
                    net.minecraft.network.chat.Component.translatable("yiukswitchboard.button.host"),
                    button -> {
                        HostRoom room = HostRoomState.get();
                        if (room != null && !room.isClosed()) minecraft.setScreen(new HostStatusScreen(screen, room));
                        else minecraft.setScreen(new RelayHostCreateScreen(screen));
                    })
                    .bounds(position.x(), insertedRowY,
                            anchor.getWidth(), anchor.getHeight()).build());
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen instanceof JoinMultiplayerScreen joinScreen
                && System.currentTimeMillis() - lastRelayEntryCheck >= 1000) {
            lastRelayEntryCheck = System.currentTimeMillis();
            ensureRelayEntries(joinScreen);
        }
        HostRoom room = HostRoomState.get();
        if (room != null && Minecraft.getInstance().getSingleplayerServer() == null) room.close();
    }

    private static void ensureRelayEntries(JoinMultiplayerScreen screen) {
        var servers = RelayServerStore.load();
        if (servers.isEmpty()) return;
        try {
            var field = java.util.Arrays.stream(JoinMultiplayerScreen.class.getDeclaredFields())
                    .filter(candidate -> ServerSelectionList.class.isAssignableFrom(candidate.getType()))
                    .findFirst().orElseThrow();
            field.setAccessible(true);
            ServerSelectionList list = (ServerSelectionList) field.get(screen);
            if (list == null) return;
            Map<String, SavedRelayServerEntry> existing = new HashMap<>();
            for (var child : list.children()) {
                if (child instanceof SavedRelayServerEntry entry) existing.put(entry.serverId(), entry);
            }
            for (var server : servers) {
                SavedRelayServerEntry entry = existing.get(server.id);
                if (entry == null) list.children().add(new SavedRelayServerEntry(screen, server));
                else entry.updateServer(server);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep vanilla multiplayer usable if another mod changes this screen's internals.
        }
    }

    private static void connectSelectedRelay(JoinMultiplayerScreen screen) {
        try {
            var field = java.util.Arrays.stream(JoinMultiplayerScreen.class.getDeclaredFields())
                    .filter(candidate -> ServerSelectionList.class.isAssignableFrom(candidate.getType()))
                    .findFirst().orElseThrow();
            field.setAccessible(true);
            ServerSelectionList list = (ServerSelectionList) field.get(screen);
            if (list != null && list.getSelected() instanceof SavedRelayServerEntry relay) relay.connectToServer();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Relay rows remain connectable by double-click if vanilla internals are changed.
        }
    }
}
