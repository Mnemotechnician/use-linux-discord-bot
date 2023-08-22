package com.github.mnemotechnician.uselinux.extensions

import com.github.mnemotechnician.uselinux.misc.*
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.annotation.*
import dev.kord.core.behavior.channel.createMessage
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import java.net.URL

class DebugExtension : ULBotExtension() {
	override val name = "debug"

	@OptIn(KordUnsafe::class, KordExperimental::class)
	override suspend fun setup() {
		val kord = kord

		ephemeralSlashCommand(::SendArgs) {
			name = "debug-send"
			description = "Toomfoolery."

			ownerOnlyCheck()
			ownerGuildOnly()

			action {
				kord.unsafe.messageChannel(arguments.channel).createMessage {
					content = arguments.content
					messageReference = arguments.reference

					arguments.attachment?.let {
						addFile(it.filename, ChannelProvider(it.size.toLong()) {
							URL(it.url).openStream().toByteReadChannel()
						})
					}
				}
				respond { content = "ok" }
			}
		}

		ephemeralSlashCommand(::RemoveArgs) {
			name = "debug-remove"
			description = "Toomfoolery."

			ownerOnlyCheck()
			ownerGuildOnly()

			action {
				kord.unsafe.message(arguments.channel, arguments.id).delete()

				respond { content = "ok" }
			}
		}
	}

	inner class SendArgs : Arguments() {
		val channel by snowflake {
			name = "channel"
			description = "Channel to send message in"
		}
		val content by string {
			name = "content"
			description = "Message content"
		}
		val reference by optionalSnowflake {
			name = "ref"
			description = "Message to reply to"
		}
		val attachment by optionalAttachment {
			name = "attachment"
			description = "Optional attachment"
		}
	}

	inner class RemoveArgs : Arguments() {
		val channel by snowflake {
			name = "channel"
			description = "Channel to remove message from"
		}
		val id by snowflake {
			name = "id"
			description = "Message id"
		}
	}
}
