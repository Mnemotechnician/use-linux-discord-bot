package com.github.mnemotechnician.uselinux.misc

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import kotlin.math.abs

fun Arguments.optionalFloatRange(builder: OptionalStringConverterBuilder.() -> Unit) = run {
	optionalString {
		validate {
			if (value == null) return@validate

			val parts = value!!.split("..")
			if (parts.size !in 1..2 || parts.map { it.toFloatOrNull() }.contains(null)) {
				fail("You must specify the range either as two numbers separated with two dots (`min..max`) or one number (equivalent to `-number..number`)")
			}
		}
		builder()
	}
}

fun String.parseFloatRange(): ClosedRange<Float> {
	val parts = split("..")

	return when {
		parts.size == 1 -> {
			val number = parts.first().toFloat()
			-abs(number)..abs(number)
		}
		parts.size == 2 -> {
			parts[0].toFloat()..parts[1].toFloat()
		}
		else -> throw IllegalArgumentException("Invalid range provided: $this")
	}
}
