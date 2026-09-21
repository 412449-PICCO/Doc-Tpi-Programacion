package ar.edu.utn.frc.tup.piv.llm.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelInvocationResultCoverageTest {
  @Test
  void exposesTheRawAdapterAnswer() {
    var result = new ModelInvocationResult("pista", "groq", "llama-3.3");

    assertThat(result.text()).isEqualTo("pista");
    assertThat(result.provider()).isEqualTo("groq");
    assertThat(result.model()).isEqualTo("llama-3.3");
  }

  @Test
  void recordSemanticsWork() {
    var a = new ModelInvocationResult("pista", "groq", "llama-3.3");
    var b = new ModelInvocationResult("pista", "groq", "llama-3.3");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a.toString()).contains("groq");
  }
}