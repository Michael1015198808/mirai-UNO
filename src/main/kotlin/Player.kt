package michael.uno

import net.mamoe.mirai.contact.Member

data class Player(val member: Member) {
    var cards = mutableListOf<String>()
}