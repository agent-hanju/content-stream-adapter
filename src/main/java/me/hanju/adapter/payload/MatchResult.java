package me.hanju.adapter.payload;

import java.util.List;

/**
 * 패턴 매칭 결과 (Sealed)
 */
public sealed interface MatchResult {

  /**
   * 일반 텍스트 토큰 (토큰 경계 보존)
   */
  record TokenMatchResult(List<String> tokens) implements MatchResult {
  }

  /**
   * 패턴 감지
   */
  record PatternMatchResult(List<String> prevTokens, String pattern) implements MatchResult {
  }

  /**
   * 매칭 결과 없음 (더 많은 입력 필요)
   */
  record NoMatchResult() implements MatchResult {
  }
}
