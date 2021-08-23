package michael.uno

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.event.events.GroupMessageEvent
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.valueParameters

object Config : AutoSavePluginConfig("GlobalConfig") {
    @ValueDescription("计时器，5s内没有任何操作则自动判定超时")
    var timer by value(false)
    @ValueDescription("抢牌，某人出完一张牌时，如果有完全相同（颜色、点数都相同）的牌，可以无视顺序直接出牌。")
    var cut by value(false)
    @ValueDescription("加牌累积，被+2（或+4）时可以打出任意加牌（或+4），将惩罚累积给下家。")
    var stack by value(false)
}

object ConfigCommand : CompositeCommand(
    owner = PluginMain,
    primaryName = "UNO",
    description = "UNO配置命令",
) {
    private val members = Config::class.declaredMembers
    /*
    @SubCommand("设置", "set")
    @Description("设置UNO选项")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.set(option: String, arg: Int) {
        fromEvent.group.sendMessage("option: $option, arg: $arg")
    }
    */
    @SubCommand("设置", "set")
    @Description("设置UNO参数")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.set(option: String, on: Boolean) {
        val field = members.find { it.name == option }
        if (field != null) {
            val mp = field as KMutableProperty1<Config, Boolean>
            mp.set(Config, on)
        } else {
            fromEvent.group.sendMessage("不存在属性$option！")
        }
    }
    @SubCommand("当前设置", "settings")
    @Description("打印UNO设置")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.settings() {
        fromEvent.group.sendMessage(
            members.joinToString("\n") { field ->
                val mp = field as KProperty<Boolean>
                """
                ${field.name}：${mp.call(Config)}
                    ${field.annotations.filterIsInstance<ValueDescription>().joinToString { it.value }}
                """.trimIndent()
            })
    }
}
