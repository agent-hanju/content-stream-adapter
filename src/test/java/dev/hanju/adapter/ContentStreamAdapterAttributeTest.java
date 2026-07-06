package dev.hanju.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.ContentStreamResult;

class ContentStreamAdapterAttributeTest {

  @Test
  void feedToken_withSingleAttribute_includesAttributeInEvent() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id")
        .build();

    List<ContentStreamResult> outputs = adapter.feedToken("<cite id=\"ref1\">content</cite>");

    ContentStreamResult.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Enter)
        .map(t -> (ContentStreamResult.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.path()).isEqualTo("/cite");
    assertThat(enterEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void feedToken_withMultipleAttributes_includesAllAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id", "source", "page")
        .build();

    List<ContentStreamResult> outputs = adapter
        .feedToken("<cite id=\"ref1\" source=\"wiki\" page=\"123\">text</cite>");

    ContentStreamResult.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Enter)
        .map(t -> (ContentStreamResult.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).hasSize(3);
    assertThat(enterEvent.attributes()).containsEntry("id", "ref1");
    assertThat(enterEvent.attributes()).containsEntry("source", "wiki");
    assertThat(enterEvent.attributes()).containsEntry("page", "123");
  }

  @Test
  void feedToken_noAttributes_hasEmptyAttributeMap() {
    TransitionSchema schema = TransitionSchema.root()
        .path("think");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/think").tag("think")
        .build();

    List<ContentStreamResult> outputs = adapter.feedToken("<think>reasoning</think>");

    ContentStreamResult.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Enter)
        .map(t -> (ContentStreamResult.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).isEmpty();
  }

  @Test
  void feedToken_tokenizedInputWithAttributes_parsesCorrectly() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id")
        .build();

    List<ContentStreamResult> outputs1 = adapter.feedToken("Text <cite id=\"re");
    List<ContentStreamResult> outputs2 = adapter.feedToken("f1\">content</cite>");

    assertThat(outputs1).hasSize(1);
    assertThat(outputs1.get(0)).isInstanceOf(ContentStreamResult.Text.class);
    assertThat(((ContentStreamResult.Text) outputs1.get(0)).content()).isEqualTo("Text ");

    ContentStreamResult.Enter enterEvent = outputs2.stream()
        .filter(t -> t instanceof ContentStreamResult.Enter)
        .map(t -> (ContentStreamResult.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void feedToken_closeEvent_isExitType() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id")
        .build();

    List<ContentStreamResult> outputs = adapter.feedToken("<cite id=\"ref1\">text</cite>");

    ContentStreamResult.Exit exitEvent = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Exit)
        .map(t -> (ContentStreamResult.Exit) t)
        .findFirst()
        .orElseThrow();

    assertThat(exitEvent.path()).isEqualTo("/cite");
  }

  @Test
  void feedToken_attributeWithSpaces_parsesCorrectly() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("title")
        .build();

    List<ContentStreamResult> outputs = adapter
        .feedToken("<cite title=\"New York Times\">article</cite>");

    ContentStreamResult.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Enter)
        .map(t -> (ContentStreamResult.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).containsEntry("title", "New York Times");
  }

  @Test
  void feedToken_nestedTagsWithAttributes_eachHasOwnAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .path("section", section -> section
            .path("cite"));

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/section").tag("section").attr("id")
        .and()
        .bind("/section/cite").tag("cite").attr("ref")
        .build();

    List<ContentStreamResult> outputs = adapter.feedToken(
        "<section id=\"s1\"><cite ref=\"r1\">text</cite></section>");

    List<ContentStreamResult.Enter> enterEvents = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Enter)
        .map(t -> (ContentStreamResult.Enter) t)
        .toList();

    assertThat(enterEvents).hasSize(2);
    assertThat(enterEvents.get(0).path()).isEqualTo("/section");
    assertThat(enterEvents.get(0).attributes()).containsEntry("id", "s1");

    assertThat(enterEvents.get(1).path()).isEqualTo("/section/cite");
    assertThat(enterEvents.get(1).attributes()).containsEntry("ref", "r1");
  }

  @Test
  void feedToken_undefinedAttributeIgnored() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id")
        .build();

    List<ContentStreamResult> outputs = adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">content</cite>");

    ContentStreamResult.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Enter)
        .map(t -> (ContentStreamResult.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).hasSize(1);
    assertThat(enterEvent.attributes()).containsEntry("id", "ref1");
    assertThat(enterEvent.attributes()).doesNotContainKey("source");
  }

  @Test
  void feedToken_contentTokens_areTextType() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite")
        .build();

    List<ContentStreamResult> outputs = adapter.feedToken("<cite>some content</cite>");

    ContentStreamResult.Text textOutput = outputs.stream()
        .filter(t -> t instanceof ContentStreamResult.Text)
        .map(t -> (ContentStreamResult.Text) t)
        .findFirst()
        .orElseThrow();

    assertThat(textOutput.content()).isEqualTo("some content");
  }

  @Test
  void flush_incompleteTagWithAttributes_emitsText() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id")
        .build();

    List<ContentStreamResult> outputs1 = adapter.feedToken("Text <cite id=\"ref1\"");
    assertThat(outputs1).hasSize(1);
    assertThat(((ContentStreamResult.Text) outputs1.get(0)).content()).isEqualTo("Text ");

    List<ContentStreamResult> flushed = adapter.flush();

    assertThat(flushed).containsExactly(new ContentStreamResult.Text("<cite id=\"ref1\""));
  }

  @Test
  void flush_incompleteTagNoAttributes_emitsText() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite")
        .build();

    adapter.feedToken("<cite");

    List<ContentStreamResult> flushed = adapter.flush();

    assertThat(flushed).containsExactly(new ContentStreamResult.Text("<cite"));
  }

  @Test
  void flush_incompleteAttributeValue_emitsText() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id", "source")
        .build();

    adapter.feedToken("<cite source=\"wiki\" id=\"ref");

    List<ContentStreamResult> flushed = adapter.flush();

    assertThat(flushed).containsExactly(new ContentStreamResult.Text("<cite source=\"wiki\" id=\"ref"));
  }

  @Test
  void getRaw_returnsAccumulatedInput() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id")
        .build();

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
        .path("cite");

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").attr("id")
        .build();

    adapter.feedToken("Start <cite id=\"re");
    adapter.feedToken("f1\">text");

    assertThat(adapter.getRaw()).isEqualTo("Start <cite id=\"ref1\">text");
  }
}
