package com.yiuk.switchboard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RelayServerStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<SavedRelayServer>>() {}.getType();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("yiuk-switchboard-servers.json");

    private RelayServerStore() {}

    public static synchronized List<SavedRelayServer> load() {
        if (!Files.exists(FILE)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            List<SavedRelayServer> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded == null) return new ArrayList<>();
            loaded.removeIf(server -> server == null || server.id == null || server.name == null || server.address == null);
            for (SavedRelayServer server : loaded) {
                if (server.nickname == null) server.nickname = "";
                if (server.password == null) server.password = "";
                if (!server.rememberPassword) server.password = "";
            }
            return loaded;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public static synchronized boolean add(SavedRelayServer server) {
        List<SavedRelayServer> servers = load();
        servers.add(server);
        return save(servers);
    }

    public static synchronized boolean update(SavedRelayServer replacement) {
        List<SavedRelayServer> servers = load();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).id.equals(replacement.id)) {
                servers.set(i, replacement);
                return save(servers);
            }
        }
        return false;
    }

    public static synchronized boolean delete(String id) {
        List<SavedRelayServer> servers = load();
        servers.removeIf(server -> server.id.equals(id));
        return save(servers);
    }

    public static synchronized boolean updateCredentials(String id, RelayConfig config) {
        List<SavedRelayServer> servers = load();
        for (SavedRelayServer server : servers) {
            if (!server.id.equals(id)) continue;
            server.address = config.address;
            server.nickname = config.nickname;
            server.rememberPassword = config.rememberPassword;
            server.password = config.rememberPassword ? config.password : "";
            break;
        }
        return save(servers);
    }

    private static boolean save(List<SavedRelayServer> servers) {
        try {
            Files.createDirectories(FILE.getParent());
            List<SavedRelayServer> safe = new ArrayList<>(servers.size());
            for (SavedRelayServer server : servers) {
                SavedRelayServer copy = new SavedRelayServer();
                copy.id = server.id;
                copy.name = server.name;
                copy.address = server.address;
                copy.nickname = server.nickname;
                copy.rememberPassword = server.rememberPassword;
                copy.password = server.rememberPassword ? server.password : "";
                safe.add(copy);
            }
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(safe, LIST_TYPE, writer);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
