package com.example.ludo8

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform