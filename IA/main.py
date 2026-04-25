import pandas as pd
import numpy as np
import re
import joblib
import json
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline
from sklearn.preprocessing import LabelEncoder
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.metrics import classification_report
from scipy.sparse import hstack, csr_matrix
import os

version = "v5"

# ─────────────────────────────────────────────
# 1. LIMPIEZA DE TEXTO
# ─────────────────────────────────────────────
def clean_text(text):
    if not isinstance(text, str):
        return ""
    text = re.sub(r'\s+', ' ', text).strip()
    return text  # NO lower() — preservamos mayúsculas para AggressionFeaturizer


def clean_text_lower(text):
    """Versión lowercase para TF-IDF y scorers de reglas."""
    return clean_text(text).lower()


# ─────────────────────────────────────────────
# 2. FEATURIZER DE AGRESIVIDAD  ✦ NUEVO
#    6 features numéricas que el ML puede usar
#    para detectar BULLYING y THREAT.
# ─────────────────────────────────────────────
class AggressionFeaturizer(BaseEstimator, TransformerMixin):
    """
    Features de señal de agresividad lingüística:
      [0] caps_ratio      — proporción de mayúsculas en texto (0-1)
      [1] exclamations    — número de signos !
      [2] questions       — número de signos ?
      [3] threat_verbs    — coincidencias con léxico de amenaza física
      [4] bully_verbs     — coincidencias con léxico de acoso digital
      [5] insults         — coincidencias con insultos directos
    """

    THREAT_VERBS = [
        "matar", "mato", "mates", "levantar", "levantamos", "levantón",
        "picar", "quemar", "quemamos", "encajuelar", "encajuelamos",
        "plomear", "plomazo", "plomazos", "tablear", "tableo",
        "jalar el gatillo", "vaciar el cargador", "dar piso",
        "sentenciado", "cobro de piso", "desaparecer", "entierro",
        "balacera", "rafaguear", "ejecutar", "ultimátum", "ultimatum",
        "no amaneces", "te buscamos", "te encontramos", "te ubicamos",
        "sabemos dónde", "sabemos donde", "tenemos ubicado",
        "pelarte", "lárgate", "24 horas", "último aviso", "ultimo aviso",
    ]

    BULLY_VERBS = [
        "funar", "quemar", "quemar en grupos", "doxear", "stalkear",
        "filtrar", "subir tu número", "mandar a la gorra",
        "hilo", "stickers con tu cara", "nadie te pela", "nadie te topa",
        "todos se ríen", "te odian", "te corran", "firmamos contra",
        "grupo sin ti", "bloqueado", "eliminado", "reportado",
        "pack", "exponer", "inventar", "quemar de todos modos",
        "borra tus cuentas", "cámbiate de escuela",
    ]

    INSULTS = [
        "idiota", "imbécil", "pendejo", "pendeja", "pend3jo",
        "estúpido", "estúpida", "inútil", "basura", "asco",
        "perra", "perro", "maldito", "maldita", "pinche",
        "culero", "culera", "güey", "wey",
        "psiquiátrica", "lástima", "cringe", "vergüenza das",
        "espantaviejas", "espantapiejas", "alucín",
    ]

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        rows = []
        for text in X:
            t_lower = text.lower()
            letters = [c for c in text if c.isalpha()]
            caps_ratio = (
                sum(1 for c in letters if c.isupper()) / len(letters)
                if letters else 0.0
            )
            exclamations = text.count('!')
            questions    = text.count('?')
            threat_hits  = sum(1 for v in self.THREAT_VERBS if v in t_lower)
            bully_hits   = sum(1 for v in self.BULLY_VERBS  if v in t_lower)
            insult_hits  = sum(1 for v in self.INSULTS       if v in t_lower)
            emoji_density = sum(text.count(e) for e in ["🍕","🐔","🥷","🪖","👁️","👺"]) / max(len(text.split()), 1)
            rows.append([
                caps_ratio, exclamations, questions,
                threat_hits, bully_hits, insult_hits, emoji_density
            ])
        return csr_matrix(np.array(rows, dtype=np.float32))


