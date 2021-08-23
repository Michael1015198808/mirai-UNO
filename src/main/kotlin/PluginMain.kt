package michael.uno

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info

val games = mutableMapOf<Long, Game>()

object PluginMain: KotlinPlugin(
    JvmPluginDescription(
        id = "mirai.UNO",
        name = "mirai UNO插件",
        version = "0.2.8"
    ) {
        author("鄢振宇https://github.com/michael1015198808")
        info("mirai的UNO插件")
    }
) {
    override fun onEnable() {
        ConfigCommand.register()
        Config.reload()
        logger.info { "Plugin loaded" }
        //配置文件目录 "${dataFolder.absolutePath}/"
        val eventChannel = GlobalEventChannel.parentScope(this)
        val UNO_POSTFIX = """\s*(UNO\s*)?"""
        val normal_cards = Regex("""[红黄蓝绿RYLG]([0-9转禁SRP]|\+2)$UNO_POSTFIX""")
        val wild_cards = Regex("""(变色|\+4)\s*[红黄蓝绿RYLG]?$UNO_POSTFIX""")
        val draw  = Regex("""PASS|不要|没有|抽牌|摸牌""")

        eventChannel.subscribeAlways<GroupMessageEvent>{
            if(message[1] is PlainText) {
                val game = games.getOrPut(group.id) { Game(group) }
                var msg = message[1].toString().trim().uppercase()
                if (msg != "UNO" && msg.startsWith("UNO")) {
                    msg = msg.substring(3)
                } else if (game.waiting) {
                    return@subscribeAlways
                }
                if (game.waiting) {
                    when (msg) {
                        "上桌" -> game.join(sender)
                        "下桌" -> game.leave(sender)
                        "GO", "启动" -> game.start()
                    }
                } else {
                    if (Config.timer) {
                        game.timer.purge()
                    }
                    if (normal_cards.matches(msg)) {
                        val l = msg.split(Regex("\\s"))
                        game.play_normal(sender, l[0], msg.endsWith("UNO"))
                    } else if (wild_cards.matches(msg)) {
                        val l = msg.split(Regex("\\s"))
                        game.play_wild(sender, l[0], l.getOrNull(1), msg.endsWith("UNO"))
                    } else if (draw.matches(msg)) {
                        game.draw(sender)
                    } else if (msg == "UNO") {
                        game.checkUNO(sender)
                    }
                    if (Config.timer) {
                        game.timer.schedule(IdleCheckingTask(game), 0)
                    }
                }
            }
        }
    }
}
