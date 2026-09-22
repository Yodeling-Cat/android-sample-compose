plugins {
    `kotlin-dsl`
}

group = "uno.lux.mosaic.buildlogic"

/*
 * AGP is compileOnly because the root build already puts it on the build classpath
 * (`plugins { alias(...) apply false }`); the convention plugins need it to compile against
 * `LibraryExtension` and `ApplicationExtension`, never to resolve a plugin at run time. The Kotlin
 * and ktlint plugins are applied by id alone, so they need no entry here at all.
 */
dependencies {
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "mosaic.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "mosaic.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "mosaic.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
