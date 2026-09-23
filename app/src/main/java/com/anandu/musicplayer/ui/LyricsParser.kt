package com.anandu.musicplayer.ui

data class LyricLine(val timestampMs: Long, val text: String)

object LyricsParser {
    fun parse(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d+):(\\d+\\.?\\d*)\\](.*)")
        
        lrcContent.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toDouble()
                val text = match.groupValues[3].trim()
                
                val timestampMs = (min * 60 * 1000) + (sec * 1000).toLong()
                lines.add(LyricLine(timestampMs, text))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
