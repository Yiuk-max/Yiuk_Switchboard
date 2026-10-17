package com.yiuk.switchboard.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yiuk.switchboard.client.RelayClient;
import com.yiuk.switchboard.config.RelayConfig;
import com.yiuk.switchboard.control.ControlConnection;
import com.yiuk.switchboard.control.RelayApi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class RelayRoomListScreen extends RelayScreen {
    private final RelayConfig config;
    private final List<JsonObject> rooms = new ArrayList<>();
    private String status = "";
    private boolean busy;
    private boolean hasLoaded;
    private int page;
    private long lastRefresh;
    private static final int ROWS = 8;

    public RelayRoomListScreen(Screen parent, RelayConfig config) {
        super(parent, Component.translatable("yiukswitchboard.screen.rooms"));
        this.config = config;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = 48;
        int count = Math.max(1, Math.min(ROWS, (height - 154) / 24));
        for (int i = 0; i < count; i++) {
            int index = page * count + i;
            if (index >= rooms.size()) break;
            JsonObject room = rooms.get(index);
            String label = ControlConnection.string(room, "name") + "   "
                    + ControlConnection.string(room, "host") + "   "
                    + safeInt(room, "players") + "/" + safeInt(room, "max")
                    + "   MC " + ControlConnection.string(room, "mc");
            addRenderableWidget(Button.builder(Component.literal(label), button -> inspectRoom(room))
                    .bounds(center - 190, top + i * 24, 380, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.refresh"), button -> refresh())
                .bounds(center - 190, height - 72, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.previous"), button -> {
            if (page > 0) { page--; rebuildRoomList(); }
        }).bounds(center - 94, height - 72, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.next"), button -> {
            if ((page + 1) * count < rooms.size()) { page++; rebuildRoomList(); }
        }).bounds(center + 2, height - 72, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(center + 98, height - 72, 92, 20).build());
        if (!hasLoaded && !busy) refresh();
    }

    private void rebuildRoomList() {
        RelayRoomListScreen replacement = new RelayRoomListScreen(parent, config);
        replacement.rooms.addAll(rooms);
        replacement.page = page;
        replacement.status = status;
        replacement.busy = busy;
        replacement.hasLoaded = hasLoaded;
        replacement.lastRefresh = lastRefresh;
        minecraft.setScreen(replacement);
    }

    private static String safeInt(JsonObject room, String key) {
        try { return room.has(key) ? Integer.toString(room.get(key).getAsInt()) : "?"; }
        catch (RuntimeException e) { return "?"; }
    }

    private void refresh() {
        if (busy) return;
        busy = true;
        setButtonsEnabled(false);
        status = Component.translatable("yiukswitchboard.status.loading").getString();
        RelayClient.IO.submit(() -> {
            List<JsonObject> result = new ArrayList<>();
            String failure = null;
            try {
                JsonObject response = RelayApi.request(config, RelayApi.message("list"));
                if (!"rooms".equals(ControlConnection.string(response, "t"))) throw new IOException("房间列表响应无效");
                if (!response.has("list") || response.get("list").isJsonNull()) {
                    // Older relays omit an empty list because of JSON omitempty.
                } else if (response.get("list").isJsonArray()) {
                    JsonArray array = response.getAsJsonArray("list");
                    array.forEach(item -> result.add(item.getAsJsonObject()));
                } else {
                    throw new IOException("房间列表响应格式无效");
                }
            } catch (IOException | RuntimeException e) { failure = RelayApi.describeError(e.getMessage()); }
            String error = failure;
            minecraft.execute(() -> {
                if (minecraft.screen != this) return;
                busy = false;
                hasLoaded = true;
                if (error == null) {
                    rooms.clear();
                    rooms.addAll(result);
                    page = 0;
                    status = rooms.isEmpty() ? Component.translatable("yiukswitchboard.status.empty").getString() : "";
                    lastRefresh = System.currentTimeMillis();
                } else {
                    status = Component.translatable("yiukswitchboard.status.refresh_failed", error).getString();
                }
                rebuildRoomList();
            });
        });
    }

    private void inspectRoom(JsonObject room) {
        if (busy) return;
        busy = true;
        setButtonsEnabled(false);
        status = Component.translatable("yiukswitchboard.status.loading_mods").getString();
        RelayClient.IO.submit(() -> {
            JsonArray mods = new JsonArray();
            String failure = null;
            try {
                JsonObject request = RelayApi.message("room_mods");
                request.addProperty("id", ControlConnection.string(room, "id"));
                JsonObject response = RelayApi.request(config, request);
                if (!"mods".equals(ControlConnection.string(response, "t"))
                        || !response.has("mods") || !response.get("mods").isJsonArray()) {
                    throw new IOException("房间模组清单格式无效");
                }
                mods = response.getAsJsonArray("mods");
            } catch (IOException | RuntimeException e) { failure = RelayApi.describeError(e.getMessage()); }
            JsonArray roomMods = mods;
            String error = failure;
            minecraft.execute(() -> {
                if (minecraft.screen != this) return;
                busy = false;
                if (error != null) {
                    status = error;
                    setButtonsEnabled(true);
                }
                else minecraft.setScreen(new RelayJoinWarningScreen(this, config, room, roomMods));
            });
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) widget.active = enabled;
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.rooms.inspect_hint"), width / 2, 30, 0xA0A0A0);
        if (!status.isBlank()) graphics.drawCenteredString(font, Component.literal(status), width / 2, height - 96, 0xFFFFFF);
        if (lastRefresh > 0) {
            String time = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(lastRefresh));
            graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.status.last_refresh", time), width / 2, height - 112, 0xA0A0A0);
        }
    }
}
