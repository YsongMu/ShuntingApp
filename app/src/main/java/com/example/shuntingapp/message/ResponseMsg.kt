package com.example.shuntingapp.message

open class ResponseMsg {
    data class HeartbeatMsg(
        val IsNew: Boolean,
        val DevID: Int,
        val PlanNo: Int,
        val Heading: String,
        val Speed: Int,
        val Receipt: String,
        val CurrentCutNum: Int,
        val TenFiveThreeCars: String,
        val IsNewWarning: Boolean,
        val ShortWarning: String,
        val LongWarning: String
    ) : ResponseMsg()

    data class SatelliteRTFMsg(
        val IsNew: Boolean,
        val DevID: Int,
        val LocomNum: Int,
        val RTKString: String
    ) : ResponseMsg()

    data class ShuntingMsg(
        val IsNew: Boolean,
        val DevID: Int,
        val Maker: String,
        val EditTime: String,
        val LocomNum: Int,
        val LocomNo: Int,
        val PlanNo: Int,
        val TrainNum: String,
        val PlanType: String,
        val Order: String,
        val PlanStTime: String,
        val PlanEndTime: String,
        val PlanCutNum: Int,
        val PlanCarNum: Int,
        val CurrentCutNum: Int,
        val Cuts: List<Cut>
    ) : ResponseMsg()

    data class Cut(
        val CutNum: Int,
        val PlanType: String,
        val LineName: String,
        val CarsNum: Int,
        val ResidueCarsNum: Int,
        val CarsWeight: Int,
        val CarsLength: Double,
        val TargetSpeed: Int,
        val NoteText: String
    )
}