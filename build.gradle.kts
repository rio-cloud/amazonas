import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.owasp.dependencycheck.reporting.ReportGenerator

version = "0.1.2-SNAPSHOT"
group = "cloud.rio"

val awsSdkVersion = "1.11.671"
val log4jVersion = "2.12.1"
val jacksonVersion = "2.10.1"

val repositoryUser: String by project
val repositoryPassword: String by project
val repositoryUrl: String by project
val releasePath: String by project
val snapshotPath: String by project

plugins {
    java
    signing
    `maven-publish`
    kotlin("jvm") version "1.3.50"
    id("com.github.ben-manes.versions") version "0.27.0"
    id("org.owasp.dependencycheck") version "5.2.2"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.amazonaws:aws-java-sdk-cloudformation:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-codepipeline:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-sts:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-s3:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-acm:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-route53:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-ecr:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-ssm:$awsSdkVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("io.findify:s3mock_2.12:0.2.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

repositories {
    mavenCentral()
}

tasks.getByName<Wrapper>("wrapper").gradleVersion = "5.4"

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

tasks {
    withType<DependencyUpdatesTask> {
        resolutionStrategy {
            componentSelection {
                all {
                    val rejected = listOf("alpha", "beta", "rc", "cr", "m", "preview").any { qualifier ->
                        candidate.version.matches(".*[.-]$qualifier[\\d.-]*".toRegex(options = setOf(RegexOption.IGNORE_CASE)))
                    }
                    if (rejected) {
                        reject("Release candidate")
                    }
                }
            }
        }
    }

    dependencyCheck {
        data {
            directory = "owasp-dependency-check/database"
        }
        failBuildOnCVSS = 0f
        format = ReportGenerator.Format.ALL
        suppressionFile = "owasp-dependency-check/suppressions.xml"
        cveValidForHours = 24 * 7
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "amazonas"
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            pom {
                name.set("Amazonas")
                description.set("This library is intended to help developers with deployments to aws.")
                url.set("https://github.com/rio-cloud/amazonas")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("danielgoetz")
                        name.set("Daniel Goetz")
                        email.set("daniel.goetz@rio.cloud")
                    }
                    developer {
                        id.set("tomzirke")
                        name.set("Tom Zirke")
                        email.set("tom.zirke@tngtech.com")
                    }
                    developer {
                        id.set("christianhagel")
                        name.set("Christian Hagel")
                        email.set("christian.hagel@rio.cloud")
                    }
                    developer {
                        id.set("antoniocastillo")
                        name.set("Antonio Castillo")
                        email.set("antonio.castillo@rio.cloud")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/rio-cloud/amazonas.git")
                    developerConnection.set("scm:git:git://github.com/rio-cloud/amazonas.git")
                    url.set("https://github.com/rio-cloud/amazonas")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = uri(repositoryUrl + releasePath)
            val snapshotsRepoUrl = uri(repositoryUrl + snapshotPath)
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = repositoryUser
                password = repositoryPassword
            }
        }
    }
}

signing {
    if (project.hasProperty("signing.keyId") && project.hasProperty("signing.password") && project.hasProperty("signing.secretKeyRingFile")) {
        sign(publishing.publications["mavenJava"])
    }
}
