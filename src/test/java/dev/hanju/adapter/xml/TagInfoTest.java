package dev.hanju.adapter.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.xml.TagInfo;
import dev.hanju.adapter.xml.TagInfo.TagType;

@DisplayName("TagInfo 테스트")
class TagInfoTest {

  @Test
  @DisplayName("name이 null이면 예외")
  void nullNameThrows() {
    assertThatThrownBy(() -> new TagInfo(null, TagType.OPEN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("name이 빈 문자열이면 예외")
  void emptyNameThrows() {
    assertThatThrownBy(() -> new TagInfo("", TagType.OPEN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("type이 null이면 예외")
  void nullTypeThrows() {
    assertThatThrownBy(() -> new TagInfo("cite", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("attributes가 null이면 빈 맵으로 대체")
  void nullAttributesDefaultsToEmptyMap() {
    TagInfo tag = new TagInfo("cite", TagType.OPEN, null);

    assertThat(tag.attributes()).isEmpty();
  }

  @Test
  @DisplayName("open(name, attributes) - OPEN 타입과 속성 보존")
  void openWithAttributes() {
    Map<String, String> attrs = Map.of("id", "ref1");
    TagInfo tag = TagInfo.open("cite", attrs);

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.type()).isEqualTo(TagType.OPEN);
    assertThat(tag.attributes()).containsEntry("id", "ref1");
  }

  @Test
  @DisplayName("open(name) - 속성 없는 OPEN 타입")
  void openWithoutAttributes() {
    TagInfo tag = TagInfo.open("cite");

    assertThat(tag.type()).isEqualTo(TagType.OPEN);
    assertThat(tag.attributes()).isEmpty();
  }

  @Test
  @DisplayName("close(name) - CLOSE 타입, 속성 없음")
  void close() {
    TagInfo tag = TagInfo.close("cite");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.type()).isEqualTo(TagType.CLOSE);
    assertThat(tag.attributes()).isEmpty();
  }
}
