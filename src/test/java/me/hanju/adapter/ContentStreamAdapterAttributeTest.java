package me.hanju.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.hanju.adapter.payload.TaggedToken;
import me.hanju.adapter.transition.TransitionSchema;

class ContentStreamAdapterAttributeTest {

  @Test
  void feedToken_withSingleAttribute_includesAttributeInEvent() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter.feedToken("<cite id=\"ref1\">content</cite>");

    // OPEN 이벤트에 attribute 포함
    TaggedToken openEvent = tokens.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(openEvent.path()).isEqualTo("/cite");
    assertThat(openEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void feedToken_withMultipleAttributes_includesAllAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id", "source", "page");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter
        .feedToken("<cite id=\"ref1\" source=\"wiki\" page=\"123\">text</cite>");

    TaggedToken openEvent = tokens.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(openEvent.attributes()).hasSize(3);
    assertThat(openEvent.attributes()).containsEntry("id", "ref1");
    assertThat(openEvent.attributes()).containsEntry("source", "wiki");
    assertThat(openEvent.attributes()).containsEntry("page", "123");
  }

  @Test
  void feedToken_noAttributes_hasEmptyAttributeMap() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("think");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter.feedToken("<think>reasoning</think>");

    TaggedToken openEvent = tokens.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(openEvent.attributes()).isEmpty();
  }

  @Test
  void feedToken_tokenizedInputWithAttributes_parsesCorrectly() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    // 태그를 토큰으로 쪼개서 입력
    List<TaggedToken> tokens1 = adapter.feedToken("Text <cite id=\"re");
    List<TaggedToken> tokens2 = adapter.feedToken("f1\">content</cite>");

    // tokens1에는 "Text " content만
    assertThat(tokens1).hasSize(1);
    assertThat(tokens1.get(0).content()).isEqualTo("Text ");

    // tokens2에서 OPEN 이벤트 확인
    TaggedToken openEvent = tokens2.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(openEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void feedToken_closeEvent_hasNoAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter.feedToken("<cite id=\"ref1\">text</cite>");

    TaggedToken closeEvent = tokens.stream()
        .filter(t -> "CLOSE".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(closeEvent.attributes()).isEmpty();
  }

  @Test
  void feedToken_attributeWithSpaces_parsesCorrectly() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("title");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter
        .feedToken("<cite title=\"New York Times\">article</cite>");

    TaggedToken openEvent = tokens.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(openEvent.attributes()).containsEntry("title", "New York Times");
  }

  @Test
  void feedToken_nestedTagsWithAttributes_eachHasOwnAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("section", section -> section
            .tag("cite").attr("ref"))
        .attr("id");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter.feedToken(
        "<section id=\"s1\"><cite ref=\"r1\">text</cite></section>");

    List<TaggedToken> openEvents = tokens.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .toList();

    assertThat(openEvents).hasSize(2);
    assertThat(openEvents.get(0).path()).isEqualTo("/section");
    assertThat(openEvents.get(0).attributes()).containsEntry("id", "s1");

    assertThat(openEvents.get(1).path()).isEqualTo("/section/cite");
    assertThat(openEvents.get(1).attributes()).containsEntry("ref", "r1");
  }

  @Test
  void feedToken_undefinedAttributeIgnored() {
    // 스키마에 정의되지 않은 attribute는 무시됨
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id"); // source는 정의 안함

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">content</cite>");

    TaggedToken openEvent = tokens.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    // id만 포함, source는 무시됨
    assertThat(openEvent.attributes()).hasSize(1);
    assertThat(openEvent.attributes()).containsEntry("id", "ref1");
    assertThat(openEvent.attributes()).doesNotContainKey("source");
  }

  @Test
  void feedToken_contentTokens_haveEmptyAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    List<TaggedToken> tokens = adapter.feedToken("<cite>some content</cite>");

    TaggedToken contentToken = tokens.stream()
        .filter(t -> t.event() == null)
        .findFirst()
        .orElseThrow();

    assertThat(contentToken.content()).isEqualTo("some content");
    assertThat(contentToken.attributes()).isEmpty();
  }

  @Test
  void flush_incompleteTagWithAttributes_emitsOpenEvent() {
    // 스트림이 중간에 끊긴 경우: <cite id="ref1" 까지만 들어옴 ('>' 없음)
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    // '>'가 없는 불완전한 태그
    List<TaggedToken> tokens1 = adapter.feedToken("Text <cite id=\"ref1\"");
    assertThat(tokens1).hasSize(1);
    assertThat(tokens1.get(0).content()).isEqualTo("Text ");

    // flush 호출 시 불완전한 태그도 OPEN 이벤트로 처리
    List<TaggedToken> flushed = adapter.flush();

    TaggedToken openEvent = flushed.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(openEvent.path()).isEqualTo("/cite");
    assertThat(openEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void flush_incompleteTagNoAttributes_emitsOpenEvent() {
    // 스트림이 중간에 끊긴 경우: <cite 까지만 들어옴
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    adapter.feedToken("<cite");

    // flush 호출 시 OPEN 이벤트 발생
    List<TaggedToken> flushed = adapter.flush();

    TaggedToken openEvent = flushed.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    assertThat(openEvent.path()).isEqualTo("/cite");
    assertThat(openEvent.attributes()).isEmpty();
  }

  @Test
  void flush_incompleteAttributeValue_parsesAvailableAttributes() {
    // 속성 값이 중간에 끊긴 경우: <cite id="ref 까지만 들어옴
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id", "source");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    // id 값이 불완전하고, source는 완전함
    adapter.feedToken("<cite source=\"wiki\" id=\"ref");

    List<TaggedToken> flushed = adapter.flush();

    TaggedToken openEvent = flushed.stream()
        .filter(t -> "OPEN".equals(t.event()))
        .findFirst()
        .orElseThrow();

    // source는 파싱됨, id는 닫는 따옴표가 없어서 파싱 안됨
    assertThat(openEvent.attributes()).containsEntry("source", "wiki");
    assertThat(openEvent.attributes()).doesNotContainKey("id");
  }

  @Test
  void getRaw_returnsAccumulatedInput() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    adapter.feedToken("Hello ");
    adapter.feedToken("<cite id=\"ref1\">");
    adapter.feedToken("content");
    adapter.feedToken("</cite>");
    adapter.feedToken(" world");

    assertThat(adapter.getRaw()).isEqualTo("Hello <cite id=\"ref1\">content</cite> world");
  }

  @Test
  void getRaw_includesIncompleteTokens() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite").attr("id");

    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    adapter.feedToken("Start <cite id=\"re");
    adapter.feedToken("f1\">text");

    // 원문은 그대로 누적됨
    assertThat(adapter.getRaw()).isEqualTo("Start <cite id=\"ref1\">text");
  }
}
