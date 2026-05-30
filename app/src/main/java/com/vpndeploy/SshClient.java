package com.vpndeploy;

import com.jcraft.jsch.*;
import java.io.*;

public class SshClient {
    
    private Session session;
    private String host;
    private int port;
    private String user;
    private String password;
    
    public SshClient(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }
    
    public void connect() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(30000);
    }
    
    public String exec(String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(err);
        
        channel.connect();
        
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }
        
        String output = out.toString();
        channel.disconnect();
        return output.isEmpty() ? "OK" : output;
    }
    
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
