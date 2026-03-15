package com.v2ray.ang.ui

import androidx.core.content.ContextCompat
import kotlinx.coroutines.isActive
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.viewmodel.MainViewModel
import com.v2ray.ang.util.SmartServerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : HelperBaseActivity() {
    val mainViewModel: MainViewModel by viewModels()
    
    private lateinit var fabIcon: ImageView 
    private lateinit var tvStatus: TextView
    private lateinit var progressCircle: ProgressBar
    private lateinit var adapter: MainRecyclerAdapter
    private lateinit var recyclerView: RecyclerView
    private var monitorJob: kotlinx.coroutines.Job? = null

    private val DEFAULT_SUBSCRIPTION_URL = "https://gist.githubusercontent.com/Danil6395/07e90d98202845dc2cacda13e98e5481/raw/servers.txt"

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startV2Ray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fabIcon = findViewById(R.id.fab_icon)
        tvStatus = findViewById(R.id.tv_status)
        progressCircle = findViewById(R.id.connect_progress)
        recyclerView = findViewById(R.id.recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MainRecyclerAdapter(this)
        recyclerView.adapter = adapter
        
        setupAllButtons()
        
        mainViewModel.startListenBroadcast()
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        
        if (MmkvManager.decodeServerList().isEmpty()) {
            realDownloadSubscription()
        } else {
            reloadAdapterData()
        }
        
        mainViewModel.isRunning.observe(this) { isRunning ->
            updateUIState(isRunning)
            adapter.notifyDataSetChanged()
            if (isRunning) {
                startMonitorLoop()
            } else {
                monitorJob?.cancel()
                tvStatus.text = "PROTECTION DISABLED"
            }
        }
    }

    private fun setupAllButtons() {
        // 1. Кнопка настроек
        findViewById<View>(R.id.btn_open_settings)?.setOnClickListener {
            startActivity(Intent(this, CustomSettingsActivity::class.java))
        }

        // 2. Кнопка SYNC
        findViewById<View>(R.id.btn_update)?.setOnClickListener {
            realDownloadSubscription()
        }

        // 3. Кнопка AUTO
        findViewById<View>(R.id.btn_auto_select)?.setOnClickListener {
            val allGuids = MmkvManager.decodeServerList()
            // Ищем сервер с минимальным пингом
            val bestGuid = allGuids
                .filter { !SmartServerUtils.isRussia(MmkvManager.decodeServerConfig(it)?.remarks) }
                .minByOrNull { 
                     val delay = MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L
                     if (delay <= 0) 999999L else delay
                }
            
            if (bestGuid != null) {
                MmkvManager.setSelectServer(bestGuid)
                reloadAdapterData()
                toast("Selected fastest node")
            } else {
                toast("Ping nodes first!")
            }
        }

        // 4. ГЛАВНАЯ КНОПКА (CONNECT / DISCONNECT)
        findViewById<View>(R.id.fab_click_area)?.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                // --- ЖЕСТКОЕ ВЫКЛЮЧЕНИЕ ---
                V2RayServiceManager.stopVService(this) // Используем Utils, это надежнее
                mainViewModel.isRunning.postValue(false) // Моментально меняем иконку
                tvStatus.text = "PROTECTION DISABLED"
                tvStatus.setTextColor(resources.getColor(R.color.colorTextSecondary))
            } else {
                smartConnect()
                // Запускаем проверку "есть ли реальный интернет" через 2 секунды после старта
                lifecycleScope.launch(Dispatchers.IO) {
                    delay(2000)
                    checkRealInternet()
                }
            }
        }

        findViewById<View>(R.id.btn_open_settings)?.setOnClickListener {
            startActivity(Intent(this, CustomSettingsActivity::class.java))
        }

        findViewById<View>(R.id.btn_update)?.setOnClickListener {
            realDownloadSubscription()
        }

        findViewById<View>(R.id.btn_auto_select)?.setOnClickListener {
            val allGuids = MmkvManager.decodeServerList()
            if (allGuids.isEmpty()) return@setOnClickListener

            // Находим самый быстрый НЕ из РФ
            var bestGuid: String? = null
            var minDelay = 999999L

            for (guid in allGuids) {
                val remarks = MmkvManager.decodeServerConfig(guid)?.remarks
                if (!SmartServerUtils.isRussia(remarks)) {
                    val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                    if (delay in 1 until minDelay) {
                        minDelay = delay
                        bestGuid = guid
                    }
                }
            }
            
            val finalGuid = bestGuid ?: allGuids[0]
            MmkvManager.setSelectServer(finalGuid)
            reloadAdapterData()
            toast("Smart selection active")
        }
    }

    private fun smartConnect() {
        val currentGuid = MmkvManager.getSelectServer()
        if (currentGuid.isNullOrEmpty()) {
            findViewById<View>(R.id.btn_auto_select)?.performClick()
        }
        handleFabAction()
    }

    private fun updateUIState(isRunning: Boolean) {
        fabIcon.setImageResource(if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp)
        if (isRunning) {
            tvStatus.text = "TUNNEL ACTIVE"
            tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorAccent))
            progressCircle.isIndeterminate = true
        } else {
            val guid = MmkvManager.getSelectServer() ?: ""
            val config = MmkvManager.decodeServerConfig(guid)
            tvStatus.text = config?.remarks?.uppercase() ?: "TAP TO SECURE"
            tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextSecondary))
            progressCircle.isIndeterminate = false
            progressCircle.progress = 0
        }
    }

    private fun reloadAdapterData() {
        mainViewModel.reloadServerList()
        adapter.updateData(MmkvManager.decodeServerList())
    }

    private fun realDownloadSubscription() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val limit = prefs.getInt("server_limit", 50)
        tvStatus.text = "SYNCING..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(DEFAULT_SUBSCRIPTION_URL).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "v2rayNG/1.8.5")
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                
                val rawList = try {
                    String(android.util.Base64.decode(content.trim(), android.util.Base64.DEFAULT))
                } catch (e: Exception) { content }

                val limitedList = rawList.split("\n")
                    .filter { it.contains("://") }
                    .take(limit)
                    .joinToString("\n")

                withContext(Dispatchers.Main) {
                    mainViewModel.importBatchConfig(limitedList)
                    delay(500)
                    mainViewModel.testAllTcping() 
                    reloadAdapterData()
                    toast("Database updated")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { tvStatus.text = "SYNC FAILED" }
            }
        }
    }

    private suspend fun checkRealInternet(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Проверяем доступ к Google (быстрый HEAD запрос)
            val connection = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            val code = connection.responseCode
            connection.disconnect()
            return@withContext code == 204 || code == 200
        } catch (e: Exception) {
            return@withContext false
        }
    }
    private fun handleFabAction() {
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) return
        V2RayServiceManager.startVService(this)
    }
    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (mainViewModel.isRunning.value == true) {
                    val hasInternet = checkRealInternet()
                    withContext(Dispatchers.Main) {
                        if (hasInternet) {
                            tvStatus.text = "ONLINE & SECURE 🚀"
                            tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorAccent))
                        } else {
                            tvStatus.text = "CONNECTED (NO INTERNET) ⚠️"
                            tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorPingRed))
                        }
                    }
                }
                delay(3000) // Проверяем каждые 3 секунды
            }
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { moveTaskToBack(false); return true }
        return super.onKeyDown(keyCode, event)
    }
}