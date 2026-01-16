package me.hanju.adapter.payload;

import java.util.Collections;
import java.util.Map;

/**
 * 태그 이름, 타입, 속성을 담는 DTO
 *
 * @param name       태그 이름
 * @param type       태그 타입 (OPEN/CLOSE)
 * @param attributes 속성 맵 (OPEN 태그만 해당)
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
   * 속성 없는 TagInfo 생성 (기존 호환성)
   */
  public TagInfo(String name, TagType type) {
    this(name, type, Collections.emptyMap());
  }

  /**
   * OPEN 태그 생성
   */
  public static TagInfo open(String name, Map<String, String> attributes) {
    return new TagInfo(name, TagType.OPEN, attributes);
  }

  /**
   * OPEN 태그 생성 (속성 없음)
   */
  public static TagInfo open(String name) {
    return new TagInfo(name, TagType.OPEN);
  }

  /**
   * CLOSE 태그 생성
   */
  public static TagInfo close(String name) {
    return new TagInfo(name, TagType.CLOSE);
  }

  public enum TagType {
    OPEN,
    CLOSE
  }
}
