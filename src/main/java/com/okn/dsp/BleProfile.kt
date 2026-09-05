package com.okn.dsp
import java.util.UUID
object BleProfile {
    const val DEVICE_NAME = "GoloPineDSPAudioTurning"
    val UUIDS = listOf(
        UUID.fromString("40af0001-9479-43f6-ae95-c45fb2afb9d2"),
        UUID.fromString("40af0002-9479-43f6-ae95-c45fb2afb9d2"),
        UUID.fromString("40af0003-9479-43f6-ae95-c45fb2afb9d2")
    )
    val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
