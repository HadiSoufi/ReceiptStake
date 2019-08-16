package com.example.receiptstake

data class Spot(
    val id: Long = counter++,
    val name: String,
    val city: String,
    val text: String
) {
    companion object {
        private var counter = 0L
    }

    override fun toString(): String {
        return """$id${0x3.toChar()}$name${0x3.toChar()}$city${0x3.toChar()}$text"""
    }
}