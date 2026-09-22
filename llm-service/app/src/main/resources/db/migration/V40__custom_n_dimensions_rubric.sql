-- Rúbricas modulares (S03-H07): discriminador de tipo, mensaje orientador docente y
-- dimensiones dinámicas de N >= 1. Modelo aditivo: no se altera rubric_dimension_v2, que
-- sigue soportando el flujo institucional de 5 dimensiones fijas (DEFAULT_INSTITUTIONAL).
ALTER TABLE llm.rubric_version_v2
  ADD COLUMN IF NOT EXISTS user_prompt TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS rubric_kind VARCHAR(32) NOT NULL DEFAULT 'DEFAULT_INSTITUTIONAL'
    CHECK (rubric_kind IN ('DEFAULT_INSTITUTIONAL', 'MODULAR_CUSTOM'));

CREATE TABLE IF NOT EXISTS llm.rubric_custom_dimensions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rubric_version_id UUID NOT NULL REFERENCES llm.rubric_version_v2(id) ON DELETE CASCADE,
  dimension_key VARCHAR(64) NOT NULL,
  label VARCHAR(160) NOT NULL,
  criterion TEXT NOT NULL,
  weight NUMERIC(5,2) NOT NULL CHECK (weight > 0 AND weight <= 100),
  anchors JSONB NOT NULL CHECK (jsonb_typeof(anchors) = 'object'),
  display_order INT NOT NULL DEFAULT 0,
  CONSTRAINT uq_rubric_custom_dimension_key UNIQUE (rubric_version_id, dimension_key)
);

CREATE INDEX IF NOT EXISTS idx_rubric_custom_dimensions_version
  ON llm.rubric_custom_dimensions(rubric_version_id);
