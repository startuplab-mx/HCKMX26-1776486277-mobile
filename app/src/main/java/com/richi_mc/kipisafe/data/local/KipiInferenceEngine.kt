package com.richi_mc.kipisafe.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Inferencia local de regresión logística multi-clase + vectorización TF-IDF
 * alineada con scikit-learn (TfidfVectorizer word 1–2, char_wb 2–5, norma L2 por bloque).
 */
data class KipiInferenceResult(
    val label: String,
    val confidence: Double,
    val level: Int,
)

private data class KipiModelJson(
    val metadata: KipiModelMetadataJson,
    val weights: List<List<Double>>,
    val intercepts: List<Double>,
    @SerializedName("vocabulary_word") val vocabularyWord: Map<String, Number>,
    @SerializedName("idf_word") val idfWord: List<Double>,
    @SerializedName("vocabulary_char") val vocabularyChar: Map<String, Number>,
    @SerializedName("idf_char") val idfChar: List<Double>,
    @SerializedName("symbols_scorer") val symbolsScorer: SymbolsScorerJson,
    @SerializedName("threat_ruler") val threatRuler: ThreatRulerJson,
    @SerializedName("aggression_featurizer") val aggressionFeaturizer: AggressionFeaturizerJson
)

private data class KipiModelMetadataJson(
    val classes: List<String>,
    @SerializedName("risk_levels") val riskLevels: Map<String, Number>
)

private data class SymbolsScorerJson(
    val threshold: Int,
    @SerializedName("risk_emojis") val riskEmojis: List<String>,
    @SerializedName("group_codes") val groupCodes: List<String>,
    @SerializedName("recruit_verbs") val recruitVerbs: List<String>,
    @SerializedName("social_context") val socialContext: List<String>
)

private data class ThreatRulerJson(
    val threshold: Int,
    @SerializedName("physical_harm") val physicalHarm: List<String>,
    val extortion: List<String>,
    val ultimatum: List<String>,
    val location: List<String>,
    val weapons: List<String>,
    val conditionals: List<String>,
    @SerializedName("safe_context") val safeContext: List<String>,
    val fictional: List<String>
)

private data class AggressionFeaturizerJson(
    @SerializedName("threat_verbs") val threatVerbs: List<String>,
    @SerializedName("bully_verbs") val bullyVerbs: List<String>,
    val insults: List<String>
)

class KipiInferenceEngine(context: Context, assetFileName: String = DEFAULT_ASSET) {

    private val classNames: Array<String>
    private val riskLevelByLabel: Map<String, Int>
    private val wordIndex: HashMap<String, Int>
    private val charIndex: HashMap<String, Int>
    private val idfWord: DoubleArray
    private val idfChar: DoubleArray
    private val coef: DoubleArray
    private val intercept: DoubleArray
    private val nClasses: Int
    private val wordDim: Int
    private val charDim: Int
    private val totalDim: Int

    private val wordCounts: IntArray
    private val charCounts: IntArray
    private val feature: DoubleArray
    private val logits: DoubleArray
    private val probs: DoubleArray

    // --- NUEVAS REGLAS Y FEATURIZERS ---
    private val symRiskEmojis: List<String>
    private val symGroupCodes: List<String>
    private val symRecruitVerbs: List<String>
    private val symSocialContext: List<String>
    private val symThreshold: Int

    private val thrPhysicalHarm: List<String>
    private val thrExtortion: List<String>
    private val thrUltimatum: List<String>
    private val thrLocation: List<String>
    private val thrWeapons: List<String>
    private val thrConditionals: List<String>
    private val thrSafeContext: List<String>
    private val thrFictional: List<String>
    private val thrThreshold: Int

    private val aggThreatVerbs: List<String>
    private val aggBullyVerbs: List<String>
    private val aggInsults: List<String>
    private val emojiDim = 4 // count, unique, two_plus, short_msg
    private val aggressionDim = 6 // caps_ratio, excl, quest, threat_hits, bully_hits, insult_hits

    private val tokenBuffer: ArrayList<String> = ArrayList(64)
    private val bigramSb = StringBuilder(48)

