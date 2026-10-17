package com.yiuk.switchboard.client.gui;

import com.yiuk.switchboard.client.RelayClient;
import com.yiuk.switchboard.config.RelayConfig;
import com.yiuk.switchboard.config.RelayServerStore;
import com.yiuk.switchboard.control.ControlConnection;
import com.yiuk.switchboard.control.RelayApi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public final class RelayLoginScreen extends RelayScreen {
    private EditBox address;
    private EditBox nickname;
    private EditBox password;
    private boolean remember;
    private String status = "";
    private boolean busy;
    private Button loginButton;
    private final RelayConfig initialConfig;
    private final boolean autoLogin;
    private final String savedServerId;

    public RelayLoginScreen(Screen parent) {
        this(parent, RelayConfig.load(), false, null);
    }

    public RelayLoginScreen(Screen parent, RelayConfig config, boolean autoLogin, String savedServerId) {
        super(parent, Component.translatable("yiukswitchboard.screen.login"));
        this.initialConfig = copy(config);
        this.autoLogin = autoLogin;
        this.savedServerId = savedServerId;
        remember = config.rememberPassword;
    }

    private static RelayConfig copy(RelayConfig source) {
        RelayConfig copy = new RelayConfig();
        copy.address = source.address;
        copy.nickname = source.nickname;
        copy.password = source.password;
        copy.rememberPassword = source.rememberPassword;
        copy.allowCommands = source.allowCommands;
        return copy;
    }

    @Override
    protected void init() {
        int center = width / 2;
        address = new EditBox(font, center - 150, 64, 300, 20, Component.translatable("yiukswitchboard.field.address"));
        nickname = new EditBox(font, center - 150, 108, 300, 20, Component.translatable("yiukswitchboard.field.nickname"));
        password = new EditBox(font, center - 150, 152, 300, 20, Component.translatable("yiukswitchboard.field.password"));
        address.setMaxLength(255);
        nickname.setMaxLength(32);
        password.setMaxLength(256);
        RelayConfig saved = initialConfig;
        address.setValue(saved.address);
        nickname.setValue(saved.nickname);
        if (saved.rememberPassword) password.setValue(saved.password);
        addRenderableWidget(address);
        addRenderableWidget(nickname);
        addRenderableWidget(password);
        addRenderableWidget(Button.builder(Component.translatable(remember ? "yiukswitchboard.remember.on" : "yiukswitchboard.remember.off"), button -> {
            remember = !remember;
            button.setMessage(Component.translatable(remember ? "yiukswitchboard.remember.on" : "yiukswitchboard.remember.off"));
        }).bounds(center - 150, 184, 300, 20).build());
        loginButton = addRenderableWidget(Button.builder(Component.translatable(busy ? "yiukswitchboard.button.connecting" : "yiukswitchboard.button.login"), button -> login())
                .bounds(center - 150, 220, 145, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(center + 5, 220, 145, 20).build());
        if (autoLogin && !saved.password.isEmpty()) minecraft.execute(this::login);
    }

    private void login() {
        if (busy) return;
        RelayConfig config = new RelayConfig();
        config.address = address.getValue().trim();
        config.nickname = nickname.getValue().trim();
        config.password = password.getValue();
        config.rememberPassword = remember;
        if (config.nickname.isBlank() || config.password.isEmpty()) {
            status = Component.translatable("yiukswitchboard.error.credentials").getString();
            return;
        }
        busy = true;
        status = Component.translatable("yiukswitchboard.status.connecting").getString();
        loginButton.active = false;
        loginButton.setMessage(Component.translatable("yiukswitchboard.button.connecting"));
        RelayClient.IO.submit(() -> {
            String failure = null;
            try (ControlConnection ignored = ControlConnection.authenticate(config)) {
                config.save();
                if (savedServerId != null) RelayServerStore.updateCredentials(savedServerId, config);
            }
            catch (IOException | RuntimeException e) { failure = RelayApi.describeError(e.getMessage()); }
            String result = failure;
            minecraft.execute(() -> {
                if (minecraft.screen != this) return;
                busy = false;
                if (result == null) minecraft.setScreen(new RelayRoomListScreen(parent, config));
                else {
                    status = result;
                    loginButton.active = true;
                    loginButton.setMessage(Component.translatable("yiukswitchboard.button.login"));
                }
            });
        });
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.address"), center, 48, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.nickname"), center, 92, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.field.password"), center, 136, 0xFFFFFF);
        if (!status.isBlank()) graphics.drawCenteredString(font, Component.literal(status), center, 258, 0xFF7777);
    }
}
