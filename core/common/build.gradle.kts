plugins {
    id("mosaic.android.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "uno.lux.mosaic.common"

    /*
     * The wire helpers and the FileLoader fake are published as test fixtures rather than
     * kept in :app, so the fake stays beside the thing it stands in for and both modules
     * test against one copy.
     *
     * AGP still marks this DSL `@Incubating`, which is all the suppressed warning is about —
     * nothing here is known to be broken. It configures a test-only source set, so the cost of
     * the API changing under us is an edit to this build file and nothing that ships.
     */
    @Suppress("UnstableApiUsage")
    testFixtures {
        enable = true
    }
}

/*
 * What two or more concerns share: the noun-free composables, the wire types that describe no
 * single aggregate, and the utilities. It knows the design system — an error screen is drawn in
 * Mosaic's terms — and it knows no concern, which is what `common knows no concern` asserts.
 */
dependencies {
    implementation(project(":core:design-system"))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.kotlinx.serialization.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.timber)
    testFixturesImplementation(libs.okhttp)
    testFixturesImplementation(libs.retrofit)
    testImplementation(libs.junit)
}
