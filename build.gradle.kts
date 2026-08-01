plugins {
    kotlin("jvm") version "2.2.10" apply false
    kotlin("android") version "2.2.10" apply false
    id("com.android.library") version "8.11.1" apply false
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

allprojects {
    group = "io.github.rdevio.websocketrecorder"
    version = "0.1.7"
}
