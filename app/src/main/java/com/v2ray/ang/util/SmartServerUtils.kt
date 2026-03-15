package com.v2ray.ang.util

import com.v2ray.ang.handler.MmkvManager

object SmartServerUtils {
    private val RU_FLAGS = listOf("ru", "rus", "russia", "россия", "москва", "🇷🇺")
    
    // Карта эмодзи флагов
    private val flags = mapOf(
        "Germany" to "🇩🇪", "USA" to "🇺🇸", "Netherlands" to "🇳🇱",
        "Turkey" to "🇹🇷", "Russia" to "🇷🇺", "Estonia" to "🇪🇪", "Other" to "🌐"
    )

    fun isRussia(name: String?): Boolean = name?.lowercase()?.let { n -> RU_FLAGS.any { n.contains(it) } } ?: false

    fun getCountryName(remarks: String): String {
        return when {
            remarks.contains("DE", true) || remarks.contains("Germany", true) -> "Germany"
            remarks.contains("US", true) || remarks.contains("USA", true) -> "USA"
            remarks.contains("NL", true) || remarks.contains("Netherlands", true) -> "Netherlands"
            remarks.contains("TR", true) || remarks.contains("Turkey", true) -> "Turkey"
            remarks.contains("EE", true) || remarks.contains("Estonia", true) -> "Estonia"
            else -> "Other"
        }
    }

    fun getFlag(country: String): String = flags[country] ?: "🌐"

    // Модель для адаптера (заголовок или сервер)
    sealed class ListItem {
        data class Header(val country: String, val flag: String, var isExpanded: Boolean = false, val count: Int) : ListItem()
        data class Server(val guid: String, val country: String, val name: String) : ListItem()
    }
}