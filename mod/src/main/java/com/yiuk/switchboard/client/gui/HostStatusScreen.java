package com.yiuk.switchboard.client.gui;

import com.yiuk.switchboard.client.RelayClient;
import com.yiuk.switchboard.client.RelayChat;
import com.yiuk.switchboard.client.host.HostRoom;
import com.yiuk.switchboard.control.RelayApi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HostStatusScreen extends RelayScreen {
    private final HostRoom room;
    private String status = "";
    private boolean closing;
    private Button closeButton;

    public HostStatusScreen(Screen parent, HostRoom room) {
        super(parent, Component.translatable("yiukswitchboard.screen.host_status"));
        this.room = room;
    }

    @Override
    protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("yiukswitchboard.button.return_game"), button -> minecraft.setScreen(null))
                .bounds(center - 150, height - 72, 145, 20).build());
        closeButton = addRenderableWidget(Button.builder(Component.translatable(closing ? "yiukswitchboard.button.closing" : "yiukswitchboard.button.close_room"), button -> closeRoom())
                .bounds(center + 5, height - 72, 145, 20).build());
    }

    private void closeRoom() {
        if (closing) return;
        closing = true;
        status = Component.translatable("yiukswitchboard.status.closing").getString();
        closeButton.active = false;
        RelayClient.IO.submit(() -> {
            String result = null;
            String failure = null;
            try { result = room.closeAndAwait(); }
            catch (java.io.IOException e) { failure = RelayApi.describeError(e.getMessage()); }
            String closeResult = result;
            String closeFailure = failure;
            minecraft.execute(() -> {
                if (minecraft.screen != this) return;
                if (closeFailure == null) {
                    status = closeResult;
                    RelayChat.roomClosed(room.name());
                    minecraft.setScreen(parent);
                } else {
                    status = closeFailure + "；本机转发已停止";
                    RelayChat.roomCloseUnconfirmed(closeFailure);
                    closing = false;
                    closeButton.active = false;
                }
            });
        });
    }

    public HostRoom room() { return room; }

    public void onRoomStopped(String reason) {
        status = reason;
        closing = true;
        if (closeButton != null) closeButton.active = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.status.room_name", room.name()), center, 80, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.status.room_id", room.id()), center, 104, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.status.room_port", room.publicPort()), center, 128, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("yiukswitchboard.status.local_port", room.localPort()), center, 152, 0xFFFFFF);
        String displayedStatus = status.isBlank() ? room.connectionStatus() : status;
        if (!displayedStatus.isBlank()) graphics.drawCenteredString(font, Component.literal(displayedStatus), center, 186, 0xFFCC55);
    }
}
