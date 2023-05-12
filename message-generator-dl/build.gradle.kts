plugins {
	kotlin("jvm") version "1.8.20"
}

group = "com.github.mnemotechnician"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}

dependencies {
}

tasks.test {
	useJUnitPlatform()
}

// Requires pipenv to be installed
tasks.create("python") {
	sourceSets.main.get().resources.srcDirs += File("python")

	outputs.upToDateWhen { false }

	doLast {
		exec {
			workingDir("python")
			commandLine("pipenv", "sync")
		}
	}
}

tasks.create("prepare-python-files") {
	outputs.dir(layout.buildDirectory.dir("python"))

	outputs.upToDateWhen { false }

	doLast {
		val outputDir = layout.buildDirectory.dir("python").get().asFile.also { it.mkdir() }

		val files = layout.projectDirectory.asFile.resolve("python-src/src").walk().filter { it.isFile }.toList() +
			rootProject.file("Pipfile") +
			rootProject.file("Pipfile.lock")

		outputDir.resolve("python-filepaths.txt").writeText(files.joinToString("\n") {
			"/" + it.name
		})

		files.forEach {
			it.copyTo(outputDir.resolve(it.name), overwrite = true)
		}
	}
}

tasks.jar {
	dependsOn("prepare-python-files")

	duplicatesStrategy = DuplicatesStrategy.EXCLUDE

	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
	from(layout.buildDirectory.file("python"))

	manifest {
		attributes["Main-Class"] = "com.github.mnemotechnician.messagegen.CliAppKt"
	}
}
