# 🛡️ Kipi Safe - Android Client (Prevención de Reclutamiento Digital)

**Hackathon 404: Threat Not Found** | Embajada de EE.UU. en México × StartupLab MX  
**Equipo:** 99% de fe (IPN)

## ⚖️ Contexto de la Problemática
En el contexto actual de seguridad nacional en México, las organizaciones criminales han trasladado sus tácticas de captación al entorno digital. Los menores de edad son el objetivo de estrategias de manipulación, reclutamiento forzado, extorsión y ciberacoso a través de videojuegos, redes sociales y apps de mensajería, utilizando lenguajes codificados y simbología aparentemente inofensiva.

**Kipi Safe** nace como una solución de defensa tecnológica (Edge AI) diseñada para detectar estas tácticas de manipulación y violencia en tiempo real, actuando como un acompañante digital que protege al menor sin vulnerar su privacidad de forma invasiva.

## 🚀 Funcionalidades de Impacto

### 1. Detección de Simbología Criminal On-Device
El sistema cuenta con un motor de análisis local capaz de identificar instantáneamente **Alertas Rojas** mediante el uso de clasificadores heurísticos (`SymbolsScorer` y `ThreatRuler`). Se monitorean códigos utilizados por cárteles para identificar jerarquías (ej. 🍕, 🥷, 🪖, 🐓, 🧿, 👹, 🆖, 🦂) y léxico explícito de violencia física o extorsión. La detección de estos patrones activa una respuesta de alta prioridad de forma inmediata con cero latencia.

### 2. Motor de Clasificación Local (Regresión Logística Multinomial)
Se migró la arquitectura de inferencia local de un modelo generativo (LLM) a un **pipeline hibrido** que combina reglas duras con un **motor de clasificación estadística (Regresión Logística Multinomial).**

El modelo se alimenta de vectores **TF-IDF** (términos n-gram 1–2 y caracteres char_wb n-gram 2–5) y un **Aggression Featurizer** que extrae 6 dimensiones adicionales (densidad de mayúsculas, signos de exclamación/interrogación e incidencias de diccionarios de acoso/amenazas), cargados desde `kipi_model_data.json`.

Esta arquitectura Edge AI **reduce el consumo de memoria RAM en un 98% y elimina la latencia de respuesta**, permitiendo un triage inmediato. Se implementó una lógica de **umbrales (thresholds) estratificados** sobre la confianza predictiva para **minimizar falsos positivos** en conversaciones cotidianas.

**Siete etiquetas del modelo y su tratamiento en `KipiNotificationService`:**

| Etiqueta | Significado aproximado | Comportamiento |
|----------|------------------------|----------------|
| **THREAT** | Amenazas directas a la integridad física o extorsión. | Si confianza **> 0.70**: overlay crítico y **se detiene el flujo** (no se espera la nube para actuar). |
| **HIGH_RISK** | Patrones fuertes de reclutamiento / códigos peligrosos | Si confianza **> 0.75**: overlay crítico y **se detiene el flujo** (no se espera la nube para actuar). |
| **BULLYING** | Ciberacoso, insultos y exclusión digital. | Si confianza **> 0.80**: overlay de advertencia con enfoque de contención y apoyo. |
| **BELONGING** | Manipulación emocional / captación (grooming) | Si confianza **> 0.85**: overlay de advertencia educativa; el flujo puede continuar. |
| **SYMBOLS** | Símbolos o patrones asociados a riesgo en el texto | Si confianza **> 0.9**: registro en log (sin overlay por defecto). |
| **MIXED** | Señales contradictorias (mezcla de riesgo y contenido benigno) | **Siempre** se llama al **backend** para una segunda opinión, **sin importar la confianza**. |
| **SAFE** | Contenido cotidiano sin señales claras de riesgo | Sin intervención local por etiqueta; la nube solo entra si la confianza local es **< 0.6** (fallback para análisis profundo o baja confianza). |

Los valores numéricos anteriores viven como constantes en el servicio y pueden ajustarse sin cambiar el modelo.

### 3. Arquitectura Híbrida Inteligente (Edge + Cloud)
* **Filtro Edge (Privacidad y Velocidad):** Clasificación determinista en el dispositivo que atrapa lo obvio (amenazas, acoso, contenido seguro) ahorrando costos de servidor y batería.
* **Capa Cognitiva en Servidor:** Cuando la confianza local es baja o el mensaje clasifica como **MIXED** (donde el ML estadístico detecta ambigüedad contextual), el preview se enruta a la API (`RetrofitClient`) para que un LLM en la nube desenmarañe el contexto y emita un veredicto profundo (`risk_level` / `kipi_response`).
* **Emojis críticos y riesgo API:** Se mantiene la lógica de intervención por simbología crítica y por umbral de `risk_level` devuelto por el backend cuando aplica la etapa en nube.

