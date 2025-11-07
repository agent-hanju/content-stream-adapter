package me.hanju.adapter.payload;

/**
 * 경로와 컨텐츠를 포함하는 출력 토큰
 *
 * @param path    현재 FSM 경로 (예: "/", "/cite", "/cite/id")
 * @param content 태그를 제외한 텍스트 (event가 있으면 null 가능)
 * @param event   이벤트 타입 ("OPEN", "CLOSE", 또는 null)
 */
public record TaggedToken(String path, String content, String event) {
  public TaggedToken {
    if (path == null) {
      throw new IllegalArgumentException("path cannot be null");
    }
    // event가 null일 때만 content가 필수
    if (event == null && content == null) {
      throw new IllegalArgumentException("content cannot be null when event is null");
    }
  }

  /**
   * 일반 토큰 생성 (event = null)
   */
  public TaggedToken(String path, String content) {
    this(path, content, null);
  }

  /**
   * OPEN 이벤트 토큰 생성
   */
  public static TaggedToken openEvent(String path) {
    return new TaggedToken(path, null, "OPEN");
  }

  /**
   * CLOSE 이벤트 토큰 생성
   */
  public static TaggedToken closeEvent(String path) {
    return new TaggedToken(path, null, "CLOSE");
  }
}
