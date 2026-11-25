package com.rider.shopeeassistant.data

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class OrderExtractionResult(
    val farePhp: Float? = null,
    val weightKg: Float? = null,
    val lengthCm: Int? = null,
    val widthCm: Int? = null,
    val heightCm: Int? = null,
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    val allExtractedTexts: List<String> = emptyList(),
    val acceptButtonNode: AccessibilityNodeInfo? = null,
    val buttonScreenBounds: Rect? = null,
    val clickX: Int? = null,
    val clickY: Int? = null,
    val verdict: DecisionVerdict = DecisionVerdict.IGNORED_NOT_AN_ORDER,
    val reasonText: String = "",
    val riderName: String? = null,
    val riderRating: String? = null,
    val riderCompletion: String? = null
)
