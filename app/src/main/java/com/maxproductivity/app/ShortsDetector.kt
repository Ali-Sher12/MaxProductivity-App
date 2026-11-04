package com.maxproductivity.app

import android.view.accessibility.AccessibilityNodeInfo

object ShortsDetector {

    val resourceIdSignatures = listOf(
        "reel_watch",
        "reel_container",
        "reel_pager",
        "reel_recycler",
        "reel_video",
        "reel_player",
        "shorts_player",
        "shorts_video",
        "shorts_reel",
        "shorts_container",
    )

    fun isShortsScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return walk(root)
    }

    private fun walk(node: AccessibilityNodeInfo): Boolean {
        if (matchesNode(node)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (walk(child)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun matchesNode(node: AccessibilityNodeInfo): Boolean {
        return matchesViewId(node.viewIdResourceName)
    }

    fun matchesViewId(viewId: String?): Boolean {
        val id = viewId?.lowercase().orEmpty()
        
        // Explicitly ignore tabs, buttons, or navigation bars to prevent home-screen false positives
        if (id.contains("tab") || id.contains("navigation") || id.contains("bar") || id.contains("button")) {
            return false
        }

        return resourceIdSignatures.any { id.contains(it) }
    }
}
