package dev.nextterm.profiles

import dev.nextterm.transport.FlowControl
import dev.nextterm.transport.Parity
import dev.nextterm.transport.SerialConfig
import dev.nextterm.transport.TxNewline
import dev.nextterm.usb.VidPid
import org.json.JSONObject

/** A named, saved connection: which device plus its serial parameters. */
data class Profile(
    val id: String,
    val name: String,
    /** Last known /dev/bus/usb path. Changes whenever the adapter is re-plugged. */
    val devicePath: String?,
    val vidPid: VidPid,
    val config: SerialConfig,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("devicePath", devicePath)
        .put("vidPid", vidPid.key)
        .put("config", config.toJson())

    companion object {
        fun fromJson(o: JSONObject): Profile? = runCatching {
            Profile(
                id = o.getString("id"),
                name = o.getString("name"),
                devicePath = o.optString("devicePath").ifEmpty { null },
                vidPid = VidPid.parse(o.getString("vidPid"))!!,
                config = serialConfigFromJson(o.getJSONObject("config")),
            )
        }.getOrNull()
    }
}

fun SerialConfig.toJson(): JSONObject = JSONObject()
    .put("baud", baudRate)
    .put("dataBits", dataBits)
    .put("stopBits", stopBits)
    .put("parity", parity.name)
    .put("flow", flowControl.name)
    .put("txNewline", txNewline.name)
    .put("rxLfAsCrLf", rxLfAsCrLf)
    .put("portIndex", portIndex)

fun serialConfigFromJson(o: JSONObject): SerialConfig {
    val d = SerialConfig()
    return SerialConfig(
        baudRate = o.optInt("baud", d.baudRate),
        dataBits = o.optInt("dataBits", d.dataBits),
        stopBits = o.optInt("stopBits", d.stopBits),
        parity = runCatching { Parity.valueOf(o.getString("parity")) }.getOrDefault(d.parity),
        flowControl = runCatching { FlowControl.valueOf(o.getString("flow")) }.getOrDefault(d.flowControl),
        txNewline = runCatching { TxNewline.valueOf(o.getString("txNewline")) }.getOrDefault(d.txNewline),
        rxLfAsCrLf = o.optBoolean("rxLfAsCrLf", d.rxLfAsCrLf),
        portIndex = o.optInt("portIndex", 0),
    )
}
