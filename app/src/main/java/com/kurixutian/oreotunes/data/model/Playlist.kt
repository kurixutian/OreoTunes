package com.kurixutian.oreotunes.data.model

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val coverUri: String? = null,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
