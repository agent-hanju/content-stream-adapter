package dev.hanju.adapter.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XmlFragment 테스트")
class XmlFragmentTest {

  @Test
  @DisplayName("Text의 raw가 null이면 예외")
  void textNullRawThrows() {
    assertThatThrownBy(() -> new XmlFragment.Text(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Open의 raw가 null이면 예외")
  void openNullRawThrows() {
    assertThatThrownBy(() -> new XmlFragment.Open(null, "cite", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("태그 조각의 name이 null이면 예외")
  void tagNameNullThrows() {
    assertThatThrownBy(() -> new XmlFragment.Open("<cite>", null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("text 조각은 raw만 갖는다")
  void textFragment() {
    XmlFragment fragment = XmlFragment.text("hello");

    assertThat(fragment).isInstanceOf(XmlFragment.Text.class);
    assertThat(fragment.raw()).isEqualTo("hello");
  }

  @Test
  @DisplayName("open 조각은 원본 태그와 속성을 보존한다")
  void openFragment() {
    XmlFragment fragment = XmlFragment.open("<cite id=\"ref1\">", "cite", Map.of("id", "ref1"));

    assertThat(fragment).isInstanceOf(XmlFragment.Open.class);
    XmlFragment.Open open = (XmlFragment.Open) fragment;
    assertThat(open.raw()).isEqualTo("<cite id=\"ref1\">");
    assertThat(open.name()).isEqualTo("cite");
    assertThat(open.attributes()).containsEntry("id", "ref1");
  }

  @Test
  @DisplayName("close 조각은 name만 갖는다")
  void closeFragment() {
    XmlFragment fragment = new XmlFragment.Close("</cite>", "cite");

    assertThat(fragment).isEqualTo(XmlFragment.close("cite"));
  }

  @Test
  @DisplayName("selfClosing 조각은 별도 타입과 속성을 보존한다")
  void selfClosingFragment() {
    XmlFragment fragment = XmlFragment.selfClosing("<cite id=\"ref1\"/>", "cite", Map.of("id", "ref1"));

    assertThat(fragment).isInstanceOf(XmlFragment.SelfClosing.class);
    XmlFragment.SelfClosing selfClosing = (XmlFragment.SelfClosing) fragment;
    assertThat(selfClosing.name()).isEqualTo("cite");
    assertThat(selfClosing.attributes()).containsEntry("id", "ref1");
  }
}
