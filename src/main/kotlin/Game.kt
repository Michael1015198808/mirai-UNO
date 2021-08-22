package michael.uno

import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.messageChainOf
import okhttp3.internal.wait

data class Game(
    val group: Group
) {
    var waiting = true
    val players = mutableListOf<Player>()
    var current = 0
    val deck = ( "红黄蓝绿".zip("0" + "123456789禁转+".repeat(2)) { it1, it2 -> it1.toString() + it2 } +
        MutableList(4) { "变色" } +
        MutableList(4) { "+4" }).toMutableList()

    suspend fun join(sender: Member) {
        players += Player(sender)
        group.sendMessage(messageChainOf(
            At(sender),
            PlainText("加入成功，目前有${players.size}人"),
            // players.map { At(it.member) }
        ))
    }
    suspend fun leave(sender: Member) {
        players -= Player(sender)
        group.sendMessage(messageChainOf(
            At(sender),
            PlainText("退出成功，目前有${players.size}人"),
        ))
    }
    suspend fun start() {
        deck.shuffle()
        players.mapIndexed {
            index, player ->
            player.cards = deck.subList(index * 7, index * 7 + 7).toMutableList()
            player.member.sendMessage(player.cards.joinToString("][", prefix = "[", postfix = "]"))
        }
        waiting = false
        group.sendMessage("游戏开始")
    }
    suspend fun play(sender: Member, card: String) {
        if (players[current].member.id == sender.id) {
            if (card in players[current].cards) {
                val old = current
                players[old].cards -= card
                current = (current + 1) % players.size
                when (players[old].cards.size) {
                    0 -> group.sendMessage(messageChainOf(
                        At(sender),
                        PlainText("""
                            打出$card，获得胜利！
                            """.trimIndent()),
                    ))
                    else -> group.sendMessage(messageChainOf(
                        At(sender),
                        PlainText("""
                            打出$card，剩余${players[old].cards.size}张牌。
                            接下来轮到
                            """.trimIndent()),
                        At(players[current].member),
                        PlainText("出牌"),
                    ))
                }
            } else {
                group.sendMessage("你没有对应的牌！")
            }
        }
    }
}