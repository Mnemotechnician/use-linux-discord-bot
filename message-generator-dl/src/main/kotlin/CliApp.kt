package com.github.mnemotechnician.messagegen

fun main() {
	println("Type (t)rain to train the network or (g)enerate to generate text.")
	print("> ")

	when (readln().firstOrNull()) {
		't' -> TextGenerator.train()
		'g' -> {
			val process = TextGenerator.load()

			while (true) {
				val (text, time) = process.generate()
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
