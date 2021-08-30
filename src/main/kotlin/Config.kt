package michael.uno

import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.event.events.GroupMessageEvent
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.valueParameters

object Config : AutoSavePluginConfig("GlobalConfig") {
    @ValueDescription("抢牌，某人出完一张牌时，如果有完全相同（颜色、点数都相同）的牌，可以无视顺序直接出牌。")
    var cut by value(false)
    @ValueDescription("加牌累积，被+2（或+4）时可以打出任意加牌（或+4），将惩罚累积给下家。")
    var stack by value(false)
    @ValueDescription("出0拍桌，当有人打出0之后，所有人要在时间内拍一拍机器人，没拍的人罚2张牌。若所有人都拍了，则最晚的罚2张牌。")
    var touch by value(false)
    @ValueDescription("出0拍桌的时间限制，以秒为单位。")
    var touchTime by value(5)
    @ValueDescription("最后一张需要是数字牌，如果最后一张不是数字牌，则要补摸一张牌（不需要喊UNO）")
    var uno_number by value(false)
    @ValueDescription("初始手牌数")
    var initial by value(7)
}

object ConfigCommand : CompositeCommand(
    owner = PluginMain,
    primaryName = "UNO",
    description = "UNO配置命令",
) {
    private val members = Config::class.declaredMembers
    private suspend inline fun <reified T> set(contact: Contact, option: String, arg: T) {
        val field = members.find { it.name == option }
        if (field != null) {
            try {
                val mp = field as KMutableProperty1<Config, T>
                mp.set(Config, arg)
                contact.sendMessage("已将${option}设为$arg")
            } catch (e: java.lang.IllegalArgumentException) {
                contact.sendMessage("${option}不是${T::class.simpleName}型变量！")
            }
        } else {
            contact.sendMessage("不存在属性$option！")
        }
    }
    @SubCommand("禁用", "disable")
    @Description("禁用UNO选项")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.disable(option: String) {
        set(fromEvent.group, option, false)
    }
    @SubCommand("启用", "enable")
    @Description("启用UNO选项")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.enable(option: String) {
        set(fromEvent.group, option, true)
    }
    @SubCommand("设置", "set")
    @Description("设置UNO参数")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.setInt(option: String, arg: Int) {
        set(fromEvent.group, option, arg)
    }
    @SubCommand("当前设置", "settings")
    @Description("打印UNO设置")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.settings() {
        fromEvent.group.sendMessage(
            members.joinToString("\n") { field ->
                """
                ◆ ${field.name}：${field.call(Config).toString()}
                ${field.annotations.filterIsInstance<ValueDescription>().joinToString { it.value }}
                """.trimIndent()
            })
    }
}
