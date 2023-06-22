package com.github.mnemotechnician.messagerating.util

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

object EmojiConverter {
	val allEmojis by lazy {
		val stream = EmojiConverter.javaClass.getResourceAsStream("/message-rating-dl/emoji-db.csv")!!

		csvReader().open(stream) {
			this.readAllAsSequence()
				.map { (emoji, shortname) ->
					Emoji(emoji, shortname.replace(" ", "_"))
				}
				.toList()
		}
	}
	private val surrogateRegex = "[\uD83C-\uDBFF\uDC00-\uDFFF]+".toRegex()

	fun emojiForName(shortName: String): Emoji? {
		val name = shortName.removeSurrounding(":")

		return allEmojis.find { it.shortName == name }
	}

	fun getEmoji(emojiString: String) =
		allEmojis.find { it.emoji == emojiString }

	fun replaceAllEmoji(string: String): String {
		// This is a very costly operation, so first we check if the string actually contains any emojis
		if (surrogateRegex !in string) {
			return string
		}

		// Check every known emoji and replace all occurrences
		var result = string
		allEmojis.forEach {
			result = result.replace(it.emoji, it.text)
		}
		return result
	}
}