    init {
        val model = loadModel(context, assetFileName)
        classNames = model.metadata.classes.toTypedArray()
        riskLevelByLabel = model.metadata.riskLevels.mapValuesTo(HashMap()) { it.value.toInt() }
        nClasses = classNames.size
        require(model.weights.size == nClasses) { "weights rows != classes" }

        wordDim = model.idfWord.size
        charDim = model.idfChar.size
        // ¡IMPORTANTE! Se suman las 4 características de Emoji y las 6 de Agresividad
        totalDim = wordDim + charDim + emojiDim + aggressionDim 
        require(model.vocabularyWord.size == wordDim) { "vocabulary_word size != idf_word" }
        require(model.vocabularyChar.size == charDim) { "vocabulary_char size != idf_char" }

        idfWord = DoubleArray(wordDim) { i -> model.idfWord[i] }
        idfChar = DoubleArray(charDim) { i -> model.idfChar[i] }

        wordIndex = HashMap(model.vocabularyWord.size)
        for ((k, v) in model.vocabularyWord) {
            wordIndex[k] = v.toInt()
        }
        charIndex = HashMap(model.vocabularyChar.size)
        for ((k, v) in model.vocabularyChar) {
            charIndex[k] = v.toInt()
        }

        coef = DoubleArray(nClasses * totalDim)
        for (c in 0 until nClasses) {
            val row = model.weights[c]
            require(row.size == totalDim) { "weights[$c].size (${row.size}) != $totalDim" }
            val base = c * totalDim
            for (j in 0 until totalDim) {
                coef[base + j] = row[j]
            }
        }
        intercept = DoubleArray(nClasses) { i -> model.intercepts[i] }

        wordCounts = IntArray(wordDim)
        charCounts = IntArray(charDim)
        feature = DoubleArray(totalDim)
        logits = DoubleArray(nClasses)
        probs = DoubleArray(nClasses)

        // Inicialización de Scorer de Símbolos
        symThreshold = model.symbolsScorer.threshold
        symRiskEmojis = model.symbolsScorer.riskEmojis
        symGroupCodes = model.symbolsScorer.groupCodes
        symRecruitVerbs = model.symbolsScorer.recruitVerbs
        symSocialContext = model.symbolsScorer.socialContext

        // Inicialización de Regla de Amenazas
        thrThreshold = model.threatRuler.threshold
        thrPhysicalHarm = model.threatRuler.physicalHarm
        thrExtortion = model.threatRuler.extortion
        thrUltimatum = model.threatRuler.ultimatum
        thrLocation = model.threatRuler.location
        thrWeapons = model.threatRuler.weapons
        thrConditionals = model.threatRuler.conditionals
        thrSafeContext = model.threatRuler.safeContext
        thrFictional = model.threatRuler.fictional

        // Inicialización de Aggression Featurizer
        aggThreatVerbs = model.aggressionFeaturizer.threatVerbs
        aggBullyVerbs = model.aggressionFeaturizer.bullyVerbs
        aggInsults = model.aggressionFeaturizer.insults
    }

    /**
     * Devuelve una copia del vector de características (TF-IDF + Aggression).
     */
    @Synchronized
    fun transform(textRaw: String): DoubleArray {
        val text = textRaw.replace(Regex("\\s+"), " ").trim()
        val textLower = text.lowercase(Locale.ROOT)
        computeFeatureVector(text, textLower)
        return feature.copyOf()
    }

    /**
     * Escribe el vector en [destination]; debe tener longitud [featureDimension].
     */
    @Synchronized
    fun transformInto(textRaw: String, destination: DoubleArray) {
        require(destination.size == totalDim) { "destination.size must be $totalDim" }
        val text = textRaw.replace(Regex("\\s+"), " ").trim()
        val textLower = text.lowercase(Locale.ROOT)
        computeFeatureVector(text, textLower)
        feature.copyInto(destination)
    }

    val featureDimension: Int
        get() = totalDim

    @Synchronized
    fun predict(textRaw: String): KipiInferenceResult {
        // 1. Limpieza
        val text = textRaw.replace(Regex("\\s+"), " ").trim()
        val textLower = text.lowercase(Locale.ROOT)
        
        val signals = getSignals(textLower)

        // 2. Paso 1: Symbols Scorer
        val symScore = scoreSymbols(textLower)
        if (symScore >= symThreshold) {
            val conf = minOf(0.5 + symScore * 0.08, 0.95)
            return KipiInferenceResult("SYMBOLS", conf, riskLevelByLabel["SYMBOLS"] ?: 0)
        }

        // 3. Paso 2: Threat Ruler
        val thrScore = scoreThreats(textLower)
        if (thrScore >= thrThreshold) {
            val conf = minOf(0.5 + thrScore * 0.07, 0.95)
            return KipiInferenceResult("THREAT", conf, riskLevelByLabel["THREAT"] ?: 0)
        }

        // 4. Paso 3: Pipeline ML (TF-IDF + Aggression)
        computeFeatureVector(text, textLower)
        computeLogitsAndSoftmax()

        var best = 0
        var bestP = probs[0]
        for (k in 1 until nClasses) {
            val p = probs[k]
            if (p > bestP) {
                bestP = p
                best = k
            }
        }
        var label = classNames[best]
        var conf = bestP

        // 5. Paso 4: Rule Booster
        val noTouchLabels = listOf("HIGH_RISK", "MIXED", "SYMBOLS", "THREAT")
        if (signals.size >= 2 && label !in noTouchLabels) {
            label = "MIXED"
            conf = minOf(conf + 0.1, 1.0)
        }

        return KipiInferenceResult(label, conf, riskLevelByLabel[label] ?: 0)
    }

