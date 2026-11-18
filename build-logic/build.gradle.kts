plugins {
    `kotlin-dsl`
}

// The Gradle plugins the conventions configure. compileOnly: each module applies them through the root build's own
// plugin declarations, so the versions come from the catalog once, and this code runs against those same classes.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

// As everywhere else - any compiler warning fails the build.
kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "ludo.android.application"
            implementationClass = "net.zodac.ludoplus.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidTest") {
            id = "ludo.android.test"
            implementationClass = "net.zodac.ludoplus.buildlogic.AndroidTestConventionPlugin"
        }
        register("kotlinMultiplatform") {
            id = "ludo.kotlin.multiplatform"
            implementationClass = "net.zodac.ludoplus.buildlogic.KotlinMultiplatformConventionPlugin"
        }
    }
}
