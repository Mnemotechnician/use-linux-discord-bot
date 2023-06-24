package com.github.mnemotechnician.messagegen

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.lang.IllegalStateException
import java.lang.ProcessBuilder.Redirect.*
import kotlin.random.Random

/**
 * A kotlin wrapper for an AI text generator in Python.
 */
object TextGenerator {
	/** The directory in which context pipenv is invoked. */
	val rootPipDir = File("${System.getProperty("user.home")}/use-linux/deepl/")
	/** The root directory of this deep learning subproject. */
	val workDir = rootPipDir.resolve("message-generator").also(File::mkdirs)
	/** The directory in which this subproject stores temporary work files. */
	val pythonDir = workDir.resolve("tmp").also(File::mkdirs)

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

	val modelExists: Boolean get() = modelFileIndex.exists() && vocabFile.exists()

	/*
	 * These phrases are used to teach the network to write coherent sentences.
	 */
	val learningPhraseLines by lazy {
		getResource("train-learning.txt")!!.bufferedReader().use {
			it.lines()
				.filter { it.isNotBlank() }
				.filter { !it.startsWith("##") }
				.toList()
		}
	}

	/*
	 * These phrases are used to train the network to generate advertisements
	 * They all begin with a starting phrase and end with the message terminator
	 */
	val advertisementPhraseLines by lazy {
		getResource("train-main.txt")!!.use {
			it.bufferedReader().use { it.lines().toList() }
		}
	}

	/** Copies the python files, if neccesary. */
	private fun copyPythonFiles() {
		if (filesCopied) return
		filesCopied = true

		// Copy the files
		val resourcesToCopy = getResource("python-filepaths.txt")!!
			.bufferedReader().use { it.readText().split("\n") }

		resourcesToCopy.forEach { resource ->
			println("Copying $resource")
			copyResource(resource, pythonDir.resolve(resource.substringAfterLast("/")))
		}
	}


	fun preparePythonEnvironment() {
		copyPythonFiles()

		// Copy the dependency list
		listOf("Pipfile", "Pipfile.lock").forEach {
			copyResource("/$it", rootPipDir.resolve(it))
		}

		try {
			ProcessBuilder("pipenv", "sync")
				.directory(rootPipDir)
				.redirectOutput(INHERIT)
				.start()
				.waitFor()
		} catch (e: Exception) {
			throw IllegalStateException("Failed to execute pipenv! Do you have pipenv installed?", e)
		}
	}

