# KipiSafe AI Documentation

## Documentación de Inteligencia Artificial: KipiSafe AI

### 1. Descripción del Modelo y Problema
Nuestra IA está diseñada para detectar patrones de reclutamiento forzado y lenguaje de riesgo en mensajes de texto dirigidos a menores. El problema central es que estos grupos utilizan jerga cifrada, emojis específicos y variaciones ortográficas para evadir filtros convencionales.

### 2. Decisión de Implementación: ¿Por qué este Stack?
Decidimos utilizar un enfoque **híbrido avanzado** combinando Machine Learning (Regresión Logística con Feature Engineering personalizado) y un Motor de Reglas (Heurísticas):

* **Técnica Híbrida (Reglas + ML):** El texto pasa primero por validadores heurísticos (`SymbolsScorer` y `ThreatRuler`) que atrapan casos críticos evidentes (emojis de grupo o amenazas de extorsión explícitas). Si el texto no es atrapado por las reglas, pasa al pipeline de Machine Learning.

* **Vectores de Texto y Feature Engineering:** Utilizamos `FeatureUnion` para combinar:

  * `TfidfVectorizer`: Enfoque de n-gramas de caracteres (2 a 5) y palabras, lo que nos permite detectar "señales" dentro de palabras mal escritas (ej. ch4p1zz4).

  * `AggressionFeaturizer` & `EmojiFeaturizer`: Clases personalizadas que extraen características numéricas (proporción de mayúsculas, signos de puntuación, coincidencia con léxico de bullying/amenaza) para darle más contexto al modelo estadístico.

* **Balance de Clases (Class Weighting):** Implementamos pesos dinámicos en el modelo para priorizar la detección de clases críticas (como `THREAT`, `BULLYING` y `HIGH_RISK`), asegurando que el modelo no sea "perezoso" y evite los falsos negativos.

### 3. Stack Tecnológico
- Lenguaje: Python 3.x
- Librerías Core:
  - `Scikit-learn` & `SciPy`: Para la construcción del Pipeline Híbrido, vectorización, extracción de características (matrices ralas / csr_matrix) y entrenamiento.
  - Pandas/NumPy: Procesamiento de datos.
  - Joblib: Serialización del modelo para producción.
- Re: Motor de limpieza basado en expresiones regulares.

### 4. Registro de Iteraciones y Mejora Continua (Model Versioning)
Para garantizar la escalabilidad del proyecto, llevamos un registro de cómo cada cambio impacta en la precisión.
Para consultar la información sobre las iteraciones entre cada iteración del entrenamiento. Revisar `results.md`



### 5. Instrucciones de Ejecución (IA Module)
- **Instalar dependencias:** 
```bash
pip install pandas numpy scikit-learn joblib
```
- **Entrenamiento:** Ejecuta `python main.py` (asegúrate de tener los archivos `train.csv`, `val.csv` y `test.csv` en la raíz).

- **Salida:** El script genera dentro de la carpeta `models/` y `assets/`:
    * `risk_model_v3.pkl` y `label_encoder_v3.pkl`: Modelo binario y codificador de etiquetas para pruebas en Python.
    * `kipi_model_data.json`: **Archivo de exportación principal**. Contiene la arquitectura híbrida, umbrales de reglas, léxicos, pesos e interceptos TF-IDF listos para ser consumidos por el cliente Android (Edge AI).

### 6. Herramientas de IA utilizadas
- **Scikit-Learn:** Motor estadístico principal de clasificación.
- **ChatGPT/Gemini:** Utilizados para la generación de datos sintéticos de entrenamiento (aumentando nuestro dataset inicial) y revisión de la lógica de limpieza de texto y construcción de los Featurizers. Así mismo, se utilizó para estructurar la documentación del módulo.

### 7. Categorías de Clasificación de Riesgo
Para entender cómo nuestra IA evalúa las conversaciones, el modelo clasifica el texto en **siete niveles distintos**. Cada categoría captura un patrón de comportamiento específico, permitiéndonos separar las conversaciones cotidianas de los diferentes riesgos (reclutamiento, acoso o violencia).

**SAFE (Nivel 0) — Mensajes seguros**
Conversaciones cotidianas sin ninguna señal de riesgo. Abarca pláticas sobre familia, amigos, escuela o planes del día sin presencia de emojis de grupo, violencia o acoso.
* Pueden estar escritos en cualquier tono: informal, formal o divertido.
* Las menciones sobre dinero, trabajo o familia son completamente válidas, ya que el contexto general lo hace seguro.
* Los emojis comunes (❤️, 😂, 🙏, etc.) están permitidos, excluyendo únicamente los críticos identificados en nuestro motor de reglas.
> Ejemplos:
> "ya voy pa la casa wey, guarda algo de comer"
> "oye me prestas tus apuntes de calculo? es q no fui"

