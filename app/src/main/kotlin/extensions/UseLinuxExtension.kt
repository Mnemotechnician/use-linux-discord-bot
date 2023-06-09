package com.github.mnemotechnician.uselinux.extensions

import com.github.mnemotechnician.messagegen.TextGenerator
import com.github.mnemotechnician.uselinux.misc.*
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.*
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.*
import com.kotlindiscord.kord.extensions.utils.*
import dev.kord.common.entity.*
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class UseLinuxExtension : ULBotExtension() {
	override val name = "use-linux"

	val targetChats = Collections.synchronizedList(mutableListOf<TargetChat>())
	val saveFile = File("${System.getProperty("user.home")}/use-linux/use-linux.json")

	lateinit var textGeneratorProcess: TextGenerator.GeneratorProcess
		private set
	val generatorProcessLoaded get() = ::textGeneratorProcess.isInitialized

	/** Maps channels to the moments /generate will be usable in them. */
	val channelTimeouts = HashMap<Snowflake, Long>()
	/** Maps users to the moments when they will be able to use /generate again. */
	val userTimeouts = HashMap<Snowflake, Long>()

	val timeoutUserSeconds = 10
	val timeoutChannelSeconds = 4

	init {
		saveFile.parentFile.mkdirs()
	}

	override suspend fun setup() {
		loadState()

		publicSlashCommand(::AddChannelArgs) {
			name = "add-channel"
			description = "Add a channel to the list of notified channels"

			action {
				val channel = arguments.target.fetchChannel()

				when {
					channel !is TextChannel ->
						respond { content = "Target must be a text channel." }

					targetChats.any { it.id == channel.id } ->
						respond { content = "This channel is already opted-in." }

					!channel.canPost() ->
						respond { content = "I can not send messages there." }

					!channel.isModifiableBy(event.interaction.user.id) ->
						respond { content = "You do not have the permission to modify that channel." }

					else -> {
						targetChats.add(TargetChat(channel.id, arguments.intervalMinutes))
						saveState()

						respond { content = "Success."}
						log("Added notification channel ${channel.name} (${channel.id}).")
					}
				}
			}
		}

		publicSlashCommand(::RemoveChannelArgs) {
			name = "remove-channel"
			description = "Remove a channel from the list of notified channels"

			action {
				val removed = targetChats.find { it.id == arguments.target.id } ?: run {
					respond { content = "This channel is not opted-in." }
					return@action
				}

				if (!removed.getChannel().isModifiableBy(event.interaction.user.id)) {
					respond { content = "You do not have the permission to modify that channel." }
					return@action
				}

				targetChats.remove(removed)
				respond { content = "Success." }
				log("Removed notification channel ${removed.getChannel().name} (${removed.id}).")
			}
		}

		publicSlashCommand {
			name = "channel-info"
			description = "View all notified channels and their intervals."

			ownerOnlyCheck()

			action {
				editingPaginator {
					val pageSize = 10

					targetChats.windowed(pageSize, pageSize, partialWindows = true).forEachIndexed { index, chats ->
						page {
							title = "Channels ${index * pageSize + 1}-${index * pageSize + pageSize}"

							chats.forEach {
								field {
									val timeLeft = it.nextNotification - System.currentTimeMillis()
									name = "#${it.getChannel().name}: ${it.id} (interval: ${it.intervalMinutes} minutes)"
									value = "${timeLeft / 1000 / 60} minutes left"
								}
							}
						}
					}
				}.send()
			}
		}

		publicSlashCommand(::GenerateArgs) {
			name = "generate"
			description = "Generate your very own message telling you to use Linux!"

			check {
				if (!generatorProcessLoaded) fail("The generator process has not started yet!")
			}
			check {
				val userTimeout = userTimeouts[event.interaction.user.id] ?: 0L
				if (System.currentTimeMillis() < userTimeout) {
					fail("You must wait $timeoutUserSeconds seconds before using this command again.")
				}
			}
			check {
				val channelTimeout = channelTimeouts[event.interaction.channelId] ?: 0L
				if (System.currentTimeMillis() < channelTimeout) {
					fail("You must wait $timeoutChannelSeconds seconds before using this command again in this channel.")
				}
			}
			check {
				arguments?.invoke()?.startingPhrase?.let {
					if (it.length > 128) fail("The starting phrase is too long.")
				}
			}

			action {
				userTimeouts[event.interaction.user.id] = System.currentTimeMillis() + timeoutUserSeconds * 1000
				channelTimeouts[event.interaction.channelId] = System.currentTimeMillis() + timeoutChannelSeconds * 1000

				val phrase = arguments.startingPhrase.trim().takeIf(String::isNotEmpty)
				val (text, time) = withContext(Dispatchers.IO) {
					if (phrase != null) {
						textGeneratorProcess.generate(phrase + " ")
					} else {
						textGeneratorProcess.generate()
					}
				}

				log("Generated a message for ${event.interaction.user.tag} in $time seconds.")

				respond {
					embed {
						description = text
						if (phrase != null) {
							description = "$phrase $description"
						}
					}
				}
			}
		}

		kord.launch {
			withContext(Dispatchers.IO) {
				textGeneratorProcess = TextGenerator.load()
				log("Text generator loaded, preparing to begin the broadcast.")
			}

			// Send a notification in every channel every 4 hours
			while (true) {
				// 20 sec interval to prevent people from overloading the bot.
				delay(20_000L)

				// Do not waste the precious processing power if there are no chats to send a notification in.
				if (targetChats.none {  it.shouldSend() }) continue

				val text = withContext(Dispatchers.IO) {
					val (text, timeTaken) = textGeneratorProcess.generate(maxRetries = 5)
					log("Generated a text in $timeTaken seconds")
					text
				}

				targetChats.forEach { chat ->
					if (chat.shouldSend()) {
						runCatching {
							chat.send(text)
						}.onFailure {
							log("Failed to send notification in ${chat.id}: $it")
							// Next attempt in no less than 30 minutes
							chat.nextNotification =
								System.currentTimeMillis() + 1000 * 60 * 30L
						}
						saveState()
					}
				}
			}
		}
	}

	fun loadState() {
		if (saveFile.exists()) runCatching {
			val state = saveFile.readText()
			val stateObj = Json.decodeFromString<State>(state)

			targetChats.addAll(stateObj.targetChats)
		}.onFailure {
			log("Failed to load the state: $it")
			return
		}

		log("State loaded successfully.")
	}

	fun saveState() {
		saveFile.writeText(
			Json.encodeToString(State(targetChats))
		)

		log("State saved")
	}

	inner class AddChannelArgs : Arguments() {
		val target by channel {
			name = "target"
			description = "Channel to add to the list of notified channels"
		}

		val intervalMinutes by defaultingNumberChoice {
			name = "interval"
			description = "Interval between notifications. Default is 4 hours."
			defaultValue = 240

			choices += mapOf(
				"30 minutes" to 30L,
				"1 hour" to 60L,
				"2 hours" to 120L,
				"3 hours" to 180L,
				"4 hours" to 240L,
				"8 hours" to 480L,
				"12 hours" to 720L,
				"1 day" to 60 * 24L,
				"1 week" to 60 * 24 * 7L,
				"1 month" to 60 * 24 * 30L,
				"1 year" to 60 * 24 * 365L,
				"1 decade" to 60 * 24 * 365 * 10L,
				"1 century" to 60 * 24 * 365 * 100L,
				"1 millennia" to 60 * 24 * 365 * 1000L,
				"1 million years" to 60 * 24 * 365 * 1_000_000L
			)
		}
	}

	inner class RemoveChannelArgs : Arguments() {
		val target by channel {
			name = "target"
			description = "Channel to remove from the list of notified channels"
		}
	}

	inner class GenerateArgs : Arguments() {
		val startingPhrase by coalescingDefaultingString {
			name = "starting-phrase"
			description = "The starting phrase. Chooses a random one if not specified."
			defaultValue = ""
		}
	}

	@Serializable
	data class State(val targetChats: List<TargetChat>)

	@Serializable
	data class TargetChat(val id: Snowflake, val intervalMinutes: Long) {
		@EncodeDefault
		var nextNotification = 0L
		@Transient
		var cachedChannel: TextChannel? = null
		val kord by getKoin().inject<Kord>()

		fun shouldSend() =
			System.currentTimeMillis() > nextNotification

		suspend fun getChannel(): TextChannel {
			if (cachedChannel != null) return cachedChannel!!

			val channel = kord.defaultSupplier.getChannelOrNull(id) as? TextChannel
				?: error("Channel not found: $id")
			cachedChannel = channel
			return channel
		}

		suspend fun send(text: String) {
			val channel = getChannel()

			channel.createMessage {
				embed { description = text }
			}

			// This accounts for the possible delays (at most 5 minutes)
			nextNotification = (nextNotification + intervalMinutes * 60 * 1000)
				.coerceAtLeast(System.currentTimeMillis() + (intervalMinutes - 5) * 60 * 1000)
		}
	}
}
