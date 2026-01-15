package me.hanju.adapter.payload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TagInfoTest {

  @Test
  void parse_withSingleAttribute_parsesCorrectly() {
    TagInfo tag = TagInfo.parse("<cite id=\"ref1\">");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.type()).isEqualTo(TagInfo.TagType.OPEN);
    assertThat(tag.attributes()).containsEntry("id", "ref1");
  }

  @Test
  void parse_withMultipleAttributes_parsesCorrectly() {
    TagInfo tag = TagInfo.parse("<cite id=\"ref1\" source=\"wiki\" page=\"123\">");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.type()).isEqualTo(TagInfo.TagType.OPEN);
    assertThat(tag.attributes()).hasSize(3);
    assertThat(tag.attributes()).containsEntry("id", "ref1");
    assertThat(tag.attributes()).containsEntry("source", "wiki");
    assertThat(tag.attributes()).containsEntry("page", "123");
  }

  @Test
  void parse_withNoAttributes_returnsEmptyMap() {
    TagInfo tag = TagInfo.parse("<cite>");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.type()).isEqualTo(TagInfo.TagType.OPEN);
    assertThat(tag.attributes()).isEmpty();
  }

  @Test
  void parse_closeTag_ignoresAttributes() {
    TagInfo tag = TagInfo.parse("</cite id=\"ref1\">");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.type()).isEqualTo(TagInfo.TagType.CLOSE);
    assertThat(tag.attributes()).isEmpty();
  }

  @Test
  void parse_withExtraSpaces_handlesCorrectly() {
    TagInfo tag = TagInfo.parse("<cite  id=\"ref1\"   source=\"wiki\"  >");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.attributes()).containsEntry("id", "ref1");
    assertThat(tag.attributes()).containsEntry("source", "wiki");
  }

  @Test
  void parse_attributeValueWithSpaces_parsesCorrectly() {
    TagInfo tag = TagInfo.parse("<cite title=\"New York Times\">");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.attributes()).containsEntry("title", "New York Times");
  }

  @Test
  void parse_attributeValueEmpty_parsesCorrectly() {
    TagInfo tag = TagInfo.parse("<cite id=\"\">");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.attributes()).containsEntry("id", "");
  }

  @Test
  void parse_complexAttributeNames_parsesCorrectly() {
    TagInfo tag = TagInfo.parse("<cite data_id=\"123\" my_value=\"test\">");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.attributes()).containsEntry("data_id", "123");
    assertThat(tag.attributes()).containsEntry("my_value", "test");
  }

  @Test
  void parse_attributeWithSpecialChars_parsesCorrectly() {
    TagInfo tag = TagInfo.parse("<cite url=\"https://example.com?a=1&b=2\">");

    assertThat(tag.name()).isEqualTo("cite");
    assertThat(tag.attributes()).containsEntry("url", "https://example.com?a=1&b=2");
  }

  @Test
  void parse_backwardCompatibility_noAttributesStillWorks() {
    TagInfo tag1 = TagInfo.parse("<think>");
    TagInfo tag2 = TagInfo.parse("</think>");

    assertThat(tag1.name()).isEqualTo("think");
    assertThat(tag1.type()).isEqualTo(TagInfo.TagType.OPEN);
    assertThat(tag1.attributes()).isEmpty();

    assertThat(tag2.name()).isEqualTo("think");
    assertThat(tag2.type()).isEqualTo(TagInfo.TagType.CLOSE);
    assertThat(tag2.attributes()).isEmpty();
  }
}