# ─────────────────────────────────────────────
# 3. CLASIFICADOR DE SÍMBOLOS (sin cambios)
# ─────────────────────────────────────────────
class SymbolsScorer:
    RISK_EMOJIS   = ["🍕", "🐔", "🥷", "🪖", "👁️", "👺"]
    GROUP_CODES   = ["ng", "701", "chapizza", "maña", "nueva generacion",
                     "nueva generación"]
    RECRUIT_VERBS = ["manda", "entra", "jale", "paga", "lana", "inbox",
                     "dm", "dinero", "reclut", "vacante", "oportunidad",
                     "trabajo", "chamba", "sueldo", "feria"]
    SOCIAL_CONTEXT = ["familia", "mamá", "papá", "hermano", "hermana",
                      "amigo", "carnal", "nadie", "somos", "aquí",
                      "solo", "casa", "equipo", "barrio"]

    def __init__(self, threshold=3):
        self.threshold = threshold

    def score(self, text: str) -> dict:
        t = text.lower()
        points = 0
        breakdown = []

        emoji_hits = [e for e in self.RISK_EMOJIS if e in t]
        if emoji_hits:
            pts = len(emoji_hits) * 2
            points += pts
            breakdown.append(f"+{pts} emojis tabla")

        if len(set(emoji_hits)) >= 2:
            points += 1
            breakdown.append("+1 diversidad emojis")

        stripped = re.sub(r'[\s]', '', t)
        non_space = stripped.replace(' ', '').replace('\t', '')
        only_emojis = (
            len(non_space) > 0
            and bool(emoji_hits)
            and all(
                any(non_space[i:].startswith(e) for e in self.RISK_EMOJIS)
                for i in range(len(non_space))
                if not any(non_space[i:].startswith(e) for e in self.RISK_EMOJIS)
                # solo verifica posiciones que NO son inicio de emoji conocido
            ) is False  # si hay algún char que no sea emoji → False
        )

        
        if only_emojis:
            points += 2
            breakdown.append("+2 solo emojis")

        social_hits = [w for w in self.SOCIAL_CONTEXT if w in t]
        if social_hits:
            points -= 2
            breakdown.append(f"-2 contexto social")

        for code in self.GROUP_CODES:
            if code in t:
                points += 1
                breakdown.append(f"+1 código '{code}'")
                break

        if len(t.split()) <= 6:
            points += 1
            breakdown.append("+1 mensaje corto")

        recruit_hits = [v for v in self.RECRUIT_VERBS if v in t]
        if recruit_hits:
            points -= 2
            breakdown.append(f"-2 verbos reclutamiento")

        return {
            "points"    : points,
            "is_symbols": points >= self.threshold,
            "breakdown" : breakdown
        }


