package com.bydmate.app.fork.net

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * «Диагностика сети»: runs [NetworkDiagnostics] off the main thread, keeps the latest report for the
 * dialog, and shares it as a text file through the same FileProvider the other dumps use. fork-local.
 */
@HiltViewModel
class NetworkDiagViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val runningState = MutableStateFlow(false)
    val running: StateFlow<Boolean> = runningState.asStateFlow()

    private val reportState = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = reportState.asStateFlow()

    fun run(speedTest: Boolean) {
        if (runningState.value) return
        runningState.value = true
        reportState.value = null
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { NetworkDiagnostics.collect(context, speedTest) }
                    .getOrElse { "network diagnostics failed: ${it.javaClass.simpleName} ${it.message}" }
            }
            reportState.value = text
            runningState.value = false
        }
    }

    fun dismiss() {
        reportState.value = null
    }

    /** Writes the current report to a text file and opens the system share sheet. */
    fun share() {
        val text = reportState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = context.getExternalFilesDir(null) ?: context.cacheDir
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "network-diag-$stamp.txt").apply { writeText(text) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }
    }
}
