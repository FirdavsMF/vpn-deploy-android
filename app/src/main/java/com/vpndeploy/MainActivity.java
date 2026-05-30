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
    
    private void deployServices() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        if (host.isEmpty() || password.isEmpty()) {
            appendOutput("❌ Host and password required!\n");
            return;
        }
        
        String services = getAllServices();
        
        // Сначала устанавливаем vps на сервер, потом деплоим
        String installCmd = "cat > /tmp/vps << 'VPSEOF'\n" + 
            readAssetFile("vps") + "\nVPSEOF\n" +
            "chmod +x /tmp/vps && /tmp/vps deploy -H " + host + 
            " --password " + password + " -s " + services;
        
        executeCommand(host, password, installCmd);
    }
    
    private String getAllServices() {
        StringBuilder services = new StringBuilder();
        if (cbVless.isChecked()) services.append("vless,");
        if (cbSS.isChecked()) services.append("shadowsocks,");
        if (cbHY2.isChecked()) services.append("hysteria2,");
        if (cbSSH.isChecked()) services.append("ssh,");
        if (services.length() == 0) return "all";
        return services.toString().replaceAll(",$", "");
    }
    
    private String readAssetFile(String filename) {
        try {
            InputStream is = getAssets().open(filename);
            return new String(is.readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }
    
    private void checkStatus() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        executeCommand(host, password, 
            "docker ps --filter 'name=vpn' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'");
    }
    
    private void downloadConfigs() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String services = getAllServices();
        executeCommand(host, password, 
            "/tmp/vps configs -H " + host + " --password " + password + 
            " -s " + services + " -o /tmp/vpn-configs && cat /tmp/vpn-configs/all-configs.txt");
    }
    
    private void executeCommand(String host, String password, String command) {
        btnDeploy.setEnabled(false);
        btnStatus.setEnabled(false);
        btnConfigs.setEnabled(false);
        tvOutput.setText("");
        appendOutput("⚡ Connecting to " + host + "...\n");
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient(host, 22, "root", password);
                ssh.connect();
                appendOutput("✅ Connected\n");
                
                String result = ssh.executeCommand(command);
                appendOutput(result);
                
                ssh.close();
            } catch (Exception e) {
                appendOutput("❌ Error: " + e.getMessage() + "\n");
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
