package com.vpndeploy;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
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
        
        StringBuilder services = new StringBuilder("all");
        if (!cbVless.isChecked() && !cbSS.isChecked() && 
            !cbHY2.isChecked() && !cbSSH.isChecked()) {
            services = new StringBuilder("all");
        } else {
            services = new StringBuilder();
            if (cbVless.isChecked()) services.append("vless,");
            if (cbSS.isChecked()) services.append("shadowsocks,");
            if (cbHY2.isChecked()) services.append("hysteria2,");
            if (cbSSH.isChecked()) services.append("ssh,");
        }
        
        String cmd = "vps deploy -H " + host + " --password " + password + 
                     " -s " + services.toString().replaceAll(",$", "");
        
        executeCommand(host, password, cmd);
    }
    
    private void checkStatus() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        executeCommand(host, password, "docker ps --filter 'name=vpn' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'");
    }
    
    private void downloadConfigs() {
        String host = etHost.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        executeCommand(host, password, "vps configs -H " + host + " --password " + password + " -o /tmp/configs && cat /tmp/configs/all-configs.txt");
    }
    
    private void executeCommand(String host, String password, String command) {
        btnDeploy.setEnabled(false);
        btnStatus.setEnabled(false);
        btnConfigs.setEnabled(false);
        tvOutput.setText("");
        
        executor.execute(() -> {
            try {
                SshClient ssh = new SshClient(host, 22, "root", password);
                ssh.connect();
                
                String result = ssh.executeCommand(command);
                
                runOnUiThread(() -> {
                    appendOutput("✅ Connected\n");
                    appendOutput(result);
                    btnDeploy.setEnabled(true);
                    btnStatus.setEnabled(true);
                    btnConfigs.setEnabled(true);
                });
                
                ssh.close();
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendOutput("❌ Error: " + e.getMessage() + "\n");
                    btnDeploy.setEnabled(true);
                    btnStatus.setEnabled(true);
                    btnConfigs.setEnabled(true);
                });
            }
        });
    }
    
    private void appendOutput(String text) {
        tvOutput.append(text + "\n");
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
