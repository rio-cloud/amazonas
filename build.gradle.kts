import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

version = "0.0.12-SNAPSHOT"
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
    signing
    `maven-publish`
    kotlin("jvm") version "1.3.11"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.apache.logging.log4j:log4j-core:$log4jVersion")
    compile("org.apache.logging.log4j:log4j-api:$log4jVersion")
    compile("com.google.code.gson:gson:2.8.5")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.amazonaws:aws-java-sdk-cloudformation:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-codepipeline:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-sts:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-s3:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-acm:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-route53:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-ecr:$awsSdkVersion")
    implementation("com.amazonaws:aws-java-sdk-ssm:$awsSdkVersion")
    testCompile("io.findify:s3mock_2.12:0.2.5")
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
