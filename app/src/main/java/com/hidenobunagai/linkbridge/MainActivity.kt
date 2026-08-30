package com.hidenobunagai.linkbridge

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private lateinit var roleManager: RoleManager
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var chipRole: TextView
    private lateinit var chipCallLog: TextView
    private lateinit var chipOverlay: TextView
    private lateinit var chipNotif: TextView
    private lateinit var hintCallLog: TextView
    private lateinit var hintOverlay: TextView
    private lateinit var hintNotif: TextView
    private lateinit var btnRole: Button
    private lateinit var btnCallLog: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnNotif: Button
    private lateinit var chipShizuku: TextView
    private lateinit var hintShizuku: TextView
    private lateinit var btnShizukuPerm: Button
    private lateinit var btnShizukuToggle: Button
    private lateinit var btnShizukuTemp: Button
    private lateinit var btnShizukuOpen: Button
    private lateinit var chipA11y: TextView
    private lateinit var hintA11y: TextView
    private lateinit var btnA11y: Button
    private lateinit var switchConfirm: MaterialSwitch

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

    private val notifLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateStatus()
        }

    private val a11yLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateStatus()
        }

    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        Handler(Looper.getMainLooper()).post { updateStatus() }
    }
    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener { updateStatusOnUi() }
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener { updateStatusOnUi() }

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
        chipNotif = findViewById(R.id.status_notif)
        hintCallLog = findViewById(R.id.hint_calllog)
        hintOverlay = findViewById(R.id.hint_overlay)
        hintNotif = findViewById(R.id.hint_notif)
        btnRole = findViewById(R.id.btn_role)
        btnCallLog = findViewById(R.id.btn_calllog)
        btnOverlay = findViewById(R.id.btn_overlay)
        btnNotif = findViewById(R.id.btn_notif)

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

        btnNotif.setOnClickListener {
            notifLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        chipShizuku = findViewById(R.id.status_shizuku)
        hintShizuku = findViewById(R.id.hint_shizuku)
        btnShizukuPerm = findViewById(R.id.btn_shizuku_perm)
        btnShizukuToggle = findViewById(R.id.btn_shizuku_toggle)
        btnShizukuTemp = findViewById(R.id.btn_shizuku_temp)
        btnShizukuOpen = findViewById(R.id.btn_shizuku_open)
        chipA11y = findViewById(R.id.status_a11y)
        hintA11y = findViewById(R.id.hint_a11y)
        btnA11y = findViewById(R.id.btn_a11y)

        btnShizukuPerm.setOnClickListener { ShizukuBlocker.requestPermission() }
        btnShizukuToggle.setOnClickListener { toggleShizukuBlock() }
        btnShizukuTemp.setOnClickListener { tempAllowShizuku() }
        btnShizukuOpen.setOnClickListener { openShizukuApp() }
        btnA11y.setOnClickListener {
            a11yLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        switchConfirm = findViewById(R.id.switch_confirm)
        switchConfirm.isChecked = LinkRedirectionService.isConfirmEachCallEnabled(this)
        switchConfirm.setOnCheckedChangeListener { _, checked ->
            LinkRedirectionService.setConfirmEachCallEnabled(this, checked)
            if (checked && !Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "選択ダイアログの表示には「画面の上に表示」の権限が必要です",
                    Toast.LENGTH_LONG
                ).show()
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermListener)
            Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        tempAllowRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (_: Exception) { }
        super.onDestroy()
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

        // getEnabledNotificationListeners() は API 36 で削除されたため、API 33+ の
        // isNotificationListenerAccessGranted() を使用する
        val notifGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(
                ComponentName(this, CallNotificationListener::class.java)
            )
        setItemStatus(chipNotif, btnNotif, hintNotif, notifGranted)

        val done = listOf(roleHeld, callLogGranted, overlayGranted, notifGranted).count { it }
        progressBar.setProgressCompat(done * 100 / 4, true)
        progressText.text = getString(R.string.setup_progress, done, 4)

        updateShizukuCard()
        updateA11yCard()
    }

    private fun updateStatusOnUi() {
        Handler(Looper.getMainLooper()).post { updateStatus() }
    }

    private fun updateShizukuCard() {
        val available = ShizukuBlocker.isShizukuAvailable()
        val permGranted = ShizukuBlocker.isPermissionGranted()
        val blockEnabled = ShizukuBlocker.isBlockEnabled(this)
        val isSuspended = try { ShizukuBlocker.isAnySuspended(this) } catch (_: Exception) { false }
        val rationale = ShizukuBlocker.shouldShowRationale()
        val binderAlive = try { Shizuku.pingBinder() } catch (_: Exception) { false }

        btnShizukuPerm.visibility = View.GONE
        btnShizukuToggle.visibility = View.GONE
        btnShizukuTemp.visibility = View.GONE
        btnShizukuOpen.visibility = View.GONE

        val state = ShizukuCardState.resolve(available, permGranted, blockEnabled, isSuspended, rationale, binderAlive)
        when (state) {
            ShizukuCardState.NotInstalled -> {
                chipShizuku.text = getString(R.string.status_no_shizuku)
                chipShizuku.setTextColor(getColor(R.color.status_ng_fg))
                chipShizuku.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ng_bg))
                setChipIcon(chipShizuku, R.drawable.ic_cancel, R.color.status_ng_fg)
                hintShizuku.text = getString(R.string.hint_shizuku_no)
                btnShizukuOpen.visibility = View.VISIBLE
            }
            is ShizukuCardState.NeedPermission -> {
                chipShizuku.text = getString(R.string.status_need_perm)
                chipShizuku.setTextColor(getColor(R.color.status_ng_fg))
                chipShizuku.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ng_bg))
                setChipIcon(chipShizuku, R.drawable.ic_cancel, R.color.status_ng_fg)
                hintShizuku.text = if (state.rationale) getString(R.string.hint_shizuku_rationale) else getString(R.string.hint_shizuku_perm)
                if (!state.binderAlive) {
                    hintShizuku.text = getString(R.string.hint_shizuku_dead)
                    btnShizukuOpen.visibility = View.VISIBLE
                } else {
                    btnShizukuPerm.visibility = View.VISIBLE
                }
            }
            ShizukuCardState.Blocked -> {
                chipShizuku.text = getString(R.string.status_blocked)
                chipShizuku.setTextColor(getColor(R.color.status_ok_fg))
                chipShizuku.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ok_bg))
                setChipIcon(chipShizuku, R.drawable.ic_check_circle, R.color.status_ok_fg)
                hintShizuku.text = getString(R.string.hint_shizuku_active)
                btnShizukuToggle.text = getString(R.string.btn_shizuku_disable)
                btnShizukuToggle.visibility = View.VISIBLE
                btnShizukuTemp.visibility = View.VISIBLE
            }
            ShizukuCardState.EnabledButNotSuspended -> {
                chipShizuku.text = getString(R.string.status_ready)
                chipShizuku.setTextColor(getColor(R.color.status_ok_fg))
                chipShizuku.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ok_bg))
                setChipIcon(chipShizuku, R.drawable.ic_check_circle, R.color.status_ok_fg)
                hintShizuku.text = getString(R.string.hint_shizuku_ready) + "\n(現在は停止されていません — 再適用します)"
                btnShizukuToggle.text = getString(R.string.btn_shizuku_disable)
                btnShizukuToggle.visibility = View.VISIBLE
                ShizukuBlocker.suspendAllAsync(this) { updateStatusOnUi() }
            }
            ShizukuCardState.Ready -> {
                chipShizuku.text = getString(R.string.status_ready)
                chipShizuku.setTextColor(getColor(R.color.status_ok_fg))
                chipShizuku.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ok_bg))
                setChipIcon(chipShizuku, R.drawable.ic_check_circle, R.color.status_ok_fg)
                hintShizuku.text = getString(R.string.hint_shizuku_ready)
                btnShizukuToggle.text = getString(R.string.btn_shizuku_enable)
                btnShizukuToggle.visibility = View.VISIBLE
            }
        }
    }

    private fun updateA11yCard() {
        val enabled = LinkBridgeAccessibilityService.isEnabled(this)
        if (enabled) {
            chipA11y.text = getString(R.string.status_done)
            chipA11y.setTextColor(getColor(R.color.status_ok_fg))
            chipA11y.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ok_bg))
            setChipIcon(chipA11y, R.drawable.ic_check_circle, R.color.status_ok_fg)
            hintA11y.text = getString(R.string.hint_a11y_on)
            btnA11y.visibility = View.GONE
        } else {
            chipA11y.text = getString(R.string.status_todo)
            chipA11y.setTextColor(getColor(R.color.status_ng_fg))
            chipA11y.backgroundTintList = ColorStateList.valueOf(getColor(R.color.status_ng_bg))
            setChipIcon(chipA11y, R.drawable.ic_cancel, R.color.status_ng_fg)
            hintA11y.text = getString(R.string.hint_a11y_off)
            btnA11y.visibility = View.VISIBLE
        }
    }

    private fun toggleShizukuBlock() {
        val enabled = ShizukuBlocker.isBlockEnabled(this)
        if (!enabled) {
            if (!ShizukuBlocker.isShizukuAvailable() || !ShizukuBlocker.isPermissionGranted()) {
                Toast.makeText(this, getString(R.string.toast_shizuku_not_ready), Toast.LENGTH_SHORT).show()
                return
            }
            ShizukuBlocker.setBlockEnabled(this, true)
            Toast.makeText(this, getString(R.string.toast_block_enabling), Toast.LENGTH_SHORT).show()
            ShizukuBlocker.suspendAllAsync(this) { ok ->
                Toast.makeText(
                    this,
                    getString(if (ok) R.string.toast_block_enabled else R.string.toast_block_enable_failed),
                    Toast.LENGTH_SHORT
                ).show()
                updateStatus()
            }
        } else {
            ShizukuBlocker.setBlockEnabled(this, false)
            Toast.makeText(this, getString(R.string.toast_block_disabling), Toast.LENGTH_SHORT).show()
            ShizukuBlocker.unsuspendAllAsync(this) { ok ->
                Toast.makeText(
                    this,
                    getString(if (ok) R.string.toast_block_disabled else R.string.toast_block_disable_failed),
                    Toast.LENGTH_SHORT
                ).show()
                updateStatus()
            }
        }
        updateStatus()
    }

    private var tempAllowRunnable: Runnable? = null

    private fun tempAllowShizuku() {
        if (!ShizukuBlocker.isShizukuAvailable() || !ShizukuBlocker.isPermissionGranted()) return
        Toast.makeText(this, getString(R.string.toast_temp_allow_start), Toast.LENGTH_SHORT).show()
        ShizukuBlocker.unsuspendAllAsync(this) { ok ->
            if (ok) {
                Toast.makeText(this, getString(R.string.toast_temp_allow_done), Toast.LENGTH_LONG).show()
                val handler = Handler(Looper.getMainLooper())
                tempAllowRunnable?.let { handler.removeCallbacks(it) }
                val appCtx = applicationContext
                val r = Runnable {
                    if (ShizukuBlocker.isBlockEnabled(appCtx)) {
                        ShizukuBlocker.suspendAllAsync(appCtx) { updateStatusOnUi() }
                    }
                }
                tempAllowRunnable = r
                handler.postDelayed(r, 10 * 60 * 1000L)
            }
            updateStatus()
        }
    }

    private fun openShizukuApp() {
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: pm.getLaunchIntentForPackage("rikka.shizuku")
        if (intent != null) {
            startActivity(intent)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            } catch (_: Exception) { }
        }
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
