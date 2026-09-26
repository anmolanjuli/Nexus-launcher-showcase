package com.nexus.launcher.search

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.data.repository.AppRepository

enum class SearchContext {
    HOME_SCREEN, APP_DRAWER
}

sealed class SearchResult {
    data class AppResult(val label: String, val packageName: String, val icon: Drawable?) : SearchResult()
    data class ContactResult(val name: String, val phone: String?, val email: String?, val photoUri: Uri?) : SearchResult()
    data class WebResult(val query: String) : SearchResult()
    data class CalculatorResult(val expression: String, val answer: String) : SearchResult()
    /** [locked]: Premium would convert this; the row offers it instead of showing the answer. */
    data class UnitConverterResult(val input: String, val output: String, val locked: Boolean = false) : SearchResult()
    data class MapsResult(val query: String) : SearchResult()
}

class NexusSearchEngine(
    private val context: Context
) {

    suspend fun query(text: String, searchContext: SearchContext, settings: NexusSearchSettings, apps: List<AppModel>): List<SearchResult> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<SearchResult>()
        val query = text.trim()
        val qLower = query.lowercase()

        // 1. Calculator / Unit Conversion (Micro Actions)
        if (settings.searchCalculatorEnabled) {
            val calcResult = evaluateCalculator(qLower)
            if (calcResult != null) {
                results.add(calcResult)
            }
        }
        
        if (settings.searchConversionEnabled) {
            val unitResult = evaluateUnitConversion(qLower)
            if (unitResult != null) {
                val locked = com.nexus.launcher.ui.premium.PremiumBadges.isShown(com.nexus.launcher.premium.PremiumFeature.SMART_SEARCH)
                results.add(unitResult.copy(locked = locked))
            }
        }

        // 2. Apps
        val appResults = mutableListOf<SearchResult.AppResult>()
        for (app in apps) {
            val labelLower = app.label.lowercase()
            val pkgLower = app.packageName.lowercase()
            if (labelLower.contains(qLower) || pkgLower.contains(qLower)) {
                // Weight startsWith higher
                val score = if (labelLower.startsWith(qLower)) 2 else 1
                appResults.add(SearchResult.AppResult(app.label, app.packageName, app.icon))
            }
        }
        // Basic fuzzy sort: matches that start with the query appear first
        appResults.sortBy { !it.label.lowercase().startsWith(qLower) }
        results.addAll(appResults)

        // 3. Contacts
        if (settings.searchContactsEnabled) {
            results.addAll(searchContacts(query))
        }

        // 4. Web Search
        if (settings.searchWebEnabled) {
            results.add(SearchResult.WebResult(query))
        }

        // 5. Maps — secondary option, only when no app or contact matched
        if (settings.searchMapsEnabled && appResults.isEmpty()
            && results.none { it is SearchResult.ContactResult }
        ) {
            detectMapsQuery(qLower, query)?.let { results.add(it) }
        }

        return results
    }

    private fun detectMapsQuery(qLower: String, original: String): SearchResult.MapsResult? {
        val prefixes = listOf("navigate to ", "directions to ", "how to get to ", "map of ", "find nearby ", "where is ")
        val suffixes = listOf(" restaurant", " cafe", " hotel", " hospital", " airport", " pharmacy", " gym", " park")
        val keywords = listOf("near me")
        val matches = prefixes.any { qLower.startsWith(it) }
            || suffixes.any { qLower.endsWith(it) }
            || keywords.any { qLower.contains(it) }
        return if (matches) SearchResult.MapsResult(original) else null
    }

    private fun evaluateCalculator(query: String): SearchResult.CalculatorResult? {
        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val isCommaDecimal = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
        val normalized = if (isCommaDecimal) query.replace(Regex("(?<=\\d),(?=\\d)"), ".") else query

        val mathPattern = Regex("^[\\d\\s\\.\\+\\-\\*\\/\\(\\)]+$")
        if (!mathPattern.matches(normalized)) return null
        
        // Filter out simple numbers without operators
        if (normalized.matches(Regex("^[\\d\\s\\.]+$"))) return null
        
        // Very basic evaluator for demo purposes (A real one would use a proper parser or ScriptEngine)
        try {
            // Simplified evaluator for basic two-operand expressions
            val basicOp = Regex("([\\d\\.]+)\\s*([\\+\\-\\*\\/])\\s*([\\d\\.]+)").find(normalized)
            if (basicOp != null) {
                val a = basicOp.groupValues[1].toDouble()
                val op = basicOp.groupValues[2]
                val b = basicOp.groupValues[3].toDouble()
                val result = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b != 0.0) a / b else return null
                    else -> return null
                }
                
                val numberFormat = java.text.NumberFormat.getNumberInstance(locale).apply {
                    maximumFractionDigits = 6
                    minimumFractionDigits = 0
                    isGroupingUsed = false
                }
                val answer = numberFormat.format(result)
                return SearchResult.CalculatorResult(query, answer)
            }
        } catch (e: Exception) {
            // Ignored
        }
        return null
    }

    private fun evaluateUnitConversion(query: String): SearchResult.UnitConverterResult? {
        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val isCommaDecimal = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
        val normalized = if (isCommaDecimal) query.replace(Regex("(?<=\\d),(?=\\d)"), ".") else query

        val regex = Regex("^([\\d\\.]+)\\s*([a-zA-Z]+)\\s+to\\s+([a-zA-Z]+)$")
        val match = regex.find(normalized) ?: return null
        
        try {
            val value = match.groupValues[1].toDouble()
            val from = match.groupValues[2].lowercase()
            val to = match.groupValues[3].lowercase()

            var result: Double? = null
            
            // Basic conversions
            if (from == "km" && to == "miles") result = value * 0.621371
            else if (from == "miles" && to == "km") result = value * 1.60934
            else if (from == "kg" && (to == "lbs" || to == "pounds")) result = value * 2.20462
            else if ((from == "lbs" || from == "pounds") && to == "kg") result = value / 2.20462
            else if (from == "cm" && to == "inches") result = value * 0.393701
            else if (from == "inches" && to == "cm") result = value * 2.54
            else if (from == "celsius" || from == "c") {
                if (to == "fahrenheit" || to == "f") result = (value * 9/5) + 32
            }
            else if (from == "fahrenheit" || from == "f") {
                if (to == "celsius" || to == "c") result = (value - 32) * 5/9
            }
            else if (from == "liters" && to == "gallons") result = value * 0.264172
            else if (from == "gallons" && to == "liters") result = value / 0.264172

            if (result != null) {
                val numberFormat = java.text.NumberFormat.getNumberInstance(locale).apply {
                    maximumFractionDigits = 2
                    minimumFractionDigits = 0
                    isGroupingUsed = false
                }
                val formatted = numberFormat.format(result)
                return SearchResult.UnitConverterResult(query, "$formatted $to")
            }
        } catch (e: Exception) {
            // Ignored
        }
        
        return null
    }

    private fun searchContacts(query: String): List<SearchResult.ContactResult> {
        val results = mutableListOf<SearchResult.ContactResult>()
        
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return results
        }

        try {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                
                var count = 0
                while (it.moveToNext() && count < 5) {
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numberIdx)
                    val photoStr = it.getString(photoIdx)
                    val photoUri = if (photoStr != null) Uri.parse(photoStr) else null
                    
                    results.add(SearchResult.ContactResult(name, number, null, photoUri))
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NexusSearch", "Error searching contacts", e)
        }
        
        return results
    }
}
