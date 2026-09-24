# Ejemplo resuelto — contrato de eventos (Kafka)

> Ilustración. El skill genera la misma **estructura** para cualquier servicio.

## Contexto de entrada que aportó el equipo (resumido)

- **Servicio:** `llm-service`.
- **Publica:** `SCORE_CALCULATED` (el resultado de evaluar un intento) — lo consume el
  servicio de prácticas, que se lo reenvía al motor de desafíos para aplicar XP.
- **Consume:** `ATTEMPT_CLOSED` (lo publica el servicio de prácticas) — dispara la evaluación.
- **Regla de dominio:** `llm-service` **nunca** asigna XP; solo devuelve el score.
- **Tópicos:** todavía no asignados por Notificaciones → nombres provisorios.

## Salida A — fragmento de AsyncAPI

```yaml
channels:
  practice-events:                     # provisorio: lo asigna Notificaciones
    address: practice-events
    x-topic-status: pending-assignment
    messages:
      attemptClosed: { $ref: '#/components/messages/AttemptClosed' }
  evaluation-events:                   # provisorio
    address: evaluation-events
    x-topic-status: pending-assignment
    messages:
      scoreCalculated: { $ref: '#/components/messages/ScoreCalculated' }
operations:
  consumeAttemptClosed:   { action: receive, channel: { $ref: '#/channels/practice-events' } }
  publishScoreCalculated: { action: send,    channel: { $ref: '#/channels/evaluation-events' } }
components:
  messages:
    AttemptClosed:   { payload: { $ref: '#/components/schemas/AttemptClosedEvent' } }
    ScoreCalculated: { payload: { $ref: '#/components/schemas/ScoreCalculatedEvent' } }
  schemas:
    Envelope:
      type: object
      required: [eventId, eventType, timestamp, producer, payload]
      additionalProperties: false
      properties:
        eventId:   { type: string, format: uuid }
        eventType: { type: string }
        timestamp: { type: string, format: date-time }
        producer:  { type: string }
        payload:   { type: object }
    ScoreCalculatedEvent:
      allOf:
        - $ref: '#/components/schemas/Envelope'
        - type: object
          properties:
            eventType: { const: SCORE_CALCULATED }
            payload:
              type: object
              required: [attemptId, courseCohortId, learnerId, score, dimensions, rubricVersionId]
              properties:
                attemptId:       { type: string, format: uuid }
                courseCohortId:  { type: string, format: uuid }
                learnerId:       { type: string, format: uuid }
                score:           { type: integer, minimum: 0, maximum: 100 }
                dimensions:      { type: object }
                rubricVersionId: { type: string, format: uuid }
    AttemptClosedEvent:
      allOf:
        - $ref: '#/components/schemas/Envelope'
        - type: object
          properties:
            eventType: { const: ATTEMPT_CLOSED }
            payload:
              type: object
              required: [attemptId, courseCohortId, learnerId, transcript]
              properties:
                attemptId:      { type: string, format: uuid }
                courseCohortId: { type: string, format: uuid }
                learnerId:      { type: string, format: uuid }
                transcript:     { type: array, items: { type: object } }
```

## Salida B — sección del doc inter-equipos

**Publicamos `SCORE_CALCULATED`** — consumidor: servicio de prácticas.
- `payload`: `score` (0–100), `dimensions`, `rubricVersionId`. **Nunca un valor de XP.**
- El servicio de prácticas se lo reenvía al motor, que traduce el score a modificador y
  aplica XP + monedas en **una** transacción.

**Consumimos `ATTEMPT_CLOSED`** — lo publica el servicio de prácticas.
- Estructura mínima que necesitamos: `attemptId`, `courseCohortId`, `learnerId`,
  `transcript` (**obligatorio**: sin la conversación no hay nada que evaluar).
- 🔴 Pedir estos campos antes de que el equipo dueño cierre el contrato de eventos.
- 🔴 Tópico: pedirle a Notificaciones el nombre definitivo.

---

## Por qué queda así

- **`eventType` en `MAYÚSCULAS_CON_GUION_BAJO` y en pasado**: `ATTEMPT_CLOSED`,
  `SCORE_CALCULATED` — son hechos consumados, no comandos.
- **Envelope de cinco campos, sin `eventVersion`**: es lo que fija el PDF. `eventId` sirve
  para que el consumidor deduplique (at-least-once).
- **Tópicos provisorios**: los grupos no crean tópicos; los asigna Notificaciones.
- La **correlación** (`traceparent`, `X-Request-Id`) va en headers de Kafka, no en el
  `payload`. Al consumir `ATTEMPT_CLOSED` y publicar `SCORE_CALCULATED`, se propagan los
  mismos headers.
- El **consumidor de `ATTEMPT_CLOSED` es idempotente**: si el evento llega dos veces, se
  encola una sola evaluación (índice por `eventId`). Un evento inválido no se descarta en
  silencio: queda guardado con el motivo (no hay tópico dead-letter).
- `SCORE_CALCULATED` se publica por **outbox**: se escribe en la misma transacción que
  guarda el resultado de la evaluación.
- El `payload` de `SCORE_CALCULATED` **no** tiene XP — es la frontera de dominio:
  `llm-service` da el número, el motor de desafíos aplica la economía.
- Este tópico mezcla `SCORE_CALCULATED` y `SCORE_DEFERRED` con payloads distintos: el
  consumidor lee `Event<?>` y ramifica por `eventType`.
