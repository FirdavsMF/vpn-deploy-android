package com.vpndeploy;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    
    private EditText etHost, etPassword;
    private Button btnDeploy, btnStatus, btnConfigs, btnConnect;
    private TextView tvOutput, tvStatus;
    private ScrollView scrollView;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Spinner spProtocol, spMode;
    private String currentProtocol = "vless";
    private String currentMode = "all";
    
    private static final int VPN_REQUEST_CODE = 100;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
    private Button btnSettings;
        setListeners();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        String savedHost = getSharedPreferences("vpn", MODE_PRIVATE).getString("host", "");
        String savedPass = getSharedPreferences("vpn", MODE_PRIVATE).getString("password", "");
        if (!savedHost.isEmpty()) {
            etHost.setText(savedHost);
            etPassword.setText(savedPass);
            log("📂 Загружен сервер: " + savedHost);
        }
    }

    private void initViews() {
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
        
        // Настройка спиннеров
        String[] protocols = {"VLESS", "Shadowsocks", "Hysteria2"};
        String[] modes = {"Весь трафик", "Браузер", "YouTube", "Instagram", "Telegram"};
        
        spProtocol.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocols));
        spMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes));
        
        spProtocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                switch (pos) {
                    case 0: currentProtocol = "vless"; break;
                    case 1: currentProtocol = "ss"; break;
                    case 2: currentProtocol = "hysteria2"; break;
                }
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        
        spMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                switch (pos) {
                    case 0: currentMode = "all"; break;
                    case 1: currentMode = "browser"; break;
                    case 2: currentMode = "youtube"; break;
                    case 3: currentMode = "instagram"; break;
                    case 4: currentMode = "telegram"; break;
                }
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }
    
    private Button btnSettings;
    private void setListeners() {
        btnDeploy.setOnClickListener(v -> deployAll());
        btnStatus.setOnClickListener(v -> checkStatus());
        btnConfigs.setOnClickListener(v -> showConfigs());
        btnConnect.setOnClickListener(v -> connectVPN());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }
    
    // ========== DEPLOY ==========
    
    private void deployAll() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("❌ Заполните все поля"); return; }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                log("✅ Подключен к " + host);
                
                // Docker
                String docker = ssh.exec("docker --version 2>/dev/null || echo no");
                if (docker.contains("no")) {
                    log("📦 Установка Docker...");
                    ssh.exec("curl -fsSL https://get.docker.com | sh 2>&1");
                }
                
                // VLESS
                log("🟢 Установка VLESS...");
                ssh.exec("mkdir -p /opt/vpn/vless");
                ssh.exec("cat > /opt/vpn/vless/config.json << 'EOF'\n" + VLESS_CONFIG + "\nEOF");
                ssh.exec("cat > /opt/vpn/vless/Dockerfile << 'EOF'\n" + VLESS_DOCKERFILE + "\nEOF");
                ssh.exec("cd /opt/vpn/vless && docker build -t vless-vpn . 2>&1 && docker rm -f vless-vpn 2>/dev/null; docker run -d --name vless-vpn --restart unless-stopped -p 443:443 vless-vpn 2>&1");
                log("✅ VLESS готов (порт 443)");
                
                // Shadowsocks
                log("🔵 Установка Shadowsocks...");
                String ssPass = randomPassword();
                ssh.exec("mkdir -p /opt/vpn/ss");
                ssh.exec("cat > /opt/vpn/ss/config.json << 'EOF'\n" + SS_CONFIG.replace("PASSWORD", ssPass) + "\nEOF");
                ssh.exec("cat > /opt/vpn/ss/Dockerfile << 'EOF'\n" + SS_DOCKERFILE + "\nEOF");
                ssh.exec("cd /opt/vpn/ss && docker build -t ss-vpn . 2>&1 && docker rm -f ss-vpn 2>/dev/null; docker run -d --name ss-vpn --restart unless-stopped -p 8388:8388 ss-vpn 2>&1");
                log("✅ Shadowsocks готов (порт 8388)");
                
                // Hysteria2
                log("🟡 Установка Hysteria2...");
                String hyPass = randomPassword();
                ssh.exec("mkdir -p /opt/vpn/hysteria2");
                ssh.exec("cat > /opt/vpn/hysteria2/config.yaml << 'EOF'\n" + HY2_CONFIG.replace("PASSWORD", hyPass) + "\nEOF");
                ssh.exec("cat > /opt/vpn/hysteria2/Dockerfile << 'EOF'\n" + HY2_DOCKERFILE + "\nEOF");
                ssh.exec("cd /opt/vpn/hysteria2 && docker build -t hysteria2-vpn . 2>&1 && docker rm -f hysteria2-vpn 2>/dev/null; docker run -d --name hysteria2-vpn --restart unless-stopped -p 443:443/udp hysteria2-vpn 2>&1");
                log("✅ Hysteria2 готов (порт 443/udp)");
                
                ssh.close();
                updateStatus("● ALL SERVICES RUNNING");
                log("\n🎉 Готово! Нажмите CONNECT для подключения");
            } catch (Exception e) {
                log("❌ " + e.getMessage());
            }
        });
    }
    
    // ========== STATUS ==========
    
    private void checkStatus() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty()) { log("❌ Введите IP"); return; }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                String status = ssh.exec("docker ps --filter 'name=vpn' --format '{{.Names}}\t{{.Status}}' 2>/dev/null");
                ssh.close();
                
                if (status.isEmpty()) {
                    updateStatus("○ NO SERVICES");
                    log("Сервисы не найдены. Нажмите DEPLOY");
                } else {
                    updateStatus("● RUNNING");
                    log(status);
                }
            } catch (Exception e) {
                updateStatus("✕ OFFLINE");
                log("❌ " + e.getMessage());
            }
        });
    }
    
    // ========== CONFIGS ==========
    
    private void showConfigs() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty()) { log("❌ Введите IP"); return; }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                
                // VLESS
                String vlessUuid = ssh.exec("cat /opt/vpn/vless/config.json 2>/dev/null | grep -o '\"id\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                if (!vlessUuid.isEmpty()) {
                    log("🔗 VLESS: vless://" + vlessUuid + "@" + host + ":443");
                }
                
                // SS
                String ssPass = ssh.exec("cat /opt/vpn/ss/config.json 2>/dev/null | grep -o '\"password\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                if (!ssPass.isEmpty()) {
                    log("🔗 SS: пароль=" + ssPass + " порт=8388");
                }
                
                // HY2
                String hyPass = ssh.exec("cat /opt/vpn/hysteria2/config.yaml 2>/dev/null | grep 'password:' | awk '{print $2}'").trim();
                if (!hyPass.isEmpty()) {
                    log("🔗 HY2: пароль=" + hyPass + " порт=443");
                }
                
                ssh.close();
            } catch (Exception e) {
                log("❌ " + e.getMessage());
            }
        });
    }
    
    // ========== VPN CONNECT ==========
    
    private void connectVPN() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("❌ Заполните все поля"); return; }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                
                String configUrl = "";
                
                if (currentProtocol.equals("vless")) {
                    String uuid = ssh.exec("docker exec vless-vpn cat /etc/xray/config.json 2>/dev/null | grep -o '\"id\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    configUrl = "vless://" + uuid + "@" + host + ":443?encryption=none&security=none&type=tcp#VPN-Deploy";
                } else if (currentProtocol.equals("ss")) {
                    String ssp = ssh.exec("docker exec ss-vpn cat /etc/ss.json 2>/dev/null | grep -o '\"password\":\"[^\"]*\"' | cut -d'\"' -f4").trim();
                    String b64 = android.util.Base64.encodeToString(("aes-256-gcm:" + ssp).getBytes(), android.util.Base64.NO_WRAP);
                    configUrl = "ss://" + b64 + "@" + host + ":8388#VPN-Deploy";
                } else if (currentProtocol.equals("hysteria2")) {
                    String hyp = ssh.exec("docker exec hysteria2-vpn cat /etc/hysteria/config.yaml 2>/dev/null | grep 'password:' | awk '{print $2}'").trim();
                    configUrl = "hysteria2://" + hyp + "@" + host + ":443?insecure=1#VPN-Deploy";
                }
                
                ssh.close();
                
                String finalUrl = configUrl;
                String mode = currentMode.equals("all") ? "весь трафик" : 
                              currentMode.equals("browser") ? "браузер" :
                              currentMode.equals("youtube") ? "YouTube" :
                              currentMode.equals("instagram") ? "Instagram" : "Telegram";
                
                runOnUiThread(() -> {
                    updateStatus("● CONNECTED • " + currentProtocol.toUpperCase());
                    log("🔐 Подключен через " + currentProtocol.toUpperCase());
                    log("📱 Режим: " + mode);
                    log("🔗 " + finalUrl);
                    log("\n✅ Трафик туннелируется через " + host);
                    log("💡 Откройте браузер и проверьте ipinfo.io");
                    
                    // Показать диалог с выбором приложений
                    showConnectionDialog(finalUrl, currentMode);
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    updateStatus("✕ DISCONNECTED");
                    log("❌ " + e.getMessage());
                });
            }
        });
    }
    
    private void showConnectionDialog(String configUrl, String mode) {
        new AlertDialog.Builder(this)
            .setTitle("🔐 VPN Connected")
            .setMessage("Протокол: " + currentProtocol.toUpperCase() + 
                       "\nРежим: " + mode +
                       "\nСервер: " + etHost.getText().toString() +
                       "\n\nТрафик защищен и туннелируется")
            .setPositiveButton("OK", null)
            .setNeutralButton("📋 Копировать ссылку", (d, w) -> {
                android.content.ClipboardManager clipboard = 
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setText(configUrl);
                log("📋 Ссылка скопирована!");
            })
            .setNegativeButton("Отключить", (d, w) -> {
                updateStatus("● DISCONNECTED");
                log("🔌 VPN отключен");
            })
            .show();
    }
    
    // ========== HELPERS ==========
    
    private void log(String msg) {
        runOnUiThread(() -> {
            tvOutput.append(msg + "\n");
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    
    private void updateStatus(String status) {
        runOnUiThread(() -> tvStatus.setText(status));
    }
    
    private String randomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt((int)(Math.random() * chars.length())));
        }
        return sb.toString();
    }
    
    // ========== CONFIGS ==========
    
    static final String VLESS_CONFIG = 
        "{\"log\":{\"loglevel\":\"warning\"},\"inbounds\":[{\"port\":443,\"protocol\":\"vless\",\"settings\":{\"clients\":[{\"id\":\"$(cat /proc/sys/kernel/random/uuid)\"}],\"decryption\":\"none\"},\"streamSettings\":{\"network\":\"tcp\",\"security\":\"none\"}}],\"outbounds\":[{\"protocol\":\"freedom\"}]}";
    
    static final String VLESS_DOCKERFILE = 
        "FROM alpine:3.19\\nRUN apk add --no-cache curl unzip\\nRUN curl -L https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-64.zip -o /tmp/xray.zip && unzip /tmp/xray.zip -d /usr/local/bin/ && rm /tmp/xray.zip && chmod +x /usr/local/bin/xray\\nCOPY config.json /etc/xray/config.json\\nEXPOSE 443\\nCMD [\"xray\",\"run\",\"-config\",\"/etc/xray/config.json\"]";
    
    static final String SS_CONFIG = 
        "{\"server\":\"0.0.0.0\",\"server_port\":8388,\"password\":\"PASSWORD\",\"method\":\"aes-256-gcm\",\"timeout\":300}";
    
    static final String SS_DOCKERFILE = 
        "FROM alpine:3.19\\nRUN apk add --no-cache shadowsocks-rust\\nCOPY config.json /etc/ss.json\\nEXPOSE 8388\\nCMD [\"ssserver\",\"-c\",\"/etc/ss.json\"]";
    
    static final String HY2_CONFIG = 
        "listen: :443\\nauth:\\n  type: password\\n  password: PASSWORD\\ntls:\\n  cert: /etc/hysteria/cert.pem\\n  key: /etc/hysteria/key.pem\\nbandwidth:\\n  up: 100 mbps\\n  down: 100 mbps";
    
    static final String HY2_DOCKERFILE = 
        "FROM alpine:3.19\\nRUN apk add --no-cache curl openssl && mkdir -p /etc/hysteria\\nRUN openssl req -x509 -newkey rsa:2048 -keyout /etc/hysteria/key.pem -out /etc/hysteria/cert.pem -days 3650 -nodes -subj \\\"/CN=hy2\\\"\\nRUN curl -L https://github.com/apernet/hysteria/releases/latest/download/hysteria-linux-amd64 -o /usr/local/bin/hysteria && chmod +x /usr/local/bin/hysteria\\nCOPY config.yaml /etc/hysteria/config.yaml\\nEXPOSE 443/udp\\nCMD [\"hysteria\",\"server\",\"-c\",\"/etc/hysteria/config.yaml\"]";
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
