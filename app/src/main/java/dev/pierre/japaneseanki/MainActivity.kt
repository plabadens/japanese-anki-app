package dev.pierre.japaneseanki

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.pierre.japaneseanki.ui.FlowTheme

/** Platform entry points only: intents, permission launchers, the window, and audio playback. */
class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(AppServices.preferenceStorage(applicationContext), (application as JapaneseAnkiApplication).audioCache) }
    internal val cardViewModel: CardViewModel by viewModels {
        viewModelFactory { initializer { CardViewModel(DraftRepository(applicationContext, AppServices.preferenceStorage(applicationContext)), settingsRepository) } }
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory { initializer { SettingsViewModel(settingsRepository) } }
    }
    internal val offlineAnalyzer: JapaneseAnalyzer get() = AppServices.offlineAnalyzer(applicationContext)
    private var incoming by mutableStateOf<SharedInput?>(null)
    private var cameraGranted by mutableStateOf(false)
    private var player: MediaPlayer? = null
    private var playbackLease: AudioFileLease? = null
    private val apiPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { cardViewModel.refreshConnection() }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    private val gallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { cardViewModel.pickImage(it.toString(), true) } }
    private val attachment = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { cardViewModel.pickImage(it.toString(), false) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveIntent(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            FlowTheme(darkTheme = dark) {
                JapaneseAnkiApp(
                    cardViewModel, settingsViewModel,
                    cameraGranted = cameraGranted,
                    incoming = incoming,
                    consumeInput = { incoming = null; setIntent(Intent()) },
                    onExit = ::finish,
                    onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onConnectAnki = ::connectAnki,
                    onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onAttachment = { attachment.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPlay = ::play,
                    onOpenNote = { startActivity(ankiBrowserIntent(it)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        cardViewModel.refreshConnection()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveIntent(intent) }
    private fun receiveIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }?.toString()?.takeIf(String::isNotBlank)
        val image = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true)
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toString() else null
        incoming = if (text != null || image != null) SharedInput(text, image) else null
    }
    private fun connectAnki() {
        if (cardViewModel.uiState.value.connection.installed) apiPermission.launch(ANKIDROID_READ_WRITE_PERMISSION)
        else runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ichi2.anki"))) }
    }
    private fun play(audio: Pair<DraftAudioFile, AudioFileLease>) {
        stopPlayback()
        val next = MediaPlayer()
        playbackLease = audio.second
        player = next
        try {
            next.setDataSource(audio.first.path)
            next.setOnPreparedListener { it.start() }
            next.setOnCompletionListener { stopPlayback() }
            next.setOnErrorListener { _, _, _ -> stopPlayback(); true }
            next.prepareAsync()
        } catch (_: Exception) { stopPlayback() }
    }
    private fun stopPlayback() { player?.release(); player = null; playbackLease?.close(); playbackLease = null }
    override fun onStop() { stopPlayback(); super.onStop() }
}
