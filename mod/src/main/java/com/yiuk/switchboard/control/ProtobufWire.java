package com.yiuk.switchboard.control;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Wire codec for proto/switchboard.proto (length-delimited protobuf frames). */
final class ProtobufWire {
    static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private static final Map<String,Integer> TYPES=Map.ofEntries(Map.entry("auth",1),Map.entry("data",2),Map.entry("ok",3),Map.entry("err",4),Map.entry("ping",5),Map.entry("pong",6),Map.entry("list",7),Map.entry("rooms",8),Map.entry("create",9),Map.entry("created",10),Map.entry("room_mods",11),Map.entry("mods",12),Map.entry("close",13),Map.entry("resume",14),Map.entry("new_session",15));
    private ProtobufWire() {}

    static byte[] encode(RelayMessage m)throws IOException{
        Integer type=TYPES.get(m.type);if(type==null)throw new IOException("Unknown control message type: "+m.type);
        ByteArrayOutputStream out=new ByteArrayOutputStream();putNumber(out,1,type);putString(out,2,m.password);putString(out,3,m.name);putNumber(out,4,m.protocolVersion);putNumber(out,5,m.maxPlayers);putString(out,6,m.minecraftVersion);putString(out,7,m.loader);
        for(RelayMessage.Mod mod:m.mods){ByteArrayOutputStream b=new ByteArrayOutputStream();putString(b,1,mod.id());putString(b,2,mod.version());putBytes(out,8,b.toByteArray());}
        putString(out,9,m.roomId);putString(out,10,m.sessionId);putString(out,11,m.token);putNumber(out,12,m.port);putNumber(out,13,m.localPort);putString(out,14,m.errorCode);putString(out,15,m.message);
        for(RelayMessage.RoomSummary room:m.rooms){ByteArrayOutputStream b=new ByteArrayOutputStream();putString(b,1,room.id);putString(b,2,room.name);putString(b,3,room.host);putNumber(b,4,room.port);putNumber(b,5,room.players);putNumber(b,6,room.maxPlayers);putString(b,7,room.minecraftVersion);putString(b,8,room.loader);putNumber(b,9,room.modCount);putBytes(out,16,b.toByteArray());}
        byte[] payload=out.toByteArray();if(payload.length>MAX_MESSAGE_BYTES)throw new IOException("Control message exceeds 64 KiB");ByteArrayOutputStream frame=new ByteArrayOutputStream();putVarint(frame,payload.length);frame.write(payload);return frame.toByteArray();
    }
    static RelayMessage read(InputStream in)throws IOException{
        long length=readVarint(in);if(length>MAX_MESSAGE_BYTES)throw new IOException("Control message exceeds 64 KiB");byte[] payload=in.readNBytes((int)length);if(payload.length!=length)throw new IOException("Relay server closed the connection");RelayMessage result=new RelayMessage();Cursor c=new Cursor(payload);
        while(c.hasRemaining()){long tag=c.varint();int f=(int)(tag>>>3),w=(int)(tag&7);switch(f){
            case 1->{long n=c.value(w);result.type=TYPES.entrySet().stream().filter(e->e.getValue()==n).map(Map.Entry::getKey).findFirst().orElseThrow(()->new IOException("Unknown relay message type"));}
            case 2->result.password=c.text(w);case 3->result.name=c.text(w);case 4->result.protocolVersion=(int)c.value(w);case 5->result.maxPlayers=(int)c.value(w);case 6->result.minecraftVersion=c.text(w);case 7->result.loader=c.text(w);
            case 8->{Cursor n=new Cursor(c.bytes(w));String id="",version="";while(n.hasRemaining()){long t=n.varint();int nf=(int)(t>>>3),nw=(int)(t&7);if(nf==1)id=n.text(nw);else if(nf==2)version=n.text(nw);else n.skip(nw);}result.mods.add(new RelayMessage.Mod(id,version));}
            case 9->result.roomId=c.text(w);case 10->result.sessionId=c.text(w);case 11->result.token=c.text(w);case 12->result.port=(int)c.value(w);case 13->result.localPort=(int)c.value(w);case 14->result.errorCode=c.text(w);case 15->result.message=c.text(w);
            case 16->{Cursor n=new Cursor(c.bytes(w));RelayMessage.RoomSummary room=new RelayMessage.RoomSummary();while(n.hasRemaining()){long t=n.varint();int nf=(int)(t>>>3),nw=(int)(t&7);switch(nf){case 1->room.id=n.text(nw);case 2->room.name=n.text(nw);case 3->room.host=n.text(nw);case 4->room.port=(int)n.value(nw);case 5->room.players=(int)n.value(nw);case 6->room.maxPlayers=(int)n.value(nw);case 7->room.minecraftVersion=n.text(nw);case 8->room.loader=n.text(nw);case 9->room.modCount=(int)n.value(nw);default->n.skip(nw);}}result.rooms.add(room);}
            default->c.skip(w);
        }}if(result.type.isEmpty())throw new IOException("Relay response has no message type");return result;
    }
    private static void putNumber(ByteArrayOutputStream o,int f,long n){if(n!=0){putVarint(o,(long)f<<3);putVarint(o,n);}}
    private static void putString(ByteArrayOutputStream o,int f,String s){if(s!=null&&!s.isEmpty())putBytes(o,f,s.getBytes(StandardCharsets.UTF_8));}
    private static void putBytes(ByteArrayOutputStream o,int f,byte[] b){putVarint(o,((long)f<<3)|2);putVarint(o,b.length);o.writeBytes(b);}
    private static void putVarint(ByteArrayOutputStream o,long n){while((n&~0x7fL)!=0){o.write((int)(n&0x7f)|0x80);n>>>=7;}o.write((int)n);}
    private static long readVarint(InputStream in)throws IOException{long n=0;for(int s=0;s<64;s+=7){int b=in.read();if(b<0)throw new IOException("Relay server closed the connection");n|=(long)(b&127)<<s;if((b&128)==0)return n;}throw new IOException("Invalid protobuf frame length");}
    private static final class Cursor{
        final byte[] b;int p;Cursor(byte[] b){this.b=b;}boolean hasRemaining(){return p<b.length;}
        long varint()throws IOException{long n=0;for(int s=0;s<64;s+=7){if(p>=b.length)throw new IOException("Truncated protobuf varint");int x=b[p++]&255;n|=(long)(x&127)<<s;if((x&128)==0)return n;}throw new IOException("Invalid protobuf varint");}
        long value(int w)throws IOException{if(w!=0)throw new IOException("Invalid protobuf integer field");return varint();}
        byte[] bytes(int w)throws IOException{if(w!=2)throw new IOException("Invalid protobuf byte field");long n=varint();if(n>b.length-p)throw new IOException("Truncated protobuf field");byte[] v=java.util.Arrays.copyOfRange(b,p,p+(int)n);p+=(int)n;return v;}
        String text(int w)throws IOException{return new String(bytes(w),StandardCharsets.UTF_8);}
        void skip(int w)throws IOException{switch(w){case 0->varint();case 1->{if(b.length-p<8)throw new IOException("Truncated protobuf fixed64");p+=8;}case 2->bytes(2);case 5->{if(b.length-p<4)throw new IOException("Truncated protobuf fixed32");p+=4;}default->throw new IOException("Unsupported protobuf wire type");}}
    }
}
