package com.github.mnemotechnician.uselinux.extensions

import com.github.mnemotechnician.uselinux.misc.*
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.*
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
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

class UseLinuxExtension : ULBotExtension() {
	override val name = "use-linux"

	val targetChats = Collections.synchronizedList(mutableListOf<TargetChat>())
	val saveFile = File("${System.getProperty("user.home")}/use-linux.json")

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

		kord.launch {
			delay(1000L)

			// Send a notification in every channel every 4 hours
			while (true) {
				targetChats.forEach { chat ->
					if (chat.shouldSend()) {
						runCatching {
							chat.send()
						}.onFailure {
							log("Failed to send notification in ${chat.id}: $it")
							// Next attempt in no less than 5 minutes
							chat.nextNotification = System.currentTimeMillis() + 1000 * 60 * 5L
						}
						saveState()
					}
				}
				delay(1000L)
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
				"10 minutes" to 10L,
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

		suspend fun send() {
			val channel = getChannel()

			channel.createMessage {
				embed { description = "Remember to use Linux!" }
			}

			// This accounts for the possible delays (at most 5 minutes)
			nextNotification = (nextNotification + intervalMinutes * 60 * 1000)
				.coerceAtLeast(System.currentTimeMillis() + (intervalMinutes - 5) * 60 * 1000)
		}
	}
}
