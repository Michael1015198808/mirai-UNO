package michael.uno

import net.mamoe.mirai.contact.Member

data class Player(val member: Member) {
    var cards = mutableListOf<String>()
    var uno = false
    suspend fun sendCards (msg: String = "") {
        cards.sortBy {
            val color = COLORS.indexOf(it[0])
            if (color >= 0) {
                color * RANKS.size + RANKS.indexOf(it.substring(1))
            } else {
                when(it) {
                    "变色" -> 80
                    "+4" -> 81
                    else -> {
                        System.err.println("$it can't be matched!")
                        82
                    }
                }
            }
        }
        member.sendMessage(msg + cards.joinToString("][", prefix = "[", postfix = "]"))
    }
}