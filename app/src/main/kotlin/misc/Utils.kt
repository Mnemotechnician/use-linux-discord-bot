package com.github.mnemotechnician.uselinux.misc

import com.kotlindiscord.kord.extensions.commands.application.slash.SlashCommand
import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.kord.common.entity.*
import dev.kord.core.Kord
import dev.kord.core.entity.channel.TextChannel

val OWNER_ID = Snowflake(502871063223336990UL)

suspend fun TextChannel.canPost() = run {
	val selfId = getKoin().inject<Kord>().value.selfId
	val botPerms = getEffectivePermissions(selfId)

	Permission.SendMessages in botPerms || Permission.ViewChannel in botPerms
}

suspend fun TextChannel.isModifiableBy(user: Snowflake) = run {
	val userPerms = getEffectivePermissions(user)
	Permission.ManageChannels in userPerms || user == OWNER_ID // TODO: do I need this backdoor?
}

fun SlashCommand<*, *, *>.ownerOnlyCheck() {
	check {
		if (event.interaction.user.id != OWNER_ID) fail("Access Denied.")
	}
}
