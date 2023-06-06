package com.github.mnemotechnician.messagegen

import java.awt.SystemColor.text

fun main() {
	println("""
		Type:
		  't' to train the model
		  'g' to generate messages using the model
		-----------------------------------------------
	""".trimIndent())

	when (prompt { it.isNotBlank() }.firstOrNull()) {
		't' -> {
			println("Continue training? (y/n)")
			val continueTraining = prompt { it in arrayOf("y", "n") } == "y"

			println("Specify the number of superepochs - training repeats. Default is 1.")
			val superEpochs = prompt { it.toIntOrNull() != null }.toInt()

			TextGenerator.train(continueTraining, superEpochs)
		}
		'g' -> {
			val process = TextGenerator.load()

			println("Type a phrase or press enter to generate a phrase.")
			println()

			var count = 0
			var phrase = ""
			while (true) {
				if (--count <= 0) {
					phrase = prompt { true }
					count = 5
				}

				val (text, time) = if (phrase.isNotEmpty()) {
					process.generate(phrase)
				} else {
					process.generate()
				}

				println("Text: $text")
				println("Took $time seconds")
				println()
			}
		}
//		'i' -> {
//			if (!TextGenerator.modelExists) {
//				println("You need to train the model to some extent first!")
//				return
//			}
//
//			TextGenerator.preparePythonEnvironment()
//			TextGenerator.runPython("train-interactive.py").waitFor()
//		}
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
		val input = readln().trim()

		if (validator(input)) {
			return input
		} else {
			println("Invalid input! Try again.")
		}
	}
}
