# Vínculo Vial: Frontend

App Android de Vínculo Vial.

## Una alerta por patente cada 24 h (US-16)

Un emisor puede enviar como máximo una alerta por patente dentro de una ventana móvil de 24 h. Las alertas rechazadas por la IA y las vencidas sin respuesta no ocupan el cupo. La regla es igual con dueño o sin él: la app nunca se entera de si la patente tiene dueños.

- **Intento:** `POST /alerts/attempts` lleva el body `{ "pat_enc": "<base64>" }`, la patente cifrada con HPKE contra Vehicle, el mismo criptograma que viaja en el envío. Se manda apenas el usuario confirma la patente escaneada o tipeada. La patente en claro nunca sale del teléfono y `Alert` no la ve. Si el intento hay que renovarlo porque venció, se vuelve a pedir con el mismo `pat_enc`.
- **`409 ALERT_DAILY_LIMIT`:** llega con `available_at` (RFC 3339), cuándo se libera el cupo. Se mapea por el `code`, no por el status; un 409 con otro `code`, o sin un `available_at` legible, es un error inesperado.
  - **En el intento:** se abre el chat con ViVIAna, que dice: "Ya enviaste una alerta para esta patente. Vas a poder enviar otra desde el 2 de enero a las 12:30." No se ofrecen los mensajes ni se abre la captura de fotos. El único botón es "Cerrar".
  - **En el envío:** el mismo mensaje aparece en el chat (por ejemplo, si el cupo se ocupó entre el intento y el envío). Se descartan las fotos y no hay reintento: el botón es "Cerrar".
- **Fecha y hora:** siempre en `America/Argentina/Buenos_Aires`, sin importar la zona del teléfono.
- **Control del servidor:** si Alert no pudo controlar el cupo en el intento (por ejemplo, Vehicle no respondió), el intento sigue con un 201 y el control definitivo lo hace el envío, que puede responder el 409.

## Beneficios de partners (US-18)

Un partner (por ejemplo, una petrolera) entrega códigos de descuento de un solo uso; Vínculo Vial le asigna uno por persona y por campaña. La app solo los muestra: no los genera ni los valida.

- **Home:** debajo de "Mis alertas" hay un carrusel (`HorizontalPager`) con los banners de las promociones vigentes, con un indicador de página. Avanza solo cada 5 s y se detiene mientras el usuario lo toca o lo arrastra. Si la lista viene vacía o falla, el carrusel no se muestra: sin error ni espacio vacío. La lista se actualiza cada vez que se vuelve al Home, así que una promoción vencida o agotada desaparece.
- **Banner:** `GET /benefits/{id}/banner` con el cliente HTTP autenticado, como bytes; no hay librería de imágenes. La ruta se arma desde el `id`, nunca desde el `banner_url` que manda el servidor, para que el Bearer no pueda ir a otro host. Se guarda en una caché en memoria por `ETag` (con `If-None-Match`, un 304 devuelve los bytes guardados). Mientras no llega, o si falla, se ve una tarjeta con el título.
- **Detalle:** banner, título, partner, "Hasta el dd/mm" (hora argentina), descripción y condiciones (plegadas).
  - Sin código: "Obtener código" (`POST /benefits/{id}/claim`, idempotente en el servidor: al volver se ve el mismo).
  - Con código: el código en grande, "Copiar" (portapapeles, marcado como sensible; avisa "Código copiado") y "Abrir <partner>" (`ACTION_VIEW` sobre `open_url`). Debajo: "Ingresá el código en la app de <partner>".
  - "Abrir" solo se ofrece si `open_url` es `https://` con host; cualquier otro esquema no se abre nunca.
- **Errores:** se mapean por el `code`, no por el status. `NO_CODES_LEFT`: "Se agotaron los códigos de esta promoción". `BENEFIT_NOT_FOUND`: "Esta promoción ya no está disponible", y se recarga la lista. En ambos casos ya no se ofrece pedir el código. El resto (429, 503, red) muestra un mensaje y deja reintentar.
- **Privacidad:** el código nunca se loguea ni sale en un `toString`. Una promoción cuyo `ends_at` no se puede leer se descarta sin tumbar al resto de la lista.
- **Regla del servidor que se nota en la app:** una campaña es visible si hay códigos disponibles o si el usuario ya tiene uno asignado. Un usuario sin código no ve una campaña agotada; solo ve el `NO_CODES_LEFT` si el pool se agota mientras tiene abierto el detalle.

