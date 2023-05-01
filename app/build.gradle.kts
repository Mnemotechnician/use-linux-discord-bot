import org.jetbrains.kotlin.kapt3.base.Kapt.kapt

plugins {
	kotlin("jvm") version "1.8.20"
	kotlin("plugin.serialization") version "1.8.20"

	id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.github.mnemotechnician"

repositories {
	mavenCentral()
	maven("https://maven.kotlindiscord.com/repository/maven-public/")
}

dependencies {
	implementation("com.kotlindiscord.kord.extensions", "kord-extensions", "1.5.6-SNAPSHOT")

	implementation("dev.kord", "kord-core", "0.9.0")

	implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.6.4")
	implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.3.2")
}

tasks.test {
	useJUnitPlatform()
}

tasks.jar {
	manifest {
		attributes(
			"Main-Class" to "com.github.mnemotechnician.uselinux.AppKt"
		)
	}
}
