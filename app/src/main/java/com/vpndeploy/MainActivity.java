package com.vpndeploy;

import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    
    private EditText etHost, etPassword;
    private Button btnDeploy, btnStatus, btnConfigs;
    private TextView tvOutput;
    private ScrollView scrollView;
    private CheckBox cbVless, cbSS, cbHY2, cbSSH;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setListeners();
    }
    
    private void initViews() {
        etHost = findViewById(R.id.etHost);
        etPassword = findViewById(R.id.etPassword);
        btnDeploy = findViewById(R.id.btnDeploy);
        btnStatus = findViewById(R.id.btnStatus);
        btnConfigs = findViewById(R.id.btnConfigs);
        tvOutput = findViewById(R.id.tvOutput);
        scrollView = findViewById(R.id.scrollView);
        cbVless = findViewById(R.id.cbVless);
        cbSS = findViewById(R.id.cbSS);
        cbHY2 = findViewById(R.id.cbHY2);
        cbSSH = findViewById(R.id.cbSSH);
    }
    
    private void setListeners() {
        btnDeploy.setOnClickListener(v -> deployServices());
        btnStatus.setOnClickListener(v -> checkStatus());
        btnConfigs.setOnClickListener(v -> downloadConfigs());
    }
    
    private byte[] getVpsBinary() {
        try {
            InputStream is = getAssets().open("vps");
            byte[] data = new byte[is.available()];
            is.read(data);
            is.close();
            return data;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void deployServices() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        if (host.isEmpty() || password.isEmpty()) {
            appendOutput("❌ Host and password required!\n");
            return;
        }
        
        String services = getServices();
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient(host, 22, "root", password);
                ssh.connect();
                appendOutput("✅ Connected\n");
                
                // Проверяем есть ли уже vps на сервере
                String check = ssh.exec("test -f /tmp/vps && echo yes || echo no");
                
                if (check.contains("no")) {
                    appendOutput("📤 Uploading vps binary...\n");
                    
                    byte[] vpsBin = getVpsBinary();
                    String b64 = Base64.encodeToString(vpsBin, Base64.NO_WRAP);
                    
                    // Разбиваем base64 на части по 1000 символов
                    int chunkSize = 1000;
                    for (int i = 0; i < b64.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, b64.length());
                        String chunk = b64.substring(i, end);
                        if (i == 0) {
                            ssh.exec("echo '" + chunk + "' > /tmp/vps.b64");
                        } else {
                            ssh.exec("echo '" + chunk + "' >> /tmp/vps.b64");
                        }
                    }
                    ssh.exec("base64 -d /tmp/vps.b64 > /tmp/vps && chmod +x /tmp/vps && rm /tmp/vps.b64");
                    appendOutput("✅ Binary uploaded\n");
                }
                
                // Деплой
                appendOutput("🚀 Deploying services...\n");
                String result = ssh.exec("/tmp/vps deploy -H " + host + " --password " + password + " -s " + services);
                appendOutput(result);
                
                ssh.close();
                appendOutput("\n✅ Done\n");
            } catch (Exception e) {
                appendOutput("❌ " + e.getMessage() + "\n");
            } finally {
                runOnUiThread(() -> {
                    btnDeploy.setEnabled(true);
                    btnStatus.setEnabled(true);
                    btnConfigs.setEnabled(true);
                });
            }
        });
    }
    
    private String getServices() {
        StringBuilder sb = new StringBuilder();
        if (cbVless.isChecked()) sb.append("vless,");
        if (cbSS.isChecked()) sb.append("shadowsocks,");
        if (cbHY2.isChecked()) sb.append("hysteria2,");
        if (cbSSH.isChecked()) sb.append("ssh,");
        return sb.length() == 0 ? "all" : sb.toString().replaceAll(",$", "");
    }
    
    private void checkStatus() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        execSimple(host, password, "/tmp/vps status -H " + host + " --password " + password);
    }
    
    private void downloadConfigs() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        execSimple(host, password, 
            "/tmp/vps configs -H " + host + " --password " + password +
            " -s " + getServices() + " -o /tmp/cfg && find /tmp/cfg -type f -exec echo '=== {} ===' \\; -exec cat {} \\;");
    }
    
    private void execSimple(String host, String password, String cmd) {
        btnDeploy.setEnabled(false);
        btnStatus.setEnabled(false);
        btnConfigs.setEnabled(false);
        tvOutput.setText("");
        appendOutput("⚡ " + host + "\n");
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient(host, 22, "root", password);
                ssh.connect();
                appendOutput("✅ Connected\n");
                String result = ssh.exec(cmd);
                appendOutput(result);
                ssh.close();
            } catch (Exception e) {
                appendOutput("❌ " + e.getMessage() + "\n");
            } finally {
                runOnUiThread(() -> {
                    btnDeploy.setEnabled(true);
                    btnStatus.setEnabled(true);
                    btnConfigs.setEnabled(true);
                });
            }
        });
    }
    
    private void appendOutput(String text) {
        runOnUiThread(() -> {
            tvOutput.append(text + "\n");
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
