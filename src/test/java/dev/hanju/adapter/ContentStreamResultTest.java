package dev.hanju.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContentStreamResult 테스트")
class ContentStreamResultTest {

  @Nested
  @DisplayName("Text")
  class TextTests {
    @Test
    @DisplayName("content가 null이면 예외")
    void nullContentThrows() {
      assertThatThrownBy(() -> new ContentStreamResult.Text(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정상 생성")
    void createsWithContent() {
      ContentStreamResult.Text text = new ContentStreamResult.Text("hello");
      assertThat(text.content()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("Enter")
  class EnterTests {
    @Test
    @DisplayName("path가 null이면 예외")
    void nullPathThrows() {
      assertThatThrownBy(() -> new ContentStreamResult.Enter(null, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("attributes가 null이면 빈 맵으로 대체")
    void nullAttributesDefaultsToEmptyMap() {
      ContentStreamResult.Enter enter = new ContentStreamResult.Enter("/cite", null);
      assertThat(enter.attributes()).isEmpty();
    }

    @Test
    @DisplayName("단일 인자 생성자 - 속성 없는 Enter")
    void singleArgConstructorDefaultsToEmptyMap() {
      ContentStreamResult.Enter enter = new ContentStreamResult.Enter("/cite");
      assertThat(enter.path()).isEqualTo("/cite");
      assertThat(enter.attributes()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Exit")
  class ExitTests {
    @Test
    @DisplayName("path가 null이면 예외")
    void nullPathThrows() {
      assertThatThrownBy(() -> new ContentStreamResult.Exit(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정상 생성")
    void createsWithPath() {
      ContentStreamResult.Exit exit = new ContentStreamResult.Exit("/cite");
      assertThat(exit.path()).isEqualTo("/cite");
    }
  }
}
