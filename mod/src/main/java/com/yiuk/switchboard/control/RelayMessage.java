package com.yiuk.switchboard.control;

import java.util.ArrayList;
import java.util.List;

/** Typed in-memory representation of the shared protobuf Message schema. */
public final class RelayMessage {
    public String type="", password="", name="", minecraftVersion="", loader="";
    public int protocolVersion, maxPlayers, port, localPort;
    public String roomId="", sessionId="", token="", errorCode="", message="";
    public final List<Mod> mods=new ArrayList<>();
    public final List<RoomSummary> rooms=new ArrayList<>();
    public static RelayMessage of(String type){RelayMessage m=new RelayMessage();m.type=type;return m;}
    public record Mod(String id,String version) {}
    public static final class RoomSummary {
        public String id="",name="",host="",minecraftVersion="",loader="";
        public int port,players,maxPlayers,modCount;
    }
}
