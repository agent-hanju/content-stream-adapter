package dev.hanju.adapter.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.matching.TokenMatchingResult.Type;

@DisplayName("TokenMatchingResult 테스트")
class TokenMatchingResultTest {

  @Test
  @DisplayName("text() - TEXT 타입과 토큰 보존")
  void textFactory() {
    List<String> tokens = List.of("Hello", " ", "World");
    TokenMatchingResult result = TokenMatchingResult.text(tokens);

    assertThat(result.type()).isEqualTo(Type.TEXT);
    assertThat(result.tokens()).containsExactly("Hello", " ", "World");
  }

  @Test
  @DisplayName("pattern() - PATTERN 타입과 토큰 보존")
  void patternFactory() {
    List<String> tokens = List.of("<", "cite", ">");
    TokenMatchingResult result = TokenMatchingResult.pattern(tokens);

    assertThat(result.type()).isEqualTo(Type.PATTERN);
    assertThat(result.tokens()).containsExactly("<", "cite", ">");
  }
}
