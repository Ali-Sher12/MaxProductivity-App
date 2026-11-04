package com.maxproductivity.app.blocking

import android.content.Context

class AppSuspensionManager(
    private var strategy: BlockStrategy,
) {

    fun setStrategy(strategy: BlockStrategy) {
        this.strategy = strategy
    }

    fun blockPackage(context: Context, packageName: String, message: String, backgroundUri: String) {
        strategy.blockPackage(context, packageName, message, backgroundUri)
    }

    fun updateSuspendedPackages(context: Context, packages: Set<String>, suspend: Boolean) {
        strategy.updateSuspendedPackages(context, packages, suspend)
    }
}
