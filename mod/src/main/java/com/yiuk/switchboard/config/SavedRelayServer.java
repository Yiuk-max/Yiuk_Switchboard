package com.yiuk.switchboard.config;

import java.util.UUID;

/** A saved relay endpoint shown separately from vanilla Minecraft servers. */
public final class SavedRelayServer {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String address = "";
    public String nickname = "";
    public String password = "";
    public boolean rememberPassword;

    public RelayConfig toConfig() {
        RelayConfig config = new RelayConfig();
        config.address = address == null ? "" : address;
        config.nickname = nickname == null ? "" : nickname;
        config.password = password == null ? "" : password;
        config.rememberPassword = rememberPassword;
        return config;
    }
}