# ─────────────────────────────────────────────
# 4. CLASIFICADOR DE AMENAZAS POR REGLAS  ✦ NUEVO
#    Atrapa amenazas físicas explícitas antes
#    del ML (igual que SymbolsScorer con emojis).
# ─────────────────────────────────────────────
class ThreatRuler:
    """
    Sistema de puntaje para THREAT explícitas.
    Umbral por defecto: 4 puntos → THREAT directo.

    Tabla de puntaje:
      +3  verbo de daño físico confirmado (matar, picar, quemar local...)
      +2  cobro de piso / extorsión (cuota, renta, piso, coopera)
      +2  ultimátum temporal (24 horas, último aviso, no amaneces)
      +2  ubicación de víctima (sabemos dónde, tenemos ubicado, te seguimos)
      +1  mención de arma (plomo, cargador, gatillo, balacera, navaja)
      +1  condicionante explícito ("o pagas o", "si no pagas", "o te")
      -3  contexto policial/denuncia (denuncia, ministerio, reporte, queja)
      -2  contexto hipotético/ficticio (si fuera, en la peli, cuento, historia)
    """

    PHYSICAL_HARM = [
        "matar", "mato", "te mato", "voy a matar", "vamos a matar",
        "picar", "te pico", "levantar", "levantón", "te levantamos",
        "encajuelar", "dar piso", "dar un plomazo", "plomazos",
        "vaciar el cargador", "jalar el gatillo", "rafaguear",
        "tablear", "quebrar", "te quebramos", "ejecutar",
        "no amaneces", "te enterramos", "te desaparecemos",
        "te voy a matar", "te mato", "se muere", "me la cobras",
    ]

    EXTORTION = [
        "cobro de piso", "cuota", "paga la renta", "coopera",
        "paga o", "pagar o", "feria o", "lana o",
        "pasas la feria", "dame dinero", "o quemo", "o te quemo",
    ]

    ULTIMATUM = [
        "24 horas", "último aviso", "ultimo aviso", "no amaneces",
        "te vas de aquí", "pelarte de la ciudad", "tienes horas",
        "última oportunidad", "ultima oportunidad", "date prisa",
        "se acaba el tiempo",
    ]

    LOCATION_OF_VICTIM = [
        "ya te tenemos ubicado", "sabemos dónde vives", "sabemos donde vives",
        "sabemos a qué escuela", "sabemos en qué carro",
        "tenemos ubicado", "te seguimos", "dando rondines",
        "afuera de tu", "ya estamos afuera",
    ]

    WEAPONS = [
        "plomo", "plomazo", "cargador", "gatillo", "balacera",
        "cuerno", "pistola", "fierro", "navaja", "cuchillo",
        "rafaga", "ráfaga", "calibre", "balazo",
    ]

    CONDITIONALS = [
        "o pagas o", "si no pagas", "o te", "o si no",
        "de lo contrario", "de no ser así", "a menos que",
        "si no cooperas", "si no te abres",
    ]

    SAFE_CONTEXT = [
        "denuncia", "ministerio", "reporte", "queja", "policía",
        "policia", "ley", "abogado",
    ]

    FICTIONAL = [
        "si fuera", "en la película", "en la pelicula", "en el cuento",
        "historia de", "personaje", "novela", "serie", "capítulo",
    ]

    def __init__(self, threshold=4):
        self.threshold = threshold

    def score(self, text: str) -> dict:
        t = text.lower()
        points = 0
        breakdown = []

        for v in self.PHYSICAL_HARM:
            if v in t:
                points += 3
                breakdown.append(f"+3 daño físico '{v}'")
                break

        extortion_hits = [v for v in self.EXTORTION if v in t]
        if extortion_hits:
            points += 2 * min(len(extortion_hits), 2)  # máx +4 por extorsión
            breakdown.append(f"+{2*min(len(extortion_hits),2)} extorsión {extortion_hits[:2]}")

        ultimatum_hits = [v for v in self.ULTIMATUM if v in t]
        if ultimatum_hits:
            points += 2
            breakdown.append(f"+2 ultimátum {ultimatum_hits[:1]}")

        for v in self.LOCATION_OF_VICTIM:
            if v in t:
                points += 2
                breakdown.append(f"+2 ubicación '{v}'")
                break

        weapon_hits = [w for w in self.WEAPONS if w in t]
        if weapon_hits:
            points += 1
            breakdown.append(f"+1 arma {weapon_hits[:2]}")

        for c in self.CONDITIONALS:
            if c in t:
                points += 1
                breakdown.append(f"+1 condicional '{c}'")
                break

        safe_hits = [s for s in self.SAFE_CONTEXT if s in t]
        if safe_hits:
            points -= 3
            breakdown.append(f"-3 contexto policial {safe_hits}")

        fictional_hits = [f for f in self.FICTIONAL if f in t]
        if fictional_hits:
            points -= 2
            breakdown.append(f"-2 contexto ficticio {fictional_hits}")

        return {
            "points"    : points,
            "is_threat" : points >= self.threshold,
            "breakdown" : breakdown
        }


