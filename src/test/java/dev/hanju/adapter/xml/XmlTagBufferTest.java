package dev.hanju.adapter.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("XmlTagBuffer 테스트")
class XmlTagBufferTest {

  private XmlTagBuffer buffer;

  @BeforeEach
  void setUp() {
    buffer = new XmlTagBuffer();
  }

  @Nested
  @DisplayName("기본 토큰화")
  class BasicTokenizing {

    @Test
    @DisplayName("일반 텍스트는 그대로 반환")
    void plainTextPassThrough() {
      List<XmlFragment> outputs = buffer.feed("hello world");

      assertThat(outputs).containsExactly(XmlFragment.text("hello world"));
    }

    @Test
    @DisplayName("여는 태그를 Tag 출력으로 반환")
    void openTag() {
      List<XmlFragment> outputs = buffer.feed("<cite id=\"ref\">");

      assertThat(outputs).hasSize(1);
      XmlFragment.Open tag = (XmlFragment.Open) outputs.getFirst();
      assertThat(tag.name()).isEqualTo("cite");
      assertThat(tag.attributes()).containsEntry("id", "ref");
      assertThat(tag.raw()).isEqualTo("<cite id=\"ref\">");
    }

    @Test
    @DisplayName("닫는 태그를 Tag 출력으로 반환")
    void closeTag() {
      List<XmlFragment> outputs = buffer.feed("</cite>");

      assertThat(outputs).hasSize(1);
      XmlFragment tag = outputs.getFirst();
      assertThat(tag).isEqualTo(XmlFragment.close("cite"));
      assertThat(tag.raw()).isEqualTo("</cite>");
    }

    @Test
    @DisplayName("자체 닫힘 태그를 별도 타입으로 반환")
    void selfClosingTag() {
      List<XmlFragment> outputs = buffer.feed("<cite id=\"ref\"/>");

      assertThat(outputs).hasSize(1);
      XmlFragment.SelfClosing tag = (XmlFragment.SelfClosing) outputs.getFirst();
      assertThat(tag.name()).isEqualTo("cite");
      assertThat(tag.attributes()).containsEntry("id", "ref");
      assertThat(tag.raw()).isEqualTo("<cite id=\"ref\"/>");
    }

    @Test
    @DisplayName("텍스트와 태그를 순서대로 반환")
    void mixedTextAndTags() {
      List<XmlFragment> outputs = buffer.feed("a<cite>b</cite>c");

      assertThat(outputs).hasSize(5);
      assertThat(outputs.get(0)).isEqualTo(XmlFragment.text("a"));
      assertThat(outputs.get(1)).isInstanceOf(XmlFragment.Open.class);
      assertThat(outputs.get(2)).isEqualTo(XmlFragment.text("b"));
      assertThat(outputs.get(3)).isInstanceOf(XmlFragment.Close.class);
      assertThat(outputs.get(4)).isEqualTo(XmlFragment.text("c"));
    }
  }

  @Nested
  @DisplayName("스트리밍")
  class Streaming {

    @Test
    @DisplayName("여는 태그가 여러 토큰에 걸쳐도 완성 시 반환")
    void splitOpenTag() {
      assertThat(buffer.feed("text <ci")).containsExactly(XmlFragment.text("text "));

      List<XmlFragment> outputs = buffer.feed("te id=\"1\">body");

      assertThat(outputs).hasSize(2);
      XmlFragment.Open tag = (XmlFragment.Open) outputs.getFirst();
      assertThat(tag.name()).isEqualTo("cite");
      assertThat(tag.attributes()).containsEntry("id", "1");
      assertThat(outputs.get(1)).isEqualTo(XmlFragment.text("body"));
    }

    @Test
    @DisplayName("닫는 태그가 여러 토큰에 걸쳐도 완성 시 반환")
    void splitCloseTag() {
      assertThat(buffer.feed("</ci")).isEmpty();

      List<XmlFragment> outputs = buffer.feed("te>tail");

      assertThat(outputs).hasSize(2);
      assertThat(outputs.getFirst()).isEqualTo(XmlFragment.close("cite"));
      assertThat(outputs.get(1)).isEqualTo(XmlFragment.text("tail"));
    }

    @Test
    @DisplayName("한 토큰 안의 여러 태그를 모두 반환")
    void multipleTagsInSingleToken() {
      List<XmlFragment> outputs = buffer.feed("<a><b/>x</a>");

      assertThat(outputs).hasSize(4);
      assertThat(((XmlFragment.Open) outputs.get(0)).name()).isEqualTo("a");
      assertThat(outputs.get(1)).isInstanceOf(XmlFragment.SelfClosing.class);
      assertThat(outputs.get(2)).isEqualTo(XmlFragment.text("x"));
      assertThat(outputs.get(3)).isEqualTo(XmlFragment.close("a"));
    }

