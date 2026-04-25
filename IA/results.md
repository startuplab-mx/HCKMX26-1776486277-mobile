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

## Segunda iteración: Aumento de datos y calibración de pesos
Para esta segunda iteración, implementamos dos mejoras fundamentales basadas en los cuellos de botella detectados anteriormente:
1. **Aumento del dataset:** Incrementamos a 477 ejemplos en total (381 Train, 48 Val y 48 Test).
2. **Ajuste de pesos dinámicos:** Al aumentar los datos a 477 ejemplos, observamos que el peso inicial de 4.5 para `SYMBOLS` resultó excesivo, provocando una sobre-predicción (Recall 0.89, Precision 0.62) y afectando la categoría `MIXED`. Para esta iteración, recalibramos los pesos reduciendo la penalización de `SYMBOLS` y dándole más presencia a `MIXED`. 

La recalibración generó un impacto altamente positivo en el balance del modelo, alcanzando una precisión general del 0.77. Al otorgarle un peso más competitivo, la clase `MIXED` se recuperó exitosamente , demostrando que el modelo ahora entiende mucho mejor los contextos ambiguos. De igual forma, relajar el peso de `SYMBOLS` fue un acierto; ya no clasifica incorrectamente tantos mensajes inofensivos, pero mantiene un excelente Recall de 0.89 para detectar códigos criminales reales. Además, el ajuste de pesos eliminó la distorsión probabilística de la regresión logística, logrando que el modelo ahora esté más seguro de sus predicciones.

Sin embargo, para nuestra tercera iteración, identificamos que la categoría `SAFE` aún presenta un Recall de 0.60. Por ello, nuestro siguiente paso será realizar un aumento dirigido exclusivamente para generar más conversaciones casuales con jerga juvenil y modismos, evitando que el modelo se asuste con el léxico informal.

Finalmente, realizaremos una revisión fina del preprocesamiento para evaluar si nuestra función de limpieza está dejando pasar caracteres especiales que generen ruido en la vectorización de n-gramas de caracteres.

```
Precisión en Validación: 0.6667

--- REPORTE DE CLASIFICACIÓN (TEST) ---
                  precision    recall  f1-score   support

    BELONGING       0.90      0.90      0.90         10
    HIGH_RISK       0.78      0.78      0.78         9
        MIXED       0.70      0.70      0.70         10
         SAFE       0.75      0.60      0.67         10
      SYMBOLS       0.73      0.89      0.80         9

     accuracy                           0.77        48
    macro avg       0.77      0.77      0.77        48
 weighted avg       0.77      0.77      0.77        48
```
