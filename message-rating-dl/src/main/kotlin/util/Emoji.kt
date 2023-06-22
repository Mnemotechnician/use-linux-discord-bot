package com.github.mnemotechnician.messagerating.util

data class Emoji(
	val emoji: String,
	val shortName: String
) {
	/** Short name enclosed in colons, e.g. `:smiling_face:` */
	val text = ":$shortName:"
}
