package me.hanju.adapter.matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 입력 토큰의 경계를 보존하는 버퍼
 *
 * LLM 스트리밍 응답에서 입력된 토큰들의 원본 분절점을 유지하면서,
 * 필요시 특정 위치까지 추출하거나 범위를 추출할 수 있습니다.
 *
 * 주요 기능:
 * - 토큰 추가 시 원본 경계 보존
 * - 특정 문자 위치까지 추출 (토큰 분할 포함)
 * - 특정 범위 추출 (패턴 검출 시 사용)
 * - 전체 버퍼 flush
 *
 * 성능 최적화:
 * - splitOffset: 첫 토큰 분할 시 실제 배열 수정 없이 오프셋만 관리 (O(1))
 * - startIndex: 토큰 제거 시 실제 배열 수정 없이 인덱스만 증가 (O(1))
 * - 주기적 정리: startIndex가 임계값을 넘으면 불필요한 토큰 제거
 */
public class TokenBuffer {
  private static final Logger log = Logger.getLogger(TokenBuffer.class.getName());
  private static final int COMPACT_THRESHOLD = 50; // 정리 임계값

  private final List<String> tokens = new ArrayList<>();
  private int startIndex = 0; // 논리적 시작 인덱스
  private int splitOffset = 0; // tokens[startIndex]의 시작 오프셋
  private int totalLength = 0;

  /** 기본 생성자. 필드는 선언 시 초기화됩니다. */
  public TokenBuffer() {
    // 필드는 선언에서 초기화됨
  }

  /**
   * 토큰 추가
   *
   * @param token 추가할 토큰
   * @throws IllegalArgumentException token이 null인 경우
   */
  public void addToken(String token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }

    if (token.isEmpty()) {
      log.warning(
          "Empty token received and ignored. This may happen with some LLM providers (e.g., DeepSeek) during initial streaming.");
      return;
    }

