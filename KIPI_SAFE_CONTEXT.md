# Contexto del Proyecto: Kipi Safe
 **Hackathon 404: Threat Not Found** (Embajada EE.UU. x StartupLab MX | CDMX 2026) 
 **Equipo:** 99% de fe 

## 1. Visión General
 Kipi Safe es un agente inteligente de protección digital on-device para menores.  No es un software espía; es un acompañante transparente que orienta sin juzgar.  El sistema analiza en tiempo real los previews de notificaciones expuestos por el sistema operativo Android para detectar riesgos sin acceder al contenido completo de las conversaciones.

##  2. Sistema Dual Adaptativo por Edad 
- **Modo Niño (10-13 años):** Supervisión directa.  Todas las alertas escalan al padre de acuerdo con el nivel de riesgo.
-  **Modo Adolescente (14-17 años):** Basado en acuerdos de privacidad.  El adolescente configura qué niveles de alerta comparte, respetando su autonomía progresiva.

##  3. Voto de Confianza Gradual (Niveles de Riesgo) 
- **Nivel 1 (Observación):** Señal aislada. Registro interno. Kipi orienta al adolescente de forma on-device.  No escala al padre.
- **Nivel 2 (Alerta Suave):** Patrón preocupante.  Escala al padre en Modo Niño.  En Modo Adolescente, solo escala si hay un acuerdo previo.
-  **Nivel 3 (Crítica):** Indicadores claros de grooming, aislamiento, o datos sensibles (dirección, teléfono, ubicación).  Siempre escala al padre (no negociable) y activa el asistente de orientación parental.

## 4. Transparencia y Visibilidad (Requisito Android)
-  El menor siempre sabe que Kipi está activo.  El icono de Kipi es visible en la barra de estado del dispositivo en todo momento — no existe monitoreo oculto.
- **Implementación:** Se requiere un `Foreground Service` con un canal de notificaciones de baja prioridad (`IMPORTANCE_LOW` o `MIN`) para mantener el icono sin generar interrupciones visuales o sonoras.

##  5. Stack Tecnológico
-  **App Android (Menor):** Kotlin, `NotificationListenerService`, UI Nativa
-  **Backend API:** Next.js.
-  **Motor IA:** Gemini 2.5 Flash-Lite (Clasificación on-device/cloud).
-  **Base de Datos:** Supabase (PostgreSQL) con sincronización en tiempo real para la PWA del padre.

##  6. Endpoints API - Clasificación 
-  **POST** `/api/notifications/analyze` 
  -  **Request Body:** `minor_id` (UUID), `app_source` (String), `text_preview` (String).
  -  **Response 200 OK:** `{ "ok": true, "analysis": { "risk_level": Int, "confidence_score": Double, "sensitive_data_flag": Boolean, "kipi_response": String }, "system_action": { "escalated_to_parent": Boolean, "reason": String }, "procesado_en_ms": Int }`.

## 7. Modelos de Datos (Referencia Supabase)
 La tabla `alerts` almacena: `id`, `minor_id`, `app_source`, `risk_level`, `confidence_score`, `sensitive_data_flag`, `escalated_to_parent`, `is_manual_help`, `created_at`.