import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.termproject"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.termproject"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ===== local.properties에서 API 키 읽어오기 =====
        val properties = Properties()
        properties.load(FileInputStream(rootProject.file("local.properties")))
        buildConfigField("String", "GEMINI_API_KEY", "\"${properties.getProperty("GEMINI_API_KEY")}\"")
        buildConfigField("String", "TOSS_CLIENT_ID", "\"${properties.getProperty("TOSS_CLIENT_ID")}\"")
        buildConfigField("String", "TOSS_CLIENT_SECRET", "\"${properties.getProperty("TOSS_CLIENT_SECRET")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // ===== BuildConfig 사용 활성화 (이거 없으면 BuildConfig.GEMINI_API_KEY 못 씀) =====
    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ===== Gemini 연동용 추가 라이브러리 =====
    // Retrofit (네트워크 통신 = 다운로드 매니저 점수)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    // Coroutine (비동기 통신 = Coroutine 점수)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // lifecycleScope (코루틴을 화면 생명주기에 맞춰 실행)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    //이건 tensorflow 용
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // MPAndroidChart (캔들 차트 UI - 줌/팬)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

}