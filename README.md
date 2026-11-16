# Vínculo Vial: Frontend

App Android de Vínculo Vial.

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
