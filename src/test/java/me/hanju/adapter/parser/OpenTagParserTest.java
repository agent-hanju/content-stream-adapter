package me.hanju.adapter.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.hanju.adapter.parser.OpenTagParser.ParsedTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OpenTagParser 테스트")
class OpenTagParserTest {

  private OpenTagParser parser;

  @BeforeEach
  void setUp() {
    parser = new OpenTagParser();
  }

  // ==================== 1. 기본 동작 ====================

  @Nested
  @DisplayName("기본 동작")
  class BasicOperation {

    @Test
    @DisplayName("단순 태그 - 한 번에 완성")
    void simpleTagComplete() {
      parser.start("<cite");

      ParsedTag result = parser.feed(">");

      assertThat(result).isNotNull();
      assertThat(result.tagName()).isEqualTo("cite");
      assertThat(result.rawTag()).isEqualTo("<cite>");
      assertThat(parser.getRemaining()).isEmpty();
    }

    @Test
    @DisplayName("태그 + 남은 내용")
    void tagWithRemaining() {
      parser.start("<cite");

      ParsedTag result = parser.feed(">content here");

      assertThat(result).isNotNull();
      assertThat(result.rawTag()).isEqualTo("<cite>");
      assertThat(parser.getRemaining()).isEqualTo("content here");
    }

    @Test
    @DisplayName("불완전한 태그")
    void incompleteTag() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" id=\"ref\"");

