package com.maxproductivity.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.maxproductivity.app.BlockOverlayActivity

class AccessibilityBlockStrategy(
    private val performHomeAction: () -> Unit,
) : BlockStrategy {

    override fun blockPackage(
        context: Context,
        packageName: String,
        message: String,
        backgroundUri: String,
    ) {
        context.startActivity(
            BlockOverlayActivity.createIntent(context, message, packageName, backgroundUri),
        )
    }

    override fun updateSuspendedPackages(context: Context, packages: Set<String>, suspend: Boolean) {
        // No-op: accessibility strategy blocks at runtime instead of suspending packages.
    }

    companion object {
        fun fromService(service: AccessibilityService): AccessibilityBlockStrategy =
            AccessibilityBlockStrategy { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }
}
