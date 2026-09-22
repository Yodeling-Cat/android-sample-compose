plugins {
    `kotlin-dsl`
}

group = "uno.lux.mosaic.buildlogic"

/*
 * The plugin jars are compileOnly because the root build already puts them on the build classpath
 * (`plugins { alias(...) apply false }`), so the convention plugins only need them to compile
 * against the typed DSL — never to resolve one at run time.
 */
dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
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
    }
}
