package com.yiuk.switchboard.client.host;

import java.util.concurrent.atomic.AtomicReference;

public final class HostRoomState {
    private static final AtomicReference<HostRoom> ACTIVE = new AtomicReference<>();
    private HostRoomState() {}
    public static HostRoom get() { return ACTIVE.get(); }
    public static void set(HostRoom room) { ACTIVE.set(room); }
    public static void clear(HostRoom room) { ACTIVE.compareAndSet(room, null); }
}