Para probarlo contra Benefits de verdad: `docker compose --profile seed run --rm vinculo-vial-benefits-seed` en `vinculo-vial-benefits` carga "Estación de prueba" (100 códigos falsos y un banner generado) y una campaña vencida que no debe aparecer.

## Términos y Condiciones y acceso a los datos (US-19)

Un único documento, **Términos y Condiciones y Política de Privacidad**, versionado. El usuario lo acepta en el alta; Identity guarda la versión aceptada y la fecha, así que el consentimiento queda demostrable (art. 5 de la Ley 25.326). El acceso, la rectificación y la supresión de datos no tienen autoservicio: el documento indica el email de contacto y los plazos (10 días corridos y 5 días hábiles).

- **Texto:** `app/src/main/res/raw/terminos.md`, copiado tal cual del contrato. No se reescribe: se cambia solo con una versión nueva (y `TERMS_VERSION`). Es un borrador; conviene que lo revise alguien con formación jurídica antes de presentarlo.
- **Datos del responsable:** los marcadores `{{RESPONSABLE}}`, `{{DOMICILIO}}`, `{{EMAIL_CONTACTO}}` y `{{VERSION}}` se completan con `BuildConfig` (`LEGAL_RESPONSIBLE_NAME`, `LEGAL_ADDRESS`, `LEGAL_CONTACT_EMAIL` y `TERMS_VERSION`, "1.0"), configurables por `local.properties` o variables de entorno (ver `local.properties.example`). Sin configurarlos, el documento muestra un "[... a completar]" visible en lugar de inventar un dato.
- **Renderizador:** `TermsMarkdown` (en `domain`, sin Android) muestra títulos, párrafos, listas, negritas y tablas; lo que no reconoce queda como texto literal. Los marcadores se reemplazan sobre el texto ya parseado, así un valor con `**` o `|` no puede alterar la estructura. Las filas de la tabla de datos personales se dibujan como tarjetas con los títulos de columna, porque tres columnas no entran en un teléfono.
- **Pantalla `Route.TERMS`:** pública y sin red. Se entra desde la Bienvenida, el formulario del alta y Settings. Es de todos los mundos: `worldRedirect` no la redirige con ningún estado de sesión, y Atrás vuelve a la pantalla de la que se vino.
- **Alta:** la casilla "Leí y acepto los Términos y Condiciones y la Política de Privacidad" es obligatoria y su texto lleva a la pantalla. Sin marcarla no se puede seguir (el botón queda deshabilitado, `validateForm` y el caso de uso la exigen también). La casilla vive en el ViewModel, así que sobrevive a ir a leer los Términos. `TERMS_VERSION` viaja como `terms_version` dentro del payload HPKE del signup.
- **`TERMS_NOT_ACCEPTED`** (400, por el `code`): "Actualizamos los términos. Volvé a aceptarlos para seguir"; se vuelve al formulario y la casilla queda sin marcar.
- **Valoración del dueño:** debajo de los botones de valoración, "Al enviar tu valoración, aceptás que las fotos redactadas se usen para mejorar la validación automática. Ver Términos", con el link.
- **Fuera de alcance:** pedir de nuevo la aceptación cuando cambie la versión; se informa en la app (sección 13 del documento).

Dependencia de backend: `TERMS_VERSION` tiene que coincidir con `TERMS_CURRENT_VERSION` de Identity. Entre los dos merges, Identity se levanta con `SIGNUP_REQUIRE_TERMS=false`.

## Evidencia de una alerta: dos fotos (US-15)

Todo tipo de alerta que la app ofrece (`CRASHED` y `PARKED_ON_GARAGE`) pide exactamente dos fotos, en este orden:

| Paso | Rol (`role`) | Qué se saca |
|---|---|---|
| 1 | `GENERAL` | El auto completo, con la patente visible |
| 2 | `PLATE` | Un primer plano de la patente |

No hay foto de daño ni de contexto: los roles `DETAIL` y `CONTEXT` no existen en la app.

- **Captura guiada y chat con ViVIAna:** el plan de pasos sale de `EvidencePlan.stepsFor`, y ViVIAna explica siempre las mismas dos fotos. En el build de desarrollo, "Elegir de la galería" entra por el mismo camino y también llena un paso por vez.
- **Envío:** `POST /alerts` es un multipart con la parte `metadata` (JSON) y dos partes de foto cifradas para IA: `general` y `plate`, una de cada una. Si falta una o sobra otra, la app no llama a la red. El backend rechaza una parte `detail` o `context` como evidencia inválida; no hay compatibilidad con la app vieja.
- **Buzón (Recibidos y Enviados):** el detalle de una alerta muestra las dos fotos redactadas, rotuladas "General" y "Patente".

