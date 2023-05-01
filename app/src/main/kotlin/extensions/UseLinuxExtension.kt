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
	val saveFile = File("${env("USER_HOME")}/use-linux.json")

	override suspend fun setup() {
		loadState()

		publicSlashCommand(::AddChannelArgs) {
			name = "add-channel"
			description = "Add a channel to the list of notified channels"

			action {
				if (arguments.target !is TextChannel) {
					respond {
						content = "Target must be a text channel"
					}
					return@action
				}

				targetChats.add(arguments.target as TextChannel)
			}
		}

		kord.launch {
			delay(10000L)

			// Send a notfication in every channel every 4 hours
			while (true) {
				if (System.currentTimeMillis() - lastSentNotification < 4 * 60 * 60 * 1000) {
					runCatching {
						targetChats.forEach {
							it.createMessage {
								embed { description = "Remember to use Linux!" }
							}
						}
						lastSentNotification = System.currentTimeMillis()
						println("Notification sent")
					}.onFailure {
						println("Error: $it")
					}
				}
				delay(1000L)
			}
		}

		saveState()
	}

	suspend fun loadState() {
		if (saveFile.exists()) {
			val state = saveFile.readText()
			val stateObj = Json.decodeFromString<State>(state)

			lastSentNotification = stateObj.lastSent
			targetChats.addAll(stateObj.channels.mapNotNull {
				kord.defaultSupplier.getChannelOrNull(it) as? TextChannel
			})
		}
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
