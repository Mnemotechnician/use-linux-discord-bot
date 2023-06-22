package com.github.mnemotechnician.messagerating

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.mnemotechnician.messagerating.util.EmojiConverter
import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.lang.IllegalStateException
import java.lang.ProcessBuilder.Redirect.*
import java.text.Normalizer
import kotlin.random.*

private val nonAsciiRegex = "[^\\p{ASCII}]".toRegex()

/**
 * A kotlin wrapper for an AI text generator in Python.
 */
object MessageRating {
	/** The directory in which context pipenv is invoked. */
	val rootPipDir = File("${System.getProperty("user.home")}/use-linux/deepl/")
	/** The root directory of this deep learning subproject. */
	val workDir = rootPipDir.resolve("message-rating").also(File::mkdirs)
	/** The directory in which this subproject stores temporary work files. */
	val pythonDir = workDir.resolve("tmp").also(File::mkdirs)

	/** Not the actual model file; may not exist even after training. See [modelFileIndex]. */
	val modelFileLocation = workDir.resolve("model.ckpt")
	val modelFileIndex = modelFileLocation.resolveSibling("model.ckpt.index")
	val vocabFile = workDir.resolve("vocab.json")

	const val BATCH_SIZE = 40
	const val MASK_TOKEN = '\u0001'

	private var filesCopied = false

	val modelExists: Boolean get() = modelFileIndex.exists() && vocabFile.exists()

	val csvReader = csvReader {
		charset = "UTF-8"
		quoteChar = '"'
		delimiter = '\t'
	}

	/*
	 * These phrases are used to teach the network to rate messages.
	 */
	val learningDataset by lazy {
		val lines = csvReader.readAll(getResource("train-main.csv")!!)
			.drop(1) // headers

		lines.map { (message, rating, isReply) ->
			LearningMessage(
				normalizeMessage(message),
				rating.removeSuffix("%").toFloat() / 100f,
				isReply.toBoolean()
			)
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

	fun train(continueTraining: Boolean, epochs: Int) {
		require(!continueTraining || modelExists) {
			"Cannot continue the training: no saved state detected."
		}

		preparePythonEnvironment()

		// Copy the datasets to the temp directory as well, formatting them properly
		println("Preparing the dataset...")

		val dataset = pythonDir.resolve("train.txt").apply {
			PrintWriter(outputStream().bufferedWriter()).use { out ->
				learningDataset
					.sortedBy { it.message.length }
					.mapNotNull {
						// Drop out 90% messages containing 0 as the target value
						if (it.rating == 0f && Random.nextInt(0..10) != 0) {
							null
						} else {
							it
						}
					}
					.windowed(BATCH_SIZE, BATCH_SIZE, true) { batch ->
						val padWidth = batch.maxOf { it.message.length + 10 }
						batch.map {
							it.message.padEnd(padWidth, MASK_TOKEN) to it.rating
						}
					}
					.flatten()
					.forEach {
						out.print(it.first)
						out.print('\t')
						out.print(it.second)
						out.print("\t\t")
					}
			}
		}

		runPython("train.py") {
			redirectInput(dataset)
			environment()["EPOCHS"] = epochs.toString()
			if (continueTraining) environment()["RESTORE_STATE"] = "1"
		}.waitFor()
	}

	/**
	 * Load the model associated with this network and start a generator process.
	 *
	 * [RatingProcess.rate] can be used to generate texts.
	 *
	 * This allocates a system resource.
	 */
	fun load() = run {
		preparePythonEnvironment()

		if (!modelExists) {
			error("Model files do not exist!")
		}

		RatingProcess().also { it.start() }
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
		javaClass.getResourceAsStream("/message-rating-dl/$name")

	fun normalizeMessage(message: String): String {
		return message
			.let { EmojiConverter.replaceAllEmoji(it) }
			.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
			.replace(nonAsciiRegex, "")
			.replace('\t', ' ')
			.trim()
			.lowercase()
	}

	class RatingProcess : Closeable {
		var started = false
			private set
		private val logFile = File.createTempFile("generator-process", "log")

		/** Initialised when start() is called. */
		lateinit var process: Process
			private set

		fun start() {
			if (started) return

			process = runPython("rate.py") {
				redirectInput(PIPE)
				redirectOutput(PIPE)
				redirectError(logFile)
			}

			started = true
		}

		/**
		 * Rates a message and returns a pair of (rating, execution_time).
		 *
		 * [start] must be called first.
		 */
		fun rate(message: String): Pair<Float, Double> {
			try {
				if (!process.isAlive) throw RuntimeException("Rating process stopped")

				process.outputStream.write(normalizeMessage(message).toByteArray())
				process.outputStream.write("\t\n".toByteArray())
				process.outputStream.flush()

				process.inputStream.bufferedReader().let {
					val output = it.readLine().trim().toFloat()
					val totalTime = it.readLine().removeSuffix("s").toDouble()
					it.readLine() // empty line

					return output to totalTime
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

	data class LearningMessage(
		val originalMessage: String,
		val rating: Float,
		val isReply: Boolean
	) {
		val message = when {
			!isReply -> originalMessage
			else -> "##$originalMessage"
		}
	}
}
