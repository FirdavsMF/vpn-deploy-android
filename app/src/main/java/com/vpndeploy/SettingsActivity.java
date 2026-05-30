package com.vpndeploy;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.*;
import java.io.*;

public class SettingsActivity extends AppCompatActivity {
    
    private EditText etHost, etPassword, etName;
    private Button btnSave, btnLoad;
    private ListView lvServers;
    private String serversFile;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        serversFile = getFilesDir() + "/servers.json";
        
        etName = findViewById(R.id.etName);
        etHost = findViewById(R.id.etHost);
        etPassword = findViewById(R.id.etPassword);
        btnSave = findViewById(R.id.btnSave);
        btnLoad = findViewById(R.id.btnLoad);
        lvServers = findViewById(R.id.lvServers);
        
        btnSave.setOnClickListener(v -> saveServer());
        loadServers();
    }
    
    private void saveServer() {
        String name = etName.getText().toString().trim();
        String host = etHost.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        
        if (host.isEmpty()) { toast("Введите IP"); return; }
        if (name.isEmpty()) name = host;
        
        try {
            JSONArray servers = loadServersArray();
            
            // Проверка на дубликат
            for (int i = 0; i < servers.length(); i++) {
                if (servers.getJSONObject(i).getString("host").equals(host)) {
                    servers.remove(i);
                    break;
                }
            }
            
            JSONObject server = new JSONObject();
            server.put("name", name);
            server.put("host", host);
            server.put("password", pass);
            servers.put(server);
            
            FileWriter fw = new FileWriter(serversFile);
            fw.write(servers.toString(2));
            fw.close();
            
            toast("✅ " + name + " сохранен");
            loadServers();
            etName.setText("");
            etHost.setText("");
            etPassword.setText("");
            
        } catch (Exception e) {
            toast("❌ " + e.getMessage());
        }
    }
    
    private JSONArray loadServersArray() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(serversFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return new JSONArray(sb.toString());
        } catch (Exception e) {
            return new JSONArray();
        }
    }
    
    private void loadServers() {
        try {
            JSONArray servers = loadServersArray();
            String[] names = new String[servers.length()];
            for (int i = 0; i < servers.length(); i++) {
                JSONObject s = servers.getJSONObject(i);
                names[i] = "🖥 " + s.getString("name") + "\n   " + s.getString("host");
            }
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, names);
            lvServers.setAdapter(adapter);
            
            lvServers.setOnItemClickListener((parent, view, pos, id) -> {
                try {
                    JSONObject s = servers.getJSONObject(pos);
                    // Возвращаем данные в MainActivity
                    getSharedPreferences("vpn", MODE_PRIVATE).edit()
                        .putString("host", s.getString("host"))
                        .putString("password", s.getString("password"))
                        .apply();
                    toast("✅ Выбран: " + s.getString("name"));
                    finish();
                } catch (Exception e) {
                    toast("❌ Ошибка");
                }
            });
            
        } catch (Exception e) {
            toast("Нет сохраненных серверов");
        }
    }
    
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
