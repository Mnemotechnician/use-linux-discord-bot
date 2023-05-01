package com.github.mnemotechnician.uselinux

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.*
import dev.kord.common.annotation.KordPreview
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.encodeToString
import java.io.File

class UseLinuxExtension : Extension() {
	override val name = "use-linux"

	val targetChats = mutableListOf<TextChannel>()
	var lastSentNotification = 0L
	val saveFile = File("${System.getProperty("user.home")}/use-linux.json")

	override suspend fun setup() {
		loadState()

		publicSlashCommand(::AddChannelArgs) {
			name = "add-channel"
			description = "Add a channel to the list of notified channels"

			action {
				val channel = arguments.target.fetchChannel()

				if (channel !is TextChannel) {
					respond {
						content = "Target must be a text channel"
					}
					return@action
				}

				targetChats.add(channel)
				saveState()

				respond { content = "Success."}
			}
		}

		publicSlashCommand {
			name = "force-send"
			description = "Sends a notification immediately."

			check {
				if (event.interaction.user.id.value != 502871063223336990UL) fail("No.")
			}

			action {
				lastSentNotification = 0L
				respond { content = "Will send a notification to ${targetChats.size} channels" }
			}
		}

		kord.launch {
			delay(1000L)

			// Send a notfication in every channel every 4 hours
			while (true) {
				if (System.currentTimeMillis() - lastSentNotification > 4 * 60 * 60 * 1000) {
					runCatching {
						targetChats.forEach {
							it.createMessage {
								embed { description = "Remember to use Linux!" }
							}
						}

						lastSentNotification = System.currentTimeMillis()
						println("Notification sent")
						saveState()
					}.onFailure {
						println("Error: $it")
					}
				}
				delay(1000L)
			}
		}
	}

	suspend fun loadState() {
		if (saveFile.exists()) runCatching {
			val state = saveFile.readText()
			val stateObj = Json.decodeFromString<State>(state)

			lastSentNotification = stateObj.lastSent
			targetChats.addAll(stateObj.channels.mapNotNull {
				kord.defaultSupplier.getChannelOrNull(it) as? TextChannel
			})
		}.onFailure {
			println("Failed to load state: $it")
			return
		}

		println("State loaded")
	}

	fun saveState() {
		saveFile.writeText(
			Json.encodeToString(
				State(
					lastSentNotification,
					targetChats.map { it.id }
				)
			)
		)

		println("State saved")
	}

	inner class AddChannelArgs : Arguments() {
		val target by channel {
			name = "target"
			description = "Channel to add to the list of notified channels"
		}
	}

	@Serializable
	data class State(val lastSent: Long, val channels: List<Snowflake>)
}
