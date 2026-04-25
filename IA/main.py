import pandas as pd
import numpy as np
import re
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import classification_report, confusion_matrix
import json
import numpy as np
import os

version = "v1"

# --- 1. LIMPIEZA DE TEXTO ---
def clean_text(text):
    if not isinstance(text, str):
        return ""
    text = text.lower()
    # Mantenemos emojis y símbolos porque son señales críticas en tu tabla
    text = re.sub(r'\s+', ' ', text).strip()
    return text

# --- 2. MOTOR DE REGLAS (HEURÍSTICAS) ---
class RuleBooster:
    def __init__(self):
        # Basado en tu tabla de emojis y términos de reclutamiento
        self.critical_tokens = {
            "high_risk_emoji": ["🍕", "🐔", "🥷", "🪖", "🧿", "👺", "🆖"],
            "recruitment_keywords": ["ng", "nueva generacion", "maña", "chapizza", "701"],
            "urgency": ["no le digas", "manda inbox", "dinero facil", "paga diaria"]
        }

    def get_signals(self, text):
        text = text.lower()
        found = []
        for category, patterns in self.critical_tokens.items():
            if any(p in text for p in patterns):
                found.append(category)
        return found

# --- 3. ENTRENAMIENTO ---
def train_and_evaluate():
    # Cargar datasets
    train_df = pd.read_csv('train.csv')
    val_df = pd.read_csv('val.csv')
    test_df = pd.read_csv('test.csv')

    # Preprocesar
    train_df['clean'] = train_df['texto'].apply(clean_text)
    val_df['clean'] = val_df['texto'].apply(clean_text)
    test_df['clean'] = test_df['texto'].apply(clean_text)

    # Codificar etiquetas
    le = LabelEncoder()
    y_train = le.fit_transform(train_df['clase'])
    y_val = le.transform(val_df['clase'])
    y_test = le.transform(test_df['clase'])

    # Llamamos a la función para obtener el pipeline optimizado con los pesos ajustados
    model = get_optimized_pipeline(le)

    # Ajuste
    model.fit(train_df['clean'], y_train)

    # Validación
    val_score = model.score(val_df['clean'], y_val)
    print(f"Precisión en Validación: {val_score:.4f}")

    # Evaluación Final con Test
    y_pred = model.predict(test_df['clean'])
    print("\n--- REPORTE DE CLASIFICACIÓN (TEST) ---")
    print(classification_report(y_test, y_pred, target_names=le.classes_))

    # Guardar para producción
    os.makedirs('models', exist_ok=True)
    os.makedirs("assets", exist_ok=True)
    joblib.dump(model, f'models/risk_model_{version}.pkl')
    joblib.dump(le, f'models/label_encoder_{version}.pkl')

    return model, le

# --- 4. CLASE DE INFERENCIA (RESULTADO JSON) ---
class RiskInference:
    def __init__(self, model_path, encoder_path):
        self.model = joblib.load(model_path)
        self.le = joblib.load(encoder_path)
        self.booster = RuleBooster()
        self.risk_map = {
            "SAFE": 0, "SYMBOLS": 1, "BELONGING": 2, "MIXED": 3, "HIGH_RISK": 4
        }

    def analyze(self, text):
        clean = clean_text(text)
        signals = self.booster.get_signals(clean)
        
        # Predicción probabilística
        probs = self.model.predict_proba([clean])[0]
        max_idx = np.argmax(probs)
        label = self.le.inverse_transform([max_idx])[0]
        confidence = probs[max_idx]

        # Lógica de Refuerzo (Boost)
        # Si hay señales críticas pero el modelo duda, subimos la categoría
        if len(signals) >= 2 and label not in ["HIGH_RISK", "SYMBOLS", "MIXED"]:
            label = "MIXED"
            confidence += 0.1

        return {
            "text": text,
            "level": self.risk_map.get(label, 0),
            "label": label,
            "confidence": round(float(min(confidence, 1.0)), 2),
            "signals": signals if signals else ["pattern_match"]
        }

# --- 5. AJUSTE DE PESOS DINÁMICO ---
def get_optimized_pipeline(le):
    # Definimos los pesos usando los nombres de las clases
    text_weights = {
        "SAFE": 1.0,
        "SYMBOLS": 4.5,
        "BELONGING": 2.0,
        "HIGH_RISK": 2.2,
        "MIXED": 1.0
    }
    
    # Convertimos los nombres a los números que el modelo entiende
    # Esto soluciona el ValueError
    numeric_weights = {le.transform([label])[0]: weight for label, weight in text_weights.items()}

    # Vectorización Híbrida Ajustada (n-grams de caracteres desde 2)
    union = FeatureUnion([
        ("word_tfidf", TfidfVectorizer(
            ngram_range=(1, 2), 
            max_features=3000, 
            min_df=1
        )),
        ("char_tfidf", TfidfVectorizer(
            ngram_range=(2, 5), # Bajamos a 2 para captar símbolos aislados
            analyzer='char', 
            max_features=8000,
            min_df=1
        ))
    ])

    pipeline = Pipeline([
        ('vectorizer', union),
        ('classifier', LogisticRegression(
            max_iter=2000, 
            class_weight=numeric_weights, # Usamos el diccionario numérico
            C=10.0, 
            solver='lbfgs'
        ))
    ])
    
    return pipeline

# Clase para manejar tipos de datos de NumPy en JSON
class NpEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, np.integer):
            return int(obj)
        if isinstance(obj, np.floating):
            return float(obj)
        if isinstance(obj, np.ndarray):
            return obj.tolist()
        return super(NpEncoder, self).default(obj)

# --- 6. Exportar modelo ---
def export_kipi_model(pipeline, le, filename="assets/kipi_model_data.json"):
    vectorizer = pipeline.named_steps['vectorizer']
    classifier = pipeline.named_steps['classifier']
    
    word_v = vectorizer.transformer_list[0][1]
    char_v = vectorizer.transformer_list[1][1]
    
    model_data = {
        "metadata": {
            "project": "KipiSafe - ESCOM",
            "classes": le.classes_.tolist(),
            "risk_levels": {"SAFE": 0, "SYMBOLS": 1, "BELONGING": 2, "MIXED": 3, "HIGH_RISK": 4}
        },
        "weights": classifier.coef_.tolist(),
        "intercepts": classifier.intercept_.tolist(),
        "vocabulary_word": word_v.vocabulary_,
        "idf_word": word_v.idf_.tolist(),
        "vocabulary_char": char_v.vocabulary_,
        "idf_char": char_v.idf_.tolist(),
        "params": {
            "ngram_word": [1, 2],
            "ngram_char": [2, 5]
        }
    }
    
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(model_data, f, ensure_ascii=False, indent=2, cls=NpEncoder)
    
    print(f"Parámetros exportados correctamente a {filename}")

# --- EJECUCIÓN ---
if __name__ == "__main__":
    # 1. Entrenar
    model, le = train_and_evaluate()
    
    # 2. Probar inferencia
    try:
        engine = RiskInference(f'models/risk_model_{version}.pkl', f'models/label_encoder_{version}.pkl')
        
        test_phrases = [
            "Puro 🍕 reportando, manda dm para jale 💰",
            "Hola mamá, ya voy para la casa",
            "Aquí somos familia 🐔 nadie te deja solo carnal"
        ]
        
        for p in test_phrases:
            print(json.dumps(engine.analyze(p), indent=2, ensure_ascii=False))

        export_kipi_model(model, le)

    except FileNotFoundError:
        print("Primero entrena el modelo para generar los archivos .pkl")