    @Test
    @DisplayName("flush는 미완성 태그를 텍스트로 반환")
    void flushIncompleteTagAsText() {
      assertThat(buffer.feed("before <cite id=\"1\"")).containsExactly(XmlFragment.text("before "));

      List<XmlFragment> outputs = buffer.flush();

      assertThat(outputs).containsExactly(XmlFragment.text("<cite id=\"1\""));
    }
  }

  @Nested
  @DisplayName("속성")
  class Attributes {

    @Test
    @DisplayName("큰따옴표 안의 >는 태그 종료로 보지 않음")
    void doubleQuoteIgnoresGreaterThan() {
      List<XmlFragment> outputs = buffer.feed("<cite expr=\"a>b\">tail");

      XmlFragment.Open tag = (XmlFragment.Open) outputs.getFirst();
      assertThat(tag.attributes()).containsEntry("expr", "a>b");
      assertThat(outputs.get(1)).isEqualTo(XmlFragment.text("tail"));
    }

    @Test
    @DisplayName("작은따옴표 속성값도 파싱")
    void singleQuoteAttribute() {
      List<XmlFragment> outputs = buffer.feed("<cite expr='a>b'>");

      XmlFragment.Open tag = (XmlFragment.Open) outputs.getFirst();
      assertThat(tag.attributes()).containsEntry("expr", "a>b");
    }

    @Test
    @DisplayName("여러 속성을 파싱")
    void multipleAttributes() {
      List<XmlFragment> outputs = buffer.feed("<cite id=\"1\" source=\"wiki\">");

      XmlFragment.Open tag = (XmlFragment.Open) outputs.getFirst();
      assertThat(tag.attributes())
          .containsEntry("id", "1")
          .containsEntry("source", "wiki");
    }
  }

  @Nested
  @DisplayName("예외와 비태그")
  class InvalidInput {

    @Test
    @DisplayName("null 토큰은 예외")
    void nullToken() {
      assertThatThrownBy(() -> buffer.feed(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("태그 이름이 없으면 텍스트로 반환")
    void emptyTagNameAsText() {
      List<XmlFragment> outputs = buffer.feed("< >");

      assertThat(outputs).containsExactly(XmlFragment.text("< >"));
    }

    @Test
    @DisplayName("태그 후보 안에서 새 <를 만나면 이전 후보를 텍스트로 처리하고 새 후보를 시작")
    void nestedLessThanStartsNewCandidate() {
      List<XmlFragment> outputs = buffer.feed("a < b <tag>c");

      assertThat(outputs).hasSize(3);
      assertThat(outputs.get(0)).isEqualTo(XmlFragment.text("a < b "));
      assertThat(((XmlFragment.Open) outputs.get(1)).name()).isEqualTo("tag");
      assertThat(outputs.get(2)).isEqualTo(XmlFragment.text("c"));
    }
  }

  @Nested
  @DisplayName("targetTags")
  class TargetTags {

    @Test
    @DisplayName("targetTags에 있는 태그만 Tag 출력")
    void onlyTargetTagsBecomeTagOutput() {
      XmlTagBuffer targetBuffer = new XmlTagBuffer(Set.of("cite"));

      List<XmlFragment> outputs = targetBuffer.feed("<cite>ok</cite><other>x</other>");

      assertThat(outputs).hasSize(4);
      assertThat(outputs.get(0)).isEqualTo(XmlFragment.open("cite"));
      assertThat(outputs.get(1)).isEqualTo(XmlFragment.text("ok"));
      assertThat(outputs.get(2)).isEqualTo(XmlFragment.close("cite"));
      assertThat(outputs.get(3)).isEqualTo(XmlFragment.text("<other>x</other>"));
    }

    @Test
    @DisplayName("targetTags에 없는 태그는 raw text")
    void nonTargetTagAsText() {
      XmlTagBuffer targetBuffer = new XmlTagBuffer(Set.of("cite"));

      List<XmlFragment> outputs = targetBuffer.feed("a<other id=\"1\">b");

      assertThat(outputs).containsExactly(XmlFragment.text("a<other id=\"1\">b"));
    }

    @Test
    @DisplayName("null targetTags는 예외")
    void nullTargetTagsThrows() {
      assertThatThrownBy(() -> new XmlTagBuffer(null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
