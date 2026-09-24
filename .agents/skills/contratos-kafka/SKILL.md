---
name: contratos-kafka
description: >-
  Referencia para escribir los contratos de eventos asíncronos de un
  microservicio de forma que cumplan con el bus de mensajería de la plataforma
  (Kafka), según el estándar de la cátedra (KAFKA.pdf): envelope de cinco campos
  (eventId, eventType, timestamp, producer, payload) sin eventVersion, eventType
  en MAYÚSCULAS_CON_GUION_BAJO, todo lo emitido en inglés, tópicos asignados por
  el grupo de Notificaciones (los grupos no crean tópicos), bus event-bus:29092,
  group-id igual al nombre del servicio, un evento por hecho de negocio. Más la
  correlación en headers de Kafka (traceparent + X-Request-Id, nunca en el
  payload), entrega at-least-once con consumidores idempotentes (deduplicación
  por eventId) y el patrón outbox. Consultá esto ANTES de definir, publicar o
  consumir un evento, o de escribir el AsyncAPI. Las reglas del bus son de la
  cátedra y del grupo de Notificaciones: acá va el resumen, la fuente
  autoritativa es KAFKA.pdf.
---

# Contratos de eventos (Kafka) — referencia

**Esto es una referencia, no un generador.** Te dice **las reglas fijas** que un contrato
de evento tiene que cumplir para viajar por el bus de la plataforma, y te da el
**esqueleto AsyncAPI** a rellenar. Qué eventos publica o consume cada servicio lo define
su equipo; esta referencia dice *con qué forma*.

> **De quién es cada cosa.** El **contrato de eventos de la plataforma** (envelope, nombres
> de `eventType`, tópicos, bus) lo define la cátedra en `KAFKA.pdf` y **los tópicos los
> asigna el grupo de Notificaciones**: no se modifica desde acá. El estilo del payload de
> *tus* eventos (qué campos, qué enums) es tuyo, siempre **en inglés**.
>
> Es el par asíncrono de [`contratos-api-gateway`](../contratos-api-gateway/SKILL.md)
> (sincrónico). **Publicar un evento no es hacer un POST a otro microservicio:** son
> canales distintos, con garantías distintas.

## Cuándo consultarla

Antes de:

- definir un **evento nuevo** que tu servicio publica,
- **consumir** un evento de otro equipo,
- escribir o cambiar el **AsyncAPI** del servicio,
- pedir un **tópico** (se le pide a Notificaciones; no se crea),
- decidir cómo se propaga la correlación en un evento, o cómo se hace idempotente un
  consumidor.

## Qué hay acá

| Archivo | Para qué |
|---|---|
| [`references/convenciones-kafka.md`](references/convenciones-kafka.md) | Las reglas: envelope, `eventType`, tópicos, conexión y configuración Spring, un evento por hecho, correlación en headers, at-least-once + consumidor idempotente, outbox, versionado (pendiente), qué NO hacer. Marca qué manda el PDF y qué es de la casa. |
| [`references/plantilla-asyncapi.yaml`](references/plantilla-asyncapi.yaml) | Esqueleto AsyncAPI 3.0 con el envelope de cinco campos. Rellenar con los eventos del servicio. |
| [`references/ejemplo-evento.md`](references/ejemplo-evento.md) | Un evento publicado y uno consumido, resueltos, con notas de por qué quedan así. |

## Cómo se usa

1. **Leé `references/convenciones-kafka.md`** entero la primera vez. Es corto.
2. Para el **AsyncAPI**: copiá `plantilla-asyncapi.yaml`, reemplazá `<n>` por el nombre
   del servicio. **Un canal por tópico**; un tópico agrupa varios `eventType`.
   `operations` con `action: send` (publica) / `action: receive` (consume). El nombre del
   canal es **el tópico que te asignó Notificaciones**: mientras no lo tengas, márcalo
   `x-topic-status: pending-assignment`, no lo inventes como definitivo.
3. **Envelope común** en todos, exactamente cinco campos: `eventId` (UUID — sirve de
   idempotencia), `eventType`, `timestamp`, `producer`, `payload`. **No hay
   `eventVersion`.** El contenido propio del evento va en `payload`.
