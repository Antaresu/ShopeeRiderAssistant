package com.rider.shopeeassistant.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.rider.shopeeassistant.data.DecisionVerdict
import com.rider.shopeeassistant.data.OrderExtractionResult
import com.rider.shopeeassistant.data.OrderRuleModel
import java.util.regex.Pattern

object OrderParser {

    private val WEIGHT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:kg|kilos|kilo|g)", Pattern.CASE_INSENSITIVE)
    private val DIMENSION_PATTERN = Pattern.compile("(\\d+)\\s*[xX*×]\\s*(\\d+)\\s*[xX*×]\\s*(\\d+)")
    private val FARE_PATTERN = Pattern.compile("[₱P]\\s*(\\d+(?:\\.\\d+)?)")

    private val ACCEPT_BUTTON_KEYWORDS = listOf(
        "tanggapin", "accept", "tanggapin ang order", "take order", "accept order"
    )

    /**
     * Checks if the "Availability" switch is ON or OFF in the Shopee Driver drawer/screen
     */
    fun detectAvailabilitySwitch(rootNode: AccessibilityNodeInfo): Boolean? {
        var isAvailable: Boolean? = null

        fun findAvailability(node: AccessibilityNodeInfo?) {
            if (node == null || isAvailable != null) return

            val text = node.text?.toString()?.trim() ?: ""
            if (text.equals("Availability", ignoreCase = true)) {
                // Check parent or adjacent sibling for a Switch / Checkable view
                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChild(i)
                        if (child != null) {
                            val className = child.className?.toString() ?: ""
                            if (child.isCheckable || className.contains("Switch") || className.contains("CheckBox") || className.contains("CompoundButton")) {
                                isAvailable = child.isChecked
                                return
                            }
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                findAvailability(node.getChild(i))
            }
        }

        findAvailability(rootNode)
        return isAvailable
    }

    /**
     * Tries to find and click the Availability toggle switch in the active screen.
     */
    fun clickAvailabilitySwitch(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        var clicked = false

        fun searchAndClick(node: AccessibilityNodeInfo?) {
            if (node == null || clicked) return
            val text = node.text?.toString()?.trim() ?: ""
            if (text.equals("Availability", ignoreCase = true)) {
                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChild(i)
                        if (child != null) {
                            val className = child.className?.toString() ?: ""
                            if (child.isClickable || child.isCheckable || className.contains("Switch") || className.contains("CompoundButton")) {
                                clicked = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                if (clicked) return
                            }
                        }
                    }
                }
            }
            for (i in 0 until node.childCount) {
                searchAndClick(node.getChild(i))
            }
        }

        searchAndClick(rootNode)
        return clicked
    }

    /**
     * Finds and clicks a node containing any of the given keywords (e.g. "Incentives", "Settings").
     */
    fun clickNodeWithText(rootNode: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (rootNode == null) return false
        var clicked = false

        fun searchAndClick(node: AccessibilityNodeInfo?) {
            if (node == null || clicked) return
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim().lowercase()
            for (kw in keywords) {
                if (text.contains(kw.lowercase())) {
                    var target: AccessibilityNodeInfo? = node
                    while (target != null && !target.isClickable) {
                        target = target.parent
                    }
                    if (target != null && target.isClickable) {
                        clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) return
                    }
                }
            }
            for (i in 0 until node.childCount) {
                searchAndClick(node.getChild(i))
            }
        }

        searchAndClick(rootNode)
        return clicked
    }

    /**
     * Recursively collects all text nodes and potential Accept buttons from the active screen tree.
     */
    fun scanScreenTree(
        rootNode: AccessibilityNodeInfo,
        ruleModel: OrderRuleModel
    ): OrderExtractionResult {
        val extractedTexts = mutableListOf<String>()
        var foundButtonNode: AccessibilityNodeInfo? = null
        val buttonBounds = Rect()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val nodeText = node.text?.toString()?.trim()
            val nodeDesc = node.contentDescription?.toString()?.trim()
            val textContent = if (!nodeText.isNullOrEmpty()) nodeText else nodeDesc

            if (!textContent.isNullOrEmpty()) {
                extractedTexts.add(textContent)

                val lower = textContent.lowercase()
                for (kw in ACCEPT_BUTTON_KEYWORDS) {
                    if (lower == kw || lower.contains(kw)) {
                        foundButtonNode = node
                        node.getBoundsInScreen(buttonBounds)
                        break
                    }
                }
            }

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (viewId.contains("btn_accept") || viewId.contains("btn_take") || viewId.contains("button_accept")) {
                foundButtonNode = node
                node.getBoundsInScreen(buttonBounds)
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)

        return evaluateExtractedData(extractedTexts, foundButtonNode, buttonBounds, ruleModel)
    }

    private fun evaluateExtractedData(
        texts: List<String>,
        buttonNode: AccessibilityNodeInfo?,
        buttonBounds: Rect,
        rules: OrderRuleModel
    ): OrderExtractionResult {
        var fare: Float? = null
        var weightKg: Float? = null
        var length: Int? = null
        var width: Int? = null
        var height: Int? = null
        var detectedDropoff = ""

        val combinedText = texts.joinToString(" | ")

        // 1. Extract Fare
        for (t in texts) {
            val matcher = FARE_PATTERN.matcher(t)
            if (matcher.find()) {
                fare = matcher.group(1)?.toFloatOrNull()
                break
            }
        }

        // 2. Extract Weight
        for (t in texts) {
            val matcher = WEIGHT_PATTERN.matcher(t)
            if (matcher.find()) {
                val value = matcher.group(1)?.toFloatOrNull() ?: continue
                weightKg = if (t.lowercase().contains(" g") && value > 50) {
                    value / 1000.0f
                } else {
                    value
                }
                break
            }
        }

        // 3. Extract Dimensions (LxWxH)
        for (t in texts) {
            val matcher = DIMENSION_PATTERN.matcher(t)
            if (matcher.find()) {
                length = matcher.group(1)?.toIntOrNull()
                width = matcher.group(2)?.toIntOrNull()
                height = matcher.group(3)?.toIntOrNull()
                break
            }
        }

        // 4. Extract Drop-off Address
        for (t in texts) {
            val lower = t.lowercase()
            if (lower.contains("tondo") || lower.contains("manila") || lower.contains("malate") ||
                lower.contains("ermita") || lower.contains("sampaloc") || lower.contains("paco") ||
                lower.contains("binondo") || lower.contains("sta. cruz") || lower.contains("santa cruz")) {
                detectedDropoff = t
                break
            }
        }

        val clickX = if (!buttonBounds.isEmpty) buttonBounds.centerX() else null
        val clickY = if (!buttonBounds.isEmpty) buttonBounds.centerY() else null

        var verdict = DecisionVerdict.IGNORED_NOT_AN_ORDER
        var reason = ""

        if (buttonNode != null) {
            val fullUpper = combinedText.uppercase()

            val isBanned = rules.bannedAreas.any { banned ->
                fullUpper.contains(banned.uppercase())
            }

            val isAllowedCity = fullUpper.contains(rules.allowedCity.uppercase())
            val isOversizedWeight = weightKg != null && weightKg > rules.maxWeightKg
            val isOversizedDimensions = (length != null && length > rules.maxLengthCm) ||
                    (width != null && width > rules.maxWidthCm) ||
                    (height != null && height > rules.maxHeightCm)

            when {
                isBanned -> {
                    verdict = DecisionVerdict.ZERO_CLICK_BANNED_AREA
                    reason = "Banned area: Tondo. Zero click. Hayaan mag-timeout."
                }
                isOversizedWeight -> {
                    verdict = DecisionVerdict.ZERO_CLICK_OVERSIZED
                    reason = "Masyadong mabigat: ${weightKg}kg. Lagpas sa limit ng Click 125. Zero click."
                }
                isOversizedDimensions -> {
                    reason = "Masyadong malaki: ${length}x${width}x${height} cm. Hindi kasya sa 32L bag. Zero click."
                    verdict = DecisionVerdict.ZERO_CLICK_OVERSIZED
                }
                isAllowedCity || detectedDropoff.isNotEmpty() -> {
                    verdict = DecisionVerdict.AUTO_ACCEPT
                    reason = "Pasok sa rules. Maliit at Manila area. Auto-accepting."
                }
                else -> {
                    verdict = DecisionVerdict.AUTO_ACCEPT
                    reason = "Pasok sa rules. Auto-accepting order."
                }
            }
        }

        return OrderExtractionResult(
            farePhp = fare,
            weightKg = weightKg,
            lengthCm = length,
            widthCm = width,
            heightCm = height,
            dropoffAddress = detectedDropoff,
            allExtractedTexts = texts,
            acceptButtonNode = buttonNode,
            buttonScreenBounds = buttonBounds,
            clickX = clickX,
            clickY = clickY,
            verdict = verdict,
            reasonText = reason
        )
    }
}
