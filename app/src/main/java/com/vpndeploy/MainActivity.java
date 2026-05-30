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
    private String currentProtocol = "vless";
    
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
        
        String[] protocols = {"VLESS", "Shadowsocks", "Hysteria2"};
        spProtocol.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocols));
        spProtocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) currentProtocol = "vless";
                else if (pos == 1) currentProtocol = "ss";
                else currentProtocol = "hysteria2";
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        
        btnDeploy.setOnClickListener(v -> deploy());
        btnStatus.setOnClickListener(v -> status());
        btnConfigs.setOnClickListener(v -> configs());
        btnConnect.setOnClickListener(v -> connectVPN());
    }
    
    // ====== DEPLOY ======
    private void deploy() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("Enter host and password"); return; }
        
        executor.execute(() -> {
            try {
                Session session = connect(host, pass);
                log("Connected to " + host);
                
                ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect();
                sftp.put(getAssets().open("vps"), "/tmp/vps");
                sftp.disconnect();
                exec(session, "chmod +x /tmp/vps");
                log("Binary uploaded");
                
                log("Deploying all services...");
                String result = exec(session, "/tmp/vps deploy -H " + host + " --password " + pass + " -s all 2>&1");
                log(result);
                log("Done!");
                session.disconnect();
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
    }
    
    // ====== STATUS ======
    private void status() {
        execOnServer("/tmp/vps status -H " + etHost.getText().toString().trim() + " --password " + etPassword.getText().toString().trim());
    }
    
    // ====== CONFIGS ======
    private void configs() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        execOnServer("/tmp/vps configs -H " + host + " --password " + pass + " -s all -o /tmp/cfg && cat /tmp/cfg/all-configs.txt 2>/dev/null || find /tmp/cfg -type f -exec cat {} \\;");
    }
    
    // ====== VPN CONNECT ======
    private void connectVPN() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("Enter host and password"); return; }
        
        executor.execute(() -> {
            try {
                Session session = connect(host, pass);
                String vpnUrl = "";
                
                if (currentProtocol.equals("vless")) {
                    String uuid = exec(session, "cat /opt/vpn-deploy/services/vless/config.json 2>/dev/null | grep -o '\"id\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    if (uuid.isEmpty()) uuid = exec(session, "docker exec vless-vpn cat /etc/xray/config.json 2>/dev/null | grep -o '\"id\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    vpnUrl = "vless://" + uuid + "@" + host + ":443?encryption=none&security=none&type=tcp#VPN-Deploy";
                } else if (currentProtocol.equals("ss")) {
                    String sp = exec(session, "docker exec ss-vpn cat /etc/ss.json 2>/dev/null | grep -o '\"password\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    if (sp.isEmpty()) sp = exec(session, "cat /opt/vpn-deploy/services/shadowsocks/config.json 2>/dev/null | grep -o '\"password\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    String b64 = android.util.Base64.encodeToString(("aes-256-gcm:" + sp).getBytes(), android.util.Base64.NO_WRAP);
                    vpnUrl = "ss://" + b64 + "@" + host + ":8388#VPN-Deploy";
                } else {
                    String hp = exec(session, "cat /opt/vpn-deploy/services/hysteria2/config.yaml 2>/dev/null | grep 'password:' | awk '{print $2}'").trim();
                    if (hp.isEmpty()) hp = exec(session, "docker exec hysteria2-vpn cat /etc/hysteria/config.yaml 2>/dev/null | grep 'password:' | awk '{print $2}'").trim();
                    vpnUrl = "hysteria2://" + hp + "@" + host + ":443?insecure=1#VPN-Deploy";
                }
                
                session.disconnect();
                
                String finalUrl = vpnUrl;
                handler.post(() -> {
                    log("🔗 " + finalUrl);
                    log("✅ VPN URL готов! Импортируйте в sing-box/v2rayNG клиент");
                    log("📋 Ссылка скопирована в буфер обмена");
                    
                    // Копируем в буфер
                    android.content.ClipboardManager clipboard = 
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setText(finalUrl);
                    
                    // Показываем диалог
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("🔐 " + currentProtocol.toUpperCase() + " VPN")
                        .setMessage("Ссылка скопирована!\n\nОткройте sing-box или v2rayNG\n→ Импорт из буфера обмена\n→ Подключить")
                        .setPositiveButton("OK", null)
                        .setNeutralButton("📋 Копировать еще раз", (d, w) -> {
                            clipboard.setText(finalUrl);
                            log("📋 Скопировано!");
                        })
                        .show();
                });
                
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        });
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
