package com.vpndeploy;

import com.jcraft.jsch.*;
import java.io.*;

public class SshClient {
    
    private Session session;
    private ChannelSftp sftp;
    private String host, user, password;
    private int port;
    
    public SshClient(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }
    
    public void connect() throws Exception {
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10000);
        
        Channel channel = session.openChannel("sftp");
        channel.connect(5000);
        sftp = (ChannelSftp) channel;
    }
    
    public void upload(byte[] data, String remotePath) throws Exception {
        sftp.put(new ByteArrayInputStream(data), remotePath);
    }
    
    public String exec(String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(out);
        
        channel.connect();
        
        while (!channel.isClosed()) {
            Thread.sleep(200);
        }
        
        String output = out.toString();
        channel.disconnect();
        return output.isEmpty() ? "OK" : output;
    }
    
    public void close() {
        if (sftp != null && sftp.isConnected()) sftp.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
    }
}
