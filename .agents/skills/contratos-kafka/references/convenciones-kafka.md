# Convenciones de eventos (Kafka) — estilo de la plataforma

Material fijo. Reglas que aplican a **todo** evento asíncrono de la plataforma. Las
secciones marcadas **[PDF]** las fija `KAFKA.pdf` (cátedra / grupo de Notificaciones): acá
va el resumen, la fuente autoritativa es el PDF. Las marcadas **[casa]** son decisiones
nuestras que el PDF no contradice. Las marcadas **[pendiente]** no están definidas: no se
inventan.

---

## 1. Los dos canales **[PDF]**

| Canal | Transporte | Contrato | Garantías |
|---|---|---|---|
| Sincrónico | HTTP por el API Gateway | OpenAPI | request/response, `200/202/4xx/5xx` |
| Asincrónico | Kafka (`event-bus:29092`) | AsyncAPI + envelope del PDF | **publicar un evento no es un POST a otro micro** |

Publicar un evento y llamar a un endpoint son cosas distintas con garantías distintas. Si
necesitás una respuesta, es HTTP. Si notificás un hecho que ya pasó, es un evento.

## 2. Vocabulario **[PDF]**

| Concepto | Qué significa | Ejemplo |
|---|---|---|
| Emisor | El microservicio que genera un hecho y quiere comunicarlo | `challenges-service` |
| Producer | El código que publica el evento en Kafka | Kafka Producer dentro de `challenges-service` |
| Kafka | El intermediario que recibe, almacena y distribuye los eventos | tópico `challenges.results` |
| Receptor | El microservicio que recibe un evento que le interesa | `notifications-service`, `ranking-service` |
| Consumer | El código que escucha y procesa los mensajes de Kafka | `ChallengeEventConsumer` |

## 3. Envelope común **[PDF]**

Todo lo emitido se publica **en inglés** y con esta envoltura de **cinco campos**, todos
obligatorios; el contenido propio va en `payload`:

```yaml
eventId:   string (uuid)      # identifica unívocamente el evento — sirve de idempotencia
eventType: string             # qué ocurrió, MAYÚSCULAS_CON_GUION_BAJO (CHALLENGE_COMPLETED)
timestamp: string (date-time) # cuándo ocurrió, ISO-8601 en UTC
producer:  string             # qué microservicio lo emitió
payload:   object             # el contenido propio del evento
```

**No hay `eventVersion`.** No se renombran, quitan ni agregan campos del envelope.

> ⚠️ **`timestamp`.** El ejemplo del PDF es texto (`"2026-09-19T23:53:00Z"`), pero
> `JsonSerializer` de Spring Kafka escribe un `Instant` **como número**. Para publicar el
> texto: `@JsonFormat(shape = JsonFormat.Shape.STRING)` sobre el campo, o un `ObjectMapper`
> con `WRITE_DATES_AS_TIMESTAMPS` desactivado.

## 4. `eventType` **[PDF]**

`MAYÚSCULAS_CON_GUION_BAJO`, un **hecho que ya ocurrió** (no una orden):

- `CHALLENGE_COMPLETED`
- `SCORE_CALCULATED`
- `ATTEMPT_CLOSED`

Se mantiene estable una vez publicado.

## 5. Tópicos **[PDF]**

- Los tópicos se agrupan en cinco dominios: *Product Domains*, *Notifications and LLMs*,
  *Marketplace*, *Sandbox*, *Security and Audit*. Ejemplo de nombre: `challenges.results`.
- **No se permite a los grupos crear tópicos.** Si necesitan uno, avisan al grupo de
  Notificaciones.
- **[pendiente]** El PDF no trae la lista de nombres definitivos. Hasta que Notificaciones
  los asigne, los nombres del AsyncAPI son **provisorios** (`x-topic-status:
  pending-assignment`).
- Como no se pueden crear tópicos, **no hay tópico dead-letter** (`<tópico>.dlt`): los
  mensajes rechazados se guardan en el propio servicio (tabla) **[casa]**.

## 6. Conexión y configuración **[PDF]**

