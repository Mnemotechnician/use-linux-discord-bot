plugins {
	kotlin("jvm") version "1.8.20"
	application
}

group = "com.github.mnemotechnician"
version = "1.0-SNAPSHOT"

tasks.test {
	useJUnitPlatform()
}

kotlin {
	jvmToolchain(16)
}
