package dev.hanju.adapter.matching;

import java.util.List;

/**
 * 텍스트 매칭 결과를 원본 토큰 목록으로 표현하는 객체.
 *
 * <p>TEXT는 토큰 경계를 보존하여 개별 출력에 사용하고,
 * PATTERN은 토큰들을 결합한 하나의 의미 단위(예: {@code <cite>})로 처리한다.</p>
 *
 * @param type   결과 종류
 * @param tokens 원본 토큰 목록
 */
public record TokenMatchingResult(Type type, List<String> tokens) {

  /** 결과 종류: 일반 텍스트 또는 매칭된 패턴. */
  public enum Type { TEXT, PATTERN }

  /**
   * TEXT 타입 결과를 생성합니다.
   *
   * @param tokens 원본 토큰 목록
   * @return TEXT 타입 결과
   */
  public static TokenMatchingResult text(List<String> tokens) {
    return new TokenMatchingResult(Type.TEXT, tokens);
  }

  /**
   * PATTERN 타입 결과를 생성합니다.
   *
   * @param tokens 원본 토큰 목록
   * @return PATTERN 타입 결과
   */
  public static TokenMatchingResult pattern(List<String> tokens) {
    return new TokenMatchingResult(Type.PATTERN, tokens);
  }
}