**SYMBOLS (Nivel 1) — Solo emojis de grupo**
Mensajes muy cortos, típicamente encontrados en estados, reportes de zona o códigos rápidos entre miembros de un grupo. El texto es mínimo o inexistente.
* Suelen tener un máximo de 6 a 7 palabras en total (incluyendo el emoji).
* Pueden estar compuestos exclusivamente por emojis, o un emoji acompañado de 1 a 3 palabras breves.
* Si el mensaje contiene texto más largo o un contexto más elaborado, el modelo lo evalúa como MIXED.
> Ejemplos:
> "🍕 presente"
> "NG activo 🐔"

**BELONGING (Nivel 2) — Reclutamiento emocional**
Mensajes que apelan a la necesidad del menor de pertenecer, ser aceptado o tener una "familia". El gancho es netamente emocional, sin motivaciones económicas explícitas.
* Utiliza palabras clave de familiaridad: familia, carnal, banda, hermano, "nadie te juzga", "siempre te cuidamos".
* Carece de ofertas directas de dinero, trabajo o protección armada.
* No incluye los emojis de grupo catalogados como de alto riesgo.
* El tono es cálido, cercano y persuasivo.
> Ejemplos:
> "aqui nadie te juzga carnal, somos familia de verdad"
> "con nosotros nunca vas a estar solo, la banda siempre cuida a los suyos"

**MIXED (Nivel 3) — Señales contradictorias**
La categoría más compleja. El mensaje contiene emojis de grupo o léxico de riesgo, pero el contexto sugiere que es inocente o inofensivo. Por seguridad, el sistema siempre deriva estos casos al LLM del backend para una segunda opinión y desempate.
* Tipo 1 (Videojuegos): Nombres de clanes, gamertags, roles en partidas online.
* Tipo 2 (Noticias/Comentario): Personas hablando sobre el crimen sin participar en él.
* Tipo 3 (Memes/Humor): Bromas, sarcasmo o uso de los emojis sin seriedad literal.
* Demuestra la capacidad del modelo para entender el contexto: un mismo emoji puede ser `HIGH_RISK` o `MIXED` dependiendo de las palabras.
> Ejemplos:
> "busco clan en cod mobile, puros 🐔 activos pa jugar en las noches"
> "wey ya deja de mandar tiktoks de la 🍕 pareces alucin"

**HIGH_RISK (Nivel 4) — Reclutamiento activo y directo**
Representa una oferta concreta de dinero, trabajo o protección, casi siempre acompañada de emojis de grupo. Pasa de la persuasión emocional a una propuesta tangible y peligrosa.
* Contiene obligatoriamente la combinación de un emoji de grupo más una oferta económica o laboral.
* Emplea palabras clave transaccionales: jale, chamba, sueldo, paga, feria, dm, inbox, vacante.
> Ejemplos:
> "Puro 🍕 reportando, manda dm para jale 💰"
> "NG activo 🐔 buscando raza pa el equipo, paga semanal garantizada"

**BULLYING (Nivel 5) — Acoso digital entre jóvenes**
Mensajes de acoso en redes sociales, grupos de WhatsApp o plataformas digitales. El daño es reputacional y emocional. Sin violencia física (eso es THREAT).
* Contexto típico: escuela secundaria o prepa, grupos de amigos, redes sociales.
* Subcategorías comunes: exclusión social, humillación pública, doxing, difusión de contenido privado.
* Palabras clave: funar, doxear, quemar, stickers, hilo, pack, todos saben, nadie te pela.
* El tono puede ser pasivo-agresivo o muy directo, comúnmente con mala ortografía real (k, q, xq, ps, pa).
> Ejemplos:
> "te vamos a funar en twitter por ridiculo"
> "pasa el pack o lo invento y te quemo de todos modos"
> "te voy a doxear con nombre y todo pa q veas wey"

**THREAT (Nivel 6) — Amenaza de daño físico o extorsión**
Amenaza de violencia física, cobro de piso o vigilancia de la víctima. El daño amenazado es físico o económico grave. Es muy distinto a BULLYING.
* Tipos principales: daño físico directo, cobro de piso/extorsión, ultimátum, vigilancia.
* Palabras clave: levantar, picar, quemar el local, cobro de piso, cuota, no amaneces, ya te ubicamos.
* Puede mencionar armas (plomo, fierro, cargador, cuerno) y lenguaje condicional ('o pagas o...', 'si no cooperas...').
> Ejemplos:
> "cobro de piso, pagas o no amaneces"
> "ya te tenemos ubicado, un movimiento en falso y te levantamos"
> "te voy a picar si no me das el celular ahorita"