# ─────────────────────────────────────────────
# 5. FEATURIZER DE EMOJIS (sin cambios)
# ─────────────────────────────────────────────
class EmojiFeaturizer(BaseEstimator, TransformerMixin):
    RISK_EMOJIS = ["🍕", "🐔", "🥷", "🪖", "👁️", "👺"]

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        rows = []
        for text in X:
            count     = sum(text.count(e) for e in self.RISK_EMOJIS)
            unique    = len(set(e for e in self.RISK_EMOJIS if e in text))
            two_plus  = int(count >= 2)
            short_msg = int(len(text.split()) <= 6)
            rows.append([count, unique, two_plus, short_msg])
        return csr_matrix(np.array(rows, dtype=np.float32))


# ─────────────────────────────────────────────
# 6. PIPELINE HÍBRIDO COMPLETO  ✦ ThreatRuler añadido
# ─────────────────────────────────────────────
class HybridPipeline:
    def __init__(self, ml_pipeline, label_encoder,
                 symbols_threshold=3, threat_threshold=4):
        self.ml_pipeline    = ml_pipeline
        self.le             = label_encoder
        self.symbols_scorer = SymbolsScorer(threshold=symbols_threshold)
        self.threat_ruler   = ThreatRuler(threshold=threat_threshold)
        self.risk_map = {
            "SAFE": 0, "SYMBOLS": 1, "BELONGING": 2,
            "MIXED": 3, "HIGH_RISK": 4,
            "BULLYING": 5, "THREAT": 6
        }
        self.critical_tokens = {
            "high_risk_emoji" : ["🍕", "🐔", "🥷", "🪖", "👁️", "👺"],
            "recruitment_kw"  : ["ng", "nueva generacion", "maña",
                                 "chapizza", "701"],
            "urgency"         : ["no le digas", "manda inbox",
                                 "dinero facil", "paga diaria"]
        }

    def _get_signals(self, text):
        t = text.lower()
        return [cat for cat, pats in self.critical_tokens.items()
                if any(p in t for p in pats)]

    def predict(self, text: str) -> dict:
        clean   = clean_text(text)          # preserva mayúsculas
        clean_l = clean_text_lower(text)    # lowercase para reglas
        signals = self._get_signals(clean_l)

        # ── Paso 1: SymbolsScorer ──────────────────
        sym = self.symbols_scorer.score(clean_l)
        if sym["is_symbols"]:
            return {
                "text"        : text,
                "level"       : self.risk_map["SYMBOLS"],
                "label"       : "SYMBOLS",
                "confidence"  : round(min(0.5 + sym["points"] * 0.08, 0.95), 2),
                "signals"     : signals or ["symbol_score"],
                "symbol_score": sym["points"],
                "source"      : "rules"
            }

        # ── Paso 2: ThreatRuler ────────────────────
        thr = self.threat_ruler.score(clean_l)
        if thr["is_threat"]:
            return {
                "text"         : text,
                "level"        : self.risk_map["THREAT"],
                "label"        : "THREAT",
                "confidence"   : round(min(0.5 + thr["points"] * 0.07, 0.95), 2),
                "signals"      : signals or ["threat_score"],
                "threat_score" : thr["points"],
                "source"       : "rules"
            }

        # ── Paso 3: Pipeline ML ────────────────────
        probs   = self.ml_pipeline.predict_proba([clean])[0]
        max_idx = int(np.argmax(probs))
        label   = self.le.inverse_transform([max_idx])[0]
        conf    = float(probs[max_idx])

        # ── Paso 4: RuleBooster (no toca SYMBOLS/THREAT) ─
        if (len(signals) >= 2 
            and label not in ["HIGH_RISK", "MIXED", "SYMBOLS", "THREAT"]
            and conf < 0.75):   # solo boost si el ML no está seguro
            label = "MIXED"
            conf  = min(conf + 0.1, 1.0)

        return {
            "text"      : text,
            "level"     : self.risk_map.get(label, 0),
            "label"     : label,
            "confidence": round(conf, 2),
            "signals"   : signals or ["pattern_match"],
            "source"    : "ml"
        }


