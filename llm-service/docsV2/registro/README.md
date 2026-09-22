# Registro de cambios V2

Cada cambio aprobado debe registrar: fecha, decisión anterior, motivo, regla vigente, fuentes,
documentos V2 corregidos, documentos históricos relacionados, responsable y evidencia de prueba.

El primer registro a migrar será la decisión de rúbricas editables y versionadas aprobada por el
Product Owner.

## Registros

| Fecha | Registro | Qué decidió |
|---|---|---|
| 2026-09-14 | [inicio-docsv2](2026-09-14-inicio-docsv2.md) | Arranque de la documentación V2 |
| 2026-09-15 | [consolidacion-repeticiones](2026-09-15-consolidacion-repeticiones.md) | Consolidación de documentos repetidos |
| 2026-09-20 | [estandar-kafka-del-pdf](2026-09-20-estandar-kafka-del-pdf.md) | Se adopta el envelope Kafka de la cátedra |
| 2026-09-21 | [integracion-main-a-dev](2026-09-21-integracion-main-a-dev.md) | Una sola rama: arquitectura de `main`, funcionalidades de `dev` |
| 2026-09-21 | [revision-ep01-h01](2026-09-21-revision-ep01-h01.md) | ADR-003 supersede a ADR-001; variables de entorno y `ArchitectureTest` al día |
| 2026-09-21 | [revision-ep01-h02](2026-09-21-revision-ep01-h02.md) | `up` real: precondiciones del entorno, smoke autocontenido, 204 s en frío |
| 2026-09-21 | [revision-ep01-h03](2026-09-21-revision-ep01-h03.md) | `404` real para «no existe» (T9) y gate de cobertura recuperado |
| 2026-09-21 | [revision-ep01-h04](2026-09-21-revision-ep01-h04.md) | Esquema: `FlywaySchemaTest` roto y reproducibilidad desde cero probada de verdad |
| 2026-09-21 | [revision-ep01-h05](2026-09-21-revision-ep01-h05.md) | Contrato OpenAPI inválido (3.0 en 3.1) corregido, URL del mock y contrato-vs-código automatizado |
| 2026-09-21 | [revision-ep01-h06](2026-09-21-revision-ep01-h06.md) | Script de reinicio y guía de demo rotos por la integración: corregidos y ejecutados |
| 2026-09-21 | [revision-ep01-h07](2026-09-21-revision-ep01-h07.md) | Kafka: 5 CA verificados; comportamientos de `main` sin disparador y AsyncAPI que declara de más |
