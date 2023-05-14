package com.github.mnemotechnician.messagegen

import java.awt.SystemColor.text

fun main() {
	println("Type (t)rain to train the network or (g)enerate to generate text.")
	print("> ")

	when (readln().firstOrNull()) {
		't' -> {
			println("Continue training? (y/n)")
			print("> ")

			val continueTraining = when (readln().trim().firstOrNull()?.lowercaseChar()) {
				'y' -> true
				'n' -> false
				else -> error("Type y or n.")
			}
			TextGenerator.train(continueTraining)
		}
		'g' -> {
			val process = TextGenerator.load()

			println("Type a phrase or press enter to generate a phrase.")
			println()
			println()

			var count = 0
			var phrase: String? = null
			while (true) {
				if (--count <= 0) {
					phrase = readln().takeIf { it.isNotBlank() }
					count = 5
				}

				val (text, time) = if (phrase != null) {
					process.generate(phrase)
				} else {
					process.generate()
				}

				println("Text: $text")
				println("Took $time seconds")
				println()
			}
		}
		else -> {
			println("Invalid command!")
			main()
		}
	}
}
