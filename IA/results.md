# Iteraciones del entrenamiento del modelo

## Primer iteración
Para esta primer iteración, se tienen **320 ejemplos en total**, **256 en Train**, **32 en Val** y **32 en Test**.

Con base a lo obtenido, entendemos que tenemos que aumentar significativamente el volumen de datos mediante la generación de datos sintéticos. Específicamente, necesitamos fortalecer la clase SAFE, ya que los 320 ejemplos totales actuales son insuficientes para que el modelo aprenda a diferenciar la jerga juvenil inofensiva de los códigos reales.

 Por otro lado, debemos calibrar los pesos dinámicos de las clases; si bien asignamos pesos altos a las clases críticas (SYMBOLS y HIGH_RISK) para evitar que el modelo asuma que todo es seguro , el peso asignado a SYMBOLS resultó ser demasiado agresivo, lo que está forzando al modelo a clasificar mensajes normales dentro de esta categoría de riesgo. Finalmente, vamos a afinar la limpieza y las heurísticas de nuestro motor de reglas para garantizar que los emojis comunes (❤️, 😂, 🙏) se ignoren correctamente y no generen un ruido que interfiera con la detección de los verdaderos emojis de alerta (🍕, 🐔, 🥷).

```
Precisión en Validación: 0.6562

--- REPORTE DE CLASIFICACIÓN (TEST) ---
                  precision    recall  f1-score   support

    BELONGING       0.88      1.00      0.93         7
    HIGH_RISK       1.00      0.83      0.91         6
        MIXED       1.00      0.83      0.91         6
         SAFE       0.57      0.57      0.57         7
      SYMBOLS       0.57      0.67      0.62         6

     accuracy                           0.78        32
    macro avg       0.80      0.78      0.79        32
 weighted avg       0.80      0.78      0.79        32
```