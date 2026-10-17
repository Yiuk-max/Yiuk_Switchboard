package com.yiuk.switchboard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RelayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("yiuk-switchboard.json");

    public String address = "127.0.0.1:25564";
    public String nickname = "";
    public String password = "";
    public boolean rememberPassword;
    public boolean allowCommands;

    public static RelayConfig load() {
        if (!Files.exists(FILE)) return new RelayConfig();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            RelayConfig config = GSON.fromJson(reader, RelayConfig.class);
            return config == null ? new RelayConfig() : config;
        } catch (Exception ignored) {
            return new RelayConfig();
        }
    }

    public void save() {
        String originalPassword = password;
        try {
            Files.createDirectories(FILE.getParent());
            if (!rememberPassword) password = "";
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception ignored) {
            // Config persistence is best-effort; the current form remains usable for this session.
        } finally {
            password = originalPassword;
        }
    }
}