4. **La correlación va en headers de Kafka** (`traceparent`, `X-Request-Id`), nunca en el
   payload. Un servicio que consume un evento y publica otro **propaga** los mismos
   headers.
5. Para **consumir**: el consumidor es **idempotente** — procesar dos veces el mismo
   `eventId` no duplica efectos (índice de deduplicación por `eventId`). Si el tópico
   mezcla varios `eventType` con payloads distintos, consumí `Event<?>` y ramificá por
   `eventType`.
6. Para **publicar**: usá el patrón **outbox** — el evento se escribe en la misma
   transacción que el cambio de estado que lo origina, y un relay lo publica después. No
   se hace `publish()` a mano en medio de la lógica.
7. El **doc inter-equipos** (quién publica qué, quién consume qué, con qué estructura
   mínima) vive en [`contratos-api-gateway/references/plantilla-contratos-inter-equipos.md`](../contratos-api-gateway/references/plantilla-contratos-inter-equipos.md)
   — cubre los dos canales; completá la sección de eventos ahí.

## Reglas de oro (resumen de la referencia)

- **Publicar un evento ≠ POST a otro servicio.** Kafka es el bus; el Gateway es para HTTP.
- **Envelope de cinco campos:** `eventId`, `eventType`, `timestamp`, `producer`, `payload`.
  Sin `eventVersion`. Todo lo emitido en inglés.
- **`eventType` en `MAYÚSCULAS_CON_GUION_BAJO`**, un hecho ya ocurrido: `CHALLENGE_COMPLETED`.
- **No se crean tópicos.** Si necesitás uno, se lo pedís al grupo de Notificaciones.
- **Bus:** `event-bus:29092` (variable `KAFKA_BOOTSTRAP`); `group-id` = nombre del servicio.
- **Un evento por hecho de negocio**, no uno por consumidor; el payload describe el hecho,
  no es un contenedor de "todos los datos posibles".
- **Correlación en headers de Kafka**, no en el payload. Se propaga aguas abajo.
- **At-least-once:** el consumidor es idempotente; dedup por `eventId`.
- **Outbox:** publicar en la misma transacción que el cambio de estado.
- **Versionado:** el estándar no define aún cómo versionar sin `eventVersion` (pendiente
  con Notificaciones). Mientras tanto: solo cambios compatibles y aviso previo por escrito
  a los consumidores para un cambio incompatible.
- Pedí los campos que necesitás de un evento ajeno **antes** de que el equipo dueño cierre
  su contrato. Después es renegociar con varios equipos.
- **No modifiques** el contrato de eventos de otros equipos: documentá lo que necesitás
  en el doc inter-equipos, con dueño y fecha.

## Checklist de un contrato de eventos terminado

- [ ] AsyncAPI: un canal por tópico; `operations` send/receive; envelope de cinco campos;
      **valida** contra su esquema (linter en CI); sin `<placeholders>`.
- [ ] Tópicos: los asignó Notificaciones, o están marcados como provisorios.
- [ ] `eventType` en `MAYÚSCULAS_CON_GUION_BAJO`, payload en inglés, sin `eventVersion`.
- [ ] Correlación en headers de Kafka, no en el payload.
- [ ] Cada consumidor documentado como idempotente (dedup por `eventId`).
- [ ] Publicación por outbox, no `publish()` suelto.
- [ ] `timestamp` como texto ISO-8601 (ojo: `JsonSerializer` de Spring escribe un `Instant`
      como número).
- [ ] Estructura mínima de cada evento consumido, listada en el doc inter-equipos, con
      los campos obligatorios y por qué.
- [ ] Eventos ajenos pendientes de acordar, marcados 🔴 con dueño y fecha.
- [ ] Nada inventado: los eventos vienen del alcance del servicio.

## Relación con el resto del bundle

- `generar-vision-y-alcance` da las **funciones** → de ahí salen los eventos que se
  publican/consumen.
- `contratos-api-gateway` es el par sincrónico.
- `generar-historias-usuario` cita los eventos en *Dependencias*.
- `generar-backlog-y-recetas` marca en cada receta qué evento se implementa.

Para el skill hub, esto va como `type: reference`; la fuente autoritativa es `KAFKA.pdf`.
