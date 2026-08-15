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
- **Tensor de entrada o salida en float16** — ver la sección siguiente, es un caso
  aparte porque no se arregla cambiando nada de este lado.

## "DataType error: DataType 10 is not recognized in Java"

Si el log de arriba muestra exactamente este mensaje (con cualquier número, no sólo el
10), el modelo tiene el tensor de entrada o el de salida declarado en un tipo que la
API Java de `tensorflow-lite` no sabe traducir — en la práctica, casi siempre float16
(`DataType 10` es literalmente `kTfLiteFloat16` en el enum `TfLiteType` del runtime C).
Pasa cuando el modelo se exportó con cuantización float16 *de entrada/salida*, no sólo
de pesos.

**No es un problema de versión ni de dependencias.** `DataTypeUtils.fromC` —la función
que tira esta excepción— sólo reconoce FLOAT32/INT32/UINT8/INT64/STRING/BOOL/INT16/INT8
en absolutamente todas las versiones publicadas de `org.tensorflow:tensorflow-lite`,
incluida la 2.17.0 (la vigente acá, y la más nueva al momento de escribir esto).
float16 nunca se agregó a esa API. Tampoco lo resuelve sumar
`org.tensorflow:tensorflow-lite-support`: esa librería usa el mismo `DataType` de
`tensorflow-lite`, así que hereda exactamente la misma limitación. Y como
`Interpreter.getInputTensor()`/`getOutputTensor()` fallan apenas se toca el tensor —
antes de llegar siquiera a `run()`— tampoco hay forma de esquivarlo con
`Interpreter.Options` o delegates: el fallo ocurre en la capa Java, no en la de
ejecución.

**El único arreglo real es re-exportar el modelo** con el tensor de entrada y el de
salida en float32. Los pesos internos sí pueden seguir en float16 (o int8): el runtime
lo soporta perfectamente vía un `Dequantize` antes del primer nodo. Lo que no puede ser
float16 es la entrada o la salida en sí. Si se tiene el modelo original (SavedModel,
Keras, o las funciones concretas) a mano, alcanza con forzar el tipo del I/O al
reconvertir:

```python
import tensorflow as tf

converter = tf.lite.TFLiteConverter.from_saved_model(SAVED_MODEL_DIR)
converter.inference_input_type = tf.float32
converter.inference_output_type = tf.float32

open("mobilefacenet.tflite", "wb").write(converter.convert())
```

Si sólo se tiene el `.tflite` ya compilado (no el modelo fuente), no hay forma
confiable de recomprimirlo desde acá — conviene directamente buscar una build de
MobileFaceNet con entrada/salida float32, que es lo más común (por ejemplo, la que usa
[`shubham0204/OnDevice-Face-Recognition-Android`](https://github.com/shubham0204/OnDevice-Face-Recognition-Android),
con el mismo contrato `[1,112,112,3]` → `[1,192]` que espera este proyecto). Igual que
con cualquier modelo de terceros, hay que revisar la licencia antes de usarla en
producción.

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
