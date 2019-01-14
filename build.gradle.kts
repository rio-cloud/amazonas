import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

version = "0.0.1-SNAPSHOT"
group = "cloud.rio"

val awsSdkVersion = "1.11.481"
val junit5Version = "5.3.2"
val log4jVersion = "2.11.0"

val repositoryUser: String by project
val repositoryPassword: String by project
val repositoryUrl: String by project
val releasePath: String by project
val snapshotPath: String by project

plugins {
    java
    maven
    kotlin("jvm") version "1.3.11"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.apache.logging.log4j:log4j-core:$log4jVersion")
    compile("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.amazonaws:aws-java-sdk-cloudformation:$awsSdkVersion")

    testCompile("org.junit.jupiter:junit-jupiter-api:$junit5Version")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junit5Version")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
    testRuntime("org.junit.platform:junit-platform-launcher:1.3.2")
    testImplementation("io.mockk:mockk:1.8.13.kotlin13")
}

repositories {
    mavenCentral()
}

tasks.getByName<Wrapper>("wrapper").gradleVersion = "5.1"

tasks.withType<Test> {
    useJUnitPlatform()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    classifier = "sources"
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.javadoc)
    classifier = "javadoc"
}

tasks.named<Upload>("uploadArchives") {
    repositories.withGroovyBuilder {
        "mavenDeployer" {
            "repository"("url" to repositoryUrl + releasePath) {
                "authentication"("userName" to repositoryUser, "password" to repositoryPassword)
            }

            "snapshotRepository"("url" to repositoryUrl + snapshotPath) {
                "authentication"("userName" to repositoryUser, "password" to repositoryPassword)
            }

            "pom"{
                "project" {
                    setProperty("name", "Amazonas")
                    setProperty("description", "This library is intended to help developers with deployments to aws.")
                    setProperty("url", "https://github.com/rio-cloud/amazonas")
                    setProperty("packaging", "jar")

                    "scm" {
                        setProperty("connection", "scm:git:git://github.com/rio-cloud/amazonas.git")
                        setProperty("developerConnection", "scm:git:git://github.com/rio-cloud/amazonas.git")
                        setProperty("url", "https://github.com/rio-cloud/amazonas")
                    }

                    "licenses" {
                        "license" {
                            setProperty("name", "The Apache License, Version 2.0")
                            setProperty("url", "http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    "developers" {
                        "developer" {
                            setProperty("id", "danielgoetz")
                            setProperty("name", "Daniel Goetz")
                            setProperty("email", "daniel.goetz@rio.cloud")
                        }
                    }
                }
            }
        }
    }
}
