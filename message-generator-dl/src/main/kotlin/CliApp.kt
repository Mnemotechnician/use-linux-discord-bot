package com.github.mnemotechnician.messagegen

import com.github.mnemotechnician.messagegen.TextGenerator.DatasetPref
import com.github.mnemotechnician.messagegen.TextGenerator.DatasetPref.*
import org.w3c.dom.Text
import java.awt.SystemColor.text
import java.io.File
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

			println("Type a starting phrase (optional). Press enter to generate.")
			println()

			var count = 0
			var phrase = ""
			while (true) {
				if (--count <= 0) {
					phrase = prompt { true }
					count = 5
				}

				val (text, time) = process.generate(phrase)

				println("Text: $text")
				println("Took $time seconds")
				println()
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
