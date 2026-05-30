package com.vpndeploy;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.jcraft.jsch.*;
import java.io.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    
    private EditText etHost, etPassword;
    private Button btnDeploy, btnStatus, btnConfigs;
    private TextView tvOutput;
    private ScrollView scrollView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        etHost = findViewById(R.id.etHost);
        etPassword = findViewById(R.id.etPassword);
        btnDeploy = findViewById(R.id.btnDeploy);
        btnStatus = findViewById(R.id.btnStatus);
        btnConfigs = findViewById(R.id.btnConfigs);
        tvOutput = findViewById(R.id.tvOutput);
        scrollView = findViewById(R.id.scrollView);
        
        btnDeploy.setOnClickListener(v -> deploy());
        btnStatus.setOnClickListener(v -> status());
        btnConfigs.setOnClickListener(v -> configs());
    }
    
    private void deploy() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("Enter host and password"); return; }
        
        executor.execute(() -> {
            try {
                Session session = connect(host, pass);
                log("Connected to " + host);
                
                // Upload vps binary via SFTP
                log("Uploading vps binary...");
                ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect();
                InputStream in = getAssets().open("vps");
                sftp.put(in, "/tmp/vps");
                sftp.disconnect();
                exec(session, "chmod +x /tmp/vps");
                log("Binary uploaded");
                
                // Deploy all services
                log("Deploying...");
                String result = exec(session, "/tmp/vps deploy -H " + host + " --password " + pass + " -s all 2>&1");
                log(result);
                
                session.disconnect();
                log("Done!");
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
    }
    
    private void status() {
        execOnServer("/tmp/vps status -H " + etHost.getText().toString().trim() + " --password " + etPassword.getText().toString().trim());
    }
    
    private void configs() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        execOnServer("/tmp/vps configs -H " + host + " --password " + pass + " -s all -o /tmp/cfg && find /tmp/cfg -type f -exec echo '--- {} ---' \\; -exec cat {} \\;");
    }
    
    private void execOnServer(String cmd) {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty()) { log("Enter host"); return; }
        tvOutput.setText("");
        
        executor.execute(() -> {
            try {
                Session session = connect(host, pass);
                log(exec(session, cmd));
                session.disconnect();
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
    }
    
    private Session connect(String host, String pass) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession("root", host, 22);
        session.setPassword(pass);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10000);
        return session;
    }
    
    private String exec(Session session, String cmd) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(cmd);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(out);
        channel.connect();
        while (!channel.isClosed()) Thread.sleep(100);
        channel.disconnect();
        return out.toString();
    }
    
    private void log(String msg) {
        handler.post(() -> {
            tvOutput.append(msg + "\n");
            scrollView.fullScroll(View.FOCUS_DOWN);
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
