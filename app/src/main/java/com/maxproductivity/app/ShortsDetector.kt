package com.maxproductivity.app

import android.view.accessibility.AccessibilityNodeInfo

object ShortsDetector {

    private val resourceIdSignatures = listOf(
        "reel_watch",
        "reel_container",
        "reel_pager",
        "reel_recycler",
        "reel_video",
        "shorts_player",
        "shorts_video",
        "shorts_reel",
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
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        
        // Explicitly ignore tabs, buttons, or navigation bars to prevent home-screen false positives
        if (viewId.contains("tab") || viewId.contains("navigation") || viewId.contains("bar") || viewId.contains("button")) {
            return false
        }

        return resourceIdSignatures.any { viewId.contains(it) }
    }
}
