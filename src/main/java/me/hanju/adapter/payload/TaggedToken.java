package me.hanju.adapter.payload;

import java.util.Collections;
import java.util.Map;

/**
 * 경로와 컨텐츠를 포함하는 출력 토큰
 *
 * @param path       현재 FSM 경로 (예: "/", "/cite", "/cite/id")
 * @param content    태그를 제외한 텍스트 (event가 있으면 null 가능)
 * @param event      이벤트 타입 ("OPEN", "CLOSE", 또는 null)
 * @param attributes OPEN 이벤트 시 속성 맵 (없으면 빈 맵)
 */
public record TaggedToken(String path, String content, String event, Map<String, String> attributes) {
  public TaggedToken {
    if (path == null) {
      throw new IllegalArgumentException("path cannot be null");
    }
    // event가 null일 때만 content가 필수
    if (event == null && content == null) {
      throw new IllegalArgumentException("content cannot be null when event is null");
    }
    // attributes는 null이면 빈 맵으로
    if (attributes == null) {
      attributes = Collections.emptyMap();
    }
  }

  /**
   * 일반 토큰 생성 (event = null, attributes = empty)
   */
  public TaggedToken(String path, String content) {
    this(path, content, null, Collections.emptyMap());
  }

  /**
   * 이벤트 토큰 생성 (attributes = empty) - 기존 호환성
   */
  public TaggedToken(String path, String content, String event) {
    this(path, content, event, Collections.emptyMap());
  }

  /**
   * OPEN 이벤트 토큰 생성 (속성 없음)
   */
  public static TaggedToken openEvent(String path) {
    return new TaggedToken(path, null, "OPEN", Collections.emptyMap());
  }

  /**
   * OPEN 이벤트 토큰 생성 (속성 포함)
   */
  public static TaggedToken openEvent(String path, Map<String, String> attributes) {
    return new TaggedToken(path, null, "OPEN", attributes);
  }

  /**
   * CLOSE 이벤트 토큰 생성
   */
  public static TaggedToken closeEvent(String path) {
    return new TaggedToken(path, null, "CLOSE", Collections.emptyMap());
  }
}
