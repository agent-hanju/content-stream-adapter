package dev.hanju.adapter.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * 토큰 경계를 보존하면서 패턴 매칭을 수행하는 버퍼
 *
 * 내부 TokenBuffer를 사용하여 토큰 입출력 기능을 제공하고,
 * 토큰 기반 패턴 매칭 결과를 반환합니다.
 */
public class TokenMatchingBuffer {

  private final AhoCorasickTrie trie;
  private final TokenBuffer buffer;

  // === 호출 간 보존되는 상태 ===
  private int currentState;    // AhoCorasickTrie 상의 현재 위치 (buffer.getContent() 기준 scanPos까지 소비한 상태)
  private int scanPos = 0;     // buffer.getContent() 안에서 아직 훑지 않은 다음 문자 위치
  private int bestStart = -1;  // 지금까지 발견된 leftmost-longest 후보 패턴의 시작 위치 (-1이면 후보 없음)
  private int bestEnd = -1;    // 위 후보 패턴의 끝 위치 (exclusive) — 더 긴 매칭이 나오면 갱신됨

  /**
   * TokenMatchingBuffer를 생성합니다.
   *
   * @param trie 패턴 매칭에 사용할 Aho-Corasick Trie
   */
  public TokenMatchingBuffer(final AhoCorasickTrie trie) {
    if (trie == null) {
      throw new IllegalArgumentException("Trie는 null일 수 없습니다");
    }

    this.trie = trie;
    this.currentState = trie.getInitialState();
    this.buffer = new TokenBuffer();
  }

  /**
   * 단일 토큰을 입력하고 현재 확정 가능한 매칭 결과를 반환합니다.
   *
   * @param token 입력 토큰
   * @return 확정된 토큰 기반 매칭 결과 리스트
   */
  public List<TokenMatchingResult> accept(final String token) {
    buffer.addToken(token);
    return drain(false);
  }

  /**
   * 여러 토큰을 입력하고 현재 확정 가능한 매칭 결과를 반환합니다.
   *
   * <p>스트림이 계속 이어질 수 있다고 가정하므로, 이후 토큰과 합쳐져 패턴이 될 수
   * 있는 접미사는 버퍼에 남겨둡니다.</p>
   *
   * @param tokens 입력 토큰 목록
   * @return 확정된 토큰 기반 매칭 결과 리스트
   */
  public List<TokenMatchingResult> acceptAll(final List<String> tokens) {
    if (tokens == null) {
      throw new IllegalArgumentException("tokens must not be null");
    }
    for (final String token : tokens) {
      buffer.addToken(token);
    }
    return drain(false);
  }

  /**
   * 모든 버퍼 내용을 매칭 후 반환합니다.
   *
   * <p>추가 입력이 없다고 가정하고 모든 내용을 확정합니다.</p>
   *
   * @return 토큰 기반 매칭 결과 리스트
   */
  public List<TokenMatchingResult> flush() {
    final List<TokenMatchingResult> results = drain(true);

    // 남은 내용은 전부 텍스트로 확정
    if (!buffer.isEmpty()) {
      results.add(TokenMatchingResult.text(buffer.flush()));
    }

    // 상태 리셋
    this.bestStart = -1;
    this.bestEnd = -1;
    this.currentState = trie.getInitialState();
    this.scanPos = 0;
    return results;
  }

  /**
   * 버퍼를 스캔하여 확정 가능한 매칭 결과를 모두 수집합니다.
   * Leftmost-Longest 패턴(가장 이른 시작 위치의, 그중 가장 긴 패턴)을 선택합니다.
   *
   * - accepting state 도달 시 → 후보로 기록 (더 긴 매칭 가능성 대기)
   * - 직접 전이 불가 + 후보 있음 → 확정
   * - maxPatternLength 초과 → 안전장치로 확정
   */
  private List<TokenMatchingResult> drain(boolean isFinal) {
    final List<TokenMatchingResult> results = new ArrayList<>();

    while (!buffer.isEmpty()) {
      final String text = buffer.getContent();
      boolean shouldConfirm = false;

      while (scanPos < text.length()) {
        final char c = text.charAt(scanPos);

        // 후보 패턴이 있고 직접 확장할 수 없으면, 현재 후보를 leftmost-longest 결과로 확정합니다.
        if (!trie.hasNextState(currentState, c) && bestStart >= 0) {
          shouldConfirm = true;
          break;
        }

        currentState = trie.nextState(currentState, c);
        scanPos++;

        // failure link로 보고되는 매칭은 다른 시작 위치일 수 있으므로 단순 덮어쓰기 금지
        final String matchedPattern = trie.getMatchedPattern(currentState);
        if (!matchedPattern.isEmpty()) {
          final int start = scanPos - matchedPattern.length();
          if (bestStart < 0 || start < bestStart || (start == bestStart && scanPos > bestEnd)) {
            bestStart = start;
            bestEnd = scanPos;
          }
        }

        // 가장 긴 패턴 길이만큼 이미 확인했다면 더 긴 동일 시작 패턴은 존재할 수 없습니다.
        if (bestStart >= 0 && scanPos - bestStart >= trie.getMaxPatternLength()) {
          shouldConfirm = true;
          break;
        }
      }

      if (!shouldConfirm && bestStart >= 0) {
        shouldConfirm = isFinal || (bestEnd == text.length() && !trie.canContinue(currentState));
      }

      int end;
      boolean isPattern;
      if (shouldConfirm) {
        if (bestStart > 0) {
          end = bestStart;
          isPattern = false;
        } else {
          end = bestEnd;
          isPattern = true;
          bestStart = -1;
          bestEnd = -1;
          currentState = trie.getInitialState();
        }
      } else {
        // 현재 AC 상태가 의미하는 진행 길이만큼의 suffix는 다음 입력과 합쳐질 수 있으므로 남깁니다.
        int safeFlushPosition = text.length() - trie.getMatchingLength(currentState);
        if (bestStart >= 0) {
          safeFlushPosition = Math.min(safeFlushPosition, bestStart);
        }
        if (safeFlushPosition <= 0) {
          break;
        }
        end = safeFlushPosition;
        isPattern = false;
      }

      final List<String> tokens = buffer.extract(end);

      scanPos -= end;
      if (scanPos < 0) {
        throw new IllegalStateException(
            "scanPos became negative after consuming " + end + " chars. This indicates a bug.");
      }
      if (bestStart >= 0) {
        bestStart -= end;
        bestEnd -= end;
        if (bestEnd <= 0) {
          bestStart = -1;
          bestEnd = -1;
        }
      }

      results.add(isPattern ? TokenMatchingResult.pattern(tokens) : TokenMatchingResult.text(tokens));
    }

    return results;
  }

}
