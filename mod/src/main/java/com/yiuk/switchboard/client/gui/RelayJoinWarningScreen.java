package com.yiuk.switchboard.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yiuk.switchboard.config.RelayConfig;
import com.yiuk.switchboard.control.ControlConnection;
import com.yiuk.switchboard.mods.ModListSnapshot;
import com.yiuk.switchboard.protocol.RelayEndpoint;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public final class RelayJoinWarningScreen extends RelayScreen {
    private final RelayConfig config;
    private final JsonObject room;
    private final List<ModListSnapshot.Difference> modDetails;
    private int page;
    private String error = "";

    public RelayJoinWarningScreen(Screen parent, RelayConfig config, JsonObject room, JsonArray mods) {
        super(parent, Component.translatable("yiukswitchboard.screen.mod_warning"));
        this.config = config;
        this.room = room;
        this.modDetails = ModListSnapshot.differences(mods);
    }

    @Override
    protected void init() {
        int center = width / 2;
        int buttonWidth = Math.min(104, (width - 28) / 4);
        int gap = 4;
        int rowWidth = buttonWidth * 4 + gap * 3;
        int left = center - rowWidth / 2;
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.previous"), button -> {
            if (page > 0) { page--; rebuildWidgets(); }
        }).bounds(left, height - 72, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.next"), button -> {
            if ((page + 1) * rowsPerPage() < modDetails.size()) { page++; rebuildWidgets(); }
        }).bounds(left + buttonWidth + gap, height - 72, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.continue"), button -> join())
                .bounds(left + (buttonWidth + gap) * 2, height - 72, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + (buttonWidth + gap) * 3, height - 72, buttonWidth, 20).build());
    }

    private int rowsPerPage() {
        return Math.max(1, Math.min(10, (height - 162) / 14));
    }

    private void join() {
        try {
            RelayEndpoint relay = RelayEndpoint.parse(config.address);
            int port = room.has("port") ? room.get("port").getAsInt() : -1;
            if (port < 1 || port > 65535) throw new IllegalArgumentException("房间端口无效，请刷新房间列表");
            String addressHost = relay.host().contains(":") ? "[" + relay.host() + "]" : relay.host();
            String address = addressHost + ":" + port;
            ServerData server = new ServerData(ControlConnection.string(room, "name"), address, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(address), server, false, null);
        } catch (RuntimeException e) {
            error = e.getMessage() == null ? "无法连接，请刷新房间列表" : e.getMessage();
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int pages = Math.max(1, (modDetails.size() + rowsPerPage() - 1) / rowsPerPage());
        graphics.drawCenteredString(font, Component.literal(title.getString() + "  " + (page + 1) + "/" + pages), center, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.mods.room_manifest"), center, 34, 0xFFFFFF);
        List<net.minecraft.util.FormattedCharSequence> noteLines = font.split(
                Component.translatable("yiukswitchboard.mods.download_note"), Math.max(60, width - 24));
        int noteY = height - 96 - (noteLines.size() - 1) * 10;
        for (int i = 0; i < noteLines.size(); i++) graphics.drawCenteredString(font, noteLines.get(i), center, noteY + i * 10, 0xFFCC55);
        if (modDetails.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.mods.none"), center, 58, 0xFFFFFF);
        } else {
            int rows = rowsPerPage();
            int start = page * rows;
            int end = Math.min(modDetails.size(), start + rows);
            for (int i = start; i < end; i++) {
                ModListSnapshot.Difference difference = modDetails.get(i);
                MutableComponent entry = Component.literal(difference.id() + "  ");
                if (difference.status() != ModListSnapshot.Status.EXTRA) entry = entry.append(difference.roomVersion() + "  ");
                Component state = switch (difference.status()) {
                    case MATCH -> Component.translatable("yiukswitchboard.mods.installed");
                    case MISSING -> Component.translatable("yiukswitchboard.mods.missing");
                    case VERSION_MISMATCH -> Component.translatable("yiukswitchboard.mods.version_mismatch", difference.localVersion());
                    case EXTRA -> Component.translatable("yiukswitchboard.mods.extra", difference.localVersion());
                    case INVALID -> Component.translatable("yiukswitchboard.mods.invalid_entry");
                };
                entry = entry.append(state);
                String line = font.plainSubstrByWidth(entry.getString(), Math.max(40, width - 36));
                int color = difference.status() == ModListSnapshot.Status.MATCH ? 0xA0A0A0 : 0xFFCC55;
                graphics.drawString(font, Component.literal(line), 18, 54 + (i - start) * 14, color);
            }
        }
        if (!error.isBlank()) graphics.drawCenteredString(font, Component.literal(error), center, height - 82, 0xFF7777);
    }
}
