package ar.edu.utn.frc.tup.piv.llm.application.service;
import ar.edu.utn.frc.tup.piv.llm.application.CalibrationInferencePolicy;

import ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics;
import ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderInvocationGateway;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.ProviderRegistry;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.CalibrationRunRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** Executes a claimed run against the provider through the sole internal AI gateway. */
@Service
public class RealCalibrationExecutor {
  private final CalibrationRunRepository runs; private final ProviderCredentialRepository usage;
  private final ProviderInvocationGateway gateway; private final ProviderRegistry registry; private final ObjectMapper json; private final CalibrationInferencePolicy policy;
  public RealCalibrationExecutor(CalibrationRunRepository runs, ProviderCredentialRepository usage, ProviderInvocationGateway gateway, ProviderRegistry registry, ObjectMapper json, CalibrationInferencePolicy policy) { this.runs=runs;this.usage=usage;this.gateway=gateway;this.registry=registry;this.json=json;this.policy=policy; }
  public void execute(UUID runId) {
    CalibrationRunRepository.Execution execution = null;
    try {
      execution=runs.execution(runId); List<CalibrationMetrics.CaseScores> all=new ArrayList<>(); int total=execution.cases().size(); int completed=0;
      var credential=usage.get(execution.deployment().credentialId()).filter(value->"ACTIVE".equals(value.state())).orElseThrow(()->new IllegalStateException("La credencial del deployment no está activa"));
      var settings=policy.resolve(registry.required(execution.deployment().providerKey()).descriptor().capabilities(), execution.seed() == null ? 0L : execution.seed());
      String fingerprint=null;
      for(var item:execution.cases()) {
        var reply=gateway.invoke(credential,execution.deployment().modelId(),prompt(execution.rubric(),item),settings.settings(),Duration.ofSeconds(90));
        Map<Dimension,Integer> model=parseScores(reply.text());
        if (reply.providerFingerprint()!=null) fingerprint=reply.providerFingerprint();
        runs.saveCase(runId,item,model,execution.weights()); usage.recordUsage(execution.deployment().id(),"EVALUATION",reply.inputTokens(),reply.outputTokens());
        all.add(new CalibrationMetrics.CaseScores(item.humanScores(),model)); runs.progress(runId,++completed*100/total);
      }
      var metrics=CalibrationMetrics.assess(all,execution.weights()); runs.recordInference(runId, json.writeValueAsString(settings.auditView()), fingerprint); runs.finish(runId,metrics.passed(),metrics.maeFinal(),metrics.maxIndividualError()); runs.refreshStability(runId);
      settlePlatformOutcome(execution,metrics.passed());
    } catch (Exception failure) { 
        System.out.println("CALIBRATION FAILED EXCEPTION:");
        failure.printStackTrace();
        var diagnostic=diagnostic(failure); 
        runs.fail(runId,diagnostic.code(),diagnostic.detail()); 
        runs.refreshStability(runId);
        if (execution != null && "PLATFORM".equals(execution.run().stage())) usage.excludeFromCalibrationTarget(execution.deployment().id());
      }
  }
  /** T-658: una corrida institucional habilita (o vuelve a dejar en candidato) el deployment. */
  private void settlePlatformOutcome(CalibrationRunRepository.Execution execution, boolean passed) {
    if ("PLATFORM".equals(execution.run().stage())) {
      if (passed) usage.activate(execution.deployment().id());
      else usage.excludeFromCalibrationTarget(execution.deployment().id());
    }
  }
  private String prompt(String rubric, CalibrationRunRepository.Case item) {
    return "Actuás como evaluador pedagógico. Evaluá la conversación y el contexto con esta rúbrica:\n%s\nConversación: %s\nContexto: %s\nRespondé exclusivamente JSON, sin Markdown, con las cinco claves AUTONOMY, CLARITY, PROGRESSION, COMPLIANCE y EFFICIENCY. Cada valor debe ser un entero de 0 a 100.".formatted(rubric,item.transcript(),item.challengeContext());
  }
  private Map<Dimension,Integer> parseScores(String value) {
    System.out.println("LLM RESPONSE WAS: " + value);
    try {
      String clean = value;
      int start = clean.indexOf("{");
      int end = clean.lastIndexOf("}");
      if (start != -1 && end != -1 && end >= start) clean = clean.substring(start, end + 1);
      JsonNode root=json.readTree(clean); Map<Dimension,Integer> scores=new EnumMap<>(Dimension.class);
      for(var dimension:Dimension.values()) { 
          JsonNode score=root.get(dimension.name()); 
          if (score == null) score = root.get(dimension.name().toLowerCase());
          if(score==null||!score.isIntegralNumber()||score.intValue()<0||score.intValue()>100) throw new IllegalArgumentException("Puntaje de proveedor inválido"); 
          scores.put(dimension,score.intValue()); 
      }
      return Map.copyOf(scores);
    } catch (Exception error) { throw new IllegalArgumentException("La respuesta del proveedor no tiene el formato de evaluación requerido",error); }
  }
  private Diagnostic diagnostic(Exception failure) {
    String message = failure.getMessage() == null ? "" : failure.getMessage();
    if (message.contains("formato de evaluación")) return new Diagnostic("MALFORMED_MODEL_RESPONSE", "El modelo respondió, pero no devolvió los cinco puntajes enteros requeridos.");
    if (message.contains("HTTP 429")) return new Diagnostic("PROVIDER_RATE_LIMIT", "El proveedor limitó las solicitudes. Esperá un momento y reintentá.");
    if (message.contains("HTTP 5")) return new Diagnostic("PROVIDER_UNAVAILABLE", "El proveedor tuvo un error temporal. Reintentá más tarde.");
    if (message.contains("No se pudo conectar")) return new Diagnostic("PROVIDER_CONNECTION_FAILED", "No se pudo conectar con el proveedor. Verificá la credencial, el modelo y la conectividad.");
    return new Diagnostic("CALIBRATION_EXECUTION_FAILED", "La calibración no pudo completarse. Revisá la configuración del modelo e intentá nuevamente.");
  }
  private record Diagnostic(String code, String detail) {}
}
