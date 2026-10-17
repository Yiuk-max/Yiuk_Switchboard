package com.yiuk.switchboard.client.host;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.yiuk.switchboard.client.gui.HostStatusScreen;
import com.yiuk.switchboard.config.RelayConfig;
import com.yiuk.switchboard.control.ControlConnection;
import com.yiuk.switchboard.control.RelayApi;
import com.yiuk.switchboard.mods.ModListSnapshot;
import com.yiuk.switchboard.protocol.RelayEndpoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public final class HostRoom implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECONNECT_WINDOW_SECONDS = 20;
    private final RelayConfig config;
    private final ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "yiuk-switchboard-ping");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean reconnecting = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final Set<Socket> forwardingSockets = ConcurrentHashMap.newKeySet();
    private final Set<String> activeSids = ConcurrentHashMap.newKeySet();
    private final Semaphore forwardingLimit = new Semaphore(8);
    private final Object controlLock = new Object();
    private final int localPort;
    private final String id;
    // Kept from the server response only in memory; never include it in logs or UI.
    private final String roomToken;
    private final int publicPort;
    private final String name;
    private final IntegratedServer integratedServer;
    private final boolean originalAuthentication;
    private volatile ControlConnection control;
    private volatile String connectionStatus = "";
    private volatile CompletableFuture<JsonObject> closeResponse;
    private volatile long lastPongNanos = System.nanoTime();

    private HostRoom(RelayConfig config, ControlConnection control, int localPort, String id, String roomToken, int publicPort,
                     String name, IntegratedServer integratedServer, boolean originalAuthentication) {
        this.config = config;
        this.control = control;
        this.localPort = localPort;
        this.id = id;
        this.roomToken = roomToken;
        this.publicPort = publicPort;
        this.name = name;
        this.integratedServer = integratedServer;
        this.originalAuthentication = originalAuthentication;
    }

    public static HostRoom create(RelayConfig config, String roomName, int maxPlayers, int localPort,
                                  IntegratedServer integratedServer, boolean originalAuthentication) throws IOException {
        ControlConnection control = ControlConnection.authenticate(config);
        try {
            JsonObject request = RelayApi.message("create");
            request.addProperty("name", roomName);
            request.addProperty("max", maxPlayers);
            request.addProperty("local_port", localPort);
            request.addProperty("mc", "1.21.1");
            request.addProperty("loader", "neoforge");
            request.add("mods", ModListSnapshot.toJson());
            control.send(request);
            JsonObject response = control.read();
            if (!"created".equals(ControlConnection.string(response, "t"))) {
                throw new IOException(ControlConnection.errorText(response));
            }
            HostRoom room = new HostRoom(config, control, localPort,
                    ControlConnection.string(response, "id"), ControlConnection.string(response, "token"),
                    response.get("port").getAsInt(), roomName,
                    integratedServer, originalAuthentication);
            return room;
        } catch (Exception e) {
            control.close();
            if (e instanceof IOException io) throw io;
            throw new IOException("Could not create relay room", e);
        }
    }

    public void activate() throws IOException {
        if (closed.get() || closing.get()) throw new IOException("房间已关闭");
        if (!started.compareAndSet(false, true)) return;
        control.socket().setSoTimeout(1000);
        lastPongNanos = System.nanoTime();
        startReader(control);
        heartbeats.scheduleAtFixedRate(this::sendHeartbeat, 5, 5, TimeUnit.SECONDS);
        LOGGER.info("Relay room activated (room={}, publicPort={})", id, publicPort);
    }

    private void startReader(ControlConnection connection) {
        tasks.submit(() -> readControl(connection));
    }

    private void sendHeartbeat() {
        if (closed.get() || closing.get()) return;
        ControlConnection current = control;
        if (current == null) return;
        if (System.nanoTime() - lastPongNanos > TimeUnit.SECONDS.toNanos(12)) {
            IOException timeout = new IOException("Relay heartbeat response timed out");
            LOGGER.warn("Relay heartbeat response timed out (room={})", id);
            loseControl(current, timeout);
            return;
        }
        try {
            current.send(RelayApi.message("ping"));
        } catch (IOException e) {
            LOGGER.warn("Relay heartbeat send failed (room={})", id, e);
            loseControl(current, e);
        }
    }

    private void readControl(ControlConnection connection) {
        while (!closed.get() && !closing.get()) {
            try {
                JsonObject message = connection.read();
                String type = ControlConnection.string(message, "t");
                if ("pong".equals(type)) {
                    lastPongNanos = System.nanoTime();
                } else if (closing.get() && ("ok".equals(type) || "err".equals(type))) {
                    CompletableFuture<JsonObject> response = closeResponse;
                    if (response != null) response.complete(message);
                } else if ("new_session".equals(type)) {
                    String sid = ControlConnection.string(message, "sid");
                    if (sid.isBlank()) {
                        LOGGER.warn("Relay sent a new session without a sid (room={})", id);
                    } else if (!closed.get() && !closing.get()) {
                        queueForward(sid);
                    }
                } else if ("err".equals(type)) {
                    LOGGER.warn("Relay control error (room={}): {}", id, ControlConnection.errorText(message));
                }
            } catch (java.net.SocketTimeoutException ignored) {
                // Periodic timeout lets the loop observe closure and shutdown promptly.
            } catch (IOException e) {
                loseControl(connection, e);
                return;
            } catch (RuntimeException e) {
                LOGGER.error("Invalid relay control message (room={})", id, e);
                loseControl(connection, e);
                return;
            }
        }
    }

    private void queueForward(String sid) {
        if (!activeSids.add(sid)) {
            LOGGER.warn("Relay repeated a pending sid (room={})", id);
            return;
        }
        if (!forwardingLimit.tryAcquire()) {
            activeSids.remove(sid);
            LOGGER.warn("Relay sent more sessions than the client limit (room={})", id);
            return;
        }
        try {
            tasks.submit(() -> {
                try { forward(sid); }
                finally {
                    activeSids.remove(sid);
                    forwardingLimit.release();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            activeSids.remove(sid);
            forwardingLimit.release();
            if (!closed.get() && !closing.get()) LOGGER.warn("Could not start player forwarding task (room={})", id, e);
        }
    }

    private void loseControl(ControlConnection lost, Throwable cause) {
        synchronized (controlLock) {
            if (control != lost || closed.get() || closing.get()) return;
            control = null;
            lost.close();
        }
        connectionStatus = "与中转服务器断开，正在尝试恢复房间…";
        LOGGER.warn("Relay control connection lost; attempting room resume (room={})", id, cause);
        if (reconnecting.compareAndSet(false, true)) tasks.submit(this::reconnect);
    }

    private void reconnect() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RECONNECT_WINDOW_SECONDS);
        long delayMillis = 500;
        Throwable lastFailure = null;
        while (!closed.get() && !closing.get() && System.nanoTime() < deadline) {
            ControlConnection candidate = null;
            try {
                candidate = ControlConnection.authenticate(config, 1500, 1500);
                candidate.socket().setSoTimeout(2000);
                JsonObject resume = RelayApi.message("resume");
                resume.addProperty("id", id);
                resume.addProperty("token", roomToken);
                candidate.send(resume);
                JsonObject response = candidate.read();
                if (!"ok".equals(ControlConnection.string(response, "t"))) {
                    throw new IOException(ControlConnection.errorText(response));
                }
                candidate.socket().setSoTimeout(1000);
                synchronized (controlLock) {
                    if (closed.get() || closing.get()) {
                        candidate.close();
                        return;
                    }
                    control = candidate;
                }
                connectionStatus = "房间连接已恢复";
                lastPongNanos = System.nanoTime();
                LOGGER.info("Relay room control connection resumed (room={})", id);
                reconnecting.set(false);
                startReader(candidate);
                return;
            } catch (IOException | RuntimeException e) {
                lastFailure = e;
                if (candidate != null) candidate.close();
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                delayMillis = Math.min(delayMillis * 2, 4000);
            }
        }
        if (!closed.get() && !closing.get()) {
            connectionStatus = "无法恢复中转连接，房间已停止";
            LOGGER.error("Relay room resume window expired (room={})", id, lastFailure);
            shutdownLocal();
        }
        reconnecting.set(false);
    }

    private void forward(String sid) {
        if (closed.get() || closing.get()) return;
        RelayEndpoint endpoint;
        try {
            endpoint = RelayEndpoint.parse(config.address);
        } catch (IllegalArgumentException e) {
            logForwardFailure("parse relay address", e);
            return;
        }
        String stage = "connect to relay room port";
        try (Socket relay = new Socket(); Socket local = new Socket()) {
            if (!registerSocket(relay) || !registerSocket(local)) return;
            relay.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 8000);
            relay.setKeepAlive(true);
            relay.setTcpNoDelay(true);
            relay.setSoTimeout(0);
            stage = "pair sid with relay";
            JsonObject data = RelayApi.message("data");
            data.addProperty("sid", sid);
            byte[] header = (data + "\n").getBytes(StandardCharsets.UTF_8);
            relay.getOutputStream().write(header);
            relay.getOutputStream().flush();
            stage = "connect to local LAN port";
            local.connect(new InetSocketAddress("127.0.0.1", localPort), 5000);
            local.setKeepAlive(true);
            local.setTcpNoDelay(true);
            if (!closed.get() && !closing.get()) {
                stage = "forward player TCP stream";
                copyBoth(local, relay);
            }
        } catch (IOException e) {
            if (!closed.get() && !closing.get()) logForwardFailure(stage, e);
        } finally {
            forwardingSockets.removeIf(Socket::isClosed);
        }
    }

    private boolean registerSocket(Socket socket) {
        forwardingSockets.add(socket);
        if (closed.get() || closing.get()) {
            closeQuietly(socket);
            forwardingSockets.remove(socket);
            return false;
        }
        return true;
    }

    private void logForwardFailure(String stage, Exception error) {
        LOGGER.warn("Player relay session failed during {} (room={})", stage, id, error);
        if (!closed.get() && !closing.get()) connectionStatus = "有玩家连接失败，请检查中转端口和本机局域网服务";
    }

    private static void copyBoth(Socket a, Socket b) throws IOException {
        var pool = Executors.newVirtualThreadPerTaskExecutor();
        Exception failure = null;
        try {
            var one = pool.submit(() -> b.getInputStream().transferTo(a.getOutputStream()));
            var two = pool.submit(() -> a.getInputStream().transferTo(b.getOutputStream()));
            try { one.get(); } catch (ExecutionException e) { failure = e; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); failure = e; }
            closeQuietly(a);
            closeQuietly(b);
            try { two.get(); } catch (ExecutionException e) { if (failure == null) failure = e; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); if (failure == null) failure = e; }
        } finally {
            pool.shutdownNow();
        }
        if (failure != null) throw new IOException("TCP stream copy failed", failure);
    }

    public String id() { return id; }
    public int publicPort() { return publicPort; }
    public String name() { return name; }
    public int localPort() { return localPort; }
    public boolean isClosed() { return closed.get(); }
    public String connectionStatus() { return connectionStatus; }
    public String publicAddress() {
        RelayEndpoint endpoint = RelayEndpoint.parse(config.address);
        String host = endpoint.host().contains(":") ? "[" + endpoint.host() + "]" : endpoint.host();
        return host + ":" + publicPort;
    }

    /** Request a server-side close and wait for its explicit ok/err response. */
    public String closeAndAwait() throws IOException {
        if (!closing.compareAndSet(false, true)) throw new IOException("房间正在关闭");
        connectionStatus = "正在等待中转服务器确认关房…";
        heartbeats.shutdownNow();
        CompletableFuture<JsonObject> responseFuture = new CompletableFuture<>();
        closeResponse = responseFuture;
        IOException failure = null;
        ControlConnection current = control;
        try {
            if (current == null || current.socket().isClosed()) throw new IOException("中转连接已断开，服务器可能会在恢复期限后自动关闭房间");
            JsonObject request = RelayApi.message("close");
            request.addProperty("id", id);
            current.send(request);
            JsonObject response = responseFuture.get(4, TimeUnit.SECONDS);
            if (!"ok".equals(ControlConnection.string(response, "t"))) {
                throw new IOException(ControlConnection.errorText(response));
            }
            connectionStatus = "房间已由中转服务器确认关闭";
            LOGGER.info("Relay room close confirmed (room={})", id);
            return connectionStatus;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = new IOException("等待关房确认时被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            failure = new IOException("等待中转服务器关房确认超时", e);
        } catch (IOException e) {
            failure = e;
        } finally {
            if (failure != null) {
                connectionStatus = "本地已停止，但未收到中转服务器关房确认";
                LOGGER.warn("Relay room close was not confirmed (room={})", id, failure);
            }
            shutdownLocal();
        }
        throw failure;
    }

    /** Best-effort close used for world exit and application shutdown. */
    @Override public void close() {
        if (!closing.get()) {
            closing.set(true);
            heartbeats.shutdownNow();
            ControlConnection current = control;
            if (current != null && !current.socket().isClosed()) {
                JsonObject request = RelayApi.message("close");
                request.addProperty("id", id);
                try { current.send(request); }
                catch (IOException e) { LOGGER.warn("Could not send room close on shutdown (room={})", id, e); }
            }
        }
        shutdownLocal();
    }

    private void shutdownLocal() {
        if (!closed.compareAndSet(false, true)) return;
        closing.set(true);
        heartbeats.shutdownNow();
        synchronized (controlLock) {
            ControlConnection current = control;
            control = null;
            if (current != null) current.close();
        }
        for (Socket socket : forwardingSockets) closeQuietly(socket);
        forwardingSockets.clear();
        tasks.shutdownNow();
        HostRoomState.clear(this);
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().getSingleplayerServer() == integratedServer) {
                integratedServer.setUsesAuthentication(originalAuthentication);
            }
            if (Minecraft.getInstance().screen instanceof HostStatusScreen screen && screen.room() == this) {
                screen.onRoomStopped(connectionStatus);
            }
        });
    }

    private static void closeQuietly(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
