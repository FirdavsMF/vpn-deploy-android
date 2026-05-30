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
    private Button btnDeploy, btnStatus, btnConfigs, btnConnect;
    private TextView tvOutput;
    private ScrollView scrollView;
    private Spinner spProtocol;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private String protocol = "vless";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        etHost = findViewById(R.id.etHost);
        etPassword = findViewById(R.id.etPassword);
        btnDeploy = findViewById(R.id.btnDeploy);
        btnStatus = findViewById(R.id.btnStatus);
        btnConfigs = findViewById(R.id.btnConfigs);
        btnConnect = findViewById(R.id.btnConnect);
        tvOutput = findViewById(R.id.tvOutput);
        scrollView = findViewById(R.id.scrollView);
        spProtocol = findViewById(R.id.spProtocol);
        
        spProtocol.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"VLESS", "Shadowsocks", "Hysteria2"}));
        spProtocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) protocol = "vless"; else if (pos == 1) protocol = "ss"; else protocol = "hysteria2";
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        
        btnDeploy.setOnClickListener(v -> deploy());
        btnStatus.setOnClickListener(v -> status());
        btnConfigs.setOnClickListener(v -> configs());
        btnConnect.setOnClickListener(v -> connectVPN());
    }
    
    private void deploy() {
        String h = etHost.getText().toString().trim();
        String p = etPassword.getText().toString().trim();
        if (h.isEmpty() || p.isEmpty()) { log("Enter host and password"); return; }
        
        executor.execute(() -> {
            try {
                Session s = connect(h, p);
                log("Connected");
                ChannelSftp sftp = (ChannelSftp) s.openChannel("sftp");
                sftp.connect();
                sftp.put(getAssets().open("vps"), "/tmp/vps");
                sftp.disconnect();
                exec(s, "chmod +x /tmp/vps");
                log("Deploying...");
                log(exec(s, "/tmp/vps deploy -H " + h + " --password " + p + " -s all 2>&1"));
                s.disconnect();
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
    }
    
    private void status() {
        runCmd("/tmp/vps status -H " + etHost.getText().toString().trim() + " --password " + etPassword.getText().toString().trim());
    }
    
    private void configs() {
        String h = etHost.getText().toString().trim();
        String p = etPassword.getText().toString().trim();
        runCmd("/tmp/vps configs -H " + h + " --password " + p + " -s all -o /tmp/cfg && find /tmp/cfg -type f -exec cat {} \\;");
    }
    
    private void connectVPN() {
        String h = etHost.getText().toString().trim();
        String p = etPassword.getText().toString().trim();
        if (h.isEmpty() || p.isEmpty()) { log("Enter host and password"); return; }
        
        executor.execute(() -> {
            try {
                Session s = connect(h, p);
                String url = "";
                if (protocol.equals("vless")) {
                    String id = exec(s, "cat /opt/vpn-deploy/services/vless/config.json 2>/dev/null | grep -o '\"id\":\"[^\"]*\"' | cut -d'\"' -f4 || docker exec vless-vpn cat /etc/xray/config.json 2>/dev/null | grep -o '\"id\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    url = "vless://" + id + "@" + h + ":443#VPN";
                } else if (protocol.equals("ss")) {
                    String pw = exec(s, "cat /opt/vpn-deploy/services/shadowsocks/config.json 2>/dev/null | grep -o '\"password\":\"[^\"]*\"' | cut -d'\"' -f4 || docker exec ss-vpn cat /etc/ss.json 2>/dev/null | grep -o '\"password\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    url = "ss://" + android.util.Base64.encodeToString(("aes-256-gcm:" + pw).getBytes(), 0) + "@" + h + ":8388#VPN";
                } else {
                    String pw = exec(s, "cat /opt/vpn-deploy/services/hysteria2/config.yaml 2>/dev/null | grep 'password:' | awk '{print $2}' || docker exec hysteria2-vpn cat /etc/hysteria/config.yaml 2>/dev/null | grep 'password:' | awk '{print $2}'").trim();
                    url = "hysteria2://" + pw + "@" + h + ":443?insecure=1#VPN";
                }
                s.disconnect();
                String finalUrl = url;
                handler.post(() -> {
                    log("🔗 " + finalUrl);
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setText(finalUrl);
                    log("📋 Copied! Open sing-box → Import from clipboard → Connect");
                });
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
    }
    
    private void runCmd(String cmd) {
        String h = etHost.getText().toString().trim();
        String p = etPassword.getText().toString().trim();
        if (h.isEmpty()) { log("Enter host"); return; }
        tvOutput.setText("");
        executor.execute(() -> {
            try {
                Session s = connect(h, p);
                log(exec(s, cmd));
                s.disconnect();
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
    }
    
    private Session connect(String host, String pass) throws Exception {
        JSch jsch = new JSch();
        Session s = jsch.getSession("root", host, 22);
        s.setPassword(pass);
        s.setConfig("StrictHostKeyChecking", "no");
        s.connect(10000);
        return s;
    }
    
    private String exec(Session s, String cmd) throws Exception {
        ChannelExec ch = (ChannelExec) s.openChannel("exec");
        ch.setCommand(cmd);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ch.setOutputStream(out);
        ch.setErrStream(out);
        ch.connect();
        while (!ch.isClosed()) Thread.sleep(100);
        ch.disconnect();
        return out.toString();
    }
    
    private void log(String msg) {
        handler.post(() -> { tvOutput.append(msg + "\n"); scrollView.fullScroll(View.FOCUS_DOWN); });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