# ─────────────────────────────────────────────
# 7. CONSTRUCCIÓN DEL PIPELINE ML  ✦ AggressionFeaturizer añadido
# ─────────────────────────────────────────────
def build_ml_pipeline(le):
    class_weights = {
        le.transform(["SAFE"])[0]       : 1.0,
        le.transform(["SYMBOLS"])[0]    : 1.5,
        le.transform(["BELONGING"])[0]  : 2.0,
        le.transform(["HIGH_RISK"])[0]  : 3.0,  
        le.transform(["MIXED"])[0]      : 1.8,
        le.transform(["BULLYING"])[0]   : 2.5,  # Bajamos de 4.0 a 2.5 para mejorar su Precisión
        le.transform(["THREAT"])[0]     : 3.0,  # Bajamos de 5.0 a 3.0 (dejamos que ThreatRuler haga el trabajo pesado)
    }

    union = FeatureUnion([
        ("word_tfidf", TfidfVectorizer(
            ngram_range=(1, 2),
            max_features=3000,
            min_df=1,
            lowercase=True,
        )),
        ("char_tfidf", TfidfVectorizer(
            ngram_range=(2, 5),
            analyzer='char',
            max_features=8000,
            min_df=1,
            lowercase=True,
        )),
        ("emoji_feat",      EmojiFeaturizer()),
        ("aggression_feat", AggressionFeaturizer()), 
    ])

    return Pipeline([
        ('vectorizer', union),
        ('classifier', LogisticRegression(
            max_iter=2000,
            class_weight=class_weights,
            C=3.0,
            solver='lbfgs'
        ))
    ])


# ─────────────────────────────────────────────
# 8. ENTRENAMIENTO Y EVALUACIÓN
# ─────────────────────────────────────────────
def train_and_evaluate():
    train_df = pd.read_csv('train.csv')
    val_df   = pd.read_csv('val.csv')
    test_df  = pd.read_csv('test.csv')

    for df in [train_df, val_df, test_df]:
        if 'texto' in df.columns:
            df.rename(columns={'texto': 'text'}, inplace=True)
        if 'clase' in df.columns:
            df.rename(columns={'clase': 'label'}, inplace=True)

    # clean_text preserva mayúsculas para AggressionFeaturizer;
    # TF-IDF hace su propio lowercase internamente
    for df in [train_df, val_df, test_df]:
        df['clean'] = df['text'].apply(clean_text)

    le = LabelEncoder()
    y_train = le.fit_transform(train_df['label'])
    y_val   = le.transform(val_df['label'])
    y_test  = le.transform(test_df['label'])

    ml_pipeline = build_ml_pipeline(le)
    ml_pipeline.fit(train_df['clean'], y_train)

    val_score = ml_pipeline.score(val_df['clean'], y_val)
    print(f"Precisión ML en Validación: {val_score:.4f}")

    hybrid = HybridPipeline(ml_pipeline, le,
                             symbols_threshold=3,
                             threat_threshold=4)

    preds, trues = [], []
    for _, row in test_df.iterrows():
        result = hybrid.predict(row['text'])
        preds.append(result['label'])
        trues.append(row['label'])

    print("\n--- REPORTE DE CLASIFICACIÓN (TEST — HÍBRIDO) ---")
    print(classification_report(trues, preds, target_names=sorted(le.classes_)))

    os.makedirs('models', exist_ok=True)
    os.makedirs('assets', exist_ok=True)
    joblib.dump(ml_pipeline, f'models/risk_model_{version}.pkl')
    joblib.dump(le,          f'models/label_encoder_{version}.pkl')
    print(f"\nModelo guardado: models/risk_model_{version}.pkl")

    return hybrid, le


# ─────────────────────────────────────────────
# 9. EXPORTAR PARÁMETROS PARA PRODUCCIÓN
# ─────────────────────────────────────────────
class NpEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, np.integer): return int(obj)
        if isinstance(obj, np.floating): return float(obj)
        if isinstance(obj, np.ndarray): return obj.tolist()
        return super().default(obj)


