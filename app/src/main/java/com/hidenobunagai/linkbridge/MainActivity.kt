package com.hidenobunagai.linkbridge

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var roleManager: RoleManager
    private lateinit var statusRole: TextView
    private lateinit var statusCallLog: TextView

    private val roleLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateStatus()
        }

    private val permissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roleManager = getSystemService(RoleManager::class.java)
        statusRole = findViewById(R.id.status_role)
        statusCallLog = findViewById(R.id.status_calllog)

        findViewById<Button>(R.id.btn_role).setOnClickListener {
            roleLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
            )
        }

        findViewById<Button>(R.id.btn_calllog).setOnClickListener {
            permissionLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val roleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)
        statusRole.text = if (roleHeld) {
            getString(R.string.status_role_ok)
        } else {
            getString(R.string.status_role_ng)
        }

        val granted = checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        statusCallLog.text = if (granted) {
            getString(R.string.status_calllog_ok)
        } else {
            getString(R.string.status_calllog_ng) + "\n" + getString(R.string.status_calllog_hint)
        }
    }
}
