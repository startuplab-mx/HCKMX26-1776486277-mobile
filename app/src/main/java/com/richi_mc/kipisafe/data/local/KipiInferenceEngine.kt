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
 * alineada con scikit-learn (TfidfVectorizer word 1–2, char 2–5, norma L2 por bloque).
 *
 * CORRECCIONES v2 respecto a la versión anterior:
 *  [FIX-1] char n-grams: se eliminó el padding de espacios (char_wb → char puro),
 *          ahora el texto se analiza como stream continuo igual que sklearn analyzer='char'.
 *  [FIX-2] RuleBooster: se añadieron Regla A (recruitment + job_offer → HIGH_RISK)
 *          y Regla C (solo recruitment + SAFE → MIXED), que estaban ausentes.
 *  [FIX-3] getSignals(): se añadió la categoría job_offer completa y los tokens
 *          faltantes en recruitment_kw y urgency.
 *  [FIX-4] scoreThreats(): extorsión ahora acumula hasta +4 con count(), igual que Python.
 *  [FIX-5] Tokenización: se usa \p{L}\p{N} con UNICODE_CHARACTER_CLASS para alinearse
 *          con el patrón Unicode de sklearn (?u)\b\w\w+\b.
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

    // ── Symbols Scorer ──────────────────────────────────────────────────────
    private val symRiskEmojis: List<String>
    private val symGroupCodes: List<String>
    private val symRecruitVerbs: List<String>
    private val symSocialContext: List<String>
    private val symThreshold: Int

    // ── Threat Ruler ────────────────────────────────────────────────────────
    private val thrPhysicalHarm: List<String>
    private val thrExtortion: List<String>
    private val thrUltimatum: List<String>
    private val thrLocation: List<String>
    private val thrWeapons: List<String>
    private val thrConditionals: List<String>
    private val thrSafeContext: List<String>
    private val thrFictional: List<String>
    private val thrThreshold: Int

    // ── Aggression Featurizer ───────────────────────────────────────────────
    private val aggThreatVerbs: List<String>
    private val aggBullyVerbs: List<String>
    private val aggInsults: List<String>

    private val emojiDim = 4      // count, unique, two_plus, short_msg
    private val aggressionDim = 6 // caps_ratio, excl, quest, threat_hits, bully_hits, insult_hits

    // ── Buffers reutilizables ───────────────────────────────────────────────
    private val tokenBuffer: ArrayList<String> = ArrayList(64)
    private val ngramSb = StringBuilder(48)

    init {
        val model = loadModel(context, assetFileName)
        classNames = model.metadata.classes.toTypedArray()
        riskLevelByLabel = model.metadata.riskLevels.mapValuesTo(HashMap()) { it.value.toInt() }
        nClasses = classNames.size
        require(model.weights.size == nClasses) { "weights rows != classes" }

        wordDim = model.idfWord.size
        charDim = model.idfChar.size
        totalDim = wordDim + charDim + emojiDim + aggressionDim

        require(model.vocabularyWord.size == wordDim) { "vocabulary_word size != idf_word" }
        require(model.vocabularyChar.size == charDim) { "vocabulary_char size != idf_char" }

        idfWord = DoubleArray(wordDim) { i -> model.idfWord[i] }
        idfChar = DoubleArray(charDim) { i -> model.idfChar[i] }

        wordIndex = HashMap(model.vocabularyWord.size)
        for ((k, v) in model.vocabularyWord) wordIndex[k] = v.toInt()

        charIndex = HashMap(model.vocabularyChar.size)
        for ((k, v) in model.vocabularyChar) charIndex[k] = v.toInt()

        coef = DoubleArray(nClasses * totalDim)
        for (c in 0 until nClasses) {
            val row = model.weights[c]
            require(row.size == totalDim) { "weights[$c].size (${row.size}) != $totalDim" }
            val base = c * totalDim
            for (j in 0 until totalDim) coef[base + j] = row[j]
        }
        intercept = DoubleArray(nClasses) { i -> model.intercepts[i] }

        wordCounts = IntArray(wordDim)
        charCounts = IntArray(charDim)
        feature = DoubleArray(totalDim)
        logits = DoubleArray(nClasses)
        probs = DoubleArray(nClasses)

        // Symbols Scorer
        symThreshold   = model.symbolsScorer.threshold
        symRiskEmojis  = model.symbolsScorer.riskEmojis
        symGroupCodes  = model.symbolsScorer.groupCodes
        symRecruitVerbs = model.symbolsScorer.recruitVerbs
        symSocialContext = model.symbolsScorer.socialContext

        // Threat Ruler
        thrThreshold   = model.threatRuler.threshold
        thrPhysicalHarm = model.threatRuler.physicalHarm
        thrExtortion   = model.threatRuler.extortion
        thrUltimatum   = model.threatRuler.ultimatum
        thrLocation    = model.threatRuler.location
        thrWeapons     = model.threatRuler.weapons
        thrConditionals = model.threatRuler.conditionals
        thrSafeContext  = model.threatRuler.safeContext
        thrFictional   = model.threatRuler.fictional

        // Aggression Featurizer
        aggThreatVerbs = model.aggressionFeaturizer.threatVerbs
        aggBullyVerbs  = model.aggressionFeaturizer.bullyVerbs
        aggInsults     = model.aggressionFeaturizer.insults
    }

    // ────────────────────────────────────────────────────────────────────────
    // API pública
    // ────────────────────────────────────────────────────────────────────────

    @Synchronized
    fun predict(textRaw: String): KipiInferenceResult {
        // Limpieza: colapsar espacios, preservar mayúsculas (para caps_ratio)
        val text      = textRaw.replace(Regex("\\s+"), " ").trim()
        val textLower = text.lowercase(Locale.ROOT)

        val signals = getSignals(textLower)

        // ── Paso 1: Symbols Scorer ───────────────────────────────────────────
        val symScore = scoreSymbols(textLower)
        if (symScore >= symThreshold) {
            val conf = minOf(0.5 + symScore * 0.08, 0.95)
            return KipiInferenceResult("SYMBOLS", conf, riskLevelByLabel["SYMBOLS"] ?: 0)
        }

        // ── Paso 2: Threat Ruler ─────────────────────────────────────────────
        val thrScore = scoreThreats(textLower)
        if (thrScore >= thrThreshold) {
            val conf = minOf(0.5 + thrScore * 0.07, 0.95)
            return KipiInferenceResult("THREAT", conf, riskLevelByLabel["THREAT"] ?: 0)
        }

        // ── Paso 3: Pipeline ML ──────────────────────────────────────────────
        computeFeatureVector(text, textLower)
        computeLogitsAndSoftmax()

        var best = 0
        var bestP = probs[0]
        for (k in 1 until nClasses) {
            if (probs[k] > bestP) { bestP = probs[k]; best = k }
        }
        var label = classNames[best]
        var conf  = bestP

        // ── Paso 4: Rule Booster ─────────────────────────────────────────────
        // [FIX-2] Se añaden Regla A y Regla C que estaban ausentes.
        val noTouchLabels = listOf("HIGH_RISK", "MIXED", "SYMBOLS", "THREAT")

        // Regla A: cártel + oferta de trabajo → HIGH_RISK directo
        if ("recruitment_kw" in signals &&
            ("job_offer" in signals || "urgency" in signals)
        ) {
            if (label !in listOf("HIGH_RISK", "THREAT")) {
                label = "HIGH_RISK"
                conf  = maxOf(0.85, conf)
            }
        }
        // Regla B: múltiples señales → MIXED
        else if (signals.size >= 2 && label !in noTouchLabels) {
            label = "MIXED"
            conf  = minOf(conf + 0.1, 1.0)
        }
        // Regla C: solo menciona cártel pero ML dice SAFE → segunda opinión
        else if ("recruitment_kw" in signals && label == "SAFE") {
            label = "MIXED"
            conf  = minOf(conf + 0.1, 1.0)
        }

        return KipiInferenceResult(label, conf, riskLevelByLabel[label] ?: 0)
    }

    @Synchronized
    fun transform(textRaw: String): DoubleArray {
        val text      = textRaw.replace(Regex("\\s+"), " ").trim()
        val textLower = text.lowercase(Locale.ROOT)
        computeFeatureVector(text, textLower)
        return feature.copyOf()
    }

    @Synchronized
    fun transformInto(textRaw: String, destination: DoubleArray) {
        require(destination.size == totalDim) { "destination.size must be $totalDim" }
        val text      = textRaw.replace(Regex("\\s+"), " ").trim()
        val textLower = text.lowercase(Locale.ROOT)
        computeFeatureVector(text, textLower)
        feature.copyInto(destination)
    }

    val featureDimension: Int get() = totalDim

    // ────────────────────────────────────────────────────────────────────────
    // Vectorización
    // ────────────────────────────────────────────────────────────────────────

    private fun computeFeatureVector(textOriginal: String, textLower: String) {
        wordCounts.fill(0)
        charCounts.fill(0)
        feature.fill(0.0)

        // TF-IDF word (1,2)-grams
        countWordNgrams(textLower)
        // TF-IDF char (2,5)-grams  [FIX-1: analyzer='char' puro, sin padding de espacios]
        countCharNgrams(textLower)

        l2BlockInto(wordCounts, idfWord, feature, 0,       wordDim)
        l2BlockInto(charCounts, idfChar, feature, wordDim, charDim)

        // ── Emoji Featurizer ─────────────────────────────────────────────────
        var emojiCount = 0
        var emojiUnique = 0
        for (e in symRiskEmojis) {
            var idx = 0
            var occ = 0
            while (true) {
                idx = textOriginal.indexOf(e, idx)
                if (idx == -1) break
                occ++
                idx += e.length
            }
            if (occ > 0) { emojiCount += occ; emojiUnique++ }
        }
        val emojiOffset = wordDim + charDim
        feature[emojiOffset]     = emojiCount.toDouble()
        feature[emojiOffset + 1] = emojiUnique.toDouble()
        feature[emojiOffset + 2] = if (emojiCount >= 2) 1.0 else 0.0
        feature[emojiOffset + 3] = if (textOriginal.split(" ").size <= 6) 1.0 else 0.0

        // ── Aggression Featurizer ────────────────────────────────────────────
        // caps_ratio se calcula sobre texto ORIGINAL (preserva mayúsculas)
        var upperCount = 0
        var alphaCount = 0
        for (c in textOriginal) {
            if (c.isLetter()) {
                alphaCount++
                if (c.isUpperCase()) upperCount++
            }
        }
        val capsRatio    = if (alphaCount > 0) upperCount.toDouble() / alphaCount else 0.0
        val exclamations = textOriginal.count { it == '!' }.toDouble()
        val questions    = textOriginal.count { it == '?' }.toDouble()
        // Búsqueda de verbos sobre textLower
        val threatHits   = aggThreatVerbs.count { textLower.contains(it) }.toDouble()
        val bullyHits    = aggBullyVerbs.count  { textLower.contains(it) }.toDouble()
        val insultHits   = aggInsults.count     { textLower.contains(it) }.toDouble()

        val aggOffset = emojiOffset + emojiDim
        feature[aggOffset]     = capsRatio
        feature[aggOffset + 1] = exclamations
        feature[aggOffset + 2] = questions
        feature[aggOffset + 3] = threatHits
        feature[aggOffset + 4] = bullyHits
        feature[aggOffset + 5] = insultHits
    }

    // ────────────────────────────────────────────────────────────────────────
    // Word n-grams  (equivalente a sklearn analyzer='word', ngram_range=(1,2))
    // ────────────────────────────────────────────────────────────────────────

    private fun countWordNgrams(lowered: String) {
        tokenBuffer.clear()
        val matcher = TOKEN_PATTERN.matcher(lowered)
        while (matcher.find()) tokenBuffer.add(matcher.group())

        val nTok = tokenBuffer.size
        // Unigramas
        for (i in 0 until nTok) {
            wordIndex[tokenBuffer[i]]?.let { wordCounts[it]++ }
        }
        // Bigramas
        for (i in 0 until nTok - 1) {
            ngramSb.setLength(0)
            ngramSb.append(tokenBuffer[i]).append(' ').append(tokenBuffer[i + 1])
            wordIndex[ngramSb.toString()]?.let { wordCounts[it]++ }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Char n-grams  [FIX-1]
    //
    // sklearn analyzer='char' (NO char_wb):
    //   - Opera sobre el texto completo como un stream continuo de caracteres.
    //   - Los n-gramas CRUZAN espacios entre palabras.
    //   - NO añade espacios de padding alrededor de cada palabra.
    //
    // La versión anterior usaba accumulateCharWbForWord() que añadía ' ' al
    // inicio y fin de cada palabra (comportamiento de char_wb), generando
    // n-gramas que NO existen en el vocabulario exportado desde Python.
    // ────────────────────────────────────────────────────────────────────────

    private fun countCharNgrams(lowered: String) {
        // Colapsar múltiples espacios (ya viene colapsado, pero por seguridad)
        val text = lowered
        val n    = text.length
        for (gramLen in CHAR_MIN_N..CHAR_MAX_N) {
            val limit = n - gramLen + 1
            for (start in 0 until limit) {
                ngramSb.setLength(0)
                for (k in 0 until gramLen) ngramSb.append(text[start + k])
                charIndex[ngramSb.toString()]?.let { charCounts[it]++ }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // L2 normalización por bloque (igual que TfidfVectorizer norm='l2')
    // ────────────────────────────────────────────────────────────────────────

    private fun l2BlockInto(
        counts: IntArray,
        idf: DoubleArray,
        out: DoubleArray,
        offset: Int,
        dim: Int,
    ) {
        var sumSq = 0.0
        for (i in 0 until dim) {
            val v = counts[i] * idf[i]
            out[offset + i] = v
            sumSq += v * v
        }
        val norm = sqrt(sumSq)
        if (norm > 0.0) {
            val inv = 1.0 / norm
            for (i in offset until offset + dim) out[i] *= inv
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Softmax sobre logits
    // ────────────────────────────────────────────────────────────────────────

    private fun computeLogitsAndSoftmax() {
        for (c in 0 until nClasses) {
            val base = c * totalDim
            var dot  = intercept[c]
            for (j in 0 until totalDim) dot += coef[base + j] * feature[j]
            logits[c] = dot
        }
        var maxL = logits[0]
        for (c in 1 until nClasses) if (logits[c] > maxL) maxL = logits[c]

        var sum = 0.0
        for (c in 0 until nClasses) {
            val e = exp(logits[c] - maxL)
            probs[c] = e
            sum += e
        }
        val invSum = 1.0 / sum
        for (c in 0 until nClasses) probs[c] *= invSum
    }

    // ────────────────────────────────────────────────────────────────────────
    // Señales semánticas  [FIX-3]
    //
    // Se añade la categoría job_offer completa y los tokens faltantes en
    // recruitment_kw y urgency, alineando con HybridPipeline.critical_tokens.
    // ────────────────────────────────────────────────────────────────────────

    private fun getSignals(t: String): List<String> {
        val signals = mutableListOf<String>()

        // high_risk_emoji
        if (symRiskEmojis.any { t.contains(it) })
            signals.add("high_risk_emoji")

        // recruitment_kw — lista completa [FIX-3]
        val recruitKw = listOf(
            "ng", "nueva generacion", "maña", "chapizza", "701",
            "4letras", "4 letras", "cuatroletras", "cuatro letras",
            "ndgc", "cha🍕"
        )
        if (recruitKw.any { t.contains(it) })
            signals.add("recruitment_kw")

        // job_offer — categoría ausente en versión anterior [FIX-3]
        val jobOffer = listOf(
            "jale", "paga", "chamba", "recluta",
            "contratando", "vacante", "sueldo", "feria", "lana"
        )
        if (jobOffer.any { t.contains(it) })
            signals.add("job_offer")

        // urgency — se añade "manden mensaje" [FIX-3]
        val urgency = listOf(
            "no le digas", "manda inbox", "dinero facil",
            "paga diaria", "manden mensaje"
        )
        if (urgency.any { t.contains(it) })
            signals.add("urgency")

        return signals
    }

    // ────────────────────────────────────────────────────────────────────────
    // Symbols Scorer
    // ────────────────────────────────────────────────────────────────────────

    private fun scoreSymbols(t: String): Int {
        var points = 0

        val emojiHits = symRiskEmojis.filter { t.contains(it) }
        if (emojiHits.isNotEmpty())          points += emojiHits.size * 2
        if (emojiHits.toSet().size >= 2)     points += 1
        if (symSocialContext.any  { t.contains(it) }) points -= 2
        if (symGroupCodes.any     { t.contains(it) }) points += 1
        if (t.split(" ").size <= 6)          points += 1
        if (symRecruitVerbs.any   { t.contains(it) }) points -= 2

        return points
    }

    // ────────────────────────────────────────────────────────────────────────
    // Threat Ruler  [FIX-4]
    //
    // Extorsión ahora acumula hasta +4 con count(), igual que Python:
    //   points += 2 * min(len(extortion_hits), 2)
    // ────────────────────────────────────────────────────────────────────────

    private fun scoreThreats(t: String): Int {
        var points = 0

        // +3 por primer verbo de daño físico encontrado
        if (thrPhysicalHarm.any { t.contains(it) })    points += 3

        // +2 o +4 dependiendo de cuántos patrones de extorsión coinciden [FIX-4]
        val extHits = thrExtortion.count { t.contains(it) }
        if (extHits > 0) points += 2 * minOf(extHits, 2)

        // +2 por ultimátum
        if (thrUltimatum.any { t.contains(it) })       points += 2

        // +2 por ubicación de víctima (solo el primero)
        if (thrLocation.any { t.contains(it) })        points += 2

        // +1 por mención de arma
        if (thrWeapons.any { t.contains(it) })         points += 1

        // +1 por condicional explícito
        if (thrConditionals.any { t.contains(it) })    points += 1

        // -3 contexto policial / denuncia
        if (thrSafeContext.any { t.contains(it) })     points -= 3

        // -2 contexto ficticio / hipotético
        if (thrFictional.any { t.contains(it) })       points -= 2

        return points
    }

    // ────────────────────────────────────────────────────────────────────────
    // Companion
    // ────────────────────────────────────────────────────────────────────────

    companion object {
        private const val DEFAULT_ASSET = "kipi_model_data.json"
        private const val CHAR_MIN_N    = 2
        private const val CHAR_MAX_N    = 5

        /**
         * [FIX-5] Patrón equivalente a sklearn (?u)\b\w\w+\b.
         * Usa \p{L}\p{N} para incluir letras acentuadas y caracteres Unicode.
         * Mínimo 2 caracteres, igual que el patrón de sklearn.
         * Se usa UNICODE_CASE en lugar de UNICODE_CHARACTER_CLASS para evitar
         * IllegalArgumentException en algunas versiones de Android.
         */
        private val TOKEN_PATTERN: Pattern = Pattern.compile(
            "[\\p{L}\\p{N}_]{2,}",
            Pattern.UNICODE_CASE or Pattern.CASE_INSENSITIVE
        )

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