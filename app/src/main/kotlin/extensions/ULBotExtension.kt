package com.github.mnemotechnician.uselinux.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import kotlinx.datetime.*
import java.time.format.DateTimeFormatter


abstract class ULBotExtension : Extension() {
	val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

	fun log(message: String) {
		val now = Clock.System.now()
		val zoned = now.toLocalDateTime(TimeZone.currentSystemDefault())

		val formatted = formatter.format(zoned.toJavaLocalDateTime())

		print("\u001B[34m") // Blue text color
		print("[$name]")
		print("\u001B[32m") // Green text color
		print("[$formatted]")
		print("\u001B[0m ") // Reset text color
		println(message)
	}
}
