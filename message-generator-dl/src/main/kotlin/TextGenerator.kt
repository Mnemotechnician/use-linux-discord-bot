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
	/** Not the actual model file; may not exist even after training. See [modelFileIndex]. */
	val modelFileLocation = workDir.resolve("model.ckpt")
	val modelFileIndex = modelFileLocation.resolveSibling("model.ckpt.index")
	val vocabFile = workDir.resolve("vocab.json")

	// TODO: synchronize with common.py
	const val SEQUENCE_SIZE = 120
	const val MESSAGE_TERMINATOR = '$'
	const val STARTING_TEXT = "Advert: "

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
	fun train(continueTraining: Boolean) {
		require(!continueTraining || (modelFileIndex.exists() && vocabFile.exists())) {
			"Cannot continue the training: no saved state detected."
		}

		preparePythonEnvironment()

		// Copy the dataset to the temp directory as well, formatting it properly
		val dataset = pythonDir.resolve("train.txt").apply {
			outputStream().use { out ->
				TextGenerator.javaClass.getResourceAsStream("/train.txt")!!.use {
					val text = it.bufferedReader().readText()
					val lines = text.lines()
						.filter { it.isNotBlank() }
						.map { STARTING_TEXT + it.trim() + MESSAGE_TERMINATOR }
						.map {
							// Each line must have a length that's divisible by 30
							val expectedLength = it.length + (SEQUENCE_SIZE - it.length % SEQUENCE_SIZE)
							it.padEnd(expectedLength, MESSAGE_TERMINATOR)
						}

					out.bufferedWriter().use {
						it.write(lines.joinToString("\n"))
					}
				}
			}
		}

		try {
			ProcessBuilder("pipenv", "run", "python3", "train.py")
				.directory(pythonDir)
				.redirectError(INHERIT)
				.redirectOutput(INHERIT)
				.redirectInput(dataset)
				.apply {
					environment()["MODEL_SAVEFILE"] = modelFileLocation.absolutePath
					environment()["VOCAB_SAVEFILE"] = vocabFile.absolutePath
					if (continueTraining) environment()["RESTORE_STATE"] = "1"
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
		private val logFile = File.createTempFile("generator-process", "log")
		private val builder = ProcessBuilder("pipenv", "run", "python3", "generate.py")
			.redirectError(logFile)
			.directory(pythonDir)
			.apply {
				environment()["MODEL_SAVEFILE"] = modelFileLocation.absolutePath
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
			try {
				process.outputStream.write('\n'.code)
				process.outputStream.flush()

				process.inputStream.bufferedReader().apply {
					val output = readLine()
					val time = readLine().removeSuffix("s").toDouble()
					readLine() // empty line

					return output to time
				}
			} catch (e: Exception) {
				System.err.apply {
					logFile.readText().let(::println)
					println()
				}
				val exitStatus = if (process.isAlive) "" else {
					"Process exit code: ${process.exitValue()}"
				}

				throw IllegalStateException("An error has occurred. The log is printed above. $exitStatus", e)
			}
		}

		override fun close() {
			if (started) process.destroy()
		}
	}
}
