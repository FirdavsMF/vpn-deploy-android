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
        session.connect(10000);
    }
    
    public String executeCommand(String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        channel.setOutputStream(outputStream);
        channel.setErrStream(errorStream);
        
        channel.connect();
        
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }
        
        String output = outputStream.toString();
        String error = errorStream.toString();
        
        channel.disconnect();
        
        if (!error.isEmpty() && !error.contains("Warning")) {
            throw new Exception(error);
        }
        
        return output;
    }
    
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
