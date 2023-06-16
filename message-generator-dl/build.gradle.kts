plugins {
	kotlin("jvm") version "1.8.20"
}

group = "com.github.mnemotechnician"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
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

		val files = layout.projectDirectory.asFile.resolve("mg-python/src").walk().filter { it.isFile }.toList()

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
