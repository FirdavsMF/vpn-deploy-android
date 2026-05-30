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
        session.setTimeout(30000);
        session.connect(30000);
    }
    
    public String executeCommand(String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(err);
        
        channel.connect(60000);
        
        while (!channel.isClosed()) {
            Thread.sleep(200);
        }
        
        String output = out.toString();
        String error = err.toString();
        
        channel.disconnect();
        
        if (!error.isEmpty() && !error.startsWith("Warning") && !error.contains("Permanently added")) {
            return "Error: " + error;
        }
        
        return output.isEmpty() ? "No output" : output;
    }
    
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
