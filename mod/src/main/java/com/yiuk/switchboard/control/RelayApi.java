package com.yiuk.switchboard.control;

import com.yiuk.switchboard.config.RelayConfig;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public final class RelayApi {
    private RelayApi() {}

    public static RelayMessage request(RelayConfig config, RelayMessage request) throws IOException {
        try (ControlConnection connection = ControlConnection.authenticate(config)) {
            connection.socket().setSoTimeout(10000);
            connection.send(request);
            RelayMessage response = connection.read();
            if ("err".equals(response.type)) throw new IOException(ControlConnection.errorText(response));
            return response;
        }
    }

    public static RelayMessage message(String type) {
        return RelayMessage.of(type);
    }

    public static byte[] encodeFrame(RelayMessage message) throws IOException {
        return ProtobufWire.encode(message);
    }

    public static String describeError(String detail) {
        String text = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        String key = null;
        if (text.contains("bad_password") || text.contains("password incorrect")) key = "yiukswitchboard.error.bad_password";
        else if (text.contains("bad_version") || text.contains("unsupported protocol version")) key = "yiukswitchboard.error.bad_version";
        else if (text.contains("rate_limited") || text.contains("too many login")) key = "yiukswitchboard.error.rate_limited";
        else if (text.contains("room_limit") || text.contains("room limit reached")) key = "yiukswitchboard.error.room_limit";
        else if (text.contains("port_exhausted") || text.contains("no available room port")) key = "yiukswitchboard.error.port_exhausted";
        else if (text.contains("not_owner") || text.contains("caller is not owner")) key = "yiukswitchboard.error.not_owner";
        else if (text.contains("not_found") || text.contains("room not found")) key = "yiukswitchboard.error.room_not_found";
        else if (text.contains("bad_token") || text.contains("room could not be resumed")) key = "yiukswitchboard.error.resume_failed";
        else if (text.contains("unknownhost") || text.contains("no such host")) key = "yiukswitchboard.error.unknown_host";
        else if (text.contains("connection refused")) key = "yiukswitchboard.error.connection_refused";
        else if (text.contains("timed out") || text.contains("timeout")) key = "yiukswitchboard.error.timeout";
        else if (text.contains("closed the connection") || text.contains("connection reset")) key = "yiukswitchboard.error.connection_lost";
        else if (text.contains("invalid room fields") || text.contains("nickname is required")) key = "yiukswitchboard.error.invalid_request";
        if (key != null) return Component.translatable(key).getString();
        return detail == null || detail.isBlank() ? Component.translatable("yiukswitchboard.error.generic").getString() : detail;
    }
}