    private fun computeFeatureVector(textOriginal: String, textLower: String) {
        wordCounts.fill(0)
        charCounts.fill(0)
        feature.fill(0.0) // Limpiar todo el array

        // Parte TF-IDF
        countWordNgrams(textLower)
        countCharWbNgrams(textLower)

        l2BlockInto(wordCounts, idfWord, feature, 0, wordDim)
        l2BlockInto(charCounts, idfChar, feature, wordDim, charDim)
        
        // --- 1. Parte Emoji Featurizer ---
        var emojiCount = 0
        var emojiUnique = 0
        for (e in symRiskEmojis) {
            val occurrences = textOriginal.split(e).size - 1
            if (occurrences > 0) {
                emojiCount += occurrences
                emojiUnique++
            }
        }
        val emojiTwoPlus = if (emojiCount >= 2) 1.0 else 0.0
        val shortMsg = if (textOriginal.split(" ").size <= 6) 1.0 else 0.0

        val emojiOffset = wordDim + charDim
        feature[emojiOffset] = emojiCount.toDouble()
        feature[emojiOffset + 1] = emojiUnique.toDouble()
        feature[emojiOffset + 2] = emojiTwoPlus
        feature[emojiOffset + 3] = shortMsg

        // --- 2. Parte Aggression Featurizer ---
        var upperCount = 0
        var alphaCount = 0
        for (c in textOriginal) {
            if (c.isLetter()) {
                alphaCount++
                if (c.isUpperCase()) upperCount++
            }
        }
        val capsRatio = if (alphaCount > 0) upperCount.toDouble() / alphaCount else 0.0
        val exclamations = textOriginal.count { it == '!' }.toDouble()
        val questions = textOriginal.count { it == '?' }.toDouble()
        
        val threatHits = aggThreatVerbs.count { textLower.contains(it) }.toDouble()
        val bullyHits = aggBullyVerbs.count { textLower.contains(it) }.toDouble()
        val insultHits = aggInsults.count { textLower.contains(it) }.toDouble()

        val aggOffset = emojiOffset + emojiDim
        feature[aggOffset] = capsRatio
        feature[aggOffset + 1] = exclamations
        feature[aggOffset + 2] = questions
        feature[aggOffset + 3] = threatHits
        feature[aggOffset + 4] = bullyHits
        feature[aggOffset + 5] = insultHits
    }

    private fun l2BlockInto(counts: IntArray, idf: DoubleArray, out: DoubleArray, offset: Int, dim: Int) {
        var sumSq = 0.0
        for (i in 0 until dim) {
            val v = counts[i] * idf[i]
            out[offset + i] = v
            sumSq += v * v
        }
        val norm = sqrt(sumSq)
        if (norm > 0.0) {
            val inv = 1.0 / norm
            val end = offset + dim
            for (i in offset until end) {
                out[i] *= inv
            }
        }
    }

    private fun countWordNgrams(lowered: String) {
        tokenBuffer.clear()
        val matcher = TOKEN_PATTERN.matcher(lowered)
        while (matcher.find()) {
            tokenBuffer.add(matcher.group())
        }
        val nTok = tokenBuffer.size
        for (i in 0 until nTok) {
            val idx = wordIndex[tokenBuffer[i]]
            if (idx != null) {
                wordCounts[idx]++
            }
        }
        if (nTok >= 2) {
            for (i in 0 until nTok - 1) {
                bigramSb.setLength(0)
                bigramSb.append(tokenBuffer[i]).append(' ').append(tokenBuffer[i + 1])
                val idx = wordIndex[bigramSb.toString()]
                if (idx != null) {
                    wordCounts[idx]++
                }
            }
        }
    }

    private fun countCharWbNgrams(lowered: String) {
        val collapsed = MULTI_WS.replace(lowered, " ")
        var i = 0
        val n = collapsed.length
        while (i < n) {
            while (i < n && collapsed[i].isWhitespace()) {
                i++
            }
            if (i >= n) break
            val start = i
            while (i < n && !collapsed[i].isWhitespace()) {
                i++
            }
            accumulateCharWbForWord(collapsed, start, i)
        }
    }

    private fun accumulateCharWbForWord(s: String, wordStart: Int, wordEnd: Int) {
        val wLen = wordEnd - wordStart + 2
        for (gramLen in CHAR_MIN_N..CHAR_MAX_N) {
            var offset = 0
            appendCharNgram(s, wordStart, wordEnd, wLen, gramLen, offset)
            while (offset + gramLen < wLen) {
                offset++
                appendCharNgram(s, wordStart, wordEnd, wLen, gramLen, offset)
            }
            if (offset == 0) {
                break
            }
        }
    }

