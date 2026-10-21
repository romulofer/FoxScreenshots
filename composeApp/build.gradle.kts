import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The release workflow passes the tag as -PappVersion=X.Y.Z; a local build uses this default.
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                implementation(libs.dbus.java.core)
                implementation(libs.dbus.java.transport)
                implementation(libs.jnativehook)
                implementation(libs.filekit.dialogs)
                implementation(libs.slf4j.nop)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

// The version shown in Settings: written into a resource so the code has one place to read it from.
tasks.named<ProcessResources>("desktopProcessResources") {
    inputs.property("appVersion", appVersion)
    filesMatching("app.properties") { expand("version" to appVersion) }
}

// jpackage derives .deb dependencies from the launcher alone; the global-hotkey library links these as well.
tasks.withType<AbstractJPackageTask>().configureEach {
    if (name == "packageDeb") {
        freeArgs.addAll(
            "--linux-package-deps",
            // No spaces: jpackage splits the argument on them.
            "libxkbcommon0,libxkbcommon-x11-0,libxkbfile1,libxt6t64|libxt6,libxinerama1,libxcb-xkb1,libx11-xcb1,libsm6,libice6",
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.foxdevelops.foxscreenshots.MainKt"

        // JNA, JNativeHook and Skia load native libraries; say so up front instead of a warning per library on newer JDKs.
        jvmArgs("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "foxscreenshots"
            packageVersion = appVersion
            description = "Cross-platform screenshot capture & light editing"
            vendor = "Rômulo Fernandes Evangelista"
            copyright = "© 2026 Rômulo Fernandes Evangelista. MIT License."
            licenseFile.set(rootProject.file("LICENSE"))

            // What `suggestRuntimeModules` reports, plus java.logging for the keyboard hook.
            modules("java.logging", "java.instrument", "jdk.security.auth", "jdk.unsupported")

            linux {
                iconFile.set(file("packaging/icon.png"))
                // jpackage writes "vendor <maintainer>", so this is only the address.
                debMaintainer = "rfe89@hotmail.com"
                appCategory = "Graphics"
                menuGroup = "Graphics"
            }
            windows {
                iconFile.set(file("packaging/icon.ico"))
                menuGroup = "FoxScreenShots"
                perUserInstall = true
                shortcut = true
                upgradeUuid = "6b0e3d3c-5b0a-4a49-9f8e-6a1b8f6d3a21"
            }
            macOS {
                bundleID = "com.foxdevelops.foxscreenshots"
                // The release job builds icon.icns from icon.png (iconutil only exists on macOS).
                file("packaging/icon.icns").takeIf { it.exists() }?.let { iconFile.set(it) }
            }
        }
    }
}
