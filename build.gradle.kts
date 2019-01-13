import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val awsSdkVersion = "1.11.481"
val junit5Version = "5.3.2"

plugins {
    kotlin("jvm") version "1.3.11"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("com.amazonaws:aws-java-sdk-cloudformation:$awsSdkVersion")

    testCompile("org.junit.jupiter:junit-jupiter-api:$junit5Version")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junit5Version")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
    testRuntime("org.junit.platform:junit-platform-launcher:1.3.2")
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
