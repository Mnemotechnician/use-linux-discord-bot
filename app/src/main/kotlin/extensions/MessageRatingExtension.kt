package com.github.mnemotechnician.uselinux.extensions

import com.github.mnemotechnician.messagerating.MessageRating
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.GuildEmoji
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.gateway.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MessageRatingExtension : ULBotExtension() {
	override val name = "message-rating"

	lateinit var messageRatingProcess: MessageRating.RatingProcess
		private set
	val raterProcessLoaded get() = ::messageRatingProcess.isInitialized

	val acceptedGuilds = longArrayOf(
		732661261812367450,
		1102574807528259624,
		1115795127659663470
	).map { Snowflake(it) }
	val emojiCache = mutableMapOf<ULong, GuildEmoji>()

	@OptIn(PrivilegedIntent::class)
	override suspend fun setup() {
		intents += Intent.MessageContent

		kord.launch {
			withContext(Dispatchers.IO) {
				messageRatingProcess = MessageRating.load()
				log("Message rater loaded.")
			}
		}

		kord.events
			.filterIsInstance<MessageCreateEvent>()
			.filterNot { it.message.author?.isBot ?: true }
			.filter {
				// Currently only enabled in a selected few guilds
				it.guildId in acceptedGuilds
			}
			.filter { it.message.content.isNotBlank() }
			.onEach {
				val score = withContext(Dispatchers.IO) {
					messageRatingProcess.rate(it.message.content).first
				}

				log("Message ${it.message.id} rated with ${(score * 100).toInt()}%")

				val emoji = emojiForScore(score) ?: return@onEach
				try {
					it.message.addReaction(emoji)
				} catch (e: Exception) {
					log("Failed to add emoji $emoji to message ${it.message.id}")
				}
			}
			.launchIn(kord)
	}

	private suspend fun emojiForScore(score: Float) = when {
		// neutral
		score in -0.11..0.11 -> null
		// good
		score in -0.25..-0.11 -> guildEmoji(1122309286605885451U)
		score in -1.0..-0.25 -> guildEmoji(1122309339177287832U)
		// bad
		score in 0.11..0.2 -> guildEmoji(1122309372391985213U)
		score in 0.2..0.5 -> guildEmoji(1122309555100078193U)
		score in 0.5..1.0 -> guildEmoji(1122309648595296307U)

		else -> {
			log("Invalid score generated $score")
			guildEmoji(0U) // will fall back to the error emoji
		}
	}

	/** Gets an emoji from this bots guild. */
	private suspend fun guildEmoji(emojiId: ULong): GuildEmoji =
		emojiCache.getOrPut(emojiId) {
			try {
				kord.getGuildOrThrow(Snowflake(1108111954427523203))
					.getEmoji(Snowflake(emojiId))
			} catch (e: StackOverflowError) {
				// Should never happen, but I just want to log it if it does. I'll laugh.
				log("Bruh.")
				throw e
			} catch (e: Exception) {
				log("Failed to get emoji $emojiId")
				// Fallback emoji
				guildEmoji(1122309408005820436U)
			}
		}
}
