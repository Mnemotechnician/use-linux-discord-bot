package com.github.mnemotechnician.messagerating

fun main() {
	println("""
		Type:
		  't' to train the model
		  'g' to rate messages using the model
		-----------------------------------------------
	""".trimIndent())

	when (prompt { it.isNotBlank() }.firstOrNull()) {
		't' -> {
			println("Continue training? (y/n)")
			val continueTraining = prompt { it in arrayOf("y", "n") } == "y"

			println("Specify the number of epochs.")
			val epochs = prompt { it.toIntOrNull() != null }.toInt()

			MessageRating.train(continueTraining, epochs)
		}
		'g' -> {
			val process = MessageRating.load()

			while (true) {
				val message = prompt("Message >")
				val (result, time) = process.rate(message)
				val rating = (result * 1000).toInt() * 0.1f

				println("Rating: $rating%")
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
		val input = readln()

		when {
			validator(input) -> return input
			else -> println("Invalid input! Try again.")
		}
	}
}
