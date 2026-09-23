package uno.lux.mosaic.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Built on [NavigationSuiteScaffoldLayout] only to give the bar a divider; the background and inset
 * handling copy the standard scaffold's.
 */
@Composable
fun DividedNavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    dividerColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit,
) {
    val layoutType = NavigationSuiteScaffoldDefaults
        .calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val isBottomBar = layoutType == NavigationSuiteType.NavigationBar
    // The navigation component owns the system-bar inset on its own edge, so the content consumes
    // it to avoid padding for it twice — exactly what NavigationSuiteScaffold does internally.
    val contentInsets = if (isBottomBar) {
        NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
    } else {
        NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        NavigationSuiteScaffoldLayout(
            layoutType = layoutType,
            navigationSuite = {
                NavigationSuite(
                    layoutType = layoutType,
                    // Only the phone bottom bar gets the hairline; the rail already reads as a
                    // distinct side panel on larger windows.
                    modifier = if (isBottomBar) Modifier.topDivider(dividerColor) else Modifier,
                    content = navigationSuiteItems,
                )
            },
        ) {
            Box(Modifier.consumeWindowInsets(contentInsets)) {
                content()
            }
        }
    }
}

private fun Modifier.topDivider(color: Color): Modifier = drawWithContent {
    drawContent()
    val thickness = 1.dp.toPx()

    drawLine(
        color = color,
        start = Offset(x = 0f, y = thickness / 2f),
        end = Offset(x = size.width, y = thickness / 2f),
        strokeWidth = thickness,
    )
}
