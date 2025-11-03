package me.hanju.adapter.payload;

/**
 * 경로와 컨텐츠를 포함하는 출력 토큰
 *
 * @param path    현재 FSM 경로 (예: "/", "/cite", "/cite/id")
 * @param content 태그를 제외한 텍스트
 */
public record TaggedToken(String path, String content) {
  public TaggedToken {
    if (path == null) {
      throw new IllegalArgumentException("path cannot be null");
    }
    if (content == null) {
      throw new IllegalArgumentException("content cannot be null");
    }
  }
}
