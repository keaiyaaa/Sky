plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.msdksample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.msdksample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // DJI MSDK 只支持 arm64-v8a 架构
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    // DJI MSDK 所需的 SO 库打包配置
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            keepDebugSymbols.addAll(listOf(
                "**/libconstants.so",
                "**/libdji_innertools.so",
                "**/libdjibase.so",
                "**/libDJICSDKCommon.so",
                "**/libDJIFlySafeCore-CSDK.so",
                "**/libdjifs_jni-CSDK.so",
                "**/libDJIRegister.so",
                "**/libdjisdk_jni.so",
                "**/libDJIUpgradeCore.so",
                "**/libDJIUpgradeJNI.so",
                "**/libDJIWaypointV2Core-CSDK.so",
                "**/libdjiwpv2-CSDK.so",
                "**/libFlightRecordEngine.so",
                "**/libvideo-framing.so",
                "**/libwaes.so",
                "**/libagora-rtsa-sdk.so",
                "**/libc++_shared.so",
                "**/libmrtc_28181.so",
                "**/libmrtc_agora.so",
                "**/libmrtc_core.so",
                "**/libmrtc_core_jni.so",
                "**/libmrtc_data.so",
                "**/libmrtc_log.so",
                "**/libmrtc_onvif.so",
                "**/libmrtc_rtmp.so",
                "**/libmrtc_rtsp.so",
            ))
        }
    }
}

dependencies {
    // ═══════════════════════════════════════════════════════════════
    // AndroidX 核心库
    // ═══════════════════════════════════════════════════════════════
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.activity:activity:1.8.0")

    // ═══════════════════════════════════════════════════════════════
    // DJI Mobile SDK V5（来自 DJI-FLY-aerial / DJI-FLY-kami）
    // ═══════════════════════════════════════════════════════════════
    // DJI MSDK v5 依赖说明：
    //   -aircraft（runtime，内含 native so 与加固 Helper com.cySdkyc.clx.Helper，
    //             SDK 的 Java API 以加密形式打包在 assets 中）；
    //   -aircraft-provided：仅提供编译期可见的 Java API 类。
    // 因此 provided 必须用 compileOnly（不打进 APK）：
    //   - 若 implementation 打包 provided jar，其字节码与 ART 不兼容
    //     （类经加固剥离），运行时会抛 VerifyError
    //     （Verifier rejected class ...ProductType: Constructor returning
    //     without calling superclass constructor）；
    //   - 若不打包 provided 也必须在 Application.attachBaseContext 调用
    //     com.cySdkyc.clx.Helper.install(application) 解密加载运行时类，
    //     否则抛 NoClassDefFoundError: dji/v5/manager/interfaces/SDKManagerCallback。
    implementation("com.dji:dji-sdk-v5-aircraft:5.18.0")
    compileOnly("com.dji:dji-sdk-v5-aircraft-provided:5.18.0")
    implementation("com.dji:dji-sdk-v5-networkImp:5.18.0")
    implementation("com.dji:wpmzsdk:1.0.5.1")

    // ═══════════════════════════════════════════════════════════════
    // DJI V5 UXSDK（使用 Maven 发布版）
    // TODO: 如需使用 UXSDK 本地模块，将 android-sdk-v5-uxsdk 复制到项目根目录
    //       并改用 implementation(project(":android-sdk-v5-uxsdk"))
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    // MapLibre 地图（来自 DJI-FLY-kami 航线规划模块）
    // ═══════════════════════════════════════════════════════════════
    implementation("org.maplibre.gl:android-sdk:10.3.5")
    implementation("org.maplibre.gl:android-sdk-turf:5.9.1")

    // ═══════════════════════════════════════════════════════════════
    // 协程（来自 DJI-FLY-kami）
    // ═══════════════════════════════════════════════════════════════
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ═══════════════════════════════════════════════════════════════
    // 权限请求库（来自 DJI-FLY-aerial）
    // ═══════════════════════════════════════════════════════════════
    implementation("com.guolindev.permissionx:permissionx:1.7.1")

    // ═══════════════════════════════════════════════════════════════
    // Vosk 离线语音识别（来自 DJI-FLY-aerial）
    // ═══════════════════════════════════════════════════════════════
    implementation("com.alphacephei:vosk-android:0.3.47")

    // ═══════════════════════════════════════════════════════════════
    // OkHttp 网络请求（来自 DJI-FLY-aerial）
    // ═══════════════════════════════════════════════════════════════
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ═══════════════════════════════════════════════════════════════
    // Gson JSON 解析（来自 DJI-FLY-aerial）
    // ═══════════════════════════════════════════════════════════════
    implementation("com.google.code.gson:gson:2.10.1")

    // ═══════════════════════════════════════════════════════════════
    // Filament 3D 渲染（来自 DJI-FLY-aerial）
    // ═══════════════════════════════════════════════════════════════
    implementation("com.google.ar.sceneform:filament-android:1.17.1")

    // ═══════════════════════════════════════════════════════════════
    // 测试依赖
    // ═══════════════════════════════════════════════════════════════
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // ═══════════════════════════════════════════════════════════════
    // Glide 图片加载（来自 DJI-FLY-asuka912）
    // ═══════════════════════════════════════════════════════════════
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ═══════════════════════════════════════════════════════════════
    // Room 数据库（来自 DJI-FLY-asuka912）
    // ═══════════════════════════════════════════════════════════════
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}