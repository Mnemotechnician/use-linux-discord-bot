package com.github.mnemotechnician.messagegen

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.lang.IllegalStateException
import java.lang.ProcessBuilder.Redirect.*
import java.text.BreakIterator
import kotlin.random.Random
import kotlin.system.exitProcess

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

	val startingPhrases = listOf(
		"Advert: ",
		"Linux advertisement: ",
		"Linux news: ",
		"Reasons to use Linux: ",
		"Linux is good, because: ",
		"Here's why you should use Linux: "
	)

	// TODO: synchronize with common.py
	const val BATCH_SIZE = 40
	const val MESSAGE_TERMINATOR = '$'
	const val MASK_TOKEN = '\u0001'

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
	 * The model will be trained for the number of epochs specified in common.py [superEpochs] times.
	 *
	 * Must be called before loading it.
	 *
	 * @param superEpochs The number of repeats (training and saves) to perform before finishing.
	 *      Each superepoch shuffles the starting phrases in the dataset; default is 1.
	 */
	fun train(continueTraining: Boolean, superEpochs: Int = 1) {
		require(!continueTraining || (modelFileIndex.exists() && vocabFile.exists())) {
			"Cannot continue the training: no saved state detected."
		}

		preparePythonEnvironment()

		repeat(superEpochs) {
			// Even if the user wants to start a new training process,
			// The state still has to be restored for the consecutive trainings to be effective.
			val shouldRestoreState = it != 0 || continueTraining

			// Copy the datasets to the temp directory as well, formatting it properly
			println("Preparing the dataset...")
			val dataset = pythonDir.resolve("train.txt").apply {
				PrintWriter(outputStream().bufferedWriter()).use { out ->
					// These phrases are supposed to teach the network to write coherent sentences
					val learningPhrases = TextGenerator.javaClass.getResourceAsStream("/train-learning.txt")!!.bufferedReader().use {
						it.lines()
							.filter { it.isNotBlank() }
							.map {
								// A third of the lines are postfixed with a message terminator
								// The rest are postfixed with a space.
								if (Random.nextInt(0, 3) == 0) {
									it + MESSAGE_TERMINATOR
								} else {
									"$it "
								}
							}
							.toList()
					}
					// These phrases are used to train the network to generate advertisements
					// They all begin with a starting phrase and end with the message terminator
					val phraseLines = TextGenerator.javaClass.getResourceAsStream("/train-main.txt")!!.use {
						it.bufferedReader().lines()
							.map { startingPhrases.random() + it.trim() + MESSAGE_TERMINATOR }
							.toList()
					}

					(learningPhrases + phraseLines)
						.filter { it.isNotBlank() }
						.sortedBy { it.length } // To optimize the lengths
						.windowed(BATCH_SIZE, BATCH_SIZE, true)
						.flatMap {
							// Within each batch, all the lines must have an equal length
							val padLength = it.maxOf { it.codePointCount(0, it.length) }
							it.map { line ->
								// Manual padding because String.padX counts unicode code points
								line + String(CharArray(padLength - line.codePointCount(0, line.length)) { MASK_TOKEN })
							}
						}
						.forEach(out::println)
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
						if (shouldRestoreState) environment()["RESTORE_STATE"] = "1"
					}
					.start()
					.waitFor()
			} catch (e: Exception) {
				throw IllegalStateException("Failed to execute python in a virtual environment!", e)
			}
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
		 * If [startingPhrase] is not supplied, a random one will be choosen instead.
		 *
		 * [start] must be called first.
		 */
		fun generate(startingPhrase: String = startingPhrases.random()): Pair<String, Double> {
			try {
				if (!process.isAlive) throw RuntimeException("Generator stopped")

				process.outputStream.write(startingPhrase.toByteArray())
				process.outputStream.write('\n'.code)
				process.outputStream.flush()

				process.inputStream.bufferedReader().apply {
					val output = readLine()
					val time = readLine().removeSuffix("s").toDouble()
					readLine() // empty line

					return output to time
				}
			} catch (e: Throwable) {
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
