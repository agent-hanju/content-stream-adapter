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
  /**
   * 레코드 유효성 검증을 수행하는 컴팩트 생성자.
   *
   * @throws IllegalArgumentException path가 null이거나, event가 null일 때 content가 null인 경우
   */
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
   * 일반 토큰을 생성합니다 (event = null, attributes = empty).
   *
   * @param path 현재 FSM 경로
   * @param content 텍스트 컨텐츠
   */
  public TaggedToken(String path, String content) {
    this(path, content, null, Collections.emptyMap());
  }

  /**
   * 이벤트 토큰을 생성합니다 (attributes = empty).
   *
   * @param path 현재 FSM 경로
   * @param content 텍스트 컨텐츠 (이벤트 토큰은 null 가능)
   * @param event 이벤트 타입 ("OPEN", "CLOSE", 또는 null)
   */
  public TaggedToken(String path, String content, String event) {
    this(path, content, event, Collections.emptyMap());
  }

  /**
   * OPEN 이벤트 토큰을 생성합니다 (속성 없음).
   *
   * @param path 현재 FSM 경로
   * @return OPEN 이벤트 토큰
   */
  public static TaggedToken openEvent(String path) {
    return new TaggedToken(path, null, "OPEN", Collections.emptyMap());
  }

  /**
   * OPEN 이벤트 토큰을 생성합니다 (속성 포함).
   *
   * @param path 현재 FSM 경로
   * @param attributes 태그 속성 맵
   * @return OPEN 이벤트 토큰
   */
  public static TaggedToken openEvent(String path, Map<String, String> attributes) {
    return new TaggedToken(path, null, "OPEN", attributes);
  }

  /**
   * CLOSE 이벤트 토큰을 생성합니다.
   *
   * @param path 닫히는 태그의 FSM 경로
   * @return CLOSE 이벤트 토큰
   */
  public static TaggedToken closeEvent(String path) {
    return new TaggedToken(path, null, "CLOSE", Collections.emptyMap());
  }
}
