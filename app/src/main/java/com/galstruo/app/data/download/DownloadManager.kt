package com.galstruo.app.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.galstruo.app.GalAetherApp
import com.galstruo.app.data.archive.SmartArchive
import com.galstruo.app.data.network.NetConfig
import com.galstruo.app.data.shinnku.ShinnkuFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** 下载状态 */
enum class DownloadState { QUEUED, DOWNLOADING, DONE, ERROR }

/** 单个下载任务 */
data class DownloadTask(
    val id: String,
    val fileName: String,
    val typeLabel: String,
    val url: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val state: DownloadState = DownloadState.QUEUED,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

/** 下载记录(持久化,重启 App 后仍可在"下载管理"中看到) */
data class DownloadRecord(
    val url: String,
    val fileName: String,
    val typeLabel: String,
    val fileSize: Long = 0,
    val time: Long = 0,
    val state: String = "DONE",   // DONE / ERROR
    val archived: Boolean = false, // 已归档(复制到下载目录/已解压),源文件可能已删除
    val error: String? = null,
) {
    val id: String get() = url.hashCode().toString()
}

/**
 * 下载管理器:OkHttp + HTTP Range 断点续传。
 * 任务状态与下载记录通过 StateFlow 暴露,前台服务与界面共同观察。
 */
object DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun buildClient() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .also { NetConfig.apply(it) }
        .build()

    private var client = buildClient()
    private var clientVersion = NetConfig.version

    private fun currentClient(): OkHttpClient {
        if (clientVersion != NetConfig.version) {
            client = buildClient()
            clientVersion = NetConfig.version
        }
        return client
    }

    private val gson = Gson()

    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTask>> = _tasks

    private val _records = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val records: StateFlow<List<DownloadRecord>> = _records

    /** 正在执行的网络请求,用于取消 */
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()

    private fun recordsFile(): File = File(GalAetherApp.appContext.filesDir, "download_records.json")

    /** 从本地文件恢复下载记录(应用启动时调用) */
    fun loadRecords() {
        scope.launch {
            val f = recordsFile()
            if (!f.exists()) return@launch
            try {
                val list = gson.fromJson<List<DownloadRecord>>(
                    f.readText(), object : TypeToken<List<DownloadRecord>>() {}.type,
                ) ?: emptyList()
                _records.value = list
            } catch (_: Exception) {
            }
        }
    }

    private fun saveRecords() {
        try {
            recordsFile().writeText(gson.toJson(_records.value))
        } catch (_: Exception) {
        }
    }

    private fun upsertRecord(record: DownloadRecord) {
        val list = _records.value.filterNot { it.url == record.url }.toMutableList()
        list.add(0, record)
        _records.value = list
        saveRecords()
    }

    /** 归档完成(复制/解压成功后调用,源文件可能已删除) */
    fun markArchived(url: String) {
        val rec = _records.value.find { it.url == url } ?: return
        upsertRecord(rec.copy(archived = true))
    }

    /** 删除记录(并清理残留源文件) */
    fun removeRecord(url: String) {
        val rec = _records.value.find { it.url == url }
        rec?.let { recordFile(it).delete() }
        _records.value = _records.value.filterNot { it.url == url }
        saveRecords()
    }

    /** 清空所有已完成记录(未归档的源文件一并删除,失败记录保留) */
    fun clearDoneRecords() {
        _records.value.filter { it.state == "DONE" }.forEach { recordFile(it).delete() }
        _records.value = _records.value.filterNot { it.state == "DONE" }
        saveRecords()
    }

    /** 记录对应的源文件(应用下载目录内) */
    fun recordFile(record: DownloadRecord): File =
        fileOf(DownloadTask(record.id, record.fileName, record.typeLabel, record.url))

    /** 开始下载一个资源文件(同 URL 已在任务列表中则忽略) */
    fun start(file: ShinnkuFile) {
        val url = file.downloadUrl()
        val id = url.hashCode().toString()
        if (_tasks.value[id]?.state == DownloadState.DOWNLOADING) return
        set(id, DownloadTask(id, file.fileName, file.type.label, url))
        // 启动前台服务,显示下载通知
        val context = GalAetherApp.appContext
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        scope.launch { run(id) }
    }

    /** 重新下载(失败后重试) */
    fun retry(id: String) {
        val task = _tasks.value[id] ?: return
        val context = GalAetherApp.appContext
        set(id, task.copy(state = DownloadState.QUEUED, error = null))
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        scope.launch { run(id) }
    }

    /** 从下载记录重试(记录里没有内存任务时用) */
    fun retryRecord(record: DownloadRecord) {
        val context = GalAetherApp.appContext
        set(record.id, DownloadTask(record.id, record.fileName, record.typeLabel, record.url))
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        scope.launch { run(record.id) }
    }

    /** 取消下载并删除未完成的文件 */
    fun cancel(id: String) {
        val task = _tasks.value[id]
        activeCalls.remove(id)?.cancel()
        task?.let {
            fileOf(it).delete()
            _records.value = _records.value.filterNot { r -> r.url == it.url }
            saveRecords()
        }
        _tasks.value = _tasks.value - id
    }

    private suspend fun run(id: String) {
        val task = _tasks.value[id] ?: return
        val dest = requireNotNull(destFile(id))
        set(id, task.copy(state = DownloadState.DOWNLOADING, error = null))
        try {
            val existing = if (dest.exists()) dest.length() else 0L
            val request = Request.Builder()
                .url(task.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                .build()
            val call = currentClient().newCall(request)
            activeCalls[id] = call
            call.execute().use { resp ->
                when {
                    resp.code == 416 -> {  // 已完整
                        finish(id, task, existing)
                        return
                    }

                    resp.code != 200 && resp.code != 206 ->
                        throw IOException("HTTP ${resp.code}")

                    else -> {
                        val total = if (resp.code == 206) {
                            resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                                ?: (existing + (resp.body?.contentLength() ?: 0))
                        } else {
                            resp.body?.contentLength() ?: -1
                        }
                        val body = resp.body ?: throw IOException("响应为空")
                        var downloaded = if (resp.code == 206) existing else 0L
                        RandomAccessFile(dest, "rw").use { raf ->
                            val channel = raf.channel
                            if (resp.code == 206) raf.seek(existing) else raf.setLength(0)
                            var lastReport = downloaded
                            body.byteStream().use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    channel.write(ByteBuffer.wrap(buf, 0, n))
                                    downloaded += n
                                    // 节流:每 256KB 或完成时更新一次状态
                                    if (downloaded - lastReport >= 256 * 1024) {
                                        lastReport = downloaded
                                        set(id, task.copy(
                                            state = DownloadState.DOWNLOADING,
                                            downloadedBytes = downloaded,
                                            totalBytes = total,
                                        ))
                                    }
                                }
                            }
                        }
                        finish(id, task, downloaded)
                    }
                }
            }
        } catch (e: IOException) {
            if (e.message?.contains("Canceled") == true || !activeCalls.containsKey(id)) return
            val current = _tasks.value[id] ?: return
            val error = e.message ?: "下载失败"
            set(id, current.copy(state = DownloadState.ERROR, error = error))
            upsertRecord(
                DownloadRecord(
                    url = task.url, fileName = task.fileName, typeLabel = task.typeLabel,
                    fileSize = 0, time = System.currentTimeMillis(),
                    state = "ERROR", error = error,
                )
            )
        } finally {
            activeCalls.remove(id)
        }
    }

    /** 下载完成:更新状态、写入记录、触发智能归档 */
    private fun finish(id: String, task: DownloadTask, downloaded: Long) {
        val done = task.copy(state = DownloadState.DONE, downloadedBytes = downloaded, totalBytes = downloaded)
        set(id, done)
        upsertRecord(
            DownloadRecord(
                url = task.url, fileName = task.fileName, typeLabel = task.typeLabel,
                fileSize = downloaded, time = System.currentTimeMillis(), state = "DONE",
            )
        )
        SmartArchive.handleCompleted(id, done)
    }

    /** 任务对应的保存文件(应用专属外部目录,无需存储权限) */
    fun fileOf(task: DownloadTask): File {
        val dir = File(GalAetherApp.appContext.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        val safeName = task.fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        return File(dir, safeName)
    }

    private fun destFile(id: String): File? =
        _tasks.value[id]?.let { fileOf(it) }

    private fun set(id: String, task: DownloadTask) {
        _tasks.value = _tasks.value + (id to task)
    }
}
