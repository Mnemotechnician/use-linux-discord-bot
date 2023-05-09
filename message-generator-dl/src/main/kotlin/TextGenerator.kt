package com.github.mnemotechnician.messagegen

import java.io.Closeable
import java.io.File
import java.lang.IllegalStateException
import java.lang.ProcessBuilder.Redirect.*

/**
 * A kotlin wrapper for an AI text generator in Python.
 */
object TextGenerator {
	val workDir = File("${System.getProperty("user.home")}/use-linux/deepl/").also(File::mkdirs)
	val pythonDir = workDir.resolve("tmp").also(File::mkdir)
	val modelFile = workDir.resolve("model.ckpt")
	val vocabFile = workDir.resolve("vocab.json")

	private var filesCopied = false

	/** Copies the python files, if neccesary. */
	private fun copyPythonFiles() {
		if (filesCopied) return
		filesCopied = true

		// Copy the files
		val resourcesToCopy = javaClass.getResourceAsStream("/python-filepaths.txt")!!
			.bufferedReader().use { it.readText().split("\n") }

		resourcesToCopy.forEach { resource ->
			println("Copying $resource")
			javaClass.getResourceAsStream(resource)!!.use { stream ->
				pythonDir.resolve(resource.substringAfterLast("/"))
					.outputStream()
					.use {
						stream.copyTo(it)
					}
			}
		}
	}

	private fun preparePythonEnvironment() {
		copyPythonFiles()

		try {
			ProcessBuilder("pipenv", "sync")
				.directory(pythonDir)
				.redirectOutput(INHERIT)
				.start()
				.waitFor()
		} catch (e: Exception) {
			throw IllegalStateException("Failed to execute pipenv! Do you have pipenv installed?", e)
		}
	}

	/**
	 * Train the model associated with this generator and save it.
	 *
	 * Must be called before loading it.
	 */
	fun train() {
		preparePythonEnvironment()

		// Copy the dataset to the temp directory as well
		val dataset = pythonDir.resolve("train.txt").apply {
			outputStream().use { out ->
				TextGenerator.javaClass.getResourceAsStream("/train.txt")!!
					.use { it.copyTo(out) }
			}
		}

		try {
			ProcessBuilder("pipenv", "run", "python3", "train.py")
				.directory(pythonDir)
				.redirectError(INHERIT)
				.redirectOutput(INHERIT)
				.redirectInput(dataset)
				.apply {
					environment()["MODEL_SAVEFILE"] = modelFile.absolutePath
					environment()["VOCAB_SAVEFILE"] = vocabFile.absolutePath
				}
				.start()
				.waitFor()
		} catch (e: Exception) {
			throw IllegalStateException("Failed to execute python in a virtual environment!", e)
		}
	}

	/**
	 * Load the model associated with this network and start a generator process.
	 *
	 * [GeneratorProcess.generate] can be used to generate texts.
	 *
	 * This allocates a system resource.
	 */
	fun load() = run {
		preparePythonEnvironment()

		if (!vocabFile.exists()) {
			error("Model files do not exist!")
		}

		GeneratorProcess().also { it.start() }
	}

	class GeneratorProcess : Closeable {
		var started = false
			private set
		private val builder = ProcessBuilder("pipenv", "run", "python3", "generate.py")
			.redirectError(INHERIT)
			.directory(pythonDir)
			.apply {
				environment()["MODEL_SAVEFILE"] = modelFile.absolutePath
				environment()["VOCAB_SAVEFILE"] = vocabFile.absolutePath
			}

		/** Initialised when start() is called. */
		lateinit var process: Process
			private set

		fun start() {
			if (started) return
			started = true

			process = builder.start()
		}

		/**
		 * Generates a pair of (message, time in seconds).
		 *
		 * [start] must be called first.
		 */
		fun generate(): Pair<String, Double> {
			process.outputStream.write('\n'.code)
			process.outputStream.flush()

			process.inputStream.bufferedReader().apply {
				val output = readLine()
				val time = readLine().removeSuffix("s").toDouble()
				//readLine() // empty line

				return output to time
			}
		}

		override fun close() {
			if (started) process.destroy()
		}
	}
}
