package michael.uno

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info

object PluginMain: KotlinPlugin(
    JvmPluginDescription(
        id = "mirai.UNO",
        name = "mirai UNO插件",
        version = "0.1.1"
    ) {
        author("鄢振宇https://github.com/michael1015198808")
        info("mirai的UNO插件")
    }
) {
    override fun onEnable() {
        logger.info { "Plugin loaded" }
        //配置文件目录 "${dataFolder.absolutePath}/"
        val eventChannel = GlobalEventChannel.parentScope(this)
        val games = mutableMapOf<Long, Game>()
        val cards = Regex("""[红黄蓝绿RYLG][0-9转禁+SRP]|变色|\+4""")

        eventChannel.subscribeAlways<GroupMessageEvent>{
            if(message[1] is PlainText) {
                val game = games.getOrPut(group.id) { Game(group) }
                var msg = message[1].toString().trim().uppercase()
                if (game.waiting) {
                    when (msg) {
                        "上桌" -> game.join(sender)
                        "下桌" -> game.leave(sender)
                        "GO", "启动" -> game.start()
                    }
                } else {
                    if (cards.matches(msg)) {
                        game.play(sender, msg)
                    }
                }
            }
        }
    }
}
