package com.yiuk.switchboard.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class RelayScreen extends Screen {
    protected final Screen parent;

    protected RelayScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected Button backButton(int y) {
        return Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(width / 2 - 100, y, 200, 20).build();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
