package com.example.msdksample.kami.model

enum class RoutePriority(val displayName: String) {
    NORMAL("普通"),
    HIGH("高优先级"),
    URGENT("紧急");

    companion object {
        fun fromStorage(value: String?): RoutePriority {
            return entries.firstOrNull { it.name == value } ?: NORMAL
        }

        fun fromDisplayName(value: String?): RoutePriority {
            return entries.firstOrNull { it.displayName == value } ?: NORMAL
        }
    }
}
