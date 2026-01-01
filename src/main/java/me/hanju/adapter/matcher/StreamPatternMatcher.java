package me.hanju.adapter.matcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.hanju.adapter.payload.MatchResult;

/**
 * Aho-Corasick 기반 스트리밍 패턴 검출기
 * <p>
 * 토큰 경계를 보존하며 Greedy matching 방식으로 패턴을 검출합니다.
 * </p>
 */
public class StreamPatternMatcher {

  private final AhoCorasickTrie trie;
  private final TokenBuffer tokenBuffer;
  private final int bufferSizeLimit;
  private PendingPattern pendingPattern = null;

  /**
   * 기본 버퍼 크기 제한으로 StreamPatternMatcher를 생성합니다.
   * 버퍼 크기는 최장 패턴 길이의 2배로 설정됩니다.
   *
   * @param trie 패턴 매칭에 사용할 Aho-Corasick Trie
   */
  public StreamPatternMatcher(AhoCorasickTrie trie) {
    this(trie, null);
  }

  /**
   * 지정된 버퍼 크기 제한으로 StreamPatternMatcher를 생성합니다.
   *
   * @param trie            패턴 매칭에 사용할 Aho-Corasick Trie
   * @param bufferSizeLimit 버퍼 최대 크기 (null이면 기본값 사용)
   */
  public StreamPatternMatcher(AhoCorasickTrie trie, Integer bufferSizeLimit) {
    if (trie == null) {
      throw new IllegalArgumentException("Trie는 null일 수 없습니다");
    }

    this.trie = trie;
    this.tokenBuffer = new TokenBuffer();
    this.bufferSizeLimit = (bufferSizeLimit != null)
        ? bufferSizeLimit
        : trie.getMaxPatternLength() * 2;
  }

  /**
   * 새 토큰을 추가하고 패턴 매칭 결과들을 반환합니다.
   * 버퍼에 처리할 패턴이 남아있으면 모두 처리합니다.
   *
   * @param token 추가할 토큰
   * @return 매칭 결과 리스트 (패턴 검출, 안전한 텍스트 토큰들)
   */
  public List<MatchResult> addTokenAndGetResult(String token) {
    if (token == null) {
      throw new IllegalArgumentException("토큰은 null일 수 없습니다");
    }

    List<MatchResult> results = new ArrayList<>();

    if (!token.isEmpty()) {
      tokenBuffer.addToken(token);
    }

    while (!tokenBuffer.isEmpty()) {
      MatchResult result = processBuffer();
      if (result instanceof MatchResult.NoMatchResult) {
        break;
      }
      results.add(result);
    }

    return results;
  }

  /**
   * 버퍼의 내용을 처리하여 패턴 매칭을 수행합니다.
   * Aho-Corasick 알고리즘으로 Greedy matching 방식을 사용합니다.
   *
   * @return 매칭 결과 (패턴 검출, 안전한 텍스트 토큰, 또는 매칭 없음)
   */
  private MatchResult processBuffer() {
    if (tokenBuffer.isEmpty()) {
      return new MatchResult.NoMatchResult();
    }

    String text = tokenBuffer.getContentAsString();
    AhoCorasickTrie.TrieNode state = trie.getRoot();
    int longestMatchingPrefixLength = 0;

    // 1단계: 버퍼 내용 전체를 순회하며 패턴 검색
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      // 1-1. Failure link를 따라 매칭 가능한 상태로 이동
      while (state != trie.getRoot() && !state.children.containsKey(c)) {
        state = state.failureLink;
      }

      // 1-2. 다음 문자로 전이 (또는 pending pattern 반환)
      if (state.children.containsKey(c)) {
        state = state.children.get(c);
      } else if (pendingPattern != null) {
        // 더 이상 매칭이 불가능하면 pending pattern을 확정
        PendingPattern toReturn = pendingPattern;
        pendingPattern = null;

        List<String> prevTokens = tokenBuffer.extractUpTo(toReturn.patternStart);
        String pattern = tokenBuffer.extractAsString(toReturn.pattern.length());

        return new MatchResult.PatternMatchResult(prevTokens, pattern);
      }

      // 1-3. 패턴 매칭 발견 시 처리 (Greedy matching)
      if (!state.outputs.isEmpty()) {
        String longestPattern = state.outputs.stream()
            .max(Comparator.comparingInt(String::length))
            .orElse(null);

        if (longestPattern != null) {
          int patternStart = i - longestPattern.length() + 1;

          // 더 긴 패턴이 가능하면 pending으로 유지
          if (!state.children.isEmpty()) {
            pendingPattern = new PendingPattern(longestPattern, patternStart);
          } else {
            // 더 이상 확장 불가능하면 즉시 반환
            pendingPattern = null;
            List<String> prevTokens = tokenBuffer.extractUpTo(patternStart);
            String pattern = tokenBuffer.extractAsString(longestPattern.length());
            return new MatchResult.PatternMatchResult(prevTokens, pattern);
          }
        }
      }

      // 1-4. 버퍼 끝에서 최장 매칭 prefix 길이 계산
      if (i == text.length() - 1) {
        longestMatchingPrefixLength = state.depth;

        AhoCorasickTrie.TrieNode current = state.failureLink;
        while (current != null && current != trie.getRoot()) {
          longestMatchingPrefixLength = Math.max(longestMatchingPrefixLength, current.depth);
          current = current.failureLink;
        }
      }
    }

