package com.yiuk.switchboard.protocol;

public record RelayEndpoint(String host, int port) {
    public static RelayEndpoint parse(String value) {
        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) throw new IllegalArgumentException("Server address is required");
        String host;
        String portPart;
        if (input.startsWith("[")) {
            int end = input.indexOf(']');
            if (end < 0) throw new IllegalArgumentException("Invalid IPv6 address");
            host = input.substring(1, end);
            portPart = input.length() > end + 1 ? input.substring(end + 2) : "25564";
        } else {
            int colon = input.lastIndexOf(':');
            if (colon < 0) {
                host = input;
                portPart = "25564";
            } else {
                host = input.substring(0, colon);
                portPart = input.substring(colon + 1);
            }
        }
        int port;
        try { port = Integer.parseInt(portPart); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid control port"); }
        if (host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Invalid server address");
        return new RelayEndpoint(host, port);
    }
}
