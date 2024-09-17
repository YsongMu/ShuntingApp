package com.example.shuntingapp.message

data class ReceiptMsg(
    val IsNew: Boolean,
    val DevID: Int,
    val LocomNum: Int,
    val PlanNo: Int,
    val BeidouLat: Double,
    val BeidouLog: Double
)

data class ExecutionMsg(
    val IsNew: Boolean,
    val DevID: Int,
    val LocomNum: Int,
    val LineNum: Int,
    val BeidouLat: Double,
    val BeidouLog: Double,
    val CurrentCutNum: Int
)