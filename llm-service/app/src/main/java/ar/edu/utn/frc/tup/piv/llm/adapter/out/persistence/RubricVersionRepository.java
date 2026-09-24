package ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence;

import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.DimensionInput;
import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.DimensionCustomInput;
import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.Anchors;
import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.RubricInput;
import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.RubricVersion;
import ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension;
import ar.edu.utn.frc.tup.piv.llm.domain.RubricValidator.DimensionDefinition;
import ar.edu.utn.frc.tup.piv.llm.domain.RubricValidator.DimensionCustomDefinition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistence boundary for course-scoped rubric versions. */
@Repository
public class RubricVersionRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  public RubricVersionRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

  public List<DimensionDefinition> dimensionsOfDraft(UUID courseId, UUID versionId) {
    return jdbc.query("""
        select d.dimension_key, d.weight from llm.rubric_dimension_v2 d
        join llm.rubric_version_v2 v on v.id = d.rubric_version_id join llm.rubric_families f on f.id = v.family_id
        where v.id = ? and f.course_id = ? and f.scope = 'COURSE' and v.state = 'DRAFT' order by d.dimension_key
        """, (rs, row) -> new DimensionDefinition(Dimension.valueOf(rs.getString("dimension_key")), rs.getObject("weight", BigDecimal.class)), versionId, courseId);
  }

  public List<DimensionCustomDefinition> customDimensionsOfDraft(UUID courseId, UUID versionId) {
    return jdbc.query("""
        select d.dimension_key, d.weight from llm.rubric_custom_dimensions d
        join llm.rubric_version_v2 v on v.id = d.rubric_version_id join llm.rubric_families f on f.id = v.family_id
        where v.id = ? and f.course_id = ? and f.scope = 'COURSE' and v.state = 'DRAFT' order by d.display_order, d.dimension_key
        """, (rs, row) -> new DimensionCustomDefinition(rs.getString("dimension_key"), rs.getObject("weight", BigDecimal.class)), versionId, courseId);
  }

  public boolean publishDraft(UUID courseId, UUID versionId) {
    return jdbc.update("""
        update llm.rubric_version_v2 v set state = 'PUBLISHED', published_at = now() from llm.rubric_families f
        where v.family_id = f.id and v.id = ? and f.course_id = ? and f.scope = 'COURSE' and v.state = 'DRAFT'
        """, versionId, courseId) == 1;
  }

  public List<RubricVersion> list(UUID courseId) {
    return jdbc.query("""
        select v.id, v.family_id, v.version_no, v.name, v.state::text as state, v.revision, v.template_origin_version_id,
               v.user_prompt, v.rubric_kind
        from llm.rubric_version_v2 v join llm.rubric_families f on f.id = v.family_id
        where f.course_id = ? and f.scope = 'COURSE' order by v.name, v.version_no desc
        """, (rs, row) -> versionRow(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class), rs.getInt("version_no"),
        rs.getString("name"), rs.getString("state"), rs.getLong("revision"), rs.getObject("template_origin_version_id", UUID.class),
        rs.getString("user_prompt"), rs.getString("rubric_kind")), courseId);
  }

  public List<RubricVersion> listTemplates() {
    return jdbc.query("""
        select v.id, v.family_id, v.version_no, v.name, v.state::text as state, v.revision, v.template_origin_version_id,
               v.user_prompt, v.rubric_kind
        from llm.rubric_version_v2 v join llm.rubric_families f on f.id = v.family_id
        where f.scope = 'PLATFORM' and f.course_id is null order by v.name, v.version_no desc
        """, (rs, row) -> versionRow(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class), rs.getInt("version_no"),
        rs.getString("name"), rs.getString("state"), rs.getLong("revision"), rs.getObject("template_origin_version_id", UUID.class),
        rs.getString("user_prompt"), rs.getString("rubric_kind")));
  }

  public Optional<RubricVersion> findTemplate(UUID versionId) {
    return jdbc.query("""
        select v.id, v.family_id, v.version_no, v.name, v.state::text as state, v.revision, v.template_origin_version_id,
               v.user_prompt, v.rubric_kind
        from llm.rubric_version_v2 v join llm.rubric_families f on f.id = v.family_id
        where f.scope = 'PLATFORM' and f.course_id is null and v.id = ?
        """, (rs, row) -> versionRow(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class), rs.getInt("version_no"),
        rs.getString("name"), rs.getString("state"), rs.getLong("revision"), rs.getObject("template_origin_version_id", UUID.class),
        rs.getString("user_prompt"), rs.getString("rubric_kind")), versionId).stream().findFirst();
  }

  public RubricVersion createTemplateDraft(RubricInput input, UUID actorId) {
    UUID familyId = UUID.randomUUID(); UUID versionId = UUID.randomUUID();
    jdbc.update("insert into llm.rubric_families (id, scope, next_version, created_by_user_id) values (?, 'PLATFORM', 2, ?)", familyId, actorId);
    jdbc.update("insert into llm.rubric_version_v2 (id, family_id, version_no, name, created_by_user_id, user_prompt, rubric_kind) values (?, ?, 1, ?, ?, ?, ?)",
        versionId, familyId, input.name(), actorId, input.userPrompt() != null ? input.userPrompt() : "",
        input.rubricKind() != null ? input.rubricKind() : "DEFAULT_INSTITUTIONAL");
    replaceDimensions(versionId, input.dimensions());
    return versionRow(versionId, familyId, 1, input.name(), "DRAFT", 1, null, input.userPrompt(), input.rubricKind());
  }

  public boolean advanceTemplateRevision(UUID versionId, long expectedRevision) {
    return jdbc.update("""
        update llm.rubric_version_v2 v set revision = revision + 1 from llm.rubric_families f
        where v.family_id = f.id and f.scope = 'PLATFORM' and v.id = ? and v.state = 'DRAFT' and v.revision = ?
        """, versionId, expectedRevision) == 1;
  }

  public boolean publishTemplateDraft(UUID versionId) {
    return jdbc.update("""
        update llm.rubric_version_v2 v set state = 'PUBLISHED', published_at = now() from llm.rubric_families f
        where v.family_id = f.id and f.scope = 'PLATFORM' and v.id = ? and v.state = 'DRAFT'
        """, versionId) == 1;
  }

  public Optional<RubricVersion> createNextTemplateDraft(UUID publishedVersionId, UUID actorId) {
    var source = findTemplate(publishedVersionId).filter(version -> "PUBLISHED".equals(version.state()));
    if (source.isEmpty()) return Optional.empty();
    var current = source.get(); UUID versionId = UUID.randomUUID();
    int version = jdbc.queryForObject("select coalesce(max(version_no), 0) + 1 from llm.rubric_version_v2 where family_id = ?", Integer.class, current.familyId());
    jdbc.update("insert into llm.rubric_version_v2 (id, family_id, version_no, name, based_on_version_id, created_by_user_id, user_prompt, rubric_kind) values (?, ?, ?, ?, ?, ?, ?, ?)",
        versionId, current.familyId(), version, current.name(), publishedVersionId, actorId, current.userPrompt(), current.rubricKind());
    if ("MODULAR_CUSTOM".equalsIgnoreCase(current.rubricKind())) {
      jdbc.update("""
          insert into llm.rubric_custom_dimensions (rubric_version_id, dimension_key, label, criterion, anchors, weight, display_order)
          select ?, dimension_key, label, criterion, anchors, weight, display_order from llm.rubric_custom_dimensions where rubric_version_id = ?
          """, versionId, publishedVersionId);
    } else {
      jdbc.update("""
          insert into llm.rubric_dimension_v2 (rubric_version_id, dimension_key, label, criterion, anchors, weight)
          select ?, dimension_key, label, criterion, anchors, weight from llm.rubric_dimension_v2 where rubric_version_id = ?
          """, versionId, publishedVersionId);
    }
    return Optional.of(versionRow(versionId, current.familyId(), version, current.name(), "DRAFT", 1, null, current.userPrompt(), current.rubricKind()));
  }

  public Optional<RubricVersion> find(UUID courseId, UUID versionId) {
    return jdbc.query("""
        select v.id, v.family_id, v.version_no, v.name, v.state::text as state, v.revision, v.template_origin_version_id,
               v.user_prompt, v.rubric_kind
        from llm.rubric_version_v2 v join llm.rubric_families f on f.id = v.family_id
        where f.course_id = ? and f.scope = 'COURSE' and v.id = ?
        """, (rs, row) -> versionRow(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class), rs.getInt("version_no"),
        rs.getString("name"), rs.getString("state"), rs.getLong("revision"), rs.getObject("template_origin_version_id", UUID.class),
        rs.getString("user_prompt"), rs.getString("rubric_kind")), courseId, versionId).stream().findFirst();
  }

  public Optional<RubricVersion> createDraftFromPublishedTemplate(UUID courseId, UUID templateVersionId, String name, UUID actorId) {
    var template = jdbc.query("""
        select v.id, v.user_prompt, v.rubric_kind from llm.rubric_version_v2 v join llm.rubric_families f on f.id = v.family_id
        where v.id = ? and v.state = 'PUBLISHED' and f.scope = 'PLATFORM' and f.course_id is null
        """, (rs, row) -> new TemplateRow(rs.getObject("id", UUID.class), rs.getString("user_prompt"), rs.getString("rubric_kind")), templateVersionId);
    if (template.isEmpty()) return Optional.empty();
    var source = template.getFirst(); UUID familyId = UUID.randomUUID(); UUID versionId = UUID.randomUUID();
    jdbc.update("insert into llm.rubric_families (id, scope, course_id, next_version, created_by_user_id) values (?, 'COURSE', ?, 2, ?)", familyId, courseId, actorId);
    jdbc.update("insert into llm.rubric_version_v2 (id, family_id, version_no, name, template_origin_version_id, created_by_user_id, user_prompt, rubric_kind) values (?, ?, 1, ?, ?, ?, ?, ?)",
        versionId, familyId, name, source.id(), actorId, source.userPrompt() != null ? source.userPrompt() : "",
        source.rubricKind() != null ? source.rubricKind() : "DEFAULT_INSTITUTIONAL");
    if ("MODULAR_CUSTOM".equalsIgnoreCase(source.rubricKind())) {
      jdbc.update("""
          insert into llm.rubric_custom_dimensions (rubric_version_id, dimension_key, label, criterion, anchors, weight, display_order)
          select ?, dimension_key, label, criterion, anchors, weight, display_order from llm.rubric_custom_dimensions where rubric_version_id = ?
          """, versionId, source.id());
    } else {
      jdbc.update("""
          insert into llm.rubric_dimension_v2 (rubric_version_id, dimension_key, label, criterion, anchors, weight)
          select ?, dimension_key, label, criterion, anchors, weight from llm.rubric_dimension_v2 where rubric_version_id = ?
          """, versionId, source.id());
    }
    return Optional.of(versionRow(versionId, familyId, 1, name, "DRAFT", 1, source.id(), source.userPrompt(), source.rubricKind()));
  }

  /** Copies only a published version, reserves the family sequence atomically, and returns a mutable draft. */
  public Optional<RubricVersion> createNextDraft(UUID courseId, UUID publishedVersionId, UUID actorId) {
    var reservation = jdbc.query("""
        update llm.rubric_families f set next_version = f.next_version + 1
        from llm.rubric_version_v2 source
        where source.family_id = f.id and source.id = ? and source.state = 'PUBLISHED'
          and f.course_id = ? and f.scope = 'COURSE'
        returning f.id, f.next_version - 1 as version_no
        """, (rs, row) -> new FamilyVersion(rs.getObject("id", UUID.class), rs.getInt("version_no")), publishedVersionId, courseId);
    if (reservation.isEmpty()) return Optional.empty();
    FamilyVersion family = reservation.getFirst(); UUID newVersionId = UUID.randomUUID();
    var source = find(courseId, publishedVersionId).orElseThrow();
    jdbc.update("insert into llm.rubric_version_v2 (id, family_id, version_no, name, based_on_version_id, created_by_user_id, user_prompt, rubric_kind) values (?, ?, ?, ?, ?, ?, ?, ?)",
        newVersionId, family.id(), family.version(), source.name(), publishedVersionId, actorId, source.userPrompt(), source.rubricKind());
    if ("MODULAR_CUSTOM".equalsIgnoreCase(source.rubricKind())) {
      jdbc.update("""
          insert into llm.rubric_custom_dimensions (rubric_version_id, dimension_key, label, criterion, anchors, weight, display_order)
          select ?, dimension_key, label, criterion, anchors, weight, display_order
          from llm.rubric_custom_dimensions where rubric_version_id = ?
          """, newVersionId, publishedVersionId);
    } else {
      jdbc.update("""
          insert into llm.rubric_dimension_v2 (rubric_version_id, dimension_key, label, criterion, anchors, weight)
          select ?, dimension_key, label, criterion, anchors, weight
          from llm.rubric_dimension_v2 where rubric_version_id = ?
          """, newVersionId, publishedVersionId);
    }
    return Optional.of(versionRow(newVersionId, family.id(), family.version(), source.name(), "DRAFT", 1, null, source.userPrompt(), source.rubricKind()));
  }

  public boolean advanceRevision(UUID courseId, UUID versionId, long expectedRevision) {
    return jdbc.update("""
        update llm.rubric_version_v2 v set revision = revision + 1 from llm.rubric_families f
        where v.family_id = f.id and f.course_id = ? and f.scope = 'COURSE' and v.id = ? and v.state = 'DRAFT' and v.revision = ?
        """, courseId, versionId, expectedRevision) == 1;
  }

  public void updateVersionName(UUID courseId, UUID versionId, String name) {
    jdbc.update("""
        update llm.rubric_version_v2 v set name = ? from llm.rubric_families f
        where v.family_id = f.id and v.id = ? and f.course_id = ? and f.scope = 'COURSE' and v.state = 'DRAFT'
        """, name, versionId, courseId);
  }

  public void updateTemplateVersionName(UUID versionId, String name) {
    jdbc.update("""
        update llm.rubric_version_v2 v set name = ? from llm.rubric_families f
        where v.family_id = f.id and v.id = ? and f.scope = 'PLATFORM' and v.state = 'DRAFT'
        """, name, versionId);
  }

  public void updateUserPrompt(UUID courseId, UUID versionId, String userPrompt) {
    jdbc.update("""
        update llm.rubric_version_v2 v set user_prompt = ? from llm.rubric_families f
        where v.family_id = f.id and v.id = ? and f.course_id = ? and f.scope = 'COURSE' and v.state = 'DRAFT'
        """, userPrompt != null ? userPrompt : "", versionId, courseId);
  }

  public void updateRubricKind(UUID courseId, UUID versionId, String rubricKind) {
    jdbc.update("""
        update llm.rubric_version_v2 v set rubric_kind = ? from llm.rubric_families f
        where v.family_id = f.id and v.id = ? and f.course_id = ? and f.scope = 'COURSE' and v.state = 'DRAFT'
        """, rubricKind != null ? rubricKind : "DEFAULT_INSTITUTIONAL", versionId, courseId);
  }

  public void replaceDimensions(UUID versionId, List<DimensionInput> dimensions) {
    jdbc.update("delete from llm.rubric_dimension_v2 where rubric_version_id = ?", versionId);
    if (dimensions == null) return;
    for (var dimension : dimensions) jdbc.update("""
        insert into llm.rubric_dimension_v2 (rubric_version_id, dimension_key, label, criterion, anchors, weight)
        values (?, ?, ?, ?, cast(? as jsonb), ?)
        """, versionId, dimension.key().name(), dimension.label(), dimension.criterion(), serialize(dimension.anchors()), dimension.weight());
  }

  /** Pesos + evaluator_prompt de las 5 dimensiones de una versión de rúbrica - la calibración
   * siempre referencia una versión ya PUBLICADA (constraint FK), así que a diferencia de
   * {@link #dimensionsOfDraft} esto no filtra por curso ni por estado DRAFT. */
  public List<DimensionInput> weightsAndPrompts(UUID rubricVersionId) { return dimensions(rubricVersionId); }

  public void replaceCustomDimensions(UUID versionId, List<DimensionCustomInput> dimensions) {
    jdbc.update("delete from llm.rubric_custom_dimensions where rubric_version_id = ?", versionId);
    if (dimensions == null) return;
    int order = 0;
    for (var dimension : dimensions) {
      jdbc.update("""
          insert into llm.rubric_custom_dimensions (rubric_version_id, dimension_key, label, criterion, anchors, weight, display_order)
          values (?, ?, ?, ?, cast(? as jsonb), ?, ?)
          """, versionId, dimension.key(), dimension.label(), dimension.criterion(), serialize(dimension.anchors()), dimension.weight(), order++);
    }
  }

  private RubricVersion versionRow(UUID id, UUID familyId, int version, String name, String state, long revision, UUID templateOriginVersionId, String userPrompt, String rubricKind) {
    String prompt = userPrompt != null ? userPrompt : "";
    String kind = (rubricKind != null && !rubricKind.isBlank()) ? rubricKind : "DEFAULT_INSTITUTIONAL";
    if ("MODULAR_CUSTOM".equalsIgnoreCase(kind)) {
      return new RubricVersion(id, familyId, version, name, state, revision, templateOriginVersionId, prompt, kind, List.of(), customDimensions(id));
    } else {
      return new RubricVersion(id, familyId, version, name, state, revision, templateOriginVersionId, prompt, kind, dimensions(id), List.of());
    }
  }

  private RubricVersion versionRow(UUID id, UUID familyId, int version, String name, String state, long revision, UUID templateOriginVersionId) {
    return versionRow(id, familyId, version, name, state, revision, templateOriginVersionId, "", "DEFAULT_INSTITUTIONAL");
  }
  private List<DimensionInput> dimensions(UUID versionId) {
    return jdbc.query("select dimension_key, label, criterion, anchors::text as anchors, weight from llm.rubric_dimension_v2 where rubric_version_id = ? order by dimension_key",
        (rs, row) -> new DimensionInput(Dimension.valueOf(rs.getString("dimension_key")), rs.getString("label"), rs.getString("criterion"), deserialize(rs.getString("anchors")), rs.getObject("weight", BigDecimal.class)), versionId);
  }
  public List<DimensionCustomInput> customDimensions(UUID versionId) {
    return jdbc.query("select dimension_key, label, criterion, anchors::text as anchors, weight from llm.rubric_custom_dimensions where rubric_version_id = ? order by display_order, dimension_key",
        (rs, row) -> new DimensionCustomInput(rs.getString("dimension_key"), rs.getString("label"), rs.getString("criterion"), deserialize(rs.getString("anchors")), rs.getObject("weight", BigDecimal.class)), versionId);
  }
  private String serialize(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalArgumentException("Anclas inválidas", exception); } }
  private Anchors deserialize(String value) { try { return mapper.readValue(value, Anchors.class); } catch (JsonProcessingException exception) { throw new IllegalStateException("Anclas almacenadas inválidas", exception); } }
  private record FamilyVersion(UUID id, int version) {}
  private record TemplateRow(UUID id, String userPrompt, String rubricKind) {}
}