Los dos microservicios usan **Spring for Apache Kafka** (`org.springframework.kafka:spring-kafka`).

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:event-bus:29092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: notifications-service      # = nombre del propio servicio
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.*"
```

- Bus: `event-bus:29092`, variable `KAFKA_BOOTSTRAP`. `group-id` = nombre del servicio.
- Clase común: `Event<T>` (`eventId`, `eventType`, `timestamp` como `Instant`, `producer`,
  `payload`), con constructor vacío. Cada `eventType` tiene su clase de payload.
- Si el emisor publica JSON como `String` (p. ej. desde un outbox), no manda el header
  `__TypeId__`: el consumidor con `JsonDeserializer` debe poner
  `spring.json.use.type.headers: false` y `spring.json.value.default.type`.

## 7. Un evento por hecho de negocio **[PDF]**

- No se publica **un evento por consumidor** si representan el mismo hecho.
- No se mete en el evento "todos los datos posibles por si algún microservicio los
  necesita".
- El evento lleva lo necesario para **describir el hecho** y que los consumidores
  reaccionen; no es un contenedor de datos.

## 8. Consumidor tipado **[PDF]**

El tipo del `@KafkaListener` depende de cómo se define el payload:

```java
@KafkaListener(topics = "challenges.results", groupId = "ranking-service")
public void consume(Event<ChallengeCompletedPayload> event) { ... }
```

Si **un tópico mezcla varios `eventType`** con payloads distintos, no se puede tipar con un
solo payload: se consume `Event<?>` (o `JsonNode`) y se ramifica por `eventType`.

## 9. Correlación **[casa]**

- `traceparent` y `X-Request-Id` viajan en los **headers del mensaje Kafka**, **nunca**
  en el `payload`.
- Un servicio que **consume** un evento y a raíz de eso **publica** otro, **propaga** los
  mismos headers de correlación.
- Los logs del consumidor incluyen `X-Request-Id` como campo, para seguir un flujo de
  punta a punta a través de HTTP y eventos.
- Los headers `eventId` y `eventType` repiten los del envelope para filtrar sin
  deserializar. No hay header `eventVersion`.

## 10. Entrega at-least-once y consumidores idempotentes **[casa]**

- El bus entrega **al menos una vez**: un evento puede llegar más de una vez.
- Por eso **cada consumidor es idempotente**: procesar dos veces el mismo `eventId` no
  duplica efectos. Se implementa con un **índice de deduplicación** por `eventId` (o por
  `(consumidor, eventId)`).
- No se asume orden total entre tópicos distintos. Si el orden importa, va en el mismo
  tópico con la misma **Message Key** (el PDF no la define; es decisión de cada dominio).

## 11. Publicación: patrón outbox **[casa]**

- El evento se escribe en una tabla **outbox** en la **misma transacción** que el cambio
  de estado que lo origina.
- Un **relay** (o Debezium / connector) lee la outbox y publica en Kafka.
- **No** se llama `producer.send()` a mano en medio de la lógica de negocio: si la
  transacción hace rollback, el evento no debe existir; si commitea, el evento se publica
  sí o sí.

## 12. Versionado de eventos **[pendiente]**

El envelope no tiene `eventVersion` y el PDF no define cómo evolucionar un evento. Hasta
que Notificaciones lo defina:

- Solo cambios **compatibles** (agregar un campo opcional al `payload`); los consumidores
  **ignoran los campos que no conocen**.
- Un cambio **incompatible** (renombrar / quitar / cambiar el tipo de un campo) se **avisa
  antes por escrito** a los consumidores y se coordina; no se hace unilateralmente.
- Un campo nuevo que otro equipo necesita se **acuerda antes** de publicarlo — no se
  agrega "por las dudas".

## 13. Estructura mínima de un evento que consumimos **[casa]**

Por cada evento ajeno que consumís, documentá en el doc inter-equipos:

- el `payload` mínimo que necesitás y **por qué** (qué dispara en tu servicio),
- qué campos son **obligatorios** para vos,
- qué pasa si el evento llega sin esos campos (rechazo, dead-letter, alerta).

> Pedí estos campos **antes** de que el equipo dueño cierre su contrato de eventos.
> Después es renegociar con varios equipos a la vez.

## 14. Qué NO hacer

- No usar Kafka como si fuera HTTP (esperar una "respuesta" a un evento).
- No agregar `eventVersion` ni ningún otro campo al envelope.
- No crear tópicos (ni `.dlt`): se piden a Notificaciones.
- No inventar nombres de tópico definitivos: marcalos provisorios hasta que los asignen.
- No poner `trace_id` / `traceId` en el `payload`: la correlación es de headers.
- No publicar sin outbox desde dentro de una transacción de negocio.
- No asumir exactly-once ni orden entre tópicos.
- No consumir sin deduplicación por `eventId`.
- No modificar el contrato de eventos de otro equipo: documentá lo que necesitás, con
  dueño y fecha.
- No inventar eventos que no existen ni están acordados.
