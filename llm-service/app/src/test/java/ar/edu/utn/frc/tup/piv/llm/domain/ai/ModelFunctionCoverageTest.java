package ar.edu.utn.frc.tup.piv.llm.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelFunctionCoverageTest {
  @Test
  void declaresEveryProductFunctionWithARootName() {
    assertThat(ModelFunction.values())
        .containsExactly(ModelFunction.TUTOR, ModelFunction.EVALUATOR, ModelFunction.MODERATOR, ModelFunction.GENERATOR);
    assertThat(ModelFunction.TUTOR.name()).isEqualTo("TUTOR");
    assertThat(ModelFunction.valueOf("EVALUATOR")).isEqualTo(ModelFunction.EVALUATOR);
  }
}