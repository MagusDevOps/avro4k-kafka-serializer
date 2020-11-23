group = "com.github.thake.avro4k"

plugins {
    val kotlinVersion = "1.4.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    `java-library`
    idea
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version kotlinVersion
    id("net.researchgate.release") version "2.8.1"
    id("com.github.ben-manes.versions") version "0.33.0"
}

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
    maven("https://packages.confluent.io/maven/")
}

dependencies{
    val confluentVersion by extra("6.0.0")
    val avroVersion by extra("1.10.0")
    val junitVersion by extra("5.7.0")
    val logbackVersion by extra("1.2.3")
    val kotestVersion by extra("4.3.1")
    implementation("org.apache.avro:avro:${avroVersion}")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("io.confluent:kafka-streams-avro-serde:$confluentVersion")
    implementation("com.sksamuel.avro4k:avro4k-core:0.40.0")
    implementation("io.github.classgraph:classgraph:4.8.90")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:1.0.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation("ch.qos.logback:logback-core:$logbackVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("io.mockk:mockk:1.10.2")

}
// Configure existing Dokka task to output HTML to typical Javadoc directory
tasks.dokkaHtml.configure {
    outputDirectory.set(buildDir.resolve("javadoc"))
}

// Create dokka Jar task from dokka task output
val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    // dependsOn(tasks.dokka) not needed; dependency automatically inferred by from(tasks.dokka)
    from(tasks.dokkaHtml)
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    test {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
    }

    idea {
        module {
            isDownloadSources = true
            isDownloadJavadoc = false
        }
    }
}

// Create sources Jar from main kotlin sources
val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}
publishing{
    repositories{
        maven{
            name = "mavenCentral"
            url = if (project.isSnapshot) {
                uri("https://oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
    }
    publications{
        create<MavenPublication>("mavenJava"){
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
            //artifact(javadocJar.get())
            pom{
                name.set("Kafka serializer using avro4k")
                description.set("Provides Kafka SerDes and Serializer / Deserializer implementations for avro4k")
                url.set("https://github.com/thake/kafka-avro4k-serializer")
                developers {
                    developer {
                        name.set("Thorsten Hake")
                        email.set("mail@thorsten-hake.com")
                    }
                }
                scm {
                    connection.set("https://github.com/thake/kafka-avro4k-serializer.git")
                    developerConnection.set("scm:git:ssh://github.com:thake/kafka-avro4k-serializer.git")
                    url.set("https://github.com/tbroyer/gradle-incap-helper")
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}
signing {
    useGpgCmd()
    isRequired = !isSnapshot
    sign(publishing.publications["mavenJava"])
}
tasks.named("afterReleaseBuild") {
    dependsOn("publish")
}

inline val Project.isSnapshot
    get() = version.toString().endsWith("-SNAPSHOT")
