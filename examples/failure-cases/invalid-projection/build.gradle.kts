// This example attempts to use an invalid projection. The build will fail.

plugins {
    java
    id("software.amazon.smithy").version("0.2.0")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("software.amazon.smithy:smithy-model:0.8.0")
}

configure<software.amazon.smithy.gradle.SmithyExtension> {
    projection = "invalid"
}
