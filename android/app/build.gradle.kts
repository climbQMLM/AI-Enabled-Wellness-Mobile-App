plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "sg.edu.nus.iss.wellness"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "sg.edu.nus.iss.wellness"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        // ViewBinding 让每个 layout 都自动生成对应的 Binding 类，
        // 代替 findViewById，编译时安全，不会有 NullPointerException
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Kotlin JVM target 和 compileOptions 保持一致
    kotlin {
        jvmToolchain(11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.fragment.ktx)

    // RecyclerView + CardView（列表页用）
    implementation(libs.recyclerview)
    implementation(libs.cardview)

    // Retrofit2 + Gson converter（HTTP 接口 + JSON 解析）
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // OkHttp（Retrofit 底层 HTTP 客户端 + 请求日志）
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // 协程（在 Fragment/Activity 里异步调 API，避免主线程阻塞）
    implementation(libs.coroutines.android)

    // ViewModel + LiveData（MVVM 架构基础）
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // EncryptedSharedPreferences（JWT token 加密存储，比普通 SP 安全）
    implementation(libs.security.crypto)

    // MPAndroidChart（Dashboard 页的 HRV/睡眠/步数小折线图）
    implementation(libs.mpandroidchart)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
