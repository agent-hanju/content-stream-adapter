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

  /**
   * 레코드 유효성 검증을 수행하는 컴팩트 생성자.
   *
   * @throws IllegalArgumentException name이 null이거나 빈 문자열인 경우, 또는 type이 null인 경우
   */
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
   * 속성 없는 TagInfo를 생성합니다.
   *
   * @param name 태그 이름
   * @param type 태그 타입
   */
  public TagInfo(String name, TagType type) {
    this(name, type, Collections.emptyMap());
  }

  /**
   * OPEN 태그를 생성합니다.
   *
   * @param name 태그 이름
   * @param attributes 태그 속성
   * @return OPEN 태그 정보
   */
  public static TagInfo open(String name, Map<String, String> attributes) {
    return new TagInfo(name, TagType.OPEN, attributes);
  }

  /**
   * OPEN 태그를 생성합니다 (속성 없음).
   *
   * @param name 태그 이름
   * @return OPEN 태그 정보
   */
  public static TagInfo open(String name) {
    return new TagInfo(name, TagType.OPEN);
  }

  /**
   * CLOSE 태그를 생성합니다.
   *
   * @param name 태그 이름
   * @return CLOSE 태그 정보
   */
  public static TagInfo close(String name) {
    return new TagInfo(name, TagType.CLOSE);
  }

  /** 태그 타입을 나타내는 열거형. */
  public enum TagType {
    /** 여는 태그. */
    OPEN,
    /** 닫는 태그. */
    CLOSE
  }
}
