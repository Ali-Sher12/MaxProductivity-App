package com.maxproductivity.app.blocking

enum class BlockingStrategy {
    ACCESSIBILITY,
    DEVICE_OWNER;

    companion object {
        fun fromName(name: String?): BlockingStrategy =
            entries.find { it.name == name } ?: ACCESSIBILITY
    }
}

interface BlockStrategy {
    fun blockPackage(context: android.content.Context, packageName: String, message: String)
    fun updateSuspendedPackages(context: android.content.Context, packages: Set<String>, suspend: Boolean)
}
