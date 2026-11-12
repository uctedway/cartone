plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.carsound.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "kr.carsound.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            // 공모전 제출용: 디버그 표시가 없는 빌드를 기본 디버그 키로 서명한다(마켓 배포 때는 전용 키로 바꾼다)
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // 소리 파일은 압축하지 않고 넣어 SoundPool이 바로 읽게 한다
    androidResources { noCompress += "wav" }
}

dependencies {
    // Pleos Vehicle SDK: 기어·깜빡이·창문·문·좌석·와이퍼·공조 이벤트
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}
