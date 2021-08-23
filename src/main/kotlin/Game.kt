package michael.uno

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.event.events.NudgeEvent
import net.mamoe.mirai.event.nextEventOrNull
import net.mamoe.mirai.message.data.*
import okhttp3.internal.wait
import java.lang.Math.max
import java.lang.StringBuilder
import java.util.TimerTask
import java.util.Timer

val COLORS = "红黄蓝绿"
val RANKS = ("0" + "123456789禁转".repeat(2)).map { it.toString() } + listOf<String>("+2", "+2")

data class IdleCheckingTask (
    val game: Game,
): TimerTask() {
    override fun run() {
        runBlocking {
            while (true) {
                delay(30_000L)
                val currentPlayer = game.players[game.current].member
                val builder = MessageChainBuilder()
                builder += At(currentPlayer)
                builder += PlainText("超时，罚抽${max(game.stacking, 1)}张牌。\n")
                game.draw(currentPlayer, builder)
            }
        }
    }
}

data class Game(
    val group: Group
) {
    var stacking = 0 // 当前累积加牌数量，Config.stack为false时一定为0
    var plusFour = false // 累积加牌中是否有+4
    var timer = Timer()
    var waiting = true
    val players = mutableListOf<Player>()
    var current = 0
    var lastCard = ""
    var cardIndex = 0
    var clockwise = true
    val deck = ( COLORS.flatMap { it1 -> RANKS.map { it2 -> "$it1$it2"} } +
        MutableList(4) { "变色" } +
        MutableList(4) { "+4" }).toMutableList()

    suspend fun getPlayer(sender: Member): Player {
        return players.first { it.member.id == sender.id }
    }
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
            player.sendCards()
        }
        cardIndex = players.size * 7
        waiting = false
        group.sendMessage(messageChainOf(
            PlainText("游戏开始\n"),
            At(players[current].member.id),
            PlainText("先出牌"),
        ))
    }
    suspend fun prevPlayer(): Player {
        if (clockwise) {
            return players[(current + players.size - 1) % players.size]
        } else {
            return players[(current + 1) % players.size]
        }
    }
    suspend fun next() {
        if (clockwise) {
            current = (current + 1) % players.size
        } else {
            current = (current + players.size - 1) % players.size
        }
    }
    suspend fun playerInfo(builder: MessageChainBuilder, player: Player, uno: Boolean) {
        if (player.cards.size == 1 && ! player.cards[0][1].isDigit()) {
            builder += "最后一张牌不是数字牌，补摸1张"
            draw_cards(player)
        }
        when (player.cards.size) {
            0 -> {
                builder += listOf<MessageContent>(
                    At(player.member.id),
                    PlainText("获得胜利")
                )
                games.remove(group.id)
            }
            else -> builder += listOf<MessageContent>(
                PlainText("轮到"),
                At(players[current].member),
                PlainText("出牌"),
            )
        }
        if (player.cards.size == 1) {
            player.uno = uno
        }
    }
    suspend fun play_wild(sender: Member, card: String, color: String?, uno: Boolean) {
        if (Config.cut && plusFour && card == "+4") {
            val index = players.indexOfFirst { it.member.id == sender.id }
            if (card !in players[index].cards) {
                group.sendMessage("你没有对应的牌！")
                return
            }
            current = index
        }
        if (players[current].member.id == sender.id) {
            if (card !in players[current].cards) {
                group.sendMessage("你没有对应的牌！")
                return
            }
            if (card == "变色" && stacking != 0) {
                group.sendMessage("出的牌不符合牌型")
                return
            }
            val builder = MessageChainBuilder()
            val old = current
            players[old].cards -= card
            builder += At(sender)
            builder += "打出$card，剩余${players[old].cards.size}张牌。\n"
            next()
            if (card == "+4") {
                if (Config.stack) {
                    stacking += 4
                    plusFour = true
                    builder += "当前累积${stacking}张牌"
                } else {
                    draw_cards(players[current], 4)
                    builder += At(players[current].member.id)
                    builder += "抽四张牌\n"
                    next()
                }
            }
            if (color != null) {
                builder += "颜色变为$color\n"
                lastCard = "${color}色"
            } else {
                builder += "未声明颜色！按红色处理\n"
                lastCard = "红色"
            }
            playerInfo(builder, players[old], uno)
            players[old].sendCards()
            group.sendMessage(builder.build())
        }
    }
    suspend fun play_normal(sender: Member, card: String, uno: Boolean) {
        if (Config.cut && card == lastCard) {
            val index = players.indexOfFirst { it.member.id == sender.id }
            if (card !in players[index].cards) {
                group.sendMessage("你没有对应的牌！")
                return
            }
            current = index
        }
        if (players[current].member.id == sender.id) {
            if (card !in players[current].cards) {
                group.sendMessage("你没有对应的牌！")
                return
            }
            if (lastCard != "" &&
                card[0] != lastCard[0] &&
                card[1] != lastCard[1] ) {
                group.sendMessage("出的牌不符合牌型，上一次出的牌是$lastCard")
                return
            }
            if (stacking != 0) {
                if (card[1] != '+') {
                    group.sendMessage("出的牌不符合牌型，当前正在被累加")
                    return
                }
                if (plusFour) {
                    group.sendMessage("出的牌不符合牌型，只能打+4")
                    return
                }
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
                    if (Config.stack) {
                        stacking += 2
                        builder += "当前累积${stacking}张牌"
                    } else {
                        builder += At(players[current].member.id)
                        draw_cards(players[current], 2)
                        builder += "抽两张牌\n"
                        next()
                    }
                }
            }
            playerInfo(builder, players[old], uno)
            players[old].sendCards()
            group.sendMessage(builder.build())
            if (Config.touch && card[1] == '0') {
                group.sendMessage("请在5s内戳一戳机器人！")
                var unnudgePlayers = players.map { it.member.id }.toMutableSet()
                var lastNudger: Long? = null
                nextEventOrNull<NudgeEvent>(5_000L) { event ->
                    if (unnudgePlayers.remove(sender.id)) {
                        lastNudger = sender.id
                    }
                    false // Keep listening
                }
                if (unnudgePlayers.size > 0) {
                    group.sendMessage(messageChainOf(
                        unnudgePlayers.map { At(it) }.toMessageChain(),
                        PlainText("未在2s内拍，罚抽2张牌")
                    ))
                } else {
                    group.sendMessage(messageChainOf(
                        At(lastNudger!!),
                        PlainText("最晚拍，罚抽2张牌")
                    ))
                    draw_cards(players.first { it.member.id == lastNudger }, 2)
                }
            }
        }
    }
    suspend fun draw_cards(player: Player, n: Int = 1) {
        val builder = StringBuilder("抽到了")
        repeat(n) {
            val card = deck[cardIndex++]
            player.cards += card
            builder.append("[$card]")
            if (cardIndex == deck.size) {
                deck.shuffle()
                cardIndex = 0
                group.sendMessage("重新洗牌！")
            }
        }
        player.sendCards(builder.toString() + "\n")
    }
    suspend fun draw(sender: Member, builder: MessageChainBuilder = MessageChainBuilder()) {
        if (players[current].member.id == sender.id) {
            draw_cards(players[current], max(stacking, 1))
            builder += At(players[current].member.id)
            builder += "抽${max(stacking, 1)}张牌，剩余${players[current].cards.size}张牌\n"
            stacking = 0
            plusFour = false
            next()
            playerInfo(builder, players[current], false)
            group.sendMessage(builder.build())
        }
    }
    suspend fun checkUNO(sender: Member) {
        val player = prevPlayer()
        if (player.member == sender) {
            // 自己喊UNO
            if (player.cards.size != 1) {
                group.sendMessage(messageChainOf(
                    At(sender),
                    PlainText("你的手牌数超过一张，不能UNO！\n罚抽2张牌")
                ))
                draw_cards(player, 2)
                return
            }
            if (player.uno == false) {
                player.uno = true
                group.sendMessage(messageChainOf(
                    At(sender),
                    PlainText("成功UNO！红色警报！")
                ))
            }
        } else {
            // 抓人
            if (player.cards.size != 1) {
                group.sendMessage(messageChainOf(
                    At(sender),
                    PlainText("被抓的人手牌数超过一张，不能UNO！\n罚抽2张牌")
                ))
                draw_cards(getPlayer(sender), 2)
                return
            }
            if (player.uno == false) {
                group.sendMessage(messageChainOf(
                    At(sender),
                    PlainText("抓人成功！\n"),
                    At(player.member.id),
                    PlainText("罚抽2张牌。")
                ))
                draw_cards(player, 2)
            }
        }
    }
}