	fun train(continueTraining: Boolean, superEpochs: Int = 1) {
		require(!continueTraining || modelExists) {
			"Cannot continue the training: no saved state detected."
		}

		preparePythonEnvironment()

		val pretrainedEmbeddingFile = pythonDir.resolve("pretrained-embedding.txt")
		copyResource("/message-generator-dl/char-embeddings.txt", pretrainedEmbeddingFile)

		repeat(superEpochs) {
			// Even if the user wants to start a new training process,
			// The state still has to be restored during the consecutive trainings.
			val shouldRestoreState = it != 0 || continueTraining

			// Copy the datasets to the temp directory as well, formatting them properly
			println("Preparing the dataset...")

			val adverts =
				advertisementPhraseLines.map { startingPhrases.random() + it.trim() + MESSAGE_TERMINATOR }
			val learningPhrases = learningPhraseLines
				.map {
					// A third of the lines are postfixed with a message terminator
					// The rest are postfixed with a space.
					if (Random.nextInt(0, 3) == 0) {
						it + MESSAGE_TERMINATOR
					} else {
						"$it "
					}
				}

			val dataset = pythonDir.resolve("train.txt").apply {
				PrintWriter(outputStream().bufferedWriter()).use { out ->
					(learningPhrases + adverts)
						.filter { it.isNotBlank() }
						.sortedBy { it.length } // To optimize the lengths
						.windowed(BATCH_SIZE, BATCH_SIZE, true)
						.flatMap {
							// Within each batch, all the lines must have an equal length
							val padLength = it.maxOf { it.codePointCount(0, it.length) }
							it.map { line ->
								// Manual padding because String.padX counts unicode code points
								line + String(CharArray(
									padLength - line.codePointCount(0, line.length)
								) { MASK_TOKEN })
							}
						}
						.forEach(out::println)
				}
			}

			val process = runPython("train.py") {
				redirectInput(dataset)
				environment()["PRETRAINED_EMBEDDING"] = pretrainedEmbeddingFile.absolutePath
				if (shouldRestoreState) environment()["RESTORE_STATE"] = "1"
			}
			process.waitFor()

			require(process.exitValue() == 0) {
				"Trainer process terminated with non-zero exit code: ${process.exitValue()}"
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

		if (!modelExists) {
			error("Model files do not exist!")
		}

		GeneratorProcess().also { it.start() }
	}

	/**
	 * Executes a python file in the current pipenv environment, providing the MODEL_SAVEFILE and VOCAB_SAVEFILE
	 * environment variables.
	 * [preparePythonEnvironment] must be called first.
	 *
	 * @param filename The python file to execute. Must be in [pythonDir].
	 */
	inline fun runPython(
		filename: String,
		inheritErrorStream: Boolean = true,
		builder: ProcessBuilder.() -> Unit = {}
	): Process = ProcessBuilder("pipenv", "run", "python3", pythonDir.resolve(filename).canonicalPath)
		.directory(rootPipDir)
		.redirectError(if (inheritErrorStream) INHERIT else PIPE)
		.redirectOutput(INHERIT)
		.redirectInput(INHERIT)
		.apply {
			environment()["MODEL_SAVEFILE"] = modelFileLocation.absolutePath
			environment()["VOCAB_SAVEFILE"] = vocabFile.absolutePath
		}
		.apply(builder)
		.start()

	/**
	 * Safely copies a resource present in the jar file to the destination file.
	 *
	 * @throws IllegalStateException if the resource doesn't exist.
	 */
	fun copyResource(resourceName: String, destination: File) {
		val resource = javaClass.getResourceAsStream(resourceName)

		require(resource != null) { "Resource $resourceName not found" }

		destination.outputStream().use { out ->
			resource.use { it.copyTo(out) }
		}
	}

	fun getResource(name: String) =
		javaClass.getResourceAsStream("/message-generator-dl/$name")

	class GeneratorProcess : Closeable {
		var started = false
			private set
		private val logFile = File.createTempFile("generator-process", "log")

		/** Initialised when start() is called. */
		lateinit var process: Process
			private set

		fun start() {
			if (started) return

			process = runPython("generate.py") {
				redirectInput(PIPE)
				redirectOutput(PIPE)
				redirectError(logFile)
			}

			started = true
		}

		/**
		 * Generates a pair of (message, time in seconds).
		 *
		 * If [startingPhrase] is not supplied, a random one will be chosen instead.
		 *
		 * If the network generates a phrase that's already in the dataset,
		 * it may be regenerated. This can repeat up to [maxRetries] times, after which
		 * the last generated phrase will be returned.
		 *
		 * [start] must be called first.
		 */
		fun generate(
			startingPhrase: String = startingPhrases.random(),
			maxRetries: Int = 0
		): Pair<String, Double> {
			try {
				var totalTime = 0.0
				var attempts = 0
				while (true) {
					if (!process.isAlive) throw RuntimeException("Generator stopped")

					process.outputStream.write(startingPhrase.toByteArray())
					process.outputStream.write('\n'.code)
					process.outputStream.flush()

					process.inputStream.bufferedReader().let {
						val output = it.readLine().trim()
						totalTime += it.readLine().removeSuffix("s").toDouble()
						it.readLine() // empty line

						if (attempts++ >= maxRetries || advertisementPhraseLines.none { it.equals(output, true) }) {
							return output to totalTime
						} else {
							attempts++
						}
					}
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
