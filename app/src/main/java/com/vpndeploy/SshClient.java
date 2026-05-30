package com.vpndeploy;

import com.jcraft.jsch.*;
import java.io.*;

public class SshClient {
    private Session session;
    
    public void connect(String host, String user, String password) throws Exception {
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, 22);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10000);
    }
    
    public String exec(String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(out);
        channel.connect();
        while (!channel.isClosed()) Thread.sleep(100);
        channel.disconnect();
        return out.toString();
    }
    
    public void close() {
        if (session != null) session.disconnect();
    }
}
