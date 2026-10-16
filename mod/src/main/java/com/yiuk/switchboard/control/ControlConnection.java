package com.yiuk.switchboard.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yiuk.switchboard.config.RelayConfig;
import com.yiuk.switchboard.protocol.RelayEndpoint;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ControlConnection implements Closeable {
    public static final int MAX_LINE_BYTES = 64 * 1024;
    private final Socket socket;
    private final InputStreamReader reader;
    private final BufferedWriter writer;
    private final Object writeLock = new Object();
    private final StringBuilder readBuffer = new StringBuilder();

    private ControlConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public static ControlConnection authenticate(RelayConfig config) throws IOException {
        return authenticate(config, 8000, 10000);
    }

    public static ControlConnection authenticate(RelayConfig config, int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        RelayEndpoint endpoint = RelayEndpoint.parse(config.address);
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), connectTimeoutMillis);
        socket.setKeepAlive(true);
        socket.setSoTimeout(readTimeoutMillis);
        ControlConnection connection = new ControlConnection(socket);
        JsonObject auth = new JsonObject();
        auth.addProperty("t", "auth");
        auth.addProperty("pw", config.password);
        auth.addProperty("name", config.nickname);
        auth.addProperty("ver", 1);
        try {
            connection.send(auth);
            JsonObject response = connection.read();
            if (!"ok".equals(string(response, "t"))) throw new IOException(errorText(response));
            return connection;
        } catch (IOException e) {
            connection.close();
            throw e;
        }
    }

    public void send(JsonObject message) throws IOException {
        String line = message.toString();
        if (line.getBytes(StandardCharsets.UTF_8).length + 1 > MAX_LINE_BYTES) throw new IOException("Control message exceeds 64 KiB");
        synchronized (writeLock) {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        }
    }

    public synchronized JsonObject read() throws IOException {
        int value;
        while ((value = reader.read()) != -1 && value != '\n') {
            if (readBuffer.length() >= MAX_LINE_BYTES) {
                readBuffer.setLength(0);
                throw new IOException("Control message exceeds 64 KiB");
            }
            if (value != '\r') readBuffer.append((char) value);
        }
        if (value == -1 && readBuffer.isEmpty()) throw new IOException("Relay server closed the connection");
        String line = readBuffer.toString();
        readBuffer.setLength(0);
        if (line.getBytes(StandardCharsets.UTF_8).length > MAX_LINE_BYTES) throw new IOException("Control message exceeds 64 KiB");
        try { return JsonParser.parseString(line).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Invalid relay response", e); }
    }

    public Socket socket() { return socket; }
    @Override public void close() { try { socket.close(); } catch (IOException ignored) {} }

    public static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    public static String errorText(JsonObject message) {
        String text = string(message, "msg");
        String code = string(message, "code");
        return text.isBlank() ? (code.isBlank() ? "Relay request failed" : code) : text;
    }
}
