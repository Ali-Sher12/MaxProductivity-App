package com.maxproductivity.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import com.maxproductivity.app.BlockOverlayActivity

class AccessibilityBlockStrategy(
    private val performHomeAction: () -> Unit,
) : BlockStrategy {

    override fun blockPackage(context: Context, packageName: String, message: String) {
        performHomeAction()
        context.startActivity(BlockOverlayActivity.createIntent(context, message, packageName))
    }

    override fun updateSuspendedPackages(context: Context, packages: Set<String>, suspend: Boolean) {
        // No-op: accessibility strategy blocks at runtime instead of suspending packages.
    }

    companion object {
        fun fromService(service: AccessibilityService): AccessibilityBlockStrategy =
            AccessibilityBlockStrategy { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }
}
