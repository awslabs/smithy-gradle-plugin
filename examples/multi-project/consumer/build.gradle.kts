plugins {
    id("software.amazon.smithy").version("0.5.3")
}

dependencies {
    implementation(project(":producer1"))
    implementation(project(":producer2"))
}
