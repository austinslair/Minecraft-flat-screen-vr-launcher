plugins {
    id("com.android.library")
}

// Set by CI (and optionally locally) to reuse compiled objects and ThinLTO results between
// builds. glslang's precompiled headers are turned off because ccache cannot cache them.
val nativeCacheDir = providers.environmentVariable("VOXYQUEST_NATIVE_CACHE").orNull

android {
    namespace = "top.mobilegl.mobileglues"
    compileSdk = 36
    // Installed by CI; r28 links with the 16 KiB page alignment Quest firmware expects.
    ndkVersion = "28.1.13356709"

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters.add("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                // Pojlib ships libc++_shared.so from a different NDK; keep ours private.
                arguments += "-DANDROID_STL=c++_static"
                if (nativeCacheDir != null) {
                    arguments += listOf(
                        "-DCMAKE_C_COMPILER_LAUNCHER=ccache",
                        "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache",
                        "-DENABLE_PCH=OFF",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--thinlto-cache-dir=$nativeCacheDir/thinlto",
                    )
                }
            }
        }
        setProperty("archivesBaseName", "MobileGlues")
    }

    externalNativeBuild {
        cmake {
            path = file("MobileGlues-cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