## Verificación de email y recuperación de contraseña (US-13)

Todos los pedidos usan el sobre HPKE vigente contra Identity (`{ ephemeral_public_key, ciphertext }`, `info` vacío, igual que el alta y el login) y llevan la atestación del dispositivo. Los errores se mapean por el `code` del backend. Nunca se loguean ni se guardan el código, los tokens, el DNI, el email, la contraseña ni la selfie.

### Alta: verificación del email

El flujo es **Formulario → Verificar email (`signup/verify-email`) → Consentimiento → Captura → envío**.

| Paso | Endpoint | Payload cifrado | Respuesta |
|---|---|---|---|
| Pedir el código | `POST /api/v1/auth/email-verifications` | `{ email }` | 202, exista o no una cuenta con ese email |
| Confirmarlo | `POST /api/v1/auth/email-verifications/confirm` | `{ email, code }` | 200 `{ verification_token, expires_in }` |
| Registrarse | `POST /api/v1/auth/signup` | el de antes más `email_verification_token` | 201 |

- La pantalla pide el código de 6 dígitos y tiene "Reenviar" con una cuenta regresiva de `EMAIL_CODE_RESEND_SECONDS` (60 por defecto). Volver atrás permite corregir el email.
- El `verification_token` queda sólo en memoria, en el `SignUpViewModel`. Se descarta si cambia el email, al registrarse o si el servidor lo rechaza. Con el email ya verificado, volver atrás sin tocarlo no pide otro código.
- Ante `EMAIL_NOT_VERIFIED` (token vencido, usado o de otro email), la app vuelve a la verificación.
- `INVALID_CODE` (incorrecto, vencido, usado o sin intentos: el servidor no los distingue) muestra "El código no es válido o venció"; para seguir hay que pedir otro código.

### Recuperación de contraseña

Se entra desde "¿Olvidaste tu contraseña?" en el Login. Es parte del mundo público, así que también se puede iniciar con la sesión bloqueada.

| Pantalla | Qué hace |
|---|---|
| `recovery/dni` | Pide el DNI y el código (`POST /auth/password-resets`, payload `{ dni }`, siempre 202) |
| `recovery/code` | Confirma el código (`POST /auth/password-resets/confirm`, `{ dni, code }`) y guarda el `reset_token` en memoria |
| `recovery/password` | Contraseña nueva, con las reglas del alta. Desde acá se sale a la captura y se muestra el progreso o el error del envío |
| `recovery/document-scan` | Reusa `DocumentScanScreen`. Si el DNI escaneado no es el ingresado, lo avisa sin llamar al backend |
| `recovery/liveness` | Reusa `LivenessCaptureScreen`. Con la selfie, el envío arranca solo |
| `recovery/done` | "Cambiaste tu contraseña": lleva a Login |

- **Envío:** `POST /auth/password-resets/complete` con `{ reset_token, dni, tramite, sexo, selfie, new_password }` cifrado con HPKE. El DNI, el trámite y el sexo son los leídos del documento. Responde 204.
- **Al terminar:** el servidor revoca todas las sesiones. La app borra los tokens en memoria, el blob del refresh y la clave biométrica, va a Login con `popUpTo(0)` y muestra "Listo. Ingresá con tu nueva contraseña".
- **Errores:**

| `code` | Qué muestra la app |
|---|---|
| `INVALID_CODE` | "El código no es válido o venció" |
| `INVALID_RESET_TOKEN` | Vuelve a pedir el código |
| `identidad_no_verificada` | "No pudimos validar tu identidad" (cada rechazo descuenta un intento del token en el servidor) |
| `datos_invalidos` | Contraseña débil o predecible: pide elegir otra; otro campo: revisar los datos |
| `dependencia_no_disponible` (503) o 429 | Mensaje de reintento; no se pierde el avance |

- **Anti-enumeración:** después de pedir el código la app muestra siempre "Si el DNI corresponde a una cuenta, te enviamos un código al email registrado", exista o no la cuenta.

### Configuración

