package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ReaderViewModel

enum class AppScreen {
    LIBRARY,
    READER,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val pendingBookId = androidx.compose.runtime.mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialBookId = intent?.getLongExtra("EXTRA_OPEN_BOOK_ID", -1L)?.takeIf { it > 0 }
        pendingBookId.value = initialBookId

        setContent {
            val viewModel: ReaderViewModel = viewModel()
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()

            MyApplicationTheme(appTheme = appTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoxReaderApp(
                        viewModel = viewModel,
                        targetBookId = pendingBookId.value,
                        onBookHandled = { pendingBookId.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newBookId = intent.getLongExtra("EXTRA_OPEN_BOOK_ID", -1L).takeIf { it > 0 }
        if (newBookId != null) {
            pendingBookId.value = newBookId
        }
    }
}

@Composable
fun VoxReaderApp(
    viewModel: ReaderViewModel = viewModel(),
    targetBookId: Long? = null,
    onBookHandled: () -> Unit = {}
) {
    val context = LocalContext.current

    // Request notification permission on Android 13+ for background download progress
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { /* permission granted or denied */ }
        )
        LaunchedEffect(Unit) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(permission)
            }
        }
    }

    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.LIBRARY) }

    LaunchedEffect(targetBookId) {
        if (targetBookId != null && targetBookId > 0) {
            viewModel.openBook(targetBookId)
            currentScreen = AppScreen.READER
            onBookHandled()
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            AppScreen.LIBRARY -> {
                LibraryScreen(
                    viewModel = viewModel,
                    onOpenReader = { bookId ->
                        viewModel.openBook(bookId)
                        currentScreen = AppScreen.READER
                    },
                    onReadAloudBook = { bookId ->
                        viewModel.openBook(bookId)
                        viewModel.startReadAloud(null)
                        currentScreen = AppScreen.READER
                    },
                    onOpenSettings = {
                        currentScreen = AppScreen.SETTINGS
                    }
                )
            }
            AppScreen.READER -> {
                ReaderScreen(
                    viewModel = viewModel,
                    onBackToLibrary = {
                        currentScreen = AppScreen.LIBRARY
                    },
                    onOpenSettings = {
                        currentScreen = AppScreen.SETTINGS
                    }
                )
            }
            AppScreen.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackToLibrary = {
                        currentScreen = AppScreen.LIBRARY
                    }
                )
            }
        }
    }
}
