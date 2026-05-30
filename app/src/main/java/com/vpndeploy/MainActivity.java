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
    
    private String getVpsBase64() {
        try {
            InputStream is = getAssets().open("vps");
            byte[] data = new byte[is.available()];
            is.read(data);
            is.close();
            return Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
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
        
        // Копируем vps бинарник через base64
        String vpsB64 = getVpsBase64();
        String cmd = "echo '" + vpsB64 + "' | base64 -d > /tmp/vps && " +
                     "chmod +x /tmp/vps && " +
                     "/tmp/vps deploy -H " + host + 
                     " --password " + password + " -s " + services;
        
        executeCommand(host, password, cmd);
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
        executeCommand(host, password, 
            "/tmp/vps status -H " + host + " --password " + password);
    }
    
    private void downloadConfigs() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        executeCommand(host, password, 
            "/tmp/vps configs -H " + host + " --password " + password +
            " -s " + getServices() + " -o /tmp/cfg && find /tmp/cfg -type f -exec echo '--- {} ---' \\; -exec cat {} \\;");
    }
    
    private void executeCommand(String host, String password, String command) {
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
                appendOutput("⏳ Running...\n");
                
                String result = ssh.executeCommand(command);
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
