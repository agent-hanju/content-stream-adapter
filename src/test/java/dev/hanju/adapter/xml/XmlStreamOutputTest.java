package dev.hanju.adapter.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.xml.XmlStreamOutput;

@DisplayName("XmlStreamOutput 테스트")
class XmlStreamOutputTest {

  @Nested
  @DisplayName("Text")
  class TextTests {
    @Test
    @DisplayName("content가 null이면 예외")
    void nullContentThrows() {
      assertThatThrownBy(() -> new XmlStreamOutput.Text(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정상 생성")
    void createsWithContent() {
      XmlStreamOutput.Text text = new XmlStreamOutput.Text("hello");
      assertThat(text.content()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("Enter")
  class EnterTests {
    @Test
    @DisplayName("path가 null이면 예외")
    void nullPathThrows() {
      assertThatThrownBy(() -> new XmlStreamOutput.Enter(null, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("attributes가 null이면 빈 맵으로 대체")
    void nullAttributesDefaultsToEmptyMap() {
      XmlStreamOutput.Enter enter = new XmlStreamOutput.Enter("/cite", null);
      assertThat(enter.attributes()).isEmpty();
    }

    @Test
    @DisplayName("단일 인자 생성자 - 속성 없는 Enter")
    void singleArgConstructorDefaultsToEmptyMap() {
      XmlStreamOutput.Enter enter = new XmlStreamOutput.Enter("/cite");
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
      assertThatThrownBy(() -> new XmlStreamOutput.Exit(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정상 생성")
    void createsWithPath() {
      XmlStreamOutput.Exit exit = new XmlStreamOutput.Exit("/cite");
      assertThat(exit.path()).isEqualTo("/cite");
    }
  }
}
