package com.yiuk.switchboard.client.gui;

import com.yiuk.switchboard.config.SavedRelayServer;
import com.yiuk.switchboard.config.RelayServerStore;
import com.yiuk.switchboard.protocol.RelayEndpoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RelayServerAddScreen extends RelayScreen {
    private EditBox name;
    private EditBox address;
    private EditBox nickname;
    private EditBox password;
    private boolean rememberPassword;
    private String status = "";
    private final SavedRelayServer editing;

    public RelayServerAddScreen(Screen parent) {
        this(parent, null);
    }

    public RelayServerAddScreen(Screen parent, SavedRelayServer editing) {
        super(parent, Component.translatable("yiukswitchboard.screen.add_relay_server"));
        this.editing = editing;
        if (editing != null) rememberPassword = editing.rememberPassword;
    }

    @Override
    protected void init() {
        int center = width / 2;
        var previous = RelayConfigSnapshot.load();
        name = field(center, 68, "yiukswitchboard.field.server_name", editing == null ? "" : editing.name);
        address = field(center, 112, "yiukswitchboard.field.address", editing == null ? previous.address : editing.address);
        nickname = field(center, 156, "yiukswitchboard.field.nickname", editing == null ? previous.nickname : editing.nickname);
        password = new EditBox(font, center - 150, 200, 300, 20,
                Component.translatable("yiukswitchboard.field.password"));
        password.setValue(editing == null || !editing.rememberPassword ? "" : editing.password);
        password.setMaxLength(256);
        addRenderableWidget(name);
        addRenderableWidget(address);
        addRenderableWidget(nickname);
        addRenderableWidget(password);
        addRenderableWidget(Button.builder(Component.translatable(rememberPassword ? "yiukswitchboard.remember.on" : "yiukswitchboard.remember.off"), button -> {
            rememberPassword = !rememberPassword;
            button.setMessage(Component.translatable(rememberPassword ? "yiukswitchboard.remember.on" : "yiukswitchboard.remember.off"));
        }).bounds(center - 150, 236, 300, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.save"), button -> save())
                .bounds(center - 150, 274, 145, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(center + 5, 274, 145, 20).build());
    }

    private EditBox field(int center, int y, String label, String value) {
        EditBox box = new EditBox(font, center - 150, y, 300, 20, Component.translatable(label));
        box.setMaxLength(255);
        box.setValue(value);
        return box;
    }

    private void save() {
        String serverName = name.getValue().trim();
        String serverAddress = address.getValue().trim();
        String playerName = nickname.getValue().trim();
        if (serverName.isBlank() || serverAddress.isBlank() || playerName.isBlank()) {
            status = Component.translatable("yiukswitchboard.error.saved_server_fields").getString();
            return;
        }
        try { RelayEndpoint.parse(serverAddress); }
        catch (IllegalArgumentException e) {
            status = Component.translatable("yiukswitchboard.error.address_invalid").getString();
            return;
        }
        SavedRelayServer server = new SavedRelayServer();
        if (editing != null) server.id = editing.id;
        server.name = serverName;
        server.address = serverAddress;
        server.nickname = playerName;
        server.rememberPassword = rememberPassword;
        server.password = password.getValue();
        if (!(editing == null ? RelayServerStore.add(server) : RelayServerStore.update(server))) {
            status = Component.translatable("yiukswitchboard.error.saved_server_write_failed").getString();
            return;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.server_name"), center, 52, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.address"), center, 96, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.nickname"), center, 140, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.password"), center, 184, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.server.password_storage_note"), center, 260, 0xA0A0A0);
        if (!status.isBlank()) graphics.drawCenteredString(font, Component.literal(status), center, 306, 0xFF7777);
    }

    /** The add-server form only needs harmless defaults; credentials are entered explicitly here. */
    private static final class RelayConfigSnapshot {
        private final String address;
        private final String nickname;
        private RelayConfigSnapshot(String address, String nickname) { this.address = address; this.nickname = nickname; }
        private static RelayConfigSnapshot load() {
            var config = com.yiuk.switchboard.config.RelayConfig.load();
            return new RelayConfigSnapshot(config.address, config.nickname);
        }
    }
}
