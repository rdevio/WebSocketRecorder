plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":recorder-core"))
    api("com.squareup.okhttp3:okhttp:4.12.0")
}

signing {
    useGpgCmd()
}
