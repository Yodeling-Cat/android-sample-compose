package uno.lux.mosaic.app.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.designsystem.theme.MosaicDarkNavBarScrim
import uno.lux.mosaic.designsystem.theme.MosaicLightNavBarScrim
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import javax.inject.Inject

/**
 * The single Activity. It extends [AppCompatActivity] purely so AppCompat's delegate can apply the
 * user's per-app language below Android 13 (see `AppCompatLocaleRepository`) — no AppCompat UI is
 * used; the whole tree is still Compose.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // The call to enableEdgeToEdge() in setContent() is gated on a theme we don't have yet,
        // so lay the window out edge-to-edge from the first frame — splash is on screen until then.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // First launch only: adopt the device's language if we ship it. Recreates
        // this Activity once as the pinned locale takes effect, behind the splash screen.
        viewModel.resolveInitialAppLanguage()

        // Waiting for user preferences to load before showing the app.
        splashScreen.setKeepOnScreenCondition { viewModel.themeMode.value == null }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val resolvedMode = themeMode ?: return@setContent
            val darkTheme = resolvedMode.isDark(isSystemInDarkTheme())

            // Re-apply edge-to-edge styling whenever the resolved theme flips, so the system
            // bar icons keep the right contrast against the app background.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = MosaicLightNavBarScrim.toArgb(),
                        darkScrim = MosaicDarkNavBarScrim.toArgb(),
                    ) { darkTheme },
                )
                onDispose {}
            }

            MosaicTheme(darkTheme = darkTheme) {
                MosaicApp(
                    currentUserId = viewModel.currentUserId,
                    navigator = navigator,
                )
            }
        }
    }
}
