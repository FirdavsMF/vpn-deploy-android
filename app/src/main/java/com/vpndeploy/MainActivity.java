package com.vpndeploy;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    
    private EditText etHost, etPassword;
    private Button btnDeploy, btnStatus, btnConfigs, btnConnect, btnSettings;
    private TextView tvOutput, tvStatus;
    private ScrollView scrollView;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Spinner spProtocol, spMode;
    private String currentProtocol = "vless";
    private String currentMode = "all";
    
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
        btnSettings = findViewById(R.id.btnSettings);
        tvOutput = findViewById(R.id.tvOutput);
        tvStatus = findViewById(R.id.tvStatus);
        scrollView = findViewById(R.id.scrollView);
        spProtocol = findViewById(R.id.spProtocol);
        spMode = findViewById(R.id.spMode);
        
        String[] protocols = {"VLESS", "Shadowsocks", "Hysteria2"};
        String[] modes = {"Весь трафик", "Браузер", "YouTube", "Instagram", "Telegram"};
        spProtocol.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocols));
        spMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes));
        
        spProtocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                switch (pos) { case 0: currentProtocol = "vless"; break; case 1: currentProtocol = "ss"; break; case 2: currentProtocol = "hysteria2"; break; }
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        
        spMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                switch (pos) { case 0: currentMode = "all"; break; case 1: currentMode = "browser"; break; case 2: currentMode = "youtube"; break; case 3: currentMode = "instagram"; break; case 4: currentMode = "telegram"; break; }
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        
        btnDeploy.setOnClickListener(v -> deployAll());
        btnStatus.setOnClickListener(v -> checkStatus());
        btnConfigs.setOnClickListener(v -> showConfigs());
        btnConnect.setOnClickListener(v -> connectVPN());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        
        String savedHost = getSharedPreferences("vpn", MODE_PRIVATE).getString("host", "");
        if (!savedHost.isEmpty()) {
            etHost.setText(savedHost);
            etPassword.setText(getSharedPreferences("vpn", MODE_PRIVATE).getString("password", ""));
        }
    }
    
    private void deployAll() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("❌ Заполните все поля"); return; }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                log("✅ Подключен к " + host);
                
                String docker = ssh.exec("docker --version 2>/dev/null || echo no");
                if (docker.contains("no")) {
                    log("📦 Установка Docker...");
                    ssh.exec("curl -fsSL https://get.docker.com | sh 2>&1");
                }
                
                log("🟢 VLESS...");
                ssh.exec("mkdir -p /opt/vpn/vless");
                ssh.exec("cat > /opt/vpn/vless/Dockerfile << 'EOF'\nFROM alpine:3.19\nRUN apk add --no-cache curl unzip\nRUN curl -L https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-64.zip -o /tmp/xray.zip && unzip /tmp/xray.zip -d /usr/local/bin/ && rm /tmp/xray.zip && chmod +x /usr/local/bin/xray\nCOPY config.json /etc/xray/config.json\nEXPOSE 443\nCMD [\"xray\",\"run\",\"-config\",\"/etc/xray/config.json\"]\nEOF");
                ssh.exec("cat > /opt/vpn/vless/config.json << 'EOF'\n{\"log\":{\"loglevel\":\"warning\"},\"inbounds\":[{\"port\":443,\"protocol\":\"vless\",\"settings\":{\"clients\":[{\"id\":\"" + java.util.UUID.randomUUID().toString() + "\"}],\"decryption\":\"none\"},\"streamSettings\":{\"network\":\"tcp\",\"security\":\"none\"}}],\"outbounds\":[{\"protocol\":\"freedom\"}]}\nEOF");
                ssh.exec("cd /opt/vpn/vless && docker build -t vless-vpn . 2>&1 && docker rm -f vless-vpn 2>/dev/null; docker run -d --name vless-vpn --restart unless-stopped -p 443:443 vless-vpn 2>&1");
                log("✅ VLESS (443)");
                
                log("🔵 Shadowsocks...");
                String ssPass = randomPass();
                ssh.exec("mkdir -p /opt/vpn/ss");
                ssh.exec("cat > /opt/vpn/ss/Dockerfile << 'EOF'\nFROM alpine:3.19\nRUN apk add --no-cache shadowsocks-rust\nCOPY config.json /etc/ss.json\nEXPOSE 8388\nCMD [\"ssserver\",\"-c\",\"/etc/ss.json\"]\nEOF");
                ssh.exec("cat > /opt/vpn/ss/config.json << 'EOF'\n{\"server\":\"0.0.0.0\",\"server_port\":8388,\"password\":\"" + ssPass + "\",\"method\":\"aes-256-gcm\",\"timeout\":300}\nEOF");
                ssh.exec("cd /opt/vpn/ss && docker build -t ss-vpn . 2>&1 && docker rm -f ss-vpn 2>/dev/null; docker run -d --name ss-vpn --restart unless-stopped -p 8388:8388 ss-vpn 2>&1");
                log("✅ SS (8388)");
                
                log("🟡 Hysteria2...");
                String hyPass = randomPass();
                ssh.exec("mkdir -p /opt/vpn/hysteria2");
                ssh.exec("cat > /opt/vpn/hysteria2/Dockerfile << 'EOF'\nFROM alpine:3.19\nRUN apk add --no-cache curl openssl && mkdir -p /etc/hysteria\nRUN openssl req -x509 -newkey rsa:2048 -keyout /etc/hysteria/key.pem -out /etc/hysteria/cert.pem -days 3650 -nodes -subj \"/CN=hy2\"\nRUN curl -L https://github.com/apernet/hysteria/releases/latest/download/hysteria-linux-amd64 -o /usr/local/bin/hysteria && chmod +x /usr/local/bin/hysteria\nCOPY config.yaml /etc/hysteria/config.yaml\nEXPOSE 443/udp\nCMD [\"hysteria\",\"server\",\"-c\",\"/etc/hysteria/config.yaml\"]\nEOF");
                ssh.exec("cat > /opt/vpn/hysteria2/config.yaml << 'EOF'\nlisten: :443\nauth:\n  type: password\n  password: " + hyPass + "\ntls:\n  cert: /etc/hysteria/cert.pem\n  key: /etc/hysteria/key.pem\nbandwidth:\n  up: 100 mbps\n  down: 100 mbps\nEOF");
                ssh.exec("cd /opt/vpn/hysteria2 && docker build -t hysteria2-vpn . 2>&1 && docker rm -f hysteria2-vpn 2>/dev/null; docker run -d --name hysteria2-vpn --restart unless-stopped -p 443:443/udp hysteria2-vpn 2>&1");
                log("✅ HY2 (443/udp)");
                
                ssh.close();
                updateStatus("● ALL RUNNING");
                log("\n🎉 Готово! CONNECT для подключения");
            } catch (Exception e) {
                log("❌ " + e.getMessage());
            }
        });
    }
    
    private void checkStatus() {
        execCmd("docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || echo 'Нет контейнеров'");
    }
    
    private void showConfigs() {
        execCmd("echo 'VLESS:' && cat /opt/vpn/vless/config.json 2>/dev/null | grep -o '\"id\":\"[^\"]*\"' && echo 'SS:' && cat /opt/vpn/ss/config.json 2>/dev/null | grep password && echo 'HY2:' && cat /opt/vpn/hysteria2/config.yaml 2>/dev/null | grep password");
    }
    
    private void connectVPN() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("❌ Заполните поля"); return; }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                String url = "";
                if (currentProtocol.equals("vless")) {
                    String uuid = ssh.exec("cat /opt/vpn/vless/config.json | grep -o '\"id\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    url = "vless://" + uuid + "@" + host + ":443#VPN";
                } else if (currentProtocol.equals("ss")) {
                    String sp = ssh.exec("cat /opt/vpn/ss/config.json | grep -o '\"password\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    url = "ss://" + android.util.Base64.encodeToString(("aes-256-gcm:" + sp).getBytes(), 0) + "@" + host + ":8388#VPN";
                } else {
                    String hp = ssh.exec("cat /opt/vpn/hysteria2/config.yaml | grep password | awk '{print $2}'").trim();
                    url = "hysteria2://" + hp + "@" + host + ":443?insecure=1#VPN";
                }
                ssh.close();
                String finalUrl = url;
                runOnUiThread(() -> {
                    updateStatus("● CONNECTED");
                    log("🔗 " + finalUrl);
                    showDialog(finalUrl);
                });
            } catch (Exception e) {
                log("❌ " + e.getMessage());
            }
        });
    }
    
    private void showDialog(String url) {
        new AlertDialog.Builder(this)
            .setTitle("🔐 VPN Готов")
            .setMessage("Протокол: " + currentProtocol.toUpperCase() + "\nРежим: " + currentMode)
            .setPositiveButton("OK", null)
            .setNeutralButton("📋 Копировать", (d, w) -> {
                ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setText(url);
                log("📋 Скопировано!");
            })
            .show();
    }
    
    private void execCmd(String cmd) {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty()) { log("❌ Введите IP"); return; }
        tvOutput.setText("");
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                log(ssh.exec(cmd));
                ssh.close();
            } catch (Exception e) {
                log("❌ " + e.getMessage());
            }
        });
    }
    
    private void log(String msg) {
        runOnUiThread(() -> { tvOutput.append(msg + "\n"); scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN)); });
    }
    
    private void updateStatus(String s) {
        runOnUiThread(() -> tvStatus.setText(s));
    }
    
    private String randomPass() {
        return java.util.UUID.randomUUID().toString().substring(0, 16);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