def export_model(pipeline, le, filename="assets/kipi_model_data.json"):
    vec    = pipeline.named_steps['vectorizer']
    clf    = pipeline.named_steps['classifier']
    word_v = vec.transformer_list[0][1]
    char_v = vec.transformer_list[1][1]

    data = {
        "metadata": {
            "project"     : "KipiSafe - ESCOM",
            "version"     : "3.0",
            "classes"     : le.classes_.tolist(),
            "risk_levels" : {
                "SAFE":0,"SYMBOLS":1,"BELONGING":2,
                "MIXED":3,"HIGH_RISK":4,"BULLYING":5,"THREAT":6
            },
            "architecture": "hybrid — SymbolsScorer + ThreatRuler + ML pipeline"
        },
        "symbols_scorer": {
            "threshold"    : 3,
            "risk_emojis"  : SymbolsScorer.RISK_EMOJIS,
            "group_codes"  : SymbolsScorer.GROUP_CODES,
            "recruit_verbs": SymbolsScorer.RECRUIT_VERBS,
            "social_context": SymbolsScorer.SOCIAL_CONTEXT,
        },
        "threat_ruler": {                          
            "threshold"      : 4,
            "physical_harm"  : ThreatRuler.PHYSICAL_HARM,
            "extortion"      : ThreatRuler.EXTORTION,
            "ultimatum"      : ThreatRuler.ULTIMATUM,
            "location"       : ThreatRuler.LOCATION_OF_VICTIM,
            "weapons"        : ThreatRuler.WEAPONS,
            "conditionals"   : ThreatRuler.CONDITIONALS,
            "safe_context"   : ThreatRuler.SAFE_CONTEXT,
            "fictional"      : ThreatRuler.FICTIONAL,
        },
        "aggression_featurizer": {                 # ✦ nuevo — para la app móvil
            "threat_verbs" : AggressionFeaturizer.THREAT_VERBS,
            "bully_verbs"  : AggressionFeaturizer.BULLY_VERBS,
            "insults"      : AggressionFeaturizer.INSULTS,
            "features"     : [
                "caps_ratio", "exclamations", "questions",
                "threat_hits", "bully_hits", "insult_hits"
            ],
        },
        "weights"         : clf.coef_.tolist(),
        "intercepts"      : clf.intercept_.tolist(),
        "vocabulary_word" : word_v.vocabulary_,
        "idf_word"        : word_v.idf_.tolist(),
        "vocabulary_char" : char_v.vocabulary_,
        "idf_char"        : char_v.idf_.tolist(),
        "params": {
            "ngram_word": [1, 2],
            "ngram_char": [2, 5],
        }
    }

    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2, cls=NpEncoder)
    print(f"Exportado: {filename}")


# ─────────────────────────────────────────────
# 10. EJECUCIÓN
# ─────────────────────────────────────────────
if __name__ == "__main__":
    hybrid, le = train_and_evaluate()

    print("\n--- PRUEBAS DE INFERENCIA ---")
    test_phrases = [
        # símbolos
        "Puro 🍕 reportando, manda dm para jale 💰",
        "Hola mamá, ya voy para la casa",
        "Aquí somos familia 🐔 nadie te deja solo carnal",
        "🍕 presente",
        "NG activo 🐔",
        # amenazas
        "te voy a picar si no me das el celular ahorita",
        "cobro de piso, pagas o no amaneces",
        "ya te tenemos ubicado, un movimiento en falso y te levantamos",
        "una llamada más a la placa y te mueres",
        # bullying
        "te vamos a funar en twitter por ridículo",
        "pasa el pack o lo invento y te quemo de todos modos",
        "voy a hacer un hilo contando toda la mierda que eres",
    ]

    for phrase in test_phrases:
        result = hybrid.predict(phrase)
        src = result.get('source', '')
        extra = ''
        if src == 'rules' and 'symbol_score' in result:
            extra = f" [sym={result['symbol_score']}]"
        elif src == 'rules' and 'threat_score' in result:
            extra = f" [thr={result['threat_score']}]"
        print(f"[{result['label']:<10} | {src:<5}{extra}] {phrase}")

    ml_pipeline = joblib.load(f'models/risk_model_{version}.pkl')
    export_model(ml_pipeline, le)