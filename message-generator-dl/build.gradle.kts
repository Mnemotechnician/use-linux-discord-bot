plugins {
	kotlin("jvm") version "1.8.20"
}

group = "com.github.mnemotechnician"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
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
	val outname = project.name
	outputs.dir(layout.buildDirectory.dir("python/$outname"))

	outputs.upToDateWhen { false }

	doLast {
		val outputDir = layout.buildDirectory.dir("python")
			.get().asFile
			.resolve(outname)
			.also {
				it.deleteRecursively()
				it.mkdirs()
			}

		val files = layout.projectDirectory.asFile.resolve("python-src/src").walk().filter { it.isFile }.toList()

		outputDir.resolve("python-filepaths.txt").writeText(files.joinToString("\n") {
			"/$outname/${it.name}"
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
	from(rootProject.file("Pipfile"))
	from(rootProject.file("Pipfile.lock"))

	manifest {
		attributes["Main-Class"] = "com.github.mnemotechnician.messagegen.CliAppKt"
	}
}
