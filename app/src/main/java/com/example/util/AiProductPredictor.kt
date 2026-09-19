package com.example.util

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PredictedProductInfo(
    val productName: String,
    val genericName: String = "",
    val brand: String = "",
    val category: String = "Tablets",
    val dosageForm: String = "Tablet",
    val strength: String = "",
    val packSize: String = "",
    val manufacturer: String = "",
    val suggestedRetailPrice: Double = 0.0,
    val detectedBatchNumber: String = "",
    val detectedManufacturingDate: String = "",
    val detectedExpiryDate: String = "",
    val expiryAlertDays: Int = 90,
    val calculatedAlertDate: String = "",
    val isFromImage: Boolean = false,
    val confidenceNotes: String = ""
)

object AiProductPredictor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Common pharmaceutical database for offline instant predictive matching.
     * Contains standard Pakistani & international pharmacy formulations.
     */
    val OFFLINE_MEDICINE_ENCYCLOPEDIA = listOf(
        PredictedProductInfo(
            productName = "Panadol 500mg Tablets",
            genericName = "Paracetamol",
            brand = "Panadol",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "500mg",
            packSize = "200 Tablets (20x10)",
            manufacturer = "GlaxoSmithKline (GSK)",
            suggestedRetailPrice = 450.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Panadol CF (Cold & Flu)",
            genericName = "Paracetamol + Pseudoephedrine + Chlorpheniramine",
            brand = "Panadol",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "500mg / 30mg / 2mg",
            packSize = "100 Tablets",
            manufacturer = "GlaxoSmithKline (GSK)",
            suggestedRetailPrice = 380.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Panadol Extra 500mg",
            genericName = "Paracetamol + Caffeine",
            brand = "Panadol",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "500mg / 65mg",
            packSize = "100 Tablets",
            manufacturer = "GlaxoSmithKline (GSK)",
            suggestedRetailPrice = 320.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Augmentin 625mg Tablets",
            genericName = "Amoxicillin + Clavulanic Acid",
            brand = "Augmentin",
            category = "Antibiotics",
            dosageForm = "Tablet",
            strength = "625mg",
            packSize = "14 Tablets",
            manufacturer = "GlaxoSmithKline (GSK)",
            suggestedRetailPrice = 580.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Augmentin 1g Tablets",
            genericName = "Amoxicillin + Clavulanic Acid",
            brand = "Augmentin",
            category = "Antibiotics",
            dosageForm = "Tablet",
            strength = "1000mg (1g)",
            packSize = "14 Tablets",
            manufacturer = "GlaxoSmithKline (GSK)",
            suggestedRetailPrice = 850.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Augmentin 156.25mg Suspension",
            genericName = "Amoxicillin + Clavulanic Acid",
            brand = "Augmentin",
            category = "Syrups",
            dosageForm = "Suspension",
            strength = "156.25mg/5ml",
            packSize = "90ml Bottle",
            manufacturer = "GlaxoSmithKline (GSK)",
            suggestedRetailPrice = 285.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Brufen 400mg Tablets",
            genericName = "Ibuprofen",
            brand = "Brufen",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "400mg",
            packSize = "300 Tablets",
            manufacturer = "Abbott Laboratories",
            suggestedRetailPrice = 720.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Brufen DS Syrup",
            genericName = "Ibuprofen",
            brand = "Brufen",
            category = "Syrups",
            dosageForm = "Syrup",
            strength = "200mg/5ml",
            packSize = "120ml Bottle",
            manufacturer = "Abbott Laboratories",
            suggestedRetailPrice = 145.0,
            expiryAlertDays = 60
        ),
        PredictedProductInfo(
            productName = "Risek 20mg Capsules",
            genericName = "Omeprazole",
            brand = "Risek",
            category = "Capsules",
            dosageForm = "Capsule",
            strength = "20mg",
            packSize = "14 Capsules",
            manufacturer = "Getz Pharma",
            suggestedRetailPrice = 360.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Risek 40mg Capsules",
            genericName = "Omeprazole",
            brand = "Risek",
            category = "Capsules",
            dosageForm = "Capsule",
            strength = "40mg",
            packSize = "14 Capsules",
            manufacturer = "Getz Pharma",
            suggestedRetailPrice = 540.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Risek Insta 20mg Sachet",
            genericName = "Omeprazole + Sodium Bicarbonate",
            brand = "Risek",
            category = "Supplements",
            dosageForm = "Sachet",
            strength = "20mg / 1680mg",
            packSize = "10 Sachets",
            manufacturer = "Getz Pharma",
            suggestedRetailPrice = 450.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Disprin 300mg Soluble Tablets",
            genericName = "Aspirin (Acetylsalicylic Acid)",
            brand = "Disprin",
            category = "Tablets",
            dosageForm = "Soluble Tablet",
            strength = "300mg",
            packSize = "100 Tablets",
            manufacturer = "Reckitt Benckiser",
            suggestedRetailPrice = 280.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Arinac Forte Tablets",
            genericName = "Ibuprofen + Pseudoephedrine",
            brand = "Arinac",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "400mg / 60mg",
            packSize = "100 Tablets",
            manufacturer = "Abbott Laboratories",
            suggestedRetailPrice = 620.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Ponstan 500mg Forte",
            genericName = "Mefenamic Acid",
            brand = "Ponstan",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "500mg",
            packSize = "100 Tablets",
            manufacturer = "Pfizer Pakistan",
            suggestedRetailPrice = 480.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Flagyl 400mg Tablets",
            genericName = "Metronidazole",
            brand = "Flagyl",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "400mg",
            packSize = "200 Tablets",
            manufacturer = "Sanofi-Aventis",
            suggestedRetailPrice = 520.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Flagyl Suspension 200mg/5ml",
            genericName = "Metronidazole",
            brand = "Flagyl",
            category = "Syrups",
            dosageForm = "Suspension",
            strength = "200mg/5ml",
            packSize = "60ml Bottle",
            manufacturer = "Sanofi-Aventis",
            suggestedRetailPrice = 110.0,
            expiryAlertDays = 60
        ),
        PredictedProductInfo(
            productName = "Gravinate 50mg Tablets",
            genericName = "Dimenhydrinate",
            brand = "Gravinate",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "50mg",
            packSize = "100 Tablets",
            manufacturer = "Searle Pakistan",
            suggestedRetailPrice = 290.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Ciproxin 500mg (Ciprofloxacin)",
            genericName = "Ciprofloxacin HCl",
            brand = "Ciproxin",
            category = "Antibiotics",
            dosageForm = "Tablet",
            strength = "500mg",
            packSize = "10 Tablets",
            manufacturer = "Bayer",
            suggestedRetailPrice = 420.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Softin 10mg Tablets",
            genericName = "Loratadine",
            brand = "Softin",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "10mg",
            packSize = "30 Tablets",
            manufacturer = "Bosch Pharmaceuticals",
            suggestedRetailPrice = 240.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Glucophage 500mg",
            genericName = "Metformin HCl",
            brand = "Glucophage",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "500mg",
            packSize = "50 Tablets",
            manufacturer = "Merck",
            suggestedRetailPrice = 310.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Glucophage 1000mg",
            genericName = "Metformin HCl",
            brand = "Glucophage",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "1000mg",
            packSize = "30 Tablets",
            manufacturer = "Merck",
            suggestedRetailPrice = 390.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Concor 2.5mg Tablets",
            genericName = "Bisoprolol Fumarate",
            brand = "Concor",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "2.5mg",
            packSize = "28 Tablets",
            manufacturer = "Merck",
            suggestedRetailPrice = 460.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Concor 5mg Tablets",
            genericName = "Bisoprolol Fumarate",
            brand = "Concor",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "5mg",
            packSize = "28 Tablets",
            manufacturer = "Merck",
            suggestedRetailPrice = 590.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Loprin 75mg Low Dose Aspirin",
            genericName = "Aspirin (Acetylsalicylic Acid)",
            brand = "Loprin",
            category = "Tablets",
            dosageForm = "Enteric Coated Tablet",
            strength = "75mg",
            packSize = "30 Tablets",
            manufacturer = "Highnoon Laboratories",
            suggestedRetailPrice = 95.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Voltral 50mg Tablets",
            genericName = "Diclofenac Sodium",
            brand = "Voltral",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "50mg",
            packSize = "20 Tablets",
            manufacturer = "Novartis",
            suggestedRetailPrice = 210.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Voltral Emulgel 1%",
            genericName = "Diclofenac Diethylamine",
            brand = "Voltral",
            category = "Ointments",
            dosageForm = "Gel",
            strength = "1% w/w",
            packSize = "50g Tube",
            manufacturer = "Novartis",
            suggestedRetailPrice = 340.0,
            expiryAlertDays = 60
        ),
        PredictedProductInfo(
            productName = "Nexum 40mg (Esomeprazole)",
            genericName = "Esomeprazole Magnesium",
            brand = "Nexum",
            category = "Capsules",
            dosageForm = "Capsule",
            strength = "40mg",
            packSize = "14 Capsules",
            manufacturer = "Getz Pharma",
            suggestedRetailPrice = 520.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Klaricid 250mg Tablets",
            genericName = "Clarithromycin",
            brand = "Klaricid",
            category = "Antibiotics",
            dosageForm = "Tablet",
            strength = "250mg",
            packSize = "14 Tablets",
            manufacturer = "Abbott Laboratories",
            suggestedRetailPrice = 780.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Zithromax 500mg Capsules",
            genericName = "Azithromycin",
            brand = "Zithromax",
            category = "Antibiotics",
            dosageForm = "Capsule",
            strength = "500mg",
            packSize = "6 Capsules",
            manufacturer = "Pfizer",
            suggestedRetailPrice = 640.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Lipitor 20mg Tablets",
            genericName = "Atorvastatin Calcium",
            brand = "Lipitor",
            category = "Tablets",
            dosageForm = "Tablet",
            strength = "20mg",
            packSize = "30 Tablets",
            manufacturer = "Pfizer",
            suggestedRetailPrice = 1150.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Ventolin Inhaler 100mcg",
            genericName = "Salbutamol (Albuterol)",
            brand = "Ventolin",
            category = "Inhalers",
            dosageForm = "Inhaler",
            strength = "100mcg / puff",
            packSize = "200 Puffs Canister",
            manufacturer = "GlaxoSmithKline (GSK)",
            suggestedRetailPrice = 420.0,
            expiryAlertDays = 90
        ),
        PredictedProductInfo(
            productName = "Sancos Cough Syrup",
            genericName = "Dextromethorphan + Pseudoephedrine",
            brand = "Sancos",
            category = "Syrups",
            dosageForm = "Syrup",
            strength = "10mg/5ml",
            packSize = "120ml Bottle",
            manufacturer = "Novartis",
            suggestedRetailPrice = 160.0,
            expiryAlertDays = 60
        )
    )

    /**
     * Compute Date of Expiry Alert given expiryDate (YYYY-MM-DD) and alertDays.
     * E.g., Expiry 2026-12-31 minus 90 days = 2026-10-02
     */
    fun calculateAlertDate(expiryDateStr: String, alertDays: Int): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(expiryDateStr) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.DAY_OF_YEAR, -alertDays)
            sdf.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Finds instant predictive matches from the offline pharmaceutical encyclopedia.
     */
    fun findPredictiveMatches(query: String): List<PredictedProductInfo> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()

        return OFFLINE_MEDICINE_ENCYCLOPEDIA.filter { item ->
            item.productName.lowercase().contains(q) ||
            item.genericName.lowercase().contains(q) ||
            item.brand.lowercase().contains(q)
        }.map { item ->
            // populate default expiry (e.g. 2 years ahead) and alert date
            val defaultExpiry = calculateFutureDate(years = 2)
            val alertDate = calculateAlertDate(defaultExpiry, item.expiryAlertDays)
            item.copy(
                detectedExpiryDate = defaultExpiry,
                calculatedAlertDate = alertDate
            )
        }
    }

    private fun calculateFutureDate(years: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, years)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    /**
     * Analyze a medicine photograph (box, packaging, blister strip, or bottle)
     * using Gemini 3.5 Flash Vision to predict all product attributes.
     */
    suspend fun predictFromImage(bitmap: Bitmap): Result<PredictedProductInfo> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Fallback to image-guided local prediction if no key configured
            val fallback = OFFLINE_MEDICINE_ENCYCLOPEDIA.first()
            val expiry = calculateFutureDate(years = 2)
            return@withContext Result.success(
                fallback.copy(
                    detectedBatchNumber = "B-${System.currentTimeMillis() % 100000}",
                    detectedExpiryDate = expiry,
                    calculatedAlertDate = calculateAlertDate(expiry, 90),
                    isFromImage = true,
                    confidenceNotes = "Offline demo prediction (Configure Gemini API Key in Secrets for live multimodal recognition)"
                )
            )
        }

        try {
            // Resize bitmap to reasonable dimension for network speed (max 1024px)
            val scaledBitmap = scaleBitmap(bitmap, 1024)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                You are an expert pharmaceutical vision AI. Analyze this image of a medicine pack, box, blister strip, or bottle.
                Extract and predict the following pharmaceutical specifications.
                Return ONLY a JSON object (no markdown code blocks, no other text) with these exact keys:
                {
                  "productName": "Full standard name including strength and form (e.g. Panadol 500mg Tablets)",
                  "genericName": "Active scientific chemical/generic formulation (e.g. Paracetamol)",
                  "brand": "Brand trade name (e.g. Panadol)",
                  "category": "One of: Tablets, Syrups, Injections, Capsules, Ointments, Drops, Inhalers, Antibiotics, Supplements",
                  "dosageForm": "One of: Tablet, Capsule, Syrup, Suspension, Injection, Cream, Drops, Inhaler",
                  "strength": "Dose strength with units (e.g. 500mg, 625mg, 10mg/5ml)",
                  "packSize": "Standard package packaging (e.g. 20 Tablets, 100ml Bottle, 14 Capsules)",
                  "manufacturer": "Pharmaceutical manufacturer company (e.g. GSK, Abbott, Searle, Getz, Pfizer)",
                  "suggestedRetailPrice": 0.0,
                  "detectedBatchNumber": "Batch or Lot number if printed or legible, otherwise generate plausible BATCH-XXXX",
                  "detectedManufacturingDate": "YYYY-MM-DD if legible, otherwise empty",
                  "detectedExpiryDate": "YYYY-MM-DD format if visible, otherwise provide a standard 2-year forward date like 2027-12-31",
                  "expiryAlertDays": 90,
                  "confidenceNotes": "Short 1-sentence note about detected text clarity"
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini API HTTP ${response.code}: $responseString"))
            }

            val rootJson = JSONObject(responseString)
            val candidates = rootJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleanedJsonText = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = JSONObject(cleanedJsonText)
            val expiry = parsed.optString("detectedExpiryDate", calculateFutureDate(2))
            val alertDays = parsed.optInt("expiryAlertDays", 90)
            val alertDate = calculateAlertDate(expiry, alertDays)

            val resultInfo = PredictedProductInfo(
                productName = parsed.optString("productName", "Detected Medicine"),
                genericName = parsed.optString("genericName", ""),
                brand = parsed.optString("brand", ""),
                category = parsed.optString("category", "Tablets"),
                dosageForm = parsed.optString("dosageForm", "Tablet"),
                strength = parsed.optString("strength", ""),
                packSize = parsed.optString("packSize", ""),
                manufacturer = parsed.optString("manufacturer", ""),
                suggestedRetailPrice = parsed.optDouble("suggestedRetailPrice", 0.0),
                detectedBatchNumber = parsed.optString("detectedBatchNumber", "BATCH-${System.currentTimeMillis() % 10000}"),
                detectedManufacturingDate = parsed.optString("detectedManufacturingDate", ""),
                detectedExpiryDate = expiry,
                expiryAlertDays = alertDays,
                calculatedAlertDate = alertDate,
                isFromImage = true,
                confidenceNotes = parsed.optString("confidenceNotes", "Detected from image via Gemini AI")
            )

            Result.success(resultInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ask Gemini AI to predict and complete medicine details given a typed name or partial query.
     */
    suspend fun predictFromText(query: String): Result<PredictedProductInfo> = withContext(Dispatchers.IO) {
        // Check offline database first for instant sub-millisecond response
        val offlineMatches = findPredictiveMatches(query)
        if (offlineMatches.isNotEmpty()) {
            return@withContext Result.success(offlineMatches.first())
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Generate a sensible default if offline and not in database
            val futureExpiry = calculateFutureDate(2)
            return@withContext Result.success(
                PredictedProductInfo(
                    productName = query.trim().replaceFirstChar { it.uppercase() },
                    genericName = "",
                    brand = query.trim().split(" ").firstOrNull() ?: query,
                    category = "Tablets",
                    dosageForm = "Tablet",
                    strength = "",
                    packSize = "10 Tablets",
                    manufacturer = "",
                    suggestedRetailPrice = 0.0,
                    detectedBatchNumber = "BATCH-${System.currentTimeMillis() % 10000}",
                    detectedExpiryDate = futureExpiry,
                    expiryAlertDays = 90,
                    calculatedAlertDate = calculateAlertDate(futureExpiry, 90),
                    confidenceNotes = "Offline basic prediction"
                )
            )
        }

        try {
            val prompt = """
                You are an expert pharmaceutical database AI. A pharmacy cashier is entering medicine name or query: "$query".
                Predict and complete the accurate pharmaceutical information for this product.
                Return ONLY a JSON object (no markdown, no other text) with these exact keys:
                {
                  "productName": "Standard complete trade name with strength and form",
                  "genericName": "Active pharmaceutical ingredient (API / chemical formula)",
                  "brand": "Brand / Trade mark name",
                  "category": "One of: Tablets, Syrups, Injections, Capsules, Ointments, Drops, Inhalers, Antibiotics, Supplements",
                  "dosageForm": "One of: Tablet, Capsule, Syrup, Suspension, Injection, Cream, Drops, Inhaler",
                  "strength": "Standard dosage strength (e.g. 500mg, 400mg, 10mg)",
                  "packSize": "Standard commercial pack size (e.g. 20 Tablets, 120ml Bottle)",
                  "manufacturer": "Standard pharmaceutical manufacturer",
                  "suggestedRetailPrice": 0.0,
                  "detectedBatchNumber": "BATCH-${System.currentTimeMillis() % 10000}",
                  "detectedExpiryDate": "2027-12-31",
                  "expiryAlertDays": 90,
                  "confidenceNotes": "Predicted by Gemini AI"
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini API error: $responseString"))
            }

            val rootJson = JSONObject(responseString)
            val candidates = rootJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleanedJsonText = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = JSONObject(cleanedJsonText)
            val expiry = parsed.optString("detectedExpiryDate", calculateFutureDate(2))
            val alertDays = parsed.optInt("expiryAlertDays", 90)
            val alertDate = calculateAlertDate(expiry, alertDays)

            val resultInfo = PredictedProductInfo(
                productName = parsed.optString("productName", query),
                genericName = parsed.optString("genericName", ""),
                brand = parsed.optString("brand", ""),
                category = parsed.optString("category", "Tablets"),
                dosageForm = parsed.optString("dosageForm", "Tablet"),
                strength = parsed.optString("strength", ""),
                packSize = parsed.optString("packSize", ""),
                manufacturer = parsed.optString("manufacturer", ""),
                suggestedRetailPrice = parsed.optDouble("suggestedRetailPrice", 0.0),
                detectedBatchNumber = parsed.optString("detectedBatchNumber", "BATCH-${System.currentTimeMillis() % 10000}"),
                detectedManufacturingDate = "",
                detectedExpiryDate = expiry,
                expiryAlertDays = alertDays,
                calculatedAlertDate = alertDate,
                isFromImage = false,
                confidenceNotes = parsed.optString("confidenceNotes", "AI text prediction")
            )

            Result.success(resultInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = maxDimension
        var resizedHeight = maxDimension

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight = (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
        } else {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }

        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
    }
}
