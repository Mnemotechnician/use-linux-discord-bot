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

			println("Press enter to generate 5 entries")
			println()
			println()

			var count = 0
			while (true) {
				val (text, time) = process.generate()

				println("Text: $text")
				println("Took $time seconds")
				println()

				if (--count <= 0) {
					readln()
					count = 5
				}
			}
		}
		else -> {
			println("Invalid command!")
			main()
		}
	}
}
