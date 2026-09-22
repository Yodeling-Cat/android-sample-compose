plugins {
    id("mosaic.android.library.compose")
}

android {
    namespace = "uno.lux.mosaic.designsystem"
}

/*
 * The palette, the type scale and the branded controls are this module's public surface, so the
 * Compose artifacts their signatures are written in are `api` — a consumer cannot name MosaicTheme
 * without naming MaterialTheme's types too.
 */
dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