    private fun appendCharNgram(
        s: String,
        wordStart: Int,
        wordEnd: Int,
        wLen: Int,
        gramLen: Int,
        offset: Int,
    ) {
        val endPos = minOf(offset + gramLen, wLen)
        if (endPos <= offset) return
        bigramSb.setLength(0)
        for (pos in offset until endPos) {
            val ch = when (pos) {
                0 -> ' '
                wLen - 1 -> ' '
                else -> s[wordStart + pos - 1]
            }
            bigramSb.append(ch)
        }
        val idx = charIndex[bigramSb.toString()]
        if (idx != null) {
            charCounts[idx]++
        }
    }

    private fun computeLogitsAndSoftmax() {
        for (c in 0 until nClasses) {
            val base = c * totalDim
            var dot = intercept[c]
            for (j in 0 until totalDim) {
                dot += coef[base + j] * feature[j]
            }
            logits[c] = dot
        }
        var maxL = logits[0]
        for (c in 1 until nClasses) {
            if (logits[c] > maxL) {
                maxL = logits[c]
            }
        }
        var sum = 0.0
        for (c in 0 until nClasses) {
            val e = exp(logits[c] - maxL)
            probs[c] = e
            sum += e
        }
        val invSum = 1.0 / sum
        for (c in 0 until nClasses) {
            probs[c] *= invSum
        }
    }

    private fun getSignals(t: String): List<String> {
        val signals = mutableListOf<String>()
        if (symRiskEmojis.any { t.contains(it) }) signals.add("high_risk_emoji")
        val recruitKw = listOf("ng", "nueva generacion", "maña", "chapizza", "701")
        if (recruitKw.any { t.contains(it) }) signals.add("recruitment_kw")
        val urgency = listOf("no le digas", "manda inbox", "dinero facil", "paga diaria")
        if (urgency.any { t.contains(it) }) signals.add("urgency")
        return signals
    }

    private fun scoreSymbols(t: String): Int {
        var points = 0
        val emojiHits = symRiskEmojis.filter { t.contains(it) }
        
        if (emojiHits.isNotEmpty()) {
            points += emojiHits.size * 2
        }
        if (emojiHits.toSet().size >= 2) {
            points += 1
        }
        
        val stripped = t.replace(Regex("\\s"), "")
        val onlyEmojis = stripped.isNotEmpty() && emojiHits.isNotEmpty() && 
                         stripped.all { char -> symRiskEmojis.any { emoji -> emoji.contains(char) } }
        if (onlyEmojis) points += 2

        if (symSocialContext.any { t.contains(it) }) points -= 2
        if (symGroupCodes.any { t.contains(it) }) points += 1
        
        val words = t.split(" ")
        if (words.size <= 6) points += 1
        
        if (symRecruitVerbs.any { t.contains(it) }) points -= 2

        return points
    }

    private fun scoreThreats(t: String): Int {
        var points = 0
        
        if (thrPhysicalHarm.any { t.contains(it) }) points += 3
        if (thrExtortion.any { t.contains(it) }) points += 2
        if (thrUltimatum.any { t.contains(it) }) points += 2
        if (thrLocation.any { t.contains(it) }) points += 2
        
        val weaponHits = thrWeapons.filter { t.contains(it) }
        if (weaponHits.isNotEmpty()) points += 1
        
        if (thrConditionals.any { t.contains(it) }) points += 1
        if (thrSafeContext.any { t.contains(it) }) points -= 3
        if (thrFictional.any { t.contains(it) }) points -= 2

        return points
    }

    companion object {
        private const val DEFAULT_ASSET = "kipi_model_data.json"
        private const val CHAR_MIN_N = 2
        private const val CHAR_MAX_N = 5

        /**
         * Equivalente a scikit-learn `(?u)\b\w\w+\b`: `\w` Unicode.
         * En Android NO usamos Pattern.UNICODE_CHARACTER_CLASS porque no es soportado universalmente
         * y lanza IllegalArgumentException en muchas versiones. Usamos un rango manual.
         */
        private val TOKEN_PATTERN: Pattern = Pattern.compile(
            "[a-zA-Zà-ÿÀ-ß0-9_]{2,}",
            Pattern.CASE_INSENSITIVE
        )

        private val MULTI_WS by lazy { Regex("\\s\\s+") }

        private val gson by lazy { Gson() }

        private fun loadModel(context: Context, assetFileName: String): KipiModelJson {
            return try {
                context.assets.open(assetFileName).use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        gson.fromJson(reader, KipiModelJson::class.java)
                            ?: error("Modelo JSON vacío o inválido: $assetFileName")
                    }
                }
            } catch (e: Exception) {
                Log.e("KipiInferenceEngine", "Error al cargar modelo $assetFileName: ${e.message}", e)
                throw e
            }
        }
    }
}
