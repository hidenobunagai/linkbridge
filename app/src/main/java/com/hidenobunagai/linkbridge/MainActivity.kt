package com.hidenobunagai.linkbridge

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : ComponentActivity() {

    private lateinit var roleManager: RoleManager
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var chipRole: TextView
    private lateinit var chipCallLog: TextView
    private lateinit var chipOverlay: TextView
    private lateinit var hintCallLog: TextView
    private lateinit var hintOverlay: TextView
    private lateinit var btnRole: Button
    private lateinit var btnCallLog: Button
    private lateinit var btnOverlay: Button

    private val roleLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateStatus()
        }

    private val permissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateStatus()
        }

    private val overlayLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roleManager = getSystemService(RoleManager::class.java)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        chipRole = findViewById(R.id.status_role)
        chipCallLog = findViewById(R.id.status_calllog)
        chipOverlay = findViewById(R.id.status_overlay)
        hintCallLog = findViewById(R.id.hint_calllog)
        hintOverlay = findViewById(R.id.hint_overlay)
        btnRole = findViewById(R.id.btn_role)
        btnCallLog = findViewById(R.id.btn_calllog)
        btnOverlay = findViewById(R.id.btn_overlay)

        btnRole.setOnClickListener {
            roleLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
            )
        }

        btnCallLog.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
        }

        btnOverlay.setOnClickListener {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val roleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)
        setItemStatus(chipRole, btnRole, null, roleHeld)

        val callLogGranted =
            checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
        setItemStatus(chipCallLog, btnCallLog, hintCallLog, callLogGranted)

        val overlayGranted = Settings.canDrawOverlays(this)
        setItemStatus(chipOverlay, btnOverlay, hintOverlay, overlayGranted)

        val done = listOf(roleHeld, callLogGranted, overlayGranted).count { it }
        progressBar.setProgressCompat(done * 100 / 3, true)
        progressText.text = getString(R.string.setup_progress, done, 3)
    }

    private fun setItemStatus(chip: TextView, button: Button, hint: TextView?, done: Boolean) {
        if (done) {
            chip.text = getString(R.string.status_done)
            chip.setTextColor(getColor(R.color.status_ok_fg))
            chip.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ok_bg))
            setChipIcon(chip, R.drawable.ic_check_circle, R.color.status_ok_fg)
        } else {
            chip.text = getString(R.string.status_todo)
            chip.setTextColor(getColor(R.color.status_ng_fg))
            chip.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ng_bg))
            setChipIcon(chip, R.drawable.ic_cancel, R.color.status_ng_fg)
        }
        button.visibility = if (done) View.GONE else View.VISIBLE
        hint?.visibility = if (done) View.GONE else View.VISIBLE
    }

    private fun setChipIcon(chip: TextView, iconRes: Int, tintRes: Int) {
        val icon = ContextCompat.getDrawable(this, iconRes)!!.mutate()
        val size = dp(14)
        icon.setBounds(0, 0, size, size)
        icon.setTint(getColor(tintRes))
        chip.setCompoundDrawablesRelative(icon, null, null, null)
        chip.compoundDrawablePadding = dp(5)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