    tokens.add(token);
    totalLength += token.length();
  }

  /**
   * 특정 문자 위치까지 토큰들을 추출
   *
   * 토큰 경계를 최대한 보존하되, 위치가 토큰 중간인 경우 분할합니다.
   * O(1) 분할 및 제거를 위해 splitOffset과 startIndex를 사용합니다.
   *
   * 예시:
   * - 버퍼: ["Hello ", "world"], startIndex=0, splitOffset=0
   * - extractUpTo(6) → ["Hello "] 추출, startIndex=1, ["world"] 남음
   * - extractUpTo(8) → ["Hello ", "wo"] 추출, splitOffset=2, ["rld"] 남음
   *
   * @param charPosition 추출할 문자 위치 (0-based, 0보다 커야 함)
   * @return 추출된 토큰 리스트 (원본 경계 보존)
   * @throws IllegalArgumentException charPosition이 음수인 경우
   */
  public List<String> extractUpTo(int charPosition) {
    if (charPosition < 0) {
      throw new IllegalArgumentException("charPosition must be non-negative, but was: " + charPosition);
    }

    if (charPosition == 0) {
      return List.of();
    }

    List<String> extracted = new ArrayList<>();
    int currentPosition = 0;

    for (int i = startIndex; i < tokens.size() && currentPosition < charPosition; i++) {
      String token = tokens.get(i);

      // 첫 번째 토큰이면 splitOffset 적용
      String effectiveToken = (i == startIndex && splitOffset > 0)
          ? token.substring(splitOffset)
          : token;

      int tokenEnd = currentPosition + effectiveToken.length();

      if (tokenEnd <= charPosition) {
        // 토큰 전체가 추출 범위 안에 포함됨
        extracted.add(effectiveToken);
        currentPosition = tokenEnd;
        totalLength -= effectiveToken.length();

        // 다음 토큰으로 이동 (실제 제거는 하지 않음)
        startIndex = i + 1;
        splitOffset = 0;
      } else {
        // 토큰이 추출 위치를 걸쳐 있음 - 분할 필요
        int splitPos = charPosition - currentPosition;
        String prefix = effectiveToken.substring(0, splitPos);

        extracted.add(prefix);

        // O(1) 분할: 오프셋만 증가 (실제 토큰은 수정하지 않음)
        splitOffset += splitPos;
        totalLength -= prefix.length();
        currentPosition = charPosition;
        break;
      }
    }

    // 주기적 정리: 사용 완료된 토큰들 제거
    compactIfNeeded();

    return extracted;
  }

  /**
   * startIndex가 임계값을 넘으면 불필요한 토큰들을 제거
   */
  private void compactIfNeeded() {
    if (startIndex >= COMPACT_THRESHOLD) {
      tokens.subList(0, startIndex).clear();
      startIndex = 0;
    }
  }

  /**
   * 버퍼 앞부터 지정한 길이만큼 추출하여 단일 문자열로 반환
   *
   * extractUpTo()와 달리 토큰 경계를 무시하고 병합된 문자열을 반환합니다.
   * 패턴 검출 시 사용됩니다. StreamPatternMatcher에서 다음과 같이 사용:
   * 1. extractUpTo(patternStart)로 패턴 이전 텍스트 추출 (토큰 경계 보존)
   * 2. extractAsString(patternLength)로 패턴 자체 추출 (병합된 문자열)
   *
   * 성능 최적화:
   * - List 생성 없이 StringBuilder에 직접 append
   * - append(CharSequence, start, end)로 중간 String 객체 생성 제거
   *
   * 예시:
   * - 버퍼: ["Hello", "world"]
   * - extractAsString(5) → "Hello" 반환, 버퍼에서 "Hello" 제거
   * - 버퍼: ["world"] 남음
   *
   * @param length 추출할 문자 길이 (0보다 커야 함)
   * @return 추출된 내용 (단일 문자열로 병합됨)
   * @throws IllegalArgumentException length가 음수인 경우
   */
  public String extractAsString(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be non-negative, but was: " + length);
    }

    if (length == 0) {
      return "";
    }

    // 최적화: List 생성 없이 StringBuilder에 직접 append
    StringBuilder sb = new StringBuilder(length);
    int currentPosition = 0;

    for (int i = startIndex; i < tokens.size() && currentPosition < length; i++) {
      String token = tokens.get(i);

      // 첫 번째 토큰이면 splitOffset 적용
      String effectiveToken = (i == startIndex && splitOffset > 0)
          ? token.substring(splitOffset)
          : token;

      int tokenEnd = currentPosition + effectiveToken.length();

      if (tokenEnd <= length) {
        // 토큰 전체 append
        sb.append(effectiveToken);
        currentPosition = tokenEnd;
        totalLength -= effectiveToken.length();

        // 다음 토큰으로 이동
        startIndex = i + 1;
        splitOffset = 0;
      } else {
        // 일부만 append - substring 대신 append(s, start, end) 사용
        int splitPos = length - currentPosition;
        sb.append(effectiveToken, 0, splitPos);

        // O(1) 분할
        splitOffset += splitPos;
        totalLength -= splitPos;
        break;
      }
    }

    compactIfNeeded();
    return sb.toString();
  }

  /**
   * 버퍼의 모든 토큰을 추출 (flush)
   *
   * 성능 최적화:
   * - 정확한 토큰 개수로 초기 capacity 설정 (재할당 방지)
   *
   * @return 모든 토큰 리스트 (원본 경계 보존)
   */
  public List<String> flushAll() {
    int remainingTokens = tokens.size() - startIndex;
    List<String> result = new ArrayList<>(remainingTokens);

    for (int i = startIndex; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (i == startIndex && splitOffset > 0) {
        result.add(token.substring(splitOffset));
      } else {
        result.add(token);
      }
    }

    tokens.clear();
    startIndex = 0;
    splitOffset = 0;
    totalLength = 0;
    return result;
  }

  /**
   * 현재 버퍼의 전체 길이
   *
   * @return 문자 개수
   */
  public int getTotalLength() {
    return totalLength;
  }

  /**
   * 버퍼의 내용을 단일 문자열로 반환
   *
   * 패턴 매칭 등에 사용됩니다.
   *
   * 성능 최적화:
   * - totalLength로 정확한 capacity 설정 (재할당 방지)
   * - append(CharSequence, start, end)로 substring 제거
   *
   * @return 모든 토큰을 연결한 문자열
   */
  public String getContentAsString() {
    if (isEmpty()) {
      return "";
    }

    StringBuilder sb = new StringBuilder(totalLength);
    for (int i = startIndex; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (i == startIndex && splitOffset > 0) {
        sb.append(token, splitOffset, token.length());
      } else {
        sb.append(token);
      }
    }
    return sb.toString();
  }

  /**
   * 버퍼가 비어있는지 확인
   *
   * @return 비어있으면 true
   */
  public boolean isEmpty() {
    return startIndex >= tokens.size();
  }

  /**
   * 현재 버퍼의 토큰 개수
   *
   * @return 토큰 개수
   */
  public int getTokenCount() {
    return tokens.size() - startIndex;
  }
}
