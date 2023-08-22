package com.github.mnemotechnician.uselinux.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingStringChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.message.create.embed

class SuggestionMailExtension : ULBotExtension() {
	override val name = "suggestion-mail"

	val requiredAdvertWords = listOf("linux", "windows")

	override suspend fun setup() {
		ephemeralSlashCommand(::SuggestionArgs) {
			name = "suggest-phrase"
			description = "Suggest a phrase that the network will later be trained on."

			action {
				if (arguments.suggestion.length <= 20) {
					respond { content = "Too short." }
					return@action
				}
				if (arguments.kind == "advert" && requiredAdvertWords.none { arguments.suggestion.contains(it, true) }) {
					respond { content = "An advertisement must contain one of the following words: $requiredAdvertWords" }
					return@action
				}

				this@SuggestionMailExtension.kord.rest.channel.createMessage(Snowflake(1115690402079572038UL)) {
					embed {
						title = "Suggestion: ${arguments.kind}"
						description = arguments.suggestion
						author {
							name = event.interaction.user.tag
							icon = (event.interaction.user.avatar ?: event.interaction.user.defaultAvatar).url
						}
					}
				}

				respond { content = "Suggestion sent." }
			}
		}
	}

	inner class SuggestionArgs : Arguments() {
		val suggestion by string {
			name = "suggestion"
			description = "The suggestion to send. Must be longer than 20 chars."
		}
		val kind by defaultingStringChoice {
			name = "kind"
			description = "'advert' is an advertisement. 'phrase' is any training phrase. Default is 'advert'."
			choices += mapOf("advert" to "advert", "phrase" to "phrase")
			defaultValue = "advert"
		}
	}
}
