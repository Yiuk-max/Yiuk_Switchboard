package com.yiuk.switchboard.client.gui;

import com.yiuk.switchboard.client.RelayClient;
import com.yiuk.switchboard.config.RelayServerStore;
import com.yiuk.switchboard.config.SavedRelayServer;
import com.yiuk.switchboard.protocol.RelayEndpoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Relay profile rendered as a native multiplayer-list row. */
public final class SavedRelayServerEntry extends ServerSelectionList.Entry {
    private final JoinMultiplayerScreen screen;
    private final FaviconTexture icon;
    private SavedRelayServer server;
    private long lastClick;
    private volatile String status = "yiukswitchboard.server.checking";
    private volatile int ping = -1;
    private volatile long lastProbe;
    private volatile boolean probing;

    public SavedRelayServerEntry(JoinMultiplayerScreen screen, SavedRelayServer server) {
        this.screen = screen;
        this.server = server;
        this.icon = FaviconTexture.forServer(Minecraft.getInstance().getTextureManager(), "yiukswitchboard-relay-" + server.id);
    }

    public String serverId() { return server.id; }
    public void updateServer(SavedRelayServer server) { this.server = server; }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth,
                       int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
        probeIfNeeded();
        int gearX = x + rowWidth - 24;
        int tagX = gearX - 42;
        var font = Minecraft.getInstance().font;
        if (hovered) graphics.fill(x, y, x + rowWidth, y + rowHeight, 0x18FFFFFF);
        graphics.blit(icon.textureLocation(), x, y, 0.0F, 0.0F, 32, 32, 32, 32);
        String title = font.plainSubstrByWidth(server.name, tagX - (x + 35) - 5);
        graphics.drawString(font, title, x + 35, y + 1, 0xFFFFFF, false);
        graphics.drawString(Minecraft.getInstance().font, server.address, x + 35, y + 12, 0xA0A0A0, false);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable("yiukswitchboard.server.relay_tag"), tagX, y + 1, 0x80C080, false);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable(status), x + 35, y + 23, 0xA0A0A0, false);
        if (ping >= 0) graphics.drawString(Minecraft.getInstance().font, ping + " ms", tagX, y + 13, 0xA0A0A0, false);
        boolean gearHovered = mouseX >= gearX && mouseX < gearX + 20 && mouseY >= y + 8 && mouseY < y + 28;
        graphics.blitSprite(ResourceLocation.withDefaultNamespace(
                gearHovered ? "widget/button_highlighted" : "widget/button"), gearX, y + 8, 20, 20);
        drawGear(graphics, gearX + 10, y + 18, 0xFFFFFFFF, 0xFF505050);
        if (gearHovered) screen.setTooltipForNextRenderPass(Component.translatable("yiukswitchboard.button.settings"));
    }

    private static void drawGear(GuiGraphics graphics, int cx, int cy, int color, int hole) {
        // Chunky 8-tooth gear, using the same light/dark pixel treatment as vanilla buttons.
        graphics.fill(cx - 2, cy - 8, cx + 2, cy - 5, color);
        graphics.fill(cx - 2, cy + 5, cx + 2, cy + 8, color);
        graphics.fill(cx - 8, cy - 2, cx - 5, cy + 2, color);
        graphics.fill(cx + 5, cy - 2, cx + 8, cy + 2, color);
        graphics.fill(cx - 6, cy - 6, cx - 3, cy - 3, color);
        graphics.fill(cx + 3, cy - 6, cx + 6, cy - 3, color);
        graphics.fill(cx - 6, cy + 3, cx - 3, cy + 6, color);
        graphics.fill(cx + 3, cy + 3, cx + 6, cy + 6, color);
        graphics.fill(cx - 5, cy - 5, cx + 5, cy + 5, color);
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, hole);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int left = (screen.width - 305) / 2;
        if (mouseX >= left + 305 - 24) {
            Minecraft.getInstance().setScreen(new RelayServerAddScreen(screen, server));
            return true;
        }
        screen.setSelected(this);
        long now = System.currentTimeMillis();
        if (now - lastClick < 250) connectToServer();
        lastClick = now;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            connectToServer();
            return true;
        }
        return false;
    }

    public void connectToServer() {
        var config = server.toConfig();
        Minecraft.getInstance().setScreen(new RelayLoginScreen(screen, config, !config.password.isBlank(), server.id));
    }

    private void probeIfNeeded() {
        long now = System.currentTimeMillis();
        if (probing || now - lastProbe < 30_000) return;
        probing = true;
        lastProbe = now;
        status = "yiukswitchboard.server.checking";
        RelayClient.IO.submit(() -> {
            long start = System.nanoTime();
            String result;
            int elapsed = -1;
            try {
                RelayEndpoint endpoint = RelayEndpoint.parse(server.address);
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 2500);
                    elapsed = (int) ((System.nanoTime() - start) / 1_000_000L);
                }
                result = "yiukswitchboard.server.reachable";
            } catch (Exception e) {
                result = "yiukswitchboard.server.unreachable";
            }
            int resultPing = elapsed;
            String resultStatus = result;
            Minecraft.getInstance().execute(() -> {
                status = resultStatus;
                ping = resultPing;
                probing = false;
            });
        });
    }

    @Override
    public Component getNarration() {
        return Component.translatable("narrator.select", server.name);
    }

    @Override
    public void close() {
        icon.close();
    }
}
