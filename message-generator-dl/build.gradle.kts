plugins {
	kotlin("jvm") version "1.8.20"
}

group = "com.github.mnemotechnician"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
}

tasks.create("preparePythonFiles") {
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
	dependsOn("preparePythonFiles")

	from(layout.buildDirectory.file("python"))
	from(rootProject.file("Pipfile"))
	from(rootProject.file("Pipfile.lock"))
}


tasks.create<Jar>("jarRelease") {
	dependsOn("jar")
	archiveFileName.set("message-generator-release.jar")

	duplicatesStrategy = DuplicatesStrategy.INCLUDE

	from(zipTree(tasks.jar.get().outputs.files.singleFile))
	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

	manifest {
		attributes["Main-Class"] = "com.github.mnemotechnician.messagegen.CliAppKt"
	}
}
