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
