package me.hanju.adapter.payload;

/**
 * 태그 이름과 타입을 담는 파싱 결과
 *
 * @param name 태그 이름
 * @param type 태그 타입 (OPEN/CLOSE)
 */
public record TagInfo(String name, TagType type) {

  public TagInfo {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Tag name cannot be null or empty");
    }
    if (type == null) {
      throw new IllegalArgumentException("Tag type cannot be null");
    }
  }

  /**
   * 태그 문자열 파싱 (예: "&lt;cite&gt;" → TagInfo("cite", OPEN))
   */
  public static TagInfo parse(String tagString) {
    if (tagString == null || tagString.isEmpty()) {
      throw new IllegalArgumentException("Tag string cannot be null or empty");
    }

    boolean isClosing = tagString.startsWith("</");

    int tagEnd = tagString.indexOf('>');
    if (tagEnd == -1) {
      tagEnd = tagString.length();
    }

    String tagName = isClosing
        ? tagString.substring(2, tagEnd)
        : tagString.substring(1, tagEnd);

    return new TagInfo(tagName, isClosing ? TagType.CLOSE : TagType.OPEN);
  }

  public enum TagType {
    OPEN,
    CLOSE
  }
}
