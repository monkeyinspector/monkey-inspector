plugins {
    `java-library`
    `maven-publish`
    signing
    id("org.jreleaser") version "1.25.0"
}

group = "io.github.monkeyinspector"
version = "0.3.0"
description = "A lightweight runtime inspector for jMonkeyEngine applications"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnlyApi("org.jmonkeyengine:jme3-core:3.9.0-stable")
    testImplementation("org.jmonkeyengine:jme3-core:3.9.0-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "monkeyinspector"

            pom {
                name.set("Monkey Inspector")
                description.set(project.description)
                url.set("https://github.com/yuhan3958/monkey-inspector")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("yuhan3958")
                        name.set("yuhan8954")
                        email.set("yuhan8954@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/yuhan3958/monkey-inspector.git")
                    developerConnection.set("scm:git:ssh://git@github.com/yuhan3958/monkey-inspector.git")
                    url.set("https://github.com/yuhan3958/monkey-inspector")
                }
            }
        }
    }

    repositories {
        maven {
            name = "staging"
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Monkey Inspector",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "io.github.monkeyinspector"
        )
    }
}

tasks.withType<Jar>().configureEach {
    from("LICENSE") {
        into("META-INF")
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
