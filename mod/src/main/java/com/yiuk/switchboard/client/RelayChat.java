package com.yiuk.switchboard.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RelayChat {
    private RelayChat() {}

    public static void roomOpened(String name, String address) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.displayClientMessage(Component.translatable("yiukswitchboard.chat.room_opened", name).withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable("yiukswitchboard.chat.join_address", address).withStyle(ChatFormatting.GREEN), false);
    }

    public static void roomClosed(String name) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(
                Component.translatable("yiukswitchboard.chat.room_closed", name).withStyle(ChatFormatting.GREEN), false);
    }

    public static void roomCloseUnconfirmed(String detail) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.displayClientMessage(Component.translatable("yiukswitchboard.chat.room_close_unconfirmed").withStyle(ChatFormatting.RED), false);
        if (detail != null && !detail.isBlank()) player.displayClientMessage(Component.literal(detail).withStyle(ChatFormatting.YELLOW), false);
    }
}
