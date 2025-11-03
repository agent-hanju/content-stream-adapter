package me.hanju.adapter.matcher;

import java.util.ArrayList;
import java.util.Iterator;
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
 */
public class TokenBuffer {
  private static final Logger log = Logger.getLogger(TokenBuffer.class.getName());
  private final List<String> tokens = new ArrayList<>();
  private int totalLength = 0;

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
   *
   * 예시:
   * - 버퍼: ["Hello ", "world"]
   * - extractUpTo(6) → ["Hello "] 추출, ["world"] 남음
   * - extractUpTo(8) → ["Hello ", "wo"] 추출, ["rld"] 남음
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

    Iterator<String> iterator = tokens.iterator();
    while (iterator.hasNext() && currentPosition < charPosition) {
      String token = iterator.next();
      int tokenEnd = currentPosition + token.length();

      if (tokenEnd <= charPosition) {
        // 토큰 전체가 추출 범위 안에 포함됨
        extracted.add(token);
        iterator.remove();
        currentPosition = tokenEnd;
        totalLength -= token.length();
      } else {
        // 토큰이 추출 위치를 걸쳐 있음 - 분할 필요
        int splitIndex = charPosition - currentPosition;
        String prefix = token.substring(0, splitIndex);
        String suffix = token.substring(splitIndex);

        extracted.add(prefix);
        iterator.remove();

        // suffix를 버퍼의 맨 앞에 추가
        tokens.add(0, suffix);

        totalLength -= prefix.length();
        currentPosition = charPosition;
        break;
      }
    }

    return extracted;
  }

  /**
   * 버퍼 앞부터 지정한 길이만큼 추출하여 단일 문자열로 반환
   *
   * extractUpTo()와 달리 토큰 경계를 무시하고 병합된 문자열을 반환합니다.
   * 패턴 검출 시 사용됩니다. StreamPatternMatcher에서 다음과 같이 사용:
   * 1. extractUpTo(patternStart)로 패턴 이전 텍스트 추출 (토큰 경계 보존)
   * 2. extractAsString(patternLength)로 패턴 자체 추출 (병합된 문자열)
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

    // length만큼 토큰 추출
    List<String> extracted = extractUpTo(length);

    // 추출한 토큰들을 연결하여 반환
    return String.join("", extracted);
  }

  /**
   * 버퍼의 모든 토큰을 추출 (flush)
   *
   * @return 모든 토큰 리스트 (원본 경계 보존)
   */
  public List<String> flushAll() {
    List<String> result = new ArrayList<>(tokens);
    tokens.clear();
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
   * @return 모든 토큰을 연결한 문자열
   */
  public String getContentAsString() {
    return String.join("", tokens);
  }

  /**
   * 버퍼가 비어있는지 확인
   *
   * @return 비어있으면 true
   */
  public boolean isEmpty() {
    return tokens.isEmpty();
  }

  /**
   * 현재 버퍼의 토큰 개수
   *
   * @return 토큰 개수
   */
  public int getTokenCount() {
    return tokens.size();
  }
}