    // 2단계: 버퍼 크기 제한 초과 시 pending pattern 강제 확정
    if (pendingPattern != null && tokenBuffer.getTotalLength() > bufferSizeLimit) {
      PendingPattern toReturn = pendingPattern;
      pendingPattern = null;

      List<String> prevTokens = tokenBuffer.extractUpTo(toReturn.patternStart);
      String pattern = tokenBuffer.extractAsString(toReturn.pattern.length());

      return new MatchResult.PatternMatchResult(prevTokens, pattern);
    }

    // 3단계: Safe flush 위치 계산
    // Safe flush = 패턴 매칭 가능성이 없는 안전한 위치
    int safeFlushPosition = text.length() - longestMatchingPrefixLength;

    // 3-1. Pending pattern이 있으면 그 시작 위치 전까지만 안전
    if (pendingPattern != null) {
      safeFlushPosition = Math.min(safeFlushPosition, pendingPattern.patternStart);
    }

    // 3-2. 버퍼 크기 제한 초과 시 최소한의 안전 영역 보장
    if (tokenBuffer.getTotalLength() > bufferSizeLimit) {
      safeFlushPosition = Math.max(safeFlushPosition,
          tokenBuffer.getTotalLength() - trie.getMaxPatternLength());
    }

    // 4단계: 안전한 텍스트 토큰 반환
    if (safeFlushPosition > 0) {
      List<String> safeTokens = tokenBuffer.extractUpTo(safeFlushPosition);
      return new MatchResult.TokenMatchResult(safeTokens);
    }

    return new MatchResult.NoMatchResult();
  }

  /**
   * 버퍼에 남아있는 모든 내용을 반환하고 상태를 초기화합니다.
   * 스트림 종료 시 반드시 호출해야 합니다.
   *
   * @return 버퍼에 남아있던 모든 토큰
   */
  public List<String> flushRemaining() {
    if (pendingPattern != null) {
      pendingPattern = null;
    }
    return tokenBuffer.flushAll();
  }

  /**
   * 매처의 상태를 초기화합니다.
   * 버퍼와 pending pattern을 모두 비웁니다.
   *
   * @return this (메서드 체이닝용)
   */
  public StreamPatternMatcher reset() {
    tokenBuffer.flushAll();
    pendingPattern = null;
    return this;
  }

  /**
   * 현재 버퍼의 내용을 문자열로 반환합니다.
   *
   * @return 버퍼 내용 (디버깅용)
   */
  public String getBufferContent() {
    return tokenBuffer.getContentAsString();
  }

  /**
   * 현재 버퍼의 총 길이를 반환합니다.
   *
   * @return 버퍼에 저장된 문자 개수
   */
  public int getBufferSize() {
    return tokenBuffer.getTotalLength();
  }

  /**
   * 사용 중인 Trie 객체를 반환합니다.
   *
   * @return AhoCorasickTrie 인스턴스
   */
  public AhoCorasickTrie getTrie() {
    return trie;
  }

  /**
   * Greedy matching을 위해 더 긴 패턴 매칭을 대기 중인 패턴 정보를 저장합니다.
   * 현재 매칭된 패턴이 있지만 더 긴 패턴이 가능한 경우 사용됩니다.
   */
  private static class PendingPattern {
    final String pattern; // 현재까지 매칭된 패턴 문자열
    final int patternStart; // 버퍼에서 패턴 시작 위치 (인덱스)

    PendingPattern(String pattern, int patternStart) {
      this.pattern = pattern;
      this.patternStart = patternStart;
    }
  }
}
