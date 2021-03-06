// This example is an integration test to ensure that the smithy-cli version can be
// found by scanning buildScript dependencies.

plugins {
    id("software.amazon.smithy").version("0.5.3")
}

group = "software.amazon.smithy"
version = "9.9.9"

repositories {
    mavenLocal()
    mavenCentral()
}
