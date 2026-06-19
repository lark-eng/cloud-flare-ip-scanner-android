package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_ips")
data class SavedIpEntity(
    @PrimaryKey val ip: String,
    val latency: Long,
    val speed: Double,
    val port: Int,
    val timestamp: Long = System.currentTimeMillis()
)
