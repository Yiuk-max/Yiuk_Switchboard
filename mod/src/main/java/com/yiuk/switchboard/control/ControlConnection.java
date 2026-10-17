package com.yiuk.switchboard.control;

import com.yiuk.switchboard.config.RelayConfig;
import com.yiuk.switchboard.protocol.RelayEndpoint;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Authenticated protobuf control channel to the relay server. */
public final class ControlConnection implements Closeable {
    public static final int MAX_MESSAGE_BYTES = ProtobufWire.MAX_MESSAGE_BYTES;
    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final Object writeLock = new Object();

    private ControlConnection(Socket socket) throws IOException {
        this.socket=socket; this.input=socket.getInputStream(); this.output=socket.getOutputStream();
    }
    public static ControlConnection authenticate(RelayConfig config)throws IOException{return authenticate(config,8000,10000);}
    public static ControlConnection authenticate(RelayConfig config,int connectTimeoutMillis,int readTimeoutMillis)throws IOException{
        RelayEndpoint endpoint=RelayEndpoint.parse(config.address); Socket socket=new Socket();
        socket.connect(new InetSocketAddress(endpoint.host(),endpoint.port()),connectTimeoutMillis);socket.setKeepAlive(true);socket.setSoTimeout(readTimeoutMillis);
        ControlConnection connection=new ControlConnection(socket); RelayMessage auth=RelayMessage.of("auth");auth.password=config.password;auth.name=config.nickname;auth.protocolVersion=2;
        try{connection.send(auth);RelayMessage response=connection.read();if(!"ok".equals(response.type))throw new IOException(errorText(response));return connection;}catch(IOException e){connection.close();throw e;}
    }
    public void send(RelayMessage message)throws IOException{byte[] frame=ProtobufWire.encode(message);synchronized(writeLock){output.write(frame);output.flush();}}
    public synchronized RelayMessage read()throws IOException{return ProtobufWire.read(input);}
    public Socket socket(){return socket;}
    @Override public void close(){try{socket.close();}catch(IOException ignored){}}
    public static String errorText(RelayMessage message){return message.message.isBlank()?(message.errorCode.isBlank()?"Relay request failed":message.errorCode):message.message;}
}