      assertThat(result).isNull();
      assertThat(parser.isParsing()).isTrue();
    }

    @Test
    @DisplayName("여러 토큰에 걸쳐 완성")
    void multipleTokens() {
      parser.start("<cite");

      assertThat(parser.feed(" id=")).isNull();
      assertThat(parser.feed("\"ref\"")).isNull();

      ParsedTag result = parser.feed(">content");
      assertThat(result).isNotNull();
      assertThat(result.tagName()).isEqualTo("cite");
      assertThat(result.attributes()).containsEntry("id", "ref");
      assertThat(result.rawTag()).isEqualTo("<cite id=\"ref\">");
      assertThat(parser.getRemaining()).isEqualTo("content");
    }
  }

  // ==================== 2. 따옴표 처리 ====================

  @Nested
  @DisplayName("따옴표 처리")
  class QuoteHandling {

    @Test
    @DisplayName("큰따옴표 안의 > 무시")
    void doubleQuoteIgnoresGreaterThan() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" expr=\"a>b\">content");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("expr", "a>b");
      assertThat(parser.getRemaining()).isEqualTo("content");
    }

    @Test
    @DisplayName("작은따옴표 안의 > 무시")
    void singleQuoteIgnoresGreaterThan() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" expr='a>b'>content");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("expr", "a>b");
      assertThat(parser.getRemaining()).isEqualTo("content");
    }

    @Test
    @DisplayName("여러 > 가 따옴표 안에 있음")
    void multipleGreaterThanInQuotes() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" expr=\"a>b>c\">content");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("expr", "a>b>c");
      assertThat(parser.getRemaining()).isEqualTo("content");
    }

    @Test
    @DisplayName("따옴표가 토큰 경계에 걸침")
    void quoteSplitAcrossTokens() {
      parser.start("<cite");

      parser.feed(" expr=\"a>");
      ParsedTag result = parser.feed("b\">content");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("expr", "a>b");
      assertThat(parser.getRemaining()).isEqualTo("content");
    }

    @Test
    @DisplayName("혼합 따옴표 - 큰따옴표 안에 작은따옴표")
    void mixedQuotes() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" expr=\"it's > fine\">ok");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("expr", "it's > fine");
    }
  }

  // ==================== 3. 여러 속성 ====================

  @Nested
  @DisplayName("여러 속성")
  class MultipleAttributes {

    @Test
    @DisplayName("두 개의 속성")
    void twoAttributes() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" id=\"1\" source=\"wiki\">text");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).hasSize(2);
      assertThat(result.attributes()).containsEntry("id", "1");
      assertThat(result.attributes()).containsEntry("source", "wiki");
    }

    @Test
    @DisplayName("속성 없는 태그")
    void noAttributes() {
      parser.start("<cite");

      ParsedTag result = parser.feed(">text");

      assertThat(result).isNotNull();
      assertThat(result.tagName()).isEqualTo("cite");
      assertThat(result.attributes()).isEmpty();
    }

    @Test
    @DisplayName("공백만 있는 경우")
    void onlyWhitespace() {
      parser.start("<cite");

      ParsedTag result = parser.feed("   >text");

      assertThat(result).isNotNull();
      assertThat(result.tagName()).isEqualTo("cite");
      assertThat(result.attributes()).isEmpty();
    }
  }

  // ==================== 4. 상태 관리 ====================

  @Nested
  @DisplayName("상태 관리")
  class StateManagement {

    @Test
    @DisplayName("시작 전 isParsing은 false")
    void isParsingBeforeStart() {
      assertThat(parser.isParsing()).isFalse();
    }

    @Test
    @DisplayName("시작 후 isParsing은 true")
    void isParsingAfterStart() {
      parser.start("<cite");
      assertThat(parser.isParsing()).isTrue();
    }

    @Test
    @DisplayName("완료 후 isParsing은 false")
    void isParsingAfterComplete() {
      parser.start("<cite");
      parser.feed(">");
      assertThat(parser.isParsing()).isFalse();
    }

    @Test
    @DisplayName("reset 후 isParsing은 false")
    void isParsingAfterReset() {
      parser.start("<cite");
      parser.feed(" id=\"");
      parser.reset();
      assertThat(parser.isParsing()).isFalse();
    }

    @Test
    @DisplayName("start 없이 feed 호출 - 예외")
    void feedWithoutStart() {
      assertThatThrownBy(() -> parser.feed("test"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Parser not started");
    }
  }

  // ==================== 5. forceComplete ====================

  @Nested
  @DisplayName("강제 완료")
  class ForceComplete {

    @Test
    @DisplayName("불완전한 태그 강제 완료 - 완성된 속성만 포함")
    void forceCompleteIncomplete() {
      parser.start("<cite");
      parser.feed(" id=\"ref\"");

      ParsedTag result = parser.forceComplete();

      assertThat(result).isNotNull();
      assertThat(result.tagName()).isEqualTo("cite");
      assertThat(result.attributes()).containsEntry("id", "ref");
      assertThat(parser.isParsing()).isFalse();
    }

    @Test
    @DisplayName("불완전한 속성값은 무시됨")
    void forceCompleteIgnoresIncompleteAttribute() {
      parser.start("<cite");
      parser.feed(" source=\"wiki\" id=\"ref"); // id 값이 불완전

      ParsedTag result = parser.forceComplete();

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("source", "wiki");
      assertThat(result.attributes()).doesNotContainKey("id");
    }

    @Test
    @DisplayName("파싱 중이 아닐 때 forceComplete")
    void forceCompleteWhenNotParsing() {
      ParsedTag result = parser.forceComplete();
      assertThat(result).isNull();
    }
  }

  // ==================== 6. 엣지 케이스 ====================

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCases {

    @Test
    @DisplayName("빈 토큰")
    void emptyToken() {
      parser.start("<cite");

      ParsedTag result = parser.feed("");

      assertThat(result).isNull();
      assertThat(parser.isParsing()).isTrue();
    }

    @Test
    @DisplayName("> 가 첫 문자")
    void greaterThanFirst() {
      parser.start("<cite");

      ParsedTag result = parser.feed(">rest");

      assertThat(result).isNotNull();
      assertThat(result.rawTag()).isEqualTo("<cite>");
      assertThat(parser.getRemaining()).isEqualTo("rest");
    }

    @Test
    @DisplayName("self-closing 태그 스타일 (/>)")
    void selfClosingStyle() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" id=\"1\"/>rest");

      assertThat(result).isNotNull();
      assertThat(result.rawTag()).isEqualTo("<cite id=\"1\"/>");
      assertThat(parser.getRemaining()).isEqualTo("rest");
    }

    @Test
    @DisplayName("연속 사용 - 첫 번째 완료 후 두 번째 시작")
    void consecutiveUse() {
      parser.start("<cite");
      ParsedTag result1 = parser.feed(">first");
      assertThat(result1.tagName()).isEqualTo("cite");

      parser.start("<think");
      ParsedTag result2 = parser.feed(">second");
      assertThat(result2.tagName()).isEqualTo("think");
    }

    @Test
    @DisplayName("긴 속성값")
    void longAttributeValue() {
      parser.start("<cite");
      String longValue = "a".repeat(1000);

      ParsedTag result = parser.feed(" data=\"" + longValue + "\">rest");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("data", longValue);
    }

    @Test
    @DisplayName("속성값에 특수문자")
    void specialCharsInAttribute() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" url=\"https://example.com?a=1&b=2\">text");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("url", "https://example.com?a=1&b=2");
    }

    @Test
    @DisplayName("속성값에 공백")
    void spacesInAttribute() {
      parser.start("<cite");

      ParsedTag result = parser.feed(" title=\"New York Times\">text");

      assertThat(result).isNotNull();
      assertThat(result.attributes()).containsEntry("title", "New York Times");
    }
  }
}