### 4. Intervención Educativa No-Invasiva
A diferencia de las herramientas de control parental tradicionales, Kipi utiliza **Overlays Educativos**. Cuando se detecta un riesgo, la mascota "Kipi" aparece sobre la aplicación activa con respuestas escalonadas: desde contención emocional ante el ciberacoso, hasta advertencias críticas sobre el robo de datos o geolocalización ante intentos de reclutamiento, fomentando el autocuidado del menor.

### 5. Monitoreo Inteligente de Pantalla (Accessibility Service)
Para extender la protección a interacciones dentro de las aplicaciones, incorporamos `ParentalControlAccessibilityService`. Este servicio utiliza la API de Accesibilidad para identificar riesgos directamente en la interfaz de usuario.
* **Privacidad y Enfoque:** El análisis se activa únicamente cuando el menor se encuentra en aplicaciones de redes sociales o mensajería, respetando su privacidad en el resto del sistema.
* **Rendimiento Optimizado:** Para evitar ralentizar el dispositivo, el servicio realiza lecturas controladas (cada segundo), permitiendo que la IA analice el contexto sin afectar la fluidez del uso cotidiano.

## 🛠️ Estado Actual del Proyecto
* **Notificaciones funcionales:** Intercepción y procesamiento de notificaciones de mensajería (WhatsApp, Instagram, etc.) operativas.
* **Filtros de riesgo activos:** Integración del pipeline de 7 clases con TF-IDF, Aggression Featurizer y reglas heurísticas.
* **Gestor de intervención:** `KipiOverlayManager` configurado para desplegar alertas preventivas escalonadas (Críticas vs. Advertencia).
* **Transparencia:** Servicio de primer plano (Foreground Service) activo, garantizando que el usuario siempre sepa que cuenta con la protección de Kipi.

## 📁 Estructura del Core
* `ParentalControlAccessibilityService`: Servicio de accesibilidad para el monitoreo inteligente y selectivo de texto en pantalla en redes sociales.
* `KipiNotificationService`: Motor principal de escucha, umbrales por etiqueta y orquestación local / nube.
* `KipiInferenceEngine`: Carga dinámica del JSON del modelo, vectorización TF-IDF a nivel carácter/palabra y featurización de agresividad compatible con Scikit-Learn.
* `KipiLocalInference`: Fachada suspend que expone `KipiInferenceResult` (etiqueta, confianza, nivel).
* `KipiOverlayManager`: Sistema de UI persistente para la intervención y orientación del menor.
* `RetrofitClient`: Integración con la API de análisis en backend.

## 🧠 Entrenamiento del Modelo (Módulo IA)
Toda la lógica de entrenamiento, datasets y documentación técnica del modelo se encuentra en la carpeta `IA/` en la raíz del proyecto. Este módulo incluye:
* **Datasets:** Archivos `train.csv`, `val.csv` y `test.csv` con los que se entrena el clasificador.
* **Scripts de Entrenamiento:** `main.py` para la generación de los modelos y exportación a JSON.
* **Documentación Específica:** Un `README.md` detallado dentro de la carpeta que explica el stack tecnológico (Python, Scikit-learn) y las 7 categorías de riesgo.

## 🤖 Uso de Inteligencia Artificial (Hackathon Requirement)

En cumplimiento con los requerimientos del hackathon, detallamos el uso de herramientas de IA en este proyecto:

### Desarrollo de la Aplicación Móvil
*   **Herramientas:** Agentes de IA (ChatGPT, Gemini, GitHub Copilot).
*   **Uso y Nivel:** Se utilizaron agentes de IA como apoyo constante en la arquitectura del software, generación de documentación técnica, corrección de errores (debugging) y optimización de código Kotlin/Compose. La IA actuó como un "par de programadores", acelerando el proceso de implementación de la lógica de negocio y la interfaz de usuario.

### Módulo de IA (Entrenamiento y Lógica)
*   **Herramientas:** Scikit-Learn (Core estadístico), ChatGPT/Gemini.
*   **Uso:** Generación de datos sintéticos para robustecer el dataset de entrenamiento, diseño de la lógica de limpieza de texto (Regex) y estructuración de los clasificadores híbridos. Para más detalles, consultar el `README.md` en la carpeta `/IA`.

---
*Enfoque técnico centrado en la protección del interés superior del menor y el fortalecimiento de la seguridad ciudadana ante amenazas digitales mediante inteligencia artificial distribuida.*
