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
