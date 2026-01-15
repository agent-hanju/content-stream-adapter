package me.hanju.adapter.payload;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 태그 이름, 타입, 속성을 담는 파싱 결과
 *
 * @param name       태그 이름
 * @param type       태그 타입 (OPEN/CLOSE)
 * @param attributes 태그 속성 (key=value 쌍)
 */
public record TagInfo(String name, TagType type, Map<String, String> attributes) {

  public TagInfo {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Tag name cannot be null or empty");
    }
    if (type == null) {
      throw new IllegalArgumentException("Tag type cannot be null");
    }
    if (attributes == null) {
      attributes = Collections.emptyMap();
    }
  }

  /**
   * attributes 없는 생성자 (기존 호환성)
   */
  public TagInfo(String name, TagType type) {
    this(name, type, Collections.emptyMap());
  }

  /**
   * 태그 문자열 파싱 (예: "&lt;cite id=\"ref1\"&gt;" → TagInfo("cite", OPEN, {"id":"ref1"}))
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

    // 태그명 추출
    int contentStart = isClosing ? 2 : 1;
    String content = tagString.substring(contentStart, tagEnd).trim();

    // 공백으로 태그명과 attributes 분리
    int spaceIndex = content.indexOf(' ');

    String tagName;
    Map<String, String> attributes = Collections.emptyMap();

    if (spaceIndex == -1) {
      // attributes 없음
      tagName = content;
    } else {
      // attributes 있음
      tagName = content.substring(0, spaceIndex);
      String attributesString = content.substring(spaceIndex + 1).trim();

      if (!attributesString.isEmpty() && !isClosing) {
        attributes = parseAttributes(attributesString);
      }
    }

    return new TagInfo(tagName, isClosing ? TagType.CLOSE : TagType.OPEN, attributes);
  }

  /**
   * Attribute 문자열 파싱
   * <p>
   * 예: 'id="ref1" source="wiki"' → {"id": "ref1", "source": "wiki"}
   * </p>
   * <p>
   * 규칙:
   * - 큰따옴표만 지원 (작은따옴표 미지원)
   * - 값 안에 따옴표 없어야 함 (escape 미지원)
   * - 키는 알파벳, 숫자, 언더스코어만
   * </p>
   */
  private static Map<String, String> parseAttributes(String attributesString) {
    Map<String, String> attributes = new HashMap<>();

    int i = 0;
    while (i < attributesString.length()) {
      // 공백 건너뛰기
      while (i < attributesString.length() && Character.isWhitespace(attributesString.charAt(i))) {
        i++;
      }

      if (i >= attributesString.length()) {
        break;
      }

      // 키 읽기 (=까지)
      int keyStart = i;
      while (i < attributesString.length() && attributesString.charAt(i) != '=') {
        i++;
      }

      if (i >= attributesString.length()) {
        break; // = 없으면 종료
      }

      String key = attributesString.substring(keyStart, i).trim();
      i++; // = 건너뛰기

      // 공백 건너뛰기
      while (i < attributesString.length() && Character.isWhitespace(attributesString.charAt(i))) {
        i++;
      }

      if (i >= attributesString.length()) {
        break;
      }

      // 따옴표 확인
      if (attributesString.charAt(i) != '"') {
        break; // 따옴표 없으면 종료
      }

      i++; // 시작 따옴표 건너뛰기

      // 값 읽기 (다음 따옴표까지)
      int valueStart = i;
      while (i < attributesString.length() && attributesString.charAt(i) != '"') {
        i++;
      }

      if (i >= attributesString.length()) {
        break; // 닫는 따옴표 없으면 종료
      }

      String value = attributesString.substring(valueStart, i);
      i++; // 닫는 따옴표 건너뛰기

      attributes.put(key, value);
    }

    return attributes.isEmpty() ? Collections.emptyMap() : attributes;
  }

  public enum TagType {
    OPEN,
    CLOSE
  }
}
