plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.monkeyinspector"
version = "0.2.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

dependencies {
    compileOnly("org.jmonkeyengine:jme3-core:3.9.0-stable")
    testImplementation("org.jmonkeyengine:jme3-core:3.9.0-stable")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}