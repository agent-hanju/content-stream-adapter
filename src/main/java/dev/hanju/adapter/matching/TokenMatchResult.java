package dev.hanju.adapter.matching;

import java.util.List;

/**
 * 텍스트 매칭 결과를 원본 토큰 목록으로 표현하는 객체.
 *
 * <p>TEXT는 토큰 경계를 보존하여 개별 출력에 사용하고,
 * PATTERN은 토큰들을 결합한 하나의 의미 단위(예: {@code <cite>})로 처리한다.</p>
 */
public record TokenMatchResult(Type type, List<String> tokens) {

  public enum Type { TEXT, PATTERN }

  public static TokenMatchResult text(List<String> tokens) {
    return new TokenMatchResult(Type.TEXT, tokens);
  }

  public static TokenMatchResult pattern(List<String> tokens) {
    return new TokenMatchResult(Type.PATTERN, tokens);
  }
}
