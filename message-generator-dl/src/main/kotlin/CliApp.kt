package com.github.mnemotechnician.messagegen

import com.github.mnemotechnician.messagegen.TextGenerator.DatasetPref.*
import java.io.*
import java.nio.charset.Charset
import kotlin.math.log10

fun main() {
	println("""
		Type:
		  't' to train the model
		  'g' to generate messages using the model
		-----------------------------------------------
	""".trimIndent())

	when (prompt { it.isNotBlank() }.firstOrNull()) {
		't' -> {
			println("Specify the number of epochs to train the neural network for.")
			val epochs = prompt { it.toIntOrNull() != null }.toInt()

			val datasets = mapOf("main" to MAIN_ONLY, "learning" to LEARNING_ONLY, "both" to BOTH)

			println("Choose which dataset to use (main, learning, both).")
			val dataset = prompt { it.lowercase() in datasets }.let { datasets[it.lowercase()]!! }

			println("Continue training? (y/n)")
			val continueTraining = prompt { it in arrayOf("y", "n") } == "y"

			if (continueTraining) {
				val checkpoint = promptForCheckpoint()
				TextGenerator.train(checkpoint, dataset, epochs)
			} else {
				TextGenerator.train(null, dataset, epochs)
			}
		}
		'g' -> {
			val process = TextGenerator.load(promptForCheckpoint())
			val textSplitRegex = "(.{1,100}(?!\\w)|.+)".toRegex()
			val blockChar = "+"

			println("Type a starting phrase (optional). Press enter to generate.")
			println()

			while (true) {
				val phrase = prompt("Phrase >") { true }

				val outputs = (1..5)
					.map { process.generate(phrase) }
					.map {
						val lines = textSplitRegex.findAll(it.first).map { it.value.trim() }.toList()
						val time = "%.3f sec.".format(it.second)
						lines to time
					}
					.filter { it.first.isNotEmpty() }

				// Output as a table
				val textColLength = outputs.maxOf {
					it.first.maxOf { it.length }
				}.coerceAtLeast(10)

				val timeColLength = outputs.maxOf { it.second.length }.coerceAtLeast(10)
				val totalLength = 2 + textColLength + 3 + timeColLength + 2
				val dividerLine = blockChar.toString().repeat(totalLength)

				val outputsWithHeader = listOf(listOf("Text".padStart(textColLength / 2)) to "Time taken".padStart(timeColLength / 2)) +
					outputs

				println(dividerLine)
				outputsWithHeader.forEach {
					print("$blockChar ")
					print(it.first.first().padEnd(textColLength))
					print(" $blockChar ")
					print(it.second.padEnd(timeColLength))
					println(" $blockChar")

					if (it.first.size > 1) it.first.drop(1).forEach { text ->
						print("$blockChar ")
						print(text.padEnd(textColLength))
						print(" $blockChar ")
						print(" ".repeat(timeColLength))
						println(" $blockChar")
					}

					println(dividerLine)
				}
			}
		}
		else -> {
			println("Invalid command!")
			main() // super-brain moment
		}
	}
}

private inline fun prompt(
	promptText: String = ">",
	validator: (String) -> Boolean = { it.isNotEmpty() }
): String {
	while (true) {
		print("$promptText ")
		val input = readln()

		when {
			validator(input) -> return input
			else -> println("Invalid input! Try again.")
		}
	}
}

private fun promptForCheckpoint(): File {
	val checkpoints = TextGenerator.getKnownCheckpoints()
	val padLength = log10(checkpoints.size.toFloat()).toInt()

	require(checkpoints.isNotEmpty()) {
		"There are no saved checkpoints in ${TextGenerator.checkpointDir.absolutePath}."
	}

	println("Write the number of the checkpoint you want to load:")
	checkpoints.forEachIndexed { i, file ->
		println("${i + 1}. ${file.name}")
	}

	val index = prompt("Checkpoint number") { it.toIntOrNull() in 1..checkpoints.size }
	return checkpoints[index.toInt() - 1]
}
