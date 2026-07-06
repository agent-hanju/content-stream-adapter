package dev.hanju.adapter.buffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * char 연속 버퍼 기반으로 구현한 토큰 단위 버퍼.
 *
 * <p>문자들을 {@code StringBuilder} 하나에 연속으로 저장하고, 각 토큰의 끝 위치를
 * {@code int[]} 배열로 별도 관리합니다. 짧은 토큰(1~3자)이 많은 환경에서
 * {@code List<String>} 대비 String 객체 오버헤드와 캐시 미스를 줄입니다.</p>
 *
 * <p>성능 특성:</p>
 * <ul>
 *   <li>{@code addToken}: O(1) amortized</li>
 *   <li>{@code extract}: O(k), k = 추출된 토큰 수</li>
 *   <li>{@code getContent}: O(n), n = 버퍼 길이</li>
 *   <li>{@code chars}와 {@code tokenEnds}는 소비된 앞부분이 임계값을 넘으면 compact</li>
 * </ul>
 */
public class TokenBuffer {
  private static final Logger log = Logger.getLogger(TokenBuffer.class.getName());
  private static final int INITIAL_TOKEN_CAPACITY = 16;
  private static final int TOKEN_COMPACT_THRESHOLD = 64;
  private static final int CHAR_COMPACT_THRESHOLD = 8192;

  /** 전체 입력 문자를 연속으로 저장 (소비된 부분도 유지, headChar로 논리적 시작 관리) */
  private final StringBuilder chars = new StringBuilder();

  /** tokenEnds[i] = i번째 토큰의 끝 위치 (chars 기준 절대 위치, exclusive) */
  private int[] tokenEnds = new int[INITIAL_TOKEN_CAPACITY];

  /** 추가된 토큰 총 수 */
  private int tokenCount = 0;

  /** 논리적 첫 번째 live 토큰의 인덱스 (tokenEnds 기준) */
  private int headToken = 0;

  /** 논리적 첫 번째 live 문자의 절대 위치 (chars 기준) */
  private int headChar = 0;

  /** 기본 생성자 */
  public TokenBuffer() {}

  /**
   * 토큰 추가.
   *
   * @param token 추가할 토큰
   * @throws IllegalArgumentException token이 null인 경우
   */
  public void addToken(final String token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    if (token.isEmpty()) {
      log.warning("Empty token received and ignored.");
      return;
    }

    chars.append(token);
    if (tokenCount >= tokenEnds.length) {
      tokenEnds = Arrays.copyOf(tokenEnds, tokenEnds.length * 2);
    }
    tokenEnds[tokenCount++] = chars.length();
  }

  /**
   * 특정 문자 위치까지 토큰들을 추출하고 버퍼에서 제거.
   *
   * @param charPosition 추출할 문자 수 (0보다 커야 함)
   * @return 추출된 토큰 리스트 (원본 경계 보존)
   * @throws IllegalArgumentException charPosition이 음수인 경우
   */
  public List<String> extract(final int charPosition) {
    if (charPosition < 0) {
      throw new IllegalArgumentException("charPosition must be non-negative, but was: " + charPosition);
    }
    if (charPosition == 0) {
      return List.of();
    }

    final int absEnd = headChar + charPosition;
    final List<String> result = new ArrayList<>();
    int charStart = headChar;

    while (headToken < tokenCount) {
      final int tokenEnd = tokenEnds[headToken];

      if (tokenEnd <= absEnd) {
        result.add(chars.substring(charStart, tokenEnd));
        charStart = tokenEnd;
        headToken++;
      } else {
        // 토큰이 추출 경계에 걸침 — headToken은 유지, headChar만 전진
        // charStart == absEnd인 경우(정확히 경계)는 빈 문자열 추가 금지
        if (charStart < absEnd) {
          result.add(chars.substring(charStart, absEnd));
        }
        charStart = absEnd;
        break;
      }
    }

    headChar = charStart;
    compactIfNeeded();
    return result;
  }

  /**
   * 버퍼의 모든 토큰을 추출하고 초기화.
   *
   * @return 모든 토큰 리스트 (원본 경계 보존)
   */
  public List<String> flush() {
    final List<String> result = new ArrayList<>(tokenCount - headToken);
    int charStart = headChar;

    for (int i = headToken; i < tokenCount; i++) {
      result.add(chars.substring(charStart, tokenEnds[i]));
      charStart = tokenEnds[i];
    }

    chars.setLength(0);
    tokenCount = 0;
    headToken = 0;
    headChar = 0;
    return result;
  }

  /**
   * 버퍼의 현재 내용을 단일 문자열로 반환 (소비하지 않음).
   *
   * @return 모든 토큰을 연결한 문자열
   */
  public String getContent() {
    return chars.substring(headChar);
  }

  /**
   * 버퍼가 비어있는지 확인.
   *
   * @return 비어있으면 true
   */
  public boolean isEmpty() {
    return headChar >= chars.length();
  }

  /** 소비된 앞부분이 임계값을 초과하면 chars와 tokenEnds를 함께 앞으로 당김. */
  private void compactIfNeeded() {
    if (headChar == chars.length()) {
      chars.setLength(0);
      tokenCount = 0;
      headToken = 0;
      headChar = 0;
      return;
    }

    if (headToken < TOKEN_COMPACT_THRESHOLD && headChar < CHAR_COMPACT_THRESHOLD) {
      return;
    }

    final int remaining = tokenCount - headToken;
    for (int i = 0; i < remaining; i++) {
      tokenEnds[i] = tokenEnds[headToken + i] - headChar;
    }
    chars.delete(0, headChar);
    tokenCount = remaining;
    headToken = 0;
    headChar = 0;
  }
}
