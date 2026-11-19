package com.rider.shopeeassistant.data

data class OrderRuleModel(
    val maxWeightKg: Float = 15.0f,
    val maxLengthCm: Int = 35,
    val maxWidthCm: Int = 30,
    val maxHeightCm: Int = 30,
    val allowedCity: String = "Manila",
    val bannedAreas: List<String> = listOf("Tondo", "Gagalangin", "Parola", "Isla Puting Bato"),
    val allowedBarangays: List<String> = listOf(
        "Malate", "Ermita", "Paco", "Sampaloc", "Binondo",
        "Santa Cruz", "San Andres", "Santa Ana", "Quiapo", "San Miguel", "Intramuros"
    ),
    val autoAcceptEnabled: Boolean = true,
    val zeroClickOnBanned: Boolean = true,
    val voiceAlertsEnabled: Boolean = true,
    val motorcycleModel: String = "Honda Click 125 V4",
    val thermalBagCapacityLiters: Int = 32,
    val kmPerLiter: Float = 48.0f // Honda Click 125 V4 fuel efficiency
)

enum class DecisionVerdict {
    AUTO_ACCEPT,
    ZERO_CLICK_BANNED_AREA,
    ZERO_CLICK_OVERSIZED,
    IGNORED_NOT_AN_ORDER
}
