[![Maven Central](https://img.shields.io/maven-central/v/cloud.rio/amazonas.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22cloud.rio%22%20AND%20a:%22amazonas%22)
[![Build Status](https://travis-ci.com/rio-cloud/amazonas.svg?branch=master)](https://travis-ci.com/rio-cloud/amazonas)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Amazonas

Amazonas is a Kotlin library aiming to make interaction with the Amazon Web Services API as simple as possible.
It was mainly developed to be used in Gradle build scripts but it can be used in any other JVM language project.

## How to use the Library with Gradle

### Include the Library in your Buildscript Dependencies

You should include the following dependency into your build.gradle.kts file:
```kotlin
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("cloud.rio:amazonas:0.1.0")
    }
}
```

NOTE: If you want to use a snapshot version, you have to add the Sonatype snapshots repository:
```kotlin
buildscript {
    repositories {
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }
    dependencies {
        classpath("cloud.rio:amazonas:0.1.0-SNAPSHOT")
    }
}
```

### How to use the MFA Credentials Provider

This is an example of how you could use the MfaCredentialsProvider:

```kotlin
val awsCredentialsProvider: AWSCredentialsProvider by lazy {
    if (System.getProperty("profile") != null) {
        MfaCredentialsProvider(System.getProperty("profile"))
    } else {
        DefaultAWSCredentialsProviderChain()
    }
}
```

This will initialize a MfaCredentialsProvider if you call gradle with the JVM property "profile"
set to your aws profile's name. (e.g. ```./gradlew -Dprofile=[Name of the Profile to be used] [Your Task Name]```). 
If you don't set the profile property the DefaultAWSCredentialsProviderChain will be used (e.g. on an EC2 instance).

#### How should your AWS Credentials File look like

Your ~/.aws/credentials file should look like this:

```text
[default]
aws_access_key_id = <Your access key ID>
aws_secret_access_key = <Your secret access Key>

[<Name of the Profile to be used>]
role_arn = <The ARN of the role that you want to assume>
source_profile = default
mfa_serial = <The ARN of your MFA device>
```

Please make sure everything is in the credentials file, as the usage of the config file is not supported
by this library!

## License

Amazonas is licensed under the [Apache 2.0 license](https://github.com/rio-cloud/amazonas/blob/master/LICENSE).
