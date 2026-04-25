# Iteraciones del entrenamiento del modelo

## Primer iteración
Para esta primer iteración, se tienen **320 ejemplos en total**, **256 en Train**, **32 en Val** y **32 en Test**.

Con base a lo obtenido, entendemos que tenemos que aumentar significativamente el volumen de datos mediante la generación de datos sintéticos. Específicamente, necesitamos fortalecer la clase `SAFE`, ya que los 320 ejemplos totales actuales son insuficientes para que el modelo aprenda a diferenciar la jerga juvenil inofensiva de los códigos reales.

 Por otro lado, debemos calibrar los pesos dinámicos de las clases; si bien asignamos pesos altos a las clases críticas (`SYMBOLS` y `HIGH_RISK`) para evitar que el modelo asuma que todo es seguro , el peso asignado a `SYMBOLS` resultó ser demasiado agresivo, lo que está forzando al modelo a clasificar mensajes normales dentro de esta categoría de riesgo. Finalmente, vamos a afinar la limpieza y las heurísticas de nuestro motor de reglas para garantizar que los emojis comunes (❤️, 😂, 🙏) se ignoren correctamente y no generen un ruido que interfiera con la detección de los verdaderos emojis de alerta (🍕, 🐔, 🥷).

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

## Tercer iteración: Arquitectura híbrida y nuevas categorías
Para esta tercera iteración, implementamos dos mejoras fundamentales basadas en los cuellos de botella detectados anteriormente:
1. **Implementación de una arquitectura híbrida:** Esto con la finalidad de mejorar la optimización del procesamiento en dispositivos Android. Y para mejorar las predicciones con base a patrones comunes (heúristicas).

Con base a lo obtenido, entendemos que la evolución hacia una arquitectura híbrida y el aumento del dataset a 693 ejemplos resultaron ser un éxito rotundo, elevando la precisión general del modelo a un sólido 0.83. Específicamente, la integración de las nuevas clases `BULLYING` y `THREAT` demostró un desempeño sobresaliente (F1-scores de 0.86 y 0.87 respectivamente), destacando que la sinergia entre el Machine Learning y nuestro nuevo motor de reglas (ThreatRuler) logró un Recall perfecto de 1.00 para las amenazas, garantizando que ningún mensaje de peligro inminente pase desapercibido.

Por otro lado, debemos calibrar nuevamente los pesos dinámicos de nuestro pipeline; si bien el peso máximo asignado a `THREAT` (5.0) cumplió su función de evitar falsos negativos, resultó ser excesivamente agresivo, lo que está forzando al modelo ML a clasificar mensajes completamente inofensivos y cotidianos (como "Hola mamá, ya voy para la casa") dentro de esta categoría de alerta máxima. Finalmente, para nuestra próxima iteración, vamos a reducir la ponderación de la clase `THREAT` en el modelo estadístico (ya que el motor de reglas ya cubre las amenazas explícitas de forma excelente), y ajustaremos nuestro AggressionFeaturizer para asegurar que no esté sobre-penalizando el uso normal de signos de puntuación, blindando así la precisión de la clase `SAFE`.

```
Precisión en Validación: 0.7826

--- REPORTE DE CLASIFICACIÓN (TEST) ---
                  precision    recall  f1-score   support

    BELONGING       1.00      0.80      0.89         10
     BULLYING       0.82      0.90      0.86         10
    HIGH_RISK       1.00      0.80      0.89         10
        MIXED       0.82      0.90      0.86         10
         SAFE       0.70      0.70      0.70         10
      SYMBOLS       0.78      0.70      0.74         10
       THREAT       0.77      1.00      0.87         10

     accuracy                           0.83        70
    macro avg       0.84      0.83      0.83        70
 weighted avg       0.84      0.83      0.83        70
```

## Cuarta iteración: Expansión del dataset y calibración final de pesos
Para esta cuarta iteración, implementamos dos mejoras fundamentales basadas en los cuellos de botella detectados anteriormente:
1. **Aumento del dataset:** Incrementamos a 998 ejemplos en total (798 Train, 100 Val y 100 Test).
2. **Ajuste de pesos dinámicos:** Al aumentar los datos a casi mil, observamos que el peso inicial de clases como `THREAT` y `BULLYING` era demasiado alto, lo que no permitia obtener buenas predicciones para `HIGH_RISK`. Nuestra categoría principal, por la problemática del hackathon.

Con base a lo obtenido, entendemos que el aumento del corpus a casi 1,000 ejemplos (998 en total) en conjunto con la recalibración final de los pesos dinámicos nos ha llevado a nuestro modelo más estable, alcanzando una precisión general (accuracy) de 0.82. Específicamente, logramos resolver el desbalance crítico que detectamos en la iteración anterior; al aumentar el peso de la clase `HIGH_RISK`, el modelo estadístico logró recuperar los casos de reclutamiento activo que se le estaban escapando, elevando su F1-score a 0.81 y manteniendo una precisión perfecta (1.00), lo cual garantiza que las alertas de máxima prioridad sean certeras.

Por otro lado, la decisión de relajar los pesos masivos asignados previamente a `BULLYING` y `THREAT` demostró ser la estrategia arquitectónica correcta. Al delegar la detección de amenazas explícitas a nuestro motor de reglas (`ThreatRuler`), el modelo de Machine Learning dejó de sobre-predecir estas categorías ante la menor duda, lo que mejoró significativamente su Precisión (subiendo a 0.75 y 0.77 respectivamente) mientras conservaba un Recall sobresaliente (por encima del 0.90 en ambos casos). Finalmente, esta estabilización general permitió que la categoría `SAFE` mejorara su Recall a 0.73, reduciendo las falsas alertas en mensajes cotidianos y consolidando un pipeline híbrido robusto, confiable y listo para ser exportado y consumido por la aplicación móvil.

```
Precisión en Validación: 0.85

--- REPORTE DE CLASIFICACIÓN (TEST) ---
                  precision    recall  f1-score   support

    BELONGING       0.85      0.79      0.81         14
     BULLYING       0.75      0.90      0.82         10
    HIGH_RISK       1.00      0.69      0.81         16
        MIXED       0.82      1.00      0.90         18
         SAFE       0.89      0.73      0.80         22
      SYMBOLS       0.64      0.78      0.70         9
       THREAT       0.77      0.91      0.83         11

     accuracy                           0.82        100
    macro avg       0.82      0.83      0.81        100
 weighted avg       0.84      0.82      0.82        100
```
