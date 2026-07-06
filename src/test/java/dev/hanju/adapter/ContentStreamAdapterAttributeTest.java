package dev.hanju.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.xml.XmlStreamOutput;
import dev.hanju.adapter.xml.XmlTagBinding;

class ContentStreamAdapterAttributeTest {

  @Test
  void feedToken_withSingleAttribute_includesAttributeInEvent() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter.feedToken("<cite id=\"ref1\">content</cite>");

    XmlStreamOutput.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.path()).isEqualTo("/cite");
    assertThat(enterEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void feedToken_withMultipleAttributes_includesAllAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id", "source", "page")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter
        .feedToken("<cite id=\"ref1\" source=\"wiki\" page=\"123\">text</cite>");

    XmlStreamOutput.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
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

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/think").tag("think")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter.feedToken("<think>reasoning</think>");

    XmlStreamOutput.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).isEmpty();
  }

  @Test
  void feedToken_tokenizedInputWithAttributes_parsesCorrectly() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs1 = adapter.feedToken("Text <cite id=\"re");
    List<XmlStreamOutput> outputs2 = adapter.feedToken("f1\">content</cite>");

    assertThat(outputs1).hasSize(1);
    assertThat(outputs1.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
    assertThat(((XmlStreamOutput.Text) outputs1.get(0)).content()).isEqualTo("Text ");

    XmlStreamOutput.Enter enterEvent = outputs2.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void feedToken_closeEvent_isExitType() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter.feedToken("<cite id=\"ref1\">text</cite>");

    XmlStreamOutput.Exit exitEvent = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Exit)
        .map(t -> (XmlStreamOutput.Exit) t)
        .findFirst()
        .orElseThrow();

    assertThat(exitEvent.path()).isEqualTo("/cite");
  }

  @Test
  void feedToken_attributeWithSpaces_parsesCorrectly() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("title")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter
        .feedToken("<cite title=\"New York Times\">article</cite>");

    XmlStreamOutput.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).containsEntry("title", "New York Times");
  }

  @Test
  void feedToken_nestedTagsWithAttributes_eachHasOwnAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .path("section", section -> section
            .path("cite"));

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/section").tag("section").attr("id")
        .and()
        .bind("/section/cite").tag("cite").attr("ref")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter.feedToken(
        "<section id=\"s1\"><cite ref=\"r1\">text</cite></section>");

    List<XmlStreamOutput.Enter> enterEvents = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
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

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">content</cite>");

    XmlStreamOutput.Enter enterEvent = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
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

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs = adapter.feedToken("<cite>some content</cite>");

    XmlStreamOutput.Text textOutput = outputs.stream()
        .filter(t -> t instanceof XmlStreamOutput.Text)
        .map(t -> (XmlStreamOutput.Text) t)
        .findFirst()
        .orElseThrow();

    assertThat(textOutput.content()).isEqualTo("some content");
  }

  @Test
  void flush_incompleteTagWithAttributes_emitsEnterEvent() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    List<XmlStreamOutput> outputs1 = adapter.feedToken("Text <cite id=\"ref1\"");
    assertThat(outputs1).hasSize(1);
    assertThat(((XmlStreamOutput.Text) outputs1.get(0)).content()).isEqualTo("Text ");

    List<XmlStreamOutput> flushed = adapter.flush();

    XmlStreamOutput.Enter enterEvent = flushed.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.path()).isEqualTo("/cite");
    assertThat(enterEvent.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void flush_incompleteTagNoAttributes_emitsEnterEvent() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    adapter.feedToken("<cite");

    List<XmlStreamOutput> flushed = adapter.flush();

    XmlStreamOutput.Enter enterEvent = flushed.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.path()).isEqualTo("/cite");
    assertThat(enterEvent.attributes()).isEmpty();
  }

  @Test
  void flush_incompleteAttributeValue_parsesAvailableAttributes() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id", "source")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    adapter.feedToken("<cite source=\"wiki\" id=\"ref");

    List<XmlStreamOutput> flushed = adapter.flush();

    XmlStreamOutput.Enter enterEvent = flushed.stream()
        .filter(t -> t instanceof XmlStreamOutput.Enter)
        .map(t -> (XmlStreamOutput.Enter) t)
        .findFirst()
        .orElseThrow();

    assertThat(enterEvent.attributes()).containsEntry("source", "wiki");
    assertThat(enterEvent.attributes()).doesNotContainKey("id");
  }

  @Test
  void getRaw_returnsAccumulatedInput() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite");

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

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

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").attr("id")
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    adapter.feedToken("Start <cite id=\"re");
    adapter.feedToken("f1\">text");

    assertThat(adapter.getRaw()).isEqualTo("Start <cite id=\"ref1\">text");
  }
}
