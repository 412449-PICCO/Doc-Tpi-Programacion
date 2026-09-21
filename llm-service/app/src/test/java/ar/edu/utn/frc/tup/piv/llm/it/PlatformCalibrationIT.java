package ar.edu.utn.frc.tup.piv.llm.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.tup.piv.llm.application.worker.CalibrationRunWorker;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.ai.EncryptedSecretService;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.CalibrationRunRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProviderCredentialRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PlatformCalibrationIT extends AbstractIntegrationIT {
  static final String PERFECT = "{\\\"AUTONOMY\\\":85,\\\"CLARITY\\\":90,\\\"PROGRESSION\\\":88,\\\"COMPLIANCE\\\":92,\\\"EFFICIENCY\\\":85}";

  @Autowired ProviderCredentialRepository models;
  @Autowired EncryptedSecretService crypto;
  @Autowired CalibrationRunWorker worker;
  @Autowired CalibrationRunRepository runs;
  HttpServer server;

  @BeforeEach
  void drainCalibrationQueue() {
    while (runs.claimNextQueued().isPresent()) {}
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  private void provider(int status, String content) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", ex -> {
      String payload = status == 200
          ? "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}"
          : "{}";
      byte[] b = payload.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(status, b.length);
      ex.getResponseBody().write(b);
      ex.close();
    });
    server.start();
    var cred = models.create("openai-compatible", "local-platform",
        java.util.Map.of("baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
        crypto.encrypt("{\"apiKey\":\"sk-local-1234567\"}"), "sk-4567", TEACHER);
    var dep = models.createCandidate(cred.id(), descriptorDeModelo("modelo-local-platform"), 3);
    models.markChatVerified(dep.id());
    models.selectForCalibration(dep.id());
  }

  @Test
  void platformCalibrationActivatesTarget() throws Exception {
    provider(200, PERFECT);
    
    // Seed platform rubric and golden set
    UUID rid = UUID.fromString("10000000-0000-0000-0000-000000000002"); // already seeded globally by migrations
    
    // publish a golden set PLATFORM
    String pbase = "/api/llm/admin/templates/golden-sets";
    String gid = body(mvc.perform(asAdmin(post(pbase)).content("{\"name\":\"Platform G\"}")).andExpect(status().isCreated())).path("id").asText();
    for (int i = 0; i < 3; i++) {
      mvc.perform(asAdmin(post(pbase + "/" + gid + "/cases")).content(GoldenSetFlowIT.CASE)).andExpect(status().isCreated());
    }
    mvc.perform(asAdmin(post(pbase + "/" + gid + "/publish"))).andExpect(status().isNoContent());

    mvc.perform(asAdmin(post("/api/llm/admin/institutional-calibration/profile"))
        .content("{\"rubricVersionId\":\"" + rid + "\",\"goldenSetVersionId\":\"" + gid + "\"}"))
        .andExpect(status().isOk());

    UUID key = UUID.randomUUID();
    String runId = body(mvc.perform(asAdmin(post("/api/llm/admin/institutional-calibration/runs"))
        .header("Idempotency-Key", key.toString()))
        .andExpect(status().isAccepted())).path("id").asText();

    worker.dispatch();

    var detail = body(mvc.perform(asAdmin(get("/api/llm/admin/institutional-calibration/runs/" + runId))).andExpect(status().isOk()));
    assertThat(detail.path("run").path("state").asText()).isEqualTo("PASSED");
    assertThat(detail.path("dimensionErrors").isEmpty()).isFalse();

    var runsList = body(mvc.perform(asAdmin(get("/api/llm/admin/institutional-calibration/runs"))).andExpect(status().isOk()));
    assertThat(runsList.path("items").size()).isEqualTo(1);
    
    var activeOpt = models.deployments().stream().filter(d -> "ACTIVE".equals(d.state())).findFirst();
    assertThat(activeOpt).isPresent();
    assertThat(activeOpt.get().providerKey()).isEqualTo("openai-compatible");
  }

  private static ar.edu.utn.frc.tup.piv.llm.provider.spi.ModelDescriptor descriptorDeModelo(String modelId) {
    return new ar.edu.utn.frc.tup.piv.llm.provider.spi.ModelDescriptor(modelId, modelId, null,
        new ar.edu.utn.frc.tup.piv.llm.provider.spi.ProviderCapabilities(true, true, true, true, true, true, false, false),
        java.util.Map.of());
  }
}
