plugins {
    id("com.android.library")
}

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
