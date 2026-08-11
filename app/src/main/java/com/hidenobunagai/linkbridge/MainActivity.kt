package com.hidenobunagai.linkbridge

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var roleManager: RoleManager
    private lateinit var statusRole: TextView
    private lateinit var statusCallLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roleManager = getSystemService(RoleManager::class.java)
        statusRole = findViewById(R.id.status_role)
        statusCallLog = findViewById(R.id.status_calllog)

        findViewById<Button>(R.id.btn_role).setOnClickListener {
            startActivityForResult(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION),
                RC_ROLE
            )
        }

        findViewById<Button>(R.id.btn_calllog).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CALL_LOG), RC_PERMISSION)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_ROLE) {
            updateStatus()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERMISSION) {
            updateStatus()
        }
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

    companion object {
        private const val RC_ROLE = 1
        private const val RC_PERMISSION = 2
    }
}
