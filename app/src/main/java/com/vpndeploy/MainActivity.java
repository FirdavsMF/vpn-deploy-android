package com.vpndeploy;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    
    private EditText etHost, etPassword;
    private Button btnDeployVless, btnDeploySS, btnStatus, btnConfigs;
    private TextView tvOutput;
    private ScrollView scrollView;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        etHost = findViewById(R.id.etHost);
        etPassword = findViewById(R.id.etPassword);
        btnDeployVless = findViewById(R.id.btnDeploy);
        btnStatus = findViewById(R.id.btnStatus);
        btnConfigs = findViewById(R.id.btnConfigs);
        tvOutput = findViewById(R.id.tvOutput);
        scrollView = findViewById(R.id.scrollView);
        
        btnDeployVless.setOnClickListener(v -> deployAll());
        btnStatus.setOnClickListener(v -> checkStatus());
        btnConfigs.setOnClickListener(v -> showConfigs());
    }
    
    private void deployAll() {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("❌ Fill all fields"); return; }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                log("✅ Connected");
                
                // Установка Docker если нужно
                String dockerCheck = ssh.exec("docker --version 2>/dev/null || echo no");
                if (dockerCheck.contains("no")) {
                    log("📦 Installing Docker...");
                    ssh.exec("curl -fsSL https://get.docker.com | sh");
                    log("✅ Docker installed");
                }
                
                // VLESS
                log("🚀 Deploying VLESS...");
                ssh.exec("mkdir -p /opt/vpn/vless");
                ssh.exec("cat > /opt/vpn/vless/config.json << 'EOF'\n" + getVlessConfig() + "\nEOF");
                ssh.exec("cat > /opt/vpn/vless/Dockerfile << 'EOF'\n" + getVlessDockerfile() + "\nEOF");
                ssh.exec("cd /opt/vpn/vless && docker build -t vless-vpn . && docker rm -f vless-vpn 2>/dev/null; docker run -d --name vless-vpn --restart unless-stopped -p 443:443 vless-vpn");
                log("✅ VLESS deployed");
                
                // Shadowsocks
                log("🚀 Deploying Shadowsocks...");
                ssh.exec("mkdir -p /opt/vpn/ss");
                ssh.exec("cat > /opt/vpn/ss/config.json << 'EOF'\n" + getSSConfig() + "\nEOF");
                ssh.exec("cat > /opt/vpn/ss/Dockerfile << 'EOF'\n" + getSSDockerfile() + "\nEOF");
                ssh.exec("cd /opt/vpn/ss && docker build -t ss-vpn . && docker rm -f ss-vpn 2>/dev/null; docker run -d --name ss-vpn --restart unless-stopped -p 8388:8388 ss-vpn");
                log("✅ Shadowsocks deployed");
                
                // SSH
                log("🚀 Deploying SSH...");
                ssh.exec("mkdir -p /opt/vpn/ssh");
                ssh.exec("cat > /opt/vpn/ssh/Dockerfile << 'EOF'\n" + getSSHDockerfile() + "\nEOF");
                ssh.exec("cd /opt/vpn/ssh && docker build -t ssh-vpn . && docker rm -f ssh-vpn 2>/dev/null; docker run -d --name ssh-vpn --restart unless-stopped -p 2222:2222 ssh-vpn");
                log("✅ SSH deployed");
                
                ssh.close();
                log("\n🎉 All services deployed!");
            } catch (Exception e) {
                log("❌ " + e.getMessage());
            }
        });
    }
    
    private void checkStatus() {
        execSimple("docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || echo 'No containers'");
    }
    
    private void showConfigs() {
        execSimple("echo '=== VLESS ===' && cat /opt/vpn/vless/config.json 2>/dev/null && echo '' && echo '=== SS ===' && cat /opt/vpn/ss/config.json 2>/dev/null && echo '' && echo '=== SSH ===' && grep 'echo.*chpasswd' /opt/vpn/ssh/Dockerfile 2>/dev/null | sed 's/RUN //' | sed 's/| chpasswd//'");
    }
    
    private void execSimple(String cmd) {
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (host.isEmpty() || pass.isEmpty()) { log("❌ Fill all fields"); return; }
        
        tvOutput.setText("");
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient();
                ssh.connect(host, "root", pass);
                log("✅ Connected\n");
                log(ssh.exec(cmd));
                ssh.close();
            } catch (Exception e) {
                log("❌ " + e.getMessage());
            }
        });
    }
    
    private void log(String msg) {
        runOnUiThread(() -> {
            tvOutput.append(msg + "\n");
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    
    // Конфиги вшиты прямо в код
    private String getVlessConfig() {
        return "{\"log\":{\"loglevel\":\"warning\"},\"inbounds\":[{\"port\":443,\"protocol\":\"vless\",\"settings\":{\"clients\":[{\"id\":\"$(uuidgen)\"}],\"decryption\":\"none\"},\"streamSettings\":{\"network\":\"tcp\",\"security\":\"none\"}}],\"outbounds\":[{\"protocol\":\"freedom\"}]}";
    }
    
    private String getVlessDockerfile() {
        return "FROM alpine:3.19\nRUN apk add --no-cache curl unzip\nRUN curl -L https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-64.zip -o /tmp/xray.zip && unzip /tmp/xray.zip -d /usr/local/bin/ && rm /tmp/xray.zip && chmod +x /usr/local/bin/xray\nCOPY config.json /etc/xray/config.json\nEXPOSE 443\nCMD [\"xray\",\"run\",\"-config\",\"/etc/xray/config.json\"]";
    }
    
    private String getSSConfig() {
        return "{\"server\":\"0.0.0.0\",\"server_port\":8388,\"password\":\"$(openssl rand -base64 12)\",\"method\":\"aes-256-gcm\",\"timeout\":300}";
    }
    
    private String getSSDockerfile() {
        return "FROM alpine:3.19\nRUN apk add --no-cache shadowsocks-rust\nCOPY config.json /etc/ss.json\nEXPOSE 8388\nCMD [\"ssserver\",\"-c\",\"/etc/ss.json\"]";
    }
    
    private String getSSHDockerfile() {
        return "FROM alpine:3.19\nRUN apk add --no-cache openssh-server && ssh-keygen -A\nRUN adduser -D vpnuser && echo 'vpnuser:vpnpass123' | chpasswd\nRUN echo 'Port 2222' >> /etc/ssh/sshd_config && echo 'PermitRootLogin no' >> /etc/ssh/sshd_config && echo 'PasswordAuthentication yes' >> /etc/ssh/sshd_config\nEXPOSE 2222\nCMD [\"/usr/sbin/sshd\",\"-D\"]";
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
