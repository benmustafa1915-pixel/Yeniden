package com.dijital.gardiyan

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        prefs = getSharedPreferences("GardiyanPrefs", Context.MODE_PRIVATE)

        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnSavePass = findViewById<Button>(R.id.btnSavePass)
        val btnStrictMode = findViewById<Button>(R.id.btnStrictMode)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnDeviceAdmin = findViewById<Button>(R.id.btnDeviceAdmin)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        // 1. Şifre Kaydetme
        btnSavePass.setOnClickListener {
            val pass = etPassword.text.toString()
            if (pass.length >= 4) {
                prefs.edit().putString("masterPass", pass).apply()
                Toast.makeText(this, "Şifre Kaydedildi!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "En az 4 haneli olmalı!", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Sıkı Mod (Zamanlayıcı)
        btnStrictMode.setOnClickListener {
            val pass = etPassword.text.toString()
            val savedPass = prefs.getString("masterPass", "")
            if (pass == savedPass && pass.isNotEmpty()) {
                val unlockTime = System.currentTimeMillis() + 3600000 // 1 Saat
                prefs.edit().putLong("unlockTime", unlockTime).apply()
                Toast.makeText(this, "Sıkı Mod Aktif! 1 Saat boyunca kilitler açılmaz.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Hatalı Şifre!", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. İzinler
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Gardiyan Servisini Açın!", Toast.LENGTH_LONG).show()
        }
        
        btnDeviceAdmin.setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Zaten Yönetici!", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Uygulama Listesi (RecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
        recyclerView.adapter = AppAdapter(apps, pm, prefs)
    }

    // Uygulama Listesi Adaptörü
    class AppAdapter(
        private val apps: List<ApplicationInfo>,
        private val pm: PackageManager,
        private val prefs: android.content.SharedPreferences
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val appName = app.loadLabel(pm).toString()
            val pkg = app.packageName

            // Kayıtlı engelli uygulamaları al
            val blockedApps = prefs.getStringSet("blockedApps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

            // Eğer engelliyse başına [X] koy, değilse [ ] koy
            holder.tvName.text = if (blockedApps.contains(pkg)) "[X] $appName" else "[ ] $appName"
            holder.tvName.setTextColor(android.graphics.Color.WHITE)
            holder.tvName.textSize = 16f
            holder.itemView.setPadding(16, 30, 16, 30)

            // Tıklama olayı (Engelle / Kaldır)
            holder.itemView.setOnClickListener {
                if (blockedApps.contains(pkg)) {
                    blockedApps.remove(pkg)
                } else {
                    blockedApps.add(pkg)
                }
                prefs.edit().putStringSet("blockedApps", blockedApps).apply()
                notifyItemChanged(position) // Listeyi güncelle
            }
        }
        override fun getItemCount() = apps.size
    }
}
