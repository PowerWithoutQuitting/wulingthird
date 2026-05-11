package com.open.wuling.data.mqtt

import com.open.wuling.data.model.VehicleStatus

/**
 * MQTT protobuf → VehicleStatus 映射
 *
 * 从 smali s0.1.smali k(Map) 方法逐字段反编译
 */
object MqttProtoMapper {

    fun apply(fields: Map<Int, String>, current: VehicleStatus): VehicleStatus {
        var s = current

        // field 2 → climateMode ("0"=off, "1"=cool, "2"=heat)
        fields[2]?.let { v ->
            s = s.copy(climateMode = when (v) {
                "1" -> "cool"; "2" -> "heat"; else -> "off"
            })
        }

        // field 3 → isLocked (areEqual(v,"0") → "0"=已锁)
        fields[3]?.let { v -> s = s.copy(isLocked = v == "0") }

        // field 7 → lowBeamLight (vs "1")
        fields[7]?.let { v -> s = s.copy(lowBeamLight = v == "1") }

        // field 8 → dipHeadLight (vs "1")
        fields[8]?.let { v -> s = s.copy(dipHeadLight = v == "1") }

        // fields 10-14, 16-19 → doors (9 fields from DoorStatus)
        var d = s.doors
        fields[10]?.let { d = d.copy(trunk = it == "1") }              // 0xa → trunk open
        fields[11]?.let { d = d.copy(frontLeftLocked = it == "1") }    // 0xb → frontLeftLocked
        fields[12]?.let { d = d.copy(frontRightLocked = it == "1") }   // 0xc → frontRightLocked
        fields[13]?.let { d = d.copy(rearLeftLocked = it == "1") }     // 0xd → rearLeftLocked
        fields[14]?.let { d = d.copy(rearRightLocked = it == "1") }    // 0xe → rearRightLocked
        fields[16]?.let { d = d.copy(frontLeft = it == "1") }          // 0x10 → frontLeft open
        fields[17]?.let { d = d.copy(frontRight = it == "1") }         // 0x11 → frontRight open
        fields[18]?.let { d = d.copy(rearLeft = it == "1") }           // 0x12 → rearLeft open
        fields[19]?.let { d = d.copy(rearRight = it == "1") }          // 0x13 → rearRight open
        s = s.copy(doors = d)

        // fields 20-23 → windows (vs "1")
        var w = s.windows
        fields[20]?.let { w = w.copy(frontLeft = it == "1") }
        fields[21]?.let { w = w.copy(frontRight = it == "1") }
        fields[22]?.let { w = w.copy(rearLeft = it == "1") }
        fields[23]?.let { w = w.copy(rearRight = it == "1") }
        s = s.copy(windows = w)

        // field 27 → keyStatus
        fields[27]?.let { v -> s = s.copy(keyStatus = v) }

        // field 75 → tirePressure (comma-separated: FL,FR,RL,RR)
        fields[75]?.let { v ->
            val parts = v.split(",", ";")
            if (parts.size >= 4) {
                s = s.copy(
                    tirePressureFL = parts[0].toDoubleOrNull() ?: s.tirePressureFL,
                    tirePressureFR = parts[1].toDoubleOrNull() ?: s.tirePressureFR,
                    tirePressureRL = parts[2].toDoubleOrNull() ?: s.tirePressureRL,
                    tirePressureRR = parts[3].toDoubleOrNull() ?: s.tirePressureRR
                )
            }
        }

        return s
    }
}
