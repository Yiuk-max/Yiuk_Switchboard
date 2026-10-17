package com.yiuk.switchboard.client.gui;

import com.yiuk.switchboard.client.RelayClient;
import com.yiuk.switchboard.client.RelayChat;
import com.yiuk.switchboard.client.host.HostRoom;
import com.yiuk.switchboard.client.host.HostRoomState;
import com.yiuk.switchboard.config.RelayConfig;
import com.yiuk.switchboard.control.RelayApi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;

import java.io.IOException;

public final class RelayHostCreateScreen extends RelayScreen {
    private EditBox address;
    private EditBox nickname;
    private EditBox password;
    private EditBox roomName;
    private EditBox maxPlayers;
    private Button createButton;
    private Button commandsButton;
    private boolean remember;
    private boolean allowCommands;
    private boolean busy;
    private String status = "";

    public RelayHostCreateScreen(Screen parent) {
        super(parent, Component.translatable("yiukswitchboard.screen.host_create"));
        RelayConfig config = RelayConfig.load();
        remember = config.rememberPassword;
        allowCommands = config.allowCommands;
    }

    @Override
    protected void init() {
        int center = width / 2;
        RelayConfig saved = RelayConfig.load();
        address = field(center, 52, "yiukswitchboard.field.address", saved.address);
        nickname = field(center, 88, "yiukswitchboard.field.nickname", saved.nickname);
        password = new EditBox(font, center - 150, 124, 300, 20,
                Component.translatable("yiukswitchboard.field.password"));
        password.setValue(saved.rememberPassword ? saved.password : "");
        roomName = field(center, 160, "yiukswitchboard.field.room_name", saved.nickname.isBlank() ? "" : saved.nickname + "的房间");
        maxPlayers = field(center, 196, "yiukswitchboard.field.max_players", "8");
        password.setMaxLength(256);
        roomName.setMaxLength(64);
        maxPlayers.setMaxLength(2);
        addRenderableWidget(address);
        addRenderableWidget(nickname);
        addRenderableWidget(password);
        addRenderableWidget(roomName);
        addRenderableWidget(maxPlayers);
        addRenderableWidget(Button.builder(Component.translatable(remember ? "yiukswitchboard.remember.on" : "yiukswitchboard.remember.off"), button -> {
            remember = !remember;
            button.setMessage(Component.translatable(remember ? "yiukswitchboard.remember.on" : "yiukswitchboard.remember.off"));
        }).bounds(center - 150, 228, 300, 20).build());
        commandsButton = addRenderableWidget(Button.builder(Component.translatable(allowCommands
                        ? "yiukswitchboard.commands.allowed" : "yiukswitchboard.commands.denied"), button -> {
            allowCommands = !allowCommands;
            button.setMessage(Component.translatable(allowCommands
                    ? "yiukswitchboard.commands.allowed" : "yiukswitchboard.commands.denied"));
        }).bounds(center - 150, 252, 300, 20).build());
        createButton = addRenderableWidget(Button.builder(Component.translatable(busy ? "yiukswitchboard.button.creating" : "yiukswitchboard.button.create"), button -> createRoom())
                .bounds(center - 150, 282, 145, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(center + 5, 282, 145, 20).build());
    }

    private EditBox field(int center, int y, String label, String value) {
        EditBox box = new EditBox(font, center - 150, y, 300, 20, Component.translatable(label));
        box.setMaxLength(255);
        box.setValue(value);
        return box;
    }

    private void createRoom() {
        if (busy) return;
        RelayConfig config = new RelayConfig();
        config.address = address.getValue().trim();
        config.nickname = nickname.getValue().trim();
        config.password = password.getValue();
        config.rememberPassword = remember;
        config.allowCommands = allowCommands;
        String name = roomName.getValue().trim();
        int max;
        try { max = Integer.parseInt(maxPlayers.getValue()); }
        catch (NumberFormatException e) { status = Component.translatable("yiukswitchboard.error.max_players").getString(); return; }
        if (config.nickname.isBlank() || config.password.isEmpty() || name.isBlank() || max < 1 || max > 8) {
            status = Component.translatable("yiukswitchboard.error.host_fields").getString();
            return;
        }
        var integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer == null) { status = Component.translatable("yiukswitchboard.error.singleplayer_only").getString(); return; }
        boolean originalAuthentication = integratedServer.usesAuthentication();
        boolean wasPublished = integratedServer.isPublished();
        int localPort = wasPublished ? integratedServer.getPort() : HttpUtil.getAvailablePort();
        if (localPort < 1) { status = Component.translatable("yiukswitchboard.error.lan_open").getString(); return; }

        busy = true;
        status = Component.translatable("yiukswitchboard.status.creating").getString();
        createButton.active = false;
        createButton.setMessage(Component.translatable("yiukswitchboard.button.creating"));
        config.save();
        RelayClient.IO.submit(() -> {
            HostRoom created = null;
            String failure = null;
            try { created = HostRoom.create(config, name, max, localPort, integratedServer, originalAuthentication); }
            catch (IOException | RuntimeException e) { failure = RelayApi.describeError(e.getMessage()); }
            HostRoom result = created;
            String error = failure;
            minecraft.execute(() -> {
                if (minecraft.screen != this) {
                    if (result != null) result.close();
                    return;
                }
                busy = false;
                if (error != null) {
                    status = error;
                    createButton.active = true;
                    createButton.setMessage(Component.translatable("yiukswitchboard.button.create"));
                }
                else {
                    try {
                        // Don't expose the LAN listener unless the relay room was created.
                        // Start consuming new sessions only after the local listener is ready.
                        integratedServer.setUsesAuthentication(false);
                        if (!wasPublished) {
                            if (!integratedServer.publishServer(null, allowCommands, localPort)) {
                                throw new IOException(Component.translatable("yiukswitchboard.error.lan_open").getString());
                            }
                        } else {
                            var playerList = integratedServer.getPlayerList();
                            playerList.setAllowCommandsForAllPlayers(allowCommands);
                            for (var player : playerList.getPlayers()) integratedServer.getCommands().sendCommands(player);
                        }
                        if (integratedServer.getPort() != localPort) throw new IOException("LAN 端口与房间配置不一致");
                        result.activate();
                        HostRoomState.set(result);
                        RelayChat.roomOpened(result.name(), result.publicAddress());
                        minecraft.setScreen(new HostStatusScreen(parent, result));
                    } catch (IOException | RuntimeException e) {
                        integratedServer.setUsesAuthentication(originalAuthentication);
                        result.close();
                        status = RelayApi.describeError(e.getMessage());
                        createButton.active = true;
                        createButton.setMessage(Component.translatable("yiukswitchboard.button.create"));
                    }
                }
            });
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.address"), center, 38, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.nickname"), center, 74, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.password"), center, 110, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.room_name"), center, 146, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.max_players"), center, 182, 0xFFFFFF);
        if (!status.isBlank()) graphics.drawCenteredString(font, Component.literal(status), center, 314, 0xFF7777);
    }
}
