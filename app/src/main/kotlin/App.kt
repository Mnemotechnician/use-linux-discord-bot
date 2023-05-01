package com.github.mnemotechnician.uselinux

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env

private val TOKEN = env("TOKEN")

suspend fun main() {
	val bot = ExtensibleBot(TOKEN) {
		extensions {
			add(::UseLinuxExtension)
		}
	}

	bot.start()
}