`EMAIL_CODE_RESEND_SECONDS` (`local.properties` o variable de entorno, 60 por defecto) es la cuenta regresiva del botón "Reenviar". Tiene que coincidir con la de Identity, que es quien aplica el throttle. Ver `local.properties.example` y `.env.example`.

## Vinculación del vehículo por dominio (US-14)

La patente se vincula al usuario de la sesión escribiéndola a mano, sin verificar la titularidad: el Estado no ofrece a los privados un servicio para validarla. La mitigación es el tope de 3 vehículos por usuario (`VEHICLE_MAX_PER_USER` en Vehicle; la app toma el máximo de la respuesta, no lo tiene fijo). Se eliminó el escaneo del QR de la cédula (`ScanCedulaScreen`, `CedulaQrParser` y la ruta `vehicle/link`).

### Mis vehículos

Se entra desde el Home ("Mis vehículos", ruta `vehicles`). **La app nunca muestra qué patentes tiene vinculadas el usuario, sólo cuántas**: "Tenés N de 3 vehículos vinculados". Quien acceda a una sesión ajena no puede ver qué vehículos tiene esa persona.

| Botón | Qué hace |
|---|---|
| Vincular vehículo | Modal para escribir la patente y confirmarla. Deshabilitado al llegar al tope, con el texto "Podés tener hasta 3 vehículos" |
| Desvincular un vehículo | Modal para volver a escribir la patente, con la misma validación y una confirmación |
| Desvincular todos | Confirmación: "Vas a dejar de recibir las alertas de todos tus vehículos". Es la salida de quien no recuerda cómo escribió una patente |

- **Campo de patente:** mayúsculas, sin espacios ni guiones, hasta 7 caracteres, con validación de `AAA999` o `AA999AA` en la app (`PlateRules`). Una patente inválida no sale del dispositivo.
- **Confirmación del alta:** "Vas a vincular AB123CD. Vas a recibir las alertas de esta patente".

### Endpoints

Todos con `Authorization: Bearer`, sin App Attestation (JWT y rate limit en Kong). Ninguna respuesta trae patentes.

| Operación | Endpoint | Cuerpo | Respuesta |
|---|---|---|---|
| Contar | `GET /api/v1/vehicles` | — | 200 `{ count, max }` |
| Vincular | `POST /api/v1/vehicles` | `{ payload_enc }` | 201 `{ count, max }` |
| Desvincular una | `POST /api/v1/vehicles/unlink` | `{ payload_enc }` | 200 `{ count, max }` |
| Desvincular todos | `DELETE /api/v1/vehicles` | — | 204 |

`payload_enc` es el HPKE vigente contra Vehicle (clave pública `VEHICLE_HPKE_PUBLIC_KEY`, `info` vacío) de `{ "patente" }`, con la patente en base64 porque Vehicle la tipa `[]byte`.

### Errores (por `code`, no por status)

| `code` | Texto |
|---|---|
| `DUPLICATE_VEHICLE` | "Ya tenés esta patente vinculada" |
| `VEHICLE_LIMIT_REACHED` | "Podés tener hasta 3 vehículos" (el mismo del botón deshabilitado) |
| `INVALID_PLATE` | "Revisá el formato de la patente" |
| `VEHICLE_NOT_FOUND` | "No tenés esa patente vinculada" |
| `DEPENDENCY_UNAVAILABLE` (503) | Mensaje de reintento |

La patente que se escribe vive sólo en el estado del modal abierto: no se loguea, no se persiste y se descarta al cerrarlo.

### Pruebas de pantalla

Las pantallas se prueban con Robolectric y `createComposeRule` (`MyVehiclesScreenTest`). Para eso se sumaron `robolectric` y `ui-test-junit4` a las dependencias de prueba y `ui-test-manifest` a `debug`.

Se verifica el estado que muestran y los callbacks que disparan. Los tests de pantalla usan `@Config(qualifiers = "w411dp-h1600dp-xhdpi")` para que el contenido quede a la vista sin scrollear.

Corren en `testDebugUnitTest`. La `ComponentActivity` que lanza `createComposeRule` la aporta `ui-test-manifest`, que solo está en el manifiesto de `debug`, así que en `testReleaseUnitTest` esas clases no se pueden ejecutar. `app/build.gradle.kts` las excluye de esa tarea leyendo `src/test`: toda clase que use `createComposeRule` o `createAndroidComposeRule` queda afuera sin tocar nada más. En release se corre el resto de las pruebas y la guardia del manifiesto de release (`ReleaseNoGalleryTest`).
