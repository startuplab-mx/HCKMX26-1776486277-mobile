# KipiSafe AI Documentation

## Documentación de Inteligencia Artificial: KipiSafe AI

### 1. Descripción del Modelo y Problema
Nuestra IA está diseñada para detectar patrones de reclutamiento forzado y lenguaje de riesgo en mensajes de texto dirigidos a menores. El problema central es que estos grupos utilizan jerga cifrada, emojis específicos y variaciones ortográficas para evadir filtros convencionales.

### 2. Decisión de Implementación: ¿Por qué este Stack?
Decidimos utilizar un enfoque híbrido combinando Machine Learning (Regresión Logística) con un Motor de Reglas (Heurísticas):

- Técnica: Utilización de `TfidfVectorizer` con un enfoque de n-gramas de caracteres (2 a 5) y palabras.
- Justificación: A diferencia de modelos que solo ven palabras completas, los n-gramas de caracteres nos permiten detectar "señales" dentro de palabras mal escritas o combinaciones de símbolos (ej. `ch4p1zz4` o `701`).
- Balance de Clases (Class Weighting): Implementamos pesos dinámicos en el modelo para priorizar la detección de clases críticas como `SYMBOLS` y `HIGH_RISK`, asegurando que el modelo no sea "perezoso" y prefiera decir que todo es seguro.

### 3. Stack Tecnológico
- Lenguaje: Python 3.x
- Librerías Core:
  - Scikit-learn: Para el Pipeline de entrenamiento y vectorización.
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
- **Entrenamiento:** Ejecuta `python main.py` (asegurate de tener los archivos `train.csv`, `val.csv` y `test.csv`).

- **Salida:** El script genera:
    * `risk_model_v1.pkl`: Modelo binario para pruebas en Python
    * `kipi_model_data.json`: **Archivo de exportación principal**. Contiene los pesos, interceptos y vocabulario TF-IDF listos para ser consumidos por el cliente Android (Edge AI).


### 6. Herramientas de IA utilizadas
- Scikit-Learn: Motor principal de clasificación.
- ChatGPT/Gemini: Utilizados para la generación de datos sintéticos de entrenamiento (aumentando nuestro dataset inicial) y revisión de la lógica de limpieza de texto. Así mismo, se utilizo para la documentación de la IA. 

### 7. Categorías de Clasificación de Riesgo
Para entender cómo nuestra IA evalúa las conversaciones, el modelo clasifica el texto en cinco niveles distintos. Cada categoría captura un patrón de comportamiento específico, permitiéndonos separar las conversaciones cotidianas de los intentos de reclutamiento.

**SAFE (Nivel 0) — Mensajes seguros**

Conversaciones cotidianas sin ninguna señal de riesgo. Abarca pláticas sobre familia, amigos, escuela o planes del día sin presencia de emojis de grupo, violencia o acoso.

* Pueden estar escritos en cualquier tono: informal, formal o divertido.

* Las menciones sobre dinero, trabajo o familia son completamente válidas, ya que el contexto general lo hace seguro.

* Los emojis comunes (❤️, 😂, 🙏, etc.) están permitidos, excluyendo únicamente los críticos identificados en nuestro motor de reglas.

Ejemplos:
"ya voy pa la casa wey, guarda algo de comer"
"oye me prestas tus apuntes de calculo? es q no fui"
"mañana toca entregar el proyecto ps hay q apurarse"

**SYMBOLS (Nivel 1) — Solo emojis de grupo**

Mensajes muy cortos, típicamente encontrados en estados, reportes de zona o códigos rápidos entre miembros de un grupo. El texto es mínimo o inexistente.

* Suelen tener un máximo de 6 a 7 palabras en total (incluyendo el emoji).

* Pueden estar compuestos exclusivamente por emojis, o un emoji acompañado de 1 a 3 palabras breves.

* Si el mensaje contiene texto más largo o un contexto más elaborado, el modelo lo descarta de esta categoría y lo evalúa como MIXED.

  Ejemplos:
"🍕 presente"
"NG activo 🐔"
"Todo al cien 👁️"

**BELONGING (Nivel 2) — Reclutamiento emocional**

Mensajes que apelan a la necesidad del menor de pertenecer, ser aceptado o tener una "familia". El gancho es netamente emocional, sin motivaciones económicas explícitas.

* Utiliza palabras clave de familiaridad: familia, carnal, banda, hermano, "nadie te juzga", "siempre te cuidamos".

* Carece de ofertas directas de dinero, trabajo o protección armada.

* No incluye los emojis de grupo catalogados como de alto riesgo.

* El tono es cálido, cercano y persuasivo, buscando que el receptor se sienta querido y validado.

Ejemplos:
"aqui nadie te juzga carnal, somos familia de verdad"
"con nosotros nunca vas a estar solo, la banda siempre cuida a los suyos"
"te vemos y sabemos q vales, unete al equipo"

**HIGH_RISK (Nivel 4) — Reclutamiento activo y directo**

Representa una oferta concreta de dinero, trabajo o protección, casi siempre acompañada de emojis de grupo. Es la evolución del nivel BELONGING: pasa de la persuasión emocional a una propuesta tangible y peligrosa.

* Contiene obligatoriamente la combinación de un emoji de grupo más una oferta económica o laboral.

* Emplea palabras clave transaccionales: jale, chamba, sueldo, paga, feria, dm, inbox, vacante.

* Frecuentemente combina el lenguaje familiar (carnal, familia) con la oferta explícita.

* Si un mensaje tiene este lenguaje pero el contexto sugiere un videojuego o un meme, el modelo lo reclasifica a `MIXED`.

Ejemplos:
"Puro 🍕 reportando, manda dm para jale 💰"
"NG activo 🐔 buscando raza pa el equipo, paga semanal garantizada"
"empresa 👁️ contratando perfil discreto, sueldo base y bonos mensuales"

**MIXED (Nivel 3) — Señales contradictorias**

La categoría más compleja. El mensaje contiene emojis de grupo o léxico de riesgo, pero el contexto sugiere que es inocente o inofensivo. Por seguridad, el sistema siempre deriva estos casos al LLM del backend para una segunda opinión y desempate.

* Tipo 1 (Videojuegos): Nombres de clanes, gamertags, roles en partidas online (ej. Call of Duty, Free Fire).

* Tipo 2 (Noticias/Comentario): Personas hablando sobre el crimen sin participar en él.

* Tipo 3 (Memes/Humor): Bromas, sarcasmo o uso de los emojis sin seriedad literal.

* Tipo 4 (Académico): Tareas, investigaciones o discusiones escolares sobre el tema.

Demuestra la capacidad del modelo para entender el contexto: un mismo emoji puede ser `HIGH_RISK` o `MIXED` dependiendo de las palabras que lo rodean.

Ejemplos:
"busco clan en cod mobile, puros 🐔 activos pa jugar en las noches"
"vieron las noticias del CJNG 🐔 ayer? que miedo"
"mañana exponen narcotráfico y me tocó el cartel 🐔, q rollo"
"wey ya deja de mandar tiktoks de la 🍕 pareces alucin"