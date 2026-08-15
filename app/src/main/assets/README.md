# Modelo de reconocimiento facial

Acá va el `.tflite` que usa `TfLiteFaceEmbedder` para comparar la foto del DNI con la
selfie. **No está en el repositorio** (sigue en `.gitignore`) y sin él la verificación
biométrica falla de forma explícita (`BiometricsUnavailable`): nunca se da por válido un
match que no se pudo calcular. Si acá hay un archivo `mobilefacenet.tflite`, es local de
este checkout — cualquier otro clon necesita el suyo.

## Qué archivo hace falta

- Nombre por defecto: `mobilefacenet.tflite` (configurable con `FACE_EMBEDDER_MODEL_ASSET`
  si tu modelo se llama distinto — el nombre exacto tiene que coincidir con el archivo,
  o `context.assets.openFd()` falla y la verificación queda no disponible).
- Entrada: `[1, N, N, 3]` float32, cuadrada. En uso: 112×112 (MobileFaceNet estándar).
- Salida: `[1, D]` float32, el embedding. En uso: 512 dimensiones.
- Normalización esperada: `(pixel - 127.5) / 128`, que es la convención de MobileFaceNet.
  Si el modelo elegido se entrenó con otra, hay que ajustar `INPUT_MEAN` / `INPUT_SCALE`
  en `EdgeModule`.

Las dimensiones **no** están hardcodeadas: se leen del propio modelo al cargarlo (ver
`TfLiteFaceEmbedder.loadEngine()`), así que cualquier variante que cumpla esa forma
funciona sin tocar código — incluida una con otra cantidad de dimensiones de salida.

## Si `BiometricsUnavailable` aparece de nuevo

`TfLiteFaceEmbedder` loguea la excepción real con `Log.e("TfLiteFaceEmbedder", ...)`
apenas falla la carga — mirar logcat filtrando por ese tag antes de asumir nada. Las
causas típicas:

- **Nombre de archivo que no coincide** con `FACE_EMBEDDER_MODEL_ASSET` (la causa más
  común: `assets.openFd()` tira `FileNotFoundException`).
- **Modelo con una forma de entrada/salida que no cumple el contrato** (entrada no
  cuadrada NHWC, o salida que no es `[1, N]`) — el log lo dice explícito.
- Archivo corrupto o truncado a mitad de descarga (los primeros 4 bytes después del
  prefijo de longitud tienen que ser `TFL3`).

## Por qué no está versionado

1. **Licencia.** Los pesos de los modelos de reconocimiento facial que circulan tienen
   licencias heterogéneas, y varios prohíben el uso comercial. Hay que elegir uno con
   licencia compatible con el proyecto y dejar constancia de cuál es.
2. **Tamaño.** Son varios MB de binario, que no corresponde arrastrar en el historial de
   Git. Si se decide versionarlo, conviene Git LFS.

## Antes de producción

El umbral (`FACE_MATCH_THRESHOLD`, hoy `0.62`) está puesto como punto de partida, no
calibrado. Comparar una foto **impresa** de DNI contra una selfie es más ruidoso que
comparar dos selfies: el plástico, el holograma y la baja resolución de la foto del
documento bajan la similitud de manera sistemática.

Hay que calibrarlo con documentos reales y medir las dos tasas de error, porque tienen
costos opuestos:

- umbral muy alto → se rechaza a gente legítima (falsos negativos);
- umbral muy bajo → pasa alguien con el documento de otra persona (falsos positivos).
