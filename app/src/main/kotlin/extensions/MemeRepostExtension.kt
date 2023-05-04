package com.github.mnemotechnician.uselinux.extensions

import com.github.mnemotechnician.uselinux.misc.*
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class MemeRepostExtension : ULBotExtension() {
	val subscribers: MutableList<TextChannel> = Collections.synchronizedList(mutableListOf())
	val sentMemeLinks = ConcurrentLinkedQueue<String>()

	val subreddits = listOf("femboymemes")
	val saveFile = File("${System.getProperty("user.home")}/use-linux-meme-repost.json")

	var nextPostTimestamp = 0L
	val postInterval = 24 * 60 * 60 * 1000L

	override val name = "meme-repost"

	override suspend fun setup() {
		loadState()

		publicSlashCommand(::SubscriberCommandArgs) {
			name = "add-meme-subscriber"
			description = "Subscribe a channel to receive daily memes (warning: most if not all of them are femboy-related)"

			action {
				val channel = arguments.target.fetchChannel()

				when {
					channel !is TextChannel ->
						respond { content = "Target must be a text channel." }

					subscribers.any { it.id == channel.id } ->
						respond { content = "This channel is already subscribed." }

					!channel.canPost() ->
						respond { content = "I can not send messages there." }

					!channel.isModifiableBy(event.interaction.user.id) ->
						respond { content = "You do not have the permission to modify that channel." }

					else -> {
						subscribers.add(channel)
						saveState()
						respond { content = "Success. The next post will appear in that channel <t:${nextPostTimestamp / 1000}:R>." }
						log("New subscriber: ${channel.name} (${channel.id})")
					}
				}
			}
		}

		publicSlashCommand(::SubscriberCommandArgs) {
			name = "remove-meme-subscriber"
			description = "Unsubscribe a channel from receiving daily memes"

			action {
				val removed = subscribers.find { it.id == arguments.target.id } ?: run {
					respond { content = "This channel is not subscribed." }
					return@action
				}

				if (!removed.isModifiableBy(event.interaction.user.id)) {
					respond { content = "You do not have the permission to modify that channel." }
					return@action
				}

				subscribers.remove(removed)
				saveState()
				respond { content = "Success." }
				log("Channel unsubscribed: ${removed.name} (${removed.id})")
			}
		}

		publicSlashCommand {
			name = "list-meme-subscribers"
			description = "List all subscribed channels"

			ownerOnlyCheck()

			action {
				respond {
					var i = 1
					content = subscribers.joinToString("\n") {
						"${i++}. ${it.name} (${it.id})"
					}
				}
			}
		}

		kord.launch {
			while (true) {
				if (System.currentTimeMillis() > nextPostTimestamp) {
					nextPostTimestamp = (nextPostTimestamp + postInterval)
						.coerceAtLeast(System.currentTimeMillis() + postInterval - 60_000)

					postPicture()
					saveState()
				}
				delay(1000L)
			}
		}
	}

	suspend fun loadState() {
		if (saveFile.exists()) runCatching {
			val state = Json.decodeFromString<State>(saveFile.readText())

			nextPostTimestamp = state.nextPostTimestamp
			sentMemeLinks.addAll(state.sentMemeLinks)
			subscribers.addAll(state.subscribers.mapNotNull {
				kord.defaultSupplier.getChannelOrNull(it) as? TextChannel
			})
		}.onFailure {
			log("Failed to load state: $it")
		}.onSuccess {
			log("State loaded successfully. ${subscribers.size} subscribers.")
		}
	}

	fun saveState() {
		runCatching {
			saveFile.writeText(
				Json.encodeToString(
					State(
						nextPostTimestamp,
						sentMemeLinks.toList(),
						subscribers.map { it.id }
					)
				)
			)
		}.onFailure {
			log("Failed to save state: $it")
		}.onSuccess {
			log("State saved successfully.")
		}
	}

	/** Load and post a random picture in all subscribed channels and adds the picture to [sentMemeLinks]. */
	suspend fun postPicture() {
		val subreddit = subreddits.random()

		val (title, url) = runCatching {
			loadPicture(subreddit)
		}.getOrElse {
			log("Failed to load a random picture: $it")
			return
		}

		subscribers.forEach { subscriber ->
			runCatching {
				subscriber.createMessage("Title: [$title]($url)")
			}.onFailure {
				log("Failed to post a meme in #${subscriber.id}: $it")
			}
		}

		sentMemeLinks.add(url)

		log("Meme ($url) posted successfully in ${subscribers.size} channels.")
	}

	/** Loads a random picture from the subreddit and returns a pair of (title, url). May throw a network exception. */
	suspend fun loadPicture(subreddit: String): Pair<String, String> {
		kord.resources.httpClient.get("https://reddit.com/r/$subreddit/best.json?limit=30")
			.body<JsonObject>()["data"]!!
			.jsonObject["children"]!!
			.jsonArray
			.asSequence()
			.map { it.jsonObject["data"]!!.jsonObject }
			.filterNot {
				it["stickied"]!!.jsonPrimitive.boolean || it["over_18"]!!.jsonPrimitive.boolean
			}
			.mapNotNull {
				val title = it["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
				val url = it["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
				title to url
			}
			.filter { it.second.startsWith("https://i.redd.it") }
			.filterNot { it.second in sentMemeLinks }
			.firstOrNull()
			?.let { return it }

		// Should never be reached under normal circumstances.
		return "no post this time :(" to "N/A"
	}

	inner class SubscriberCommandArgs : Arguments() {
		val target by channel {
			name = "channel"
			description = "The channel to add to or remove from the list of subscribers."
		}
	}

	@Serializable
	data class State(
		val nextPostTimestamp: Long,
		val sentMemeLinks: List<String>,
		val subscribers: List<Snowflake>
	)
}
