# Shopee Driver Assistant - Native Android App (Huawei Nova 5T Ready)

Isang tunay at kumpletong **Native Android Application** (Kotlin + Android Accessibility Service + Google Maps API) na partikular na idinisenyo para sa **Huawei Nova 5T** at **Honda Click 125 V4**.

---

## 🚀 Mga Pangunahing Kakayahan (Core Features)

### 1. Live Screen Reader & Coordinate-Accurate Auto-Click
* **Accessibility Service Engine (`ShopeeAccessibilityService.kt`):**
  * Binabasa ang screen nang live tuwing magbubukas ang Shopee Driver (`com.shopee.delivery.driver`).
  * Sinusuri ang teksto para sa:
    * **Timbang:** Halimbawa `2.5 kg` (Limit: Max 15kg para sa Click 125 V4).
    * **Sukat:** Halimbawa `20x15x12 cm` (Limit: 35x30x30 cm para sa 32L thermal bag).
    * **Destinasyon:** Manila City, Tondo, Malate, Ermita, Sampaloc, atbp.
    * **Rider Fare:** `₱85.00`
  * **Accurate Touch Target:**
    * Kinukuha ang exact screen rectangle gamit ang `node.getBoundsInScreen(rect)`.
    * Kinakalkula ang exact touch center: `(rect.centerX(), rect.centerY())`.
    * Tino-trigger ang auto-click gamit ang `performAction(ACTION_CLICK)` o `dispatchGesture()` sa mismong posisyon ng **"TANGGAPIN / ACCEPT"** button.

### 2. Zero-Click / Hands-Off Policy (Proteksyon sa Acceptance Rate)
* Kapag ang order ay papuntang **Tondo** (o ibang Banned Zone) o kaya ay **Oversized**:
  * **Literal na WALANG PIPINDUTIN ang application.**
  * Hindi iki-click ang Reject at hindi rin ang Accept.
  * Hahayaan lang mag-time out ang 15-second countdown ng Shopee Driver nang kusa upang hindi mabawasan ang manual rejection metrics mo.
  * Magsasalita ng maikling boses sa Tagalog: *"Banned area. Tondo. Zero click."* o *"Masyadong malaki para sa Click 125."*

### 3. Google Maps & Geofence Area Selector (`AreaSelectorActivity.kt`)
* Naka-konekta sa **Google Maps SDK** at **Google Geocoder**.
* May interactive visual map kung saan maaaring:
  * Maghanap ng lugar gamit ang search bar (hal. `Tondo`, `Malate`).
  * Pindutin ang mapa para maglagay ng **Green Zone (Allowed)** o **Red Zone (Banned / Zero-Click)**.
  * Awtomatikong ikinukumpara ang nababasang address sa Shopee Driver laban sa mga napiling Google Zones.

### 4. Floating HUD Scanner (`FloatingOverlayService.kt`)
* Lumulutang na maliit na pill / bubble sa ibabaw ng Shopee Driver app (`SYSTEM_ALERT_WINDOW`).
* Ipinapakita nang live:
  * Nababasang order specs: `₱85 • 2.5kg • Malate, Manila`
  * Target click coordinates: `🎯 Tap Target: [X: 540, Y: 2135]`
  * Live status banner: `PASOK (Auto-Click)` vs `TONDO (Zero-Click)`
  * Maaaring i-drag at ilipat kahit saan sa screen para hindi makaharang.

### 5. Suporta sa Huawei Nova 5T Notch & EMUI Battery
* **Punch-Hole Cutout:** Naka-integrate ang `WindowInsetsCompat` at `DisplayCutout` safe padding upang hinding-hindi matakpan ng camera hole sa upper-left ang mga buttons at teksto.
* **EMUI App Launch Fix:** May one-tap button (`Huawei Fix`) para buksan ang *Manage Manually* sa EMUI Battery settings upang hindi patayin ng phone ang service habang bumibiyahe ka.

### 6. Payout & Gas Calculator para sa Click 125 V4 (`EarningsTrackerActivity.kt`)
* Tugma sa mismong **Pay-Out Summary** ng Shopee Driver (Shipping Fee, S-Points Incentive Bonus, Tips).
* May built-in fuel calculator para sa **Honda Click 125 V4** batay sa consumption nito na **48 km / Liter** upang makita ang tunay na **Take-Home Net Profit**.

---

## 📂 Structure ng Source Code

```
ShopeeRiderAssistant/
├── app/
│   ├── build.gradle.kts                 # Dependencies: Maps, Location, Coroutines
│   └── src/main/
│       ├── AndroidManifest.xml          # Permissions, Services, Activities
│       ├── java/com/rider/shopeeassistant/
│       │   ├── data/
│       │   │   ├── OrderExtractionResult.kt # Model para sa exact coordinates at text
│       │   │   └── OrderRuleModel.kt        # Rules para sa Click 125, Tondo, at Manila
│       │   ├── service/
│       │   │   ├── ShopeeAccessibilityService.kt # Core screen reader & accurate clicker
│       │   │   └── FloatingOverlayService.kt     # Live floating HUD pill
│       │   ├── ui/
│       │   │   ├── MainActivity.kt               # Main dashboard na may Nova 5T notch insets
│       │   │   ├── AreaSelectorActivity.kt       # Google Maps place picker & geofence
│       │   │   └── EarningsTrackerActivity.kt   # Payout breakdown at Click 125 gas deduction
│       │   └── util/
│       │       ├── GooglePlacesHelper.kt         # Google Geocoder at distance checker
│       │       ├── HuaweiSettingsHelper.kt       # EMUI Battery & App Launch helper
│       │       └── OrderParser.kt                # High-speed regex parser para sa Shopee
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_area_selector.xml
│           │   ├── activity_earnings.xml
│           │   └── overlay_hud.xml
│           ├── xml/
│           │   └── accessibility_service_config.xml # Accessibility config flags
│           └── values/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🛠️ Paano I-compile at I-install sa Huawei Nova 5T

1. Buksan ang folder na `ShopeeRiderAssistant` sa **Android Studio**.
2. Ilagay ang iyong **Google Maps API Key** sa `app/build.gradle.kts`:
   ```kotlin
   manifestPlaceholders["MAPS_API_KEY"] = "ANG_IYONG_GOOGLE_MAPS_KEY"
   ```
3. Ikonekta ang iyong **Huawei Nova 5T** sa PC gamit ang USB Cable (i-enable ang *USB Debugging* sa *Developer Options*).
4. Pindutin ang **Run / Install** (o patakbuhin ang `./gradlew assembleDebug` para makuha ang `.apk` file).
5. Pagkabukas sa phone:
   * Pindutin ang **Accessibility** button at i-ON ang *Shopee Driver Assistant*.
   * Pindutin ang **Draw Overlay** at i-allow ang permission.
   * Pindutin ang **Huawei Fix** at i-set sa *Manage Manually* (I-ON lahat ng tatlong switch).
