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
    var lastCard = ""
    var cardIndex = 0
    var clockwise = true
    val deck = ( "红黄蓝绿".zip("0" + "123456789禁转".repeat(2)) { it1, it2 -> "$it1$it2" } +
        "红黄蓝绿".map { "$it+2" } +
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
        cardIndex = players.size * 7
        waiting = false
        group.sendMessage("游戏开始")
    }
    suspend fun next() {
        if (clockwise) {
            current = (current + 1) % players.size
        } else {
            current = (current + players.size - 1) % players.size
        }
    }
    suspend fun play_wild(sender: Member, card: String, color: String?) {
        if (players[current].member.id == sender.id) {
            if (card !in players[current].cards) {
                group.sendMessage("你没有对应的牌！")
                return
            }
            if (card == "变色" && lastCard.contains("+")) {
                group.sendMessage("出的牌不符合牌型")
                return
            }
            val builder = MessageChainBuilder()
            val old = current
            players[old].cards -= card
            builder += At(sender)
            builder += "打出$card，剩余${players[old].cards.size}张牌。\n"
            next()
            if (color != null) {
                builder += "颜色变为$color\n"
                lastCard = "${color}色"
            } else {
                builder += "未声明颜色！按红色处理\n"
                lastCard = "红色"
            }
            when (players[old].cards.size) {
                0 -> builder += listOf<MessageContent>(
                    At(players[current].member.id),
                    PlainText("获得胜利")
                )
                else -> builder += listOf<MessageContent>(
                    PlainText("轮到"),
                    At(players[current].member),
                    PlainText("出牌"),
                )
            }
            group.sendMessage(builder.build())
        }
    }
    suspend fun play_normal(sender: Member, card: String) {
        if (players[current].member.id == sender.id) {
            if (card !in players[current].cards) {
                group.sendMessage("你没有对应的牌！")
                return
            }
            if (lastCard != "" &&
                card[0] != lastCard[0] &&
                card[1] != lastCard[1] ) {
                group.sendMessage("出的牌不符合牌型")
                return
            }
            val builder = MessageChainBuilder()
            val old = current
            players[old].cards -= card
            lastCard = card
            builder += At(sender)
            builder += "打出$card，剩余${players[old].cards.size}张牌。\n"
            next()
            when (card.substring(1)) {
                "禁" -> {
                    builder += At(players[current].member.id)
                    builder += "被跳过\n"
                    next()
                }
                "转" -> {
                    clockwise = !clockwise
                    builder += "方向变为${if (clockwise) "顺" else "逆"}时针\n"
                    next()
                    next()
                }
                "+2" -> {
                    builder += At(players[current].member.id)
                    builder += "抽两张牌\n"
                    next()
                }
            }
            when (players[old].cards.size) {
                0 -> builder += listOf<MessageContent>(
                    At(players[current].member.id),
                    PlainText("获得胜利")
                )
                else -> builder += listOf<MessageContent>(
                    PlainText("轮到"),
                    At(players[current].member),
                    PlainText("出牌"),
                )
            }
            group.sendMessage(builder.build())
        }
    }
    suspend fun draw(sender: Member) {
        if (players[current].member.id == sender.id) {
            players[current].cards += deck[cardIndex]
            ++cardIndex
        }
    }
}