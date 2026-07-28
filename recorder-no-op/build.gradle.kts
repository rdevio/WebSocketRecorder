plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(project(":recorder-core"))
    api("com.squareup.okhttp3:okhttp:4.12.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "recorder-no-op"
            from(components["java"])
        }
    }
}
