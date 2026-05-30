package com.vpndeploy;

import android.os.Bundle;
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
        String services = getServices();
        
        if (host.isEmpty() || password.isEmpty()) {
            appendOutput("❌ Host and password required!\n");
            return;
        }
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient(host, 22, "root", password);
                ssh.connect();
                appendOutput("✅ Connected\n");
                
                // Загружаем бинарник через SFTP
                appendOutput("📤 Uploading vps...\n");
                byte[] vpsBin = getVpsBinary();
                ssh.upload(vpsBin, "/tmp/vps");
                ssh.exec("chmod +x /tmp/vps");
                appendOutput("✅ Uploaded (" + (vpsBin.length / 1024 / 1024) + " MB)\n");
                
                // Деплой
                appendOutput("🚀 Deploying...\n");
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
        execOnServer("/tmp/vps status -H " + etHost.getText().toString().trim() + " --password " + etPassword.getText().toString().trim());
    }
    
    private void downloadConfigs() {
        execOnServer("/tmp/vps configs -H " + etHost.getText().toString().trim() + " --password " + etPassword.getText().toString().trim() + " -s " + getServices() + " -o /tmp/cfg && cat /tmp/cfg/all-configs.txt 2>/dev/null || find /tmp/cfg -type f -exec cat {} \\;");
    }
    
    private void execOnServer(String cmd) {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        btnDeploy.setEnabled(false);
        btnStatus.setEnabled(false);
        btnConfigs.setEnabled(false);
        tvOutput.setText("");
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient(host, 22, "root", password);
                ssh.connect();
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
