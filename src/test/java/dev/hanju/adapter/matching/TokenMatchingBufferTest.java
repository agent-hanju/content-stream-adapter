package dev.hanju.adapter.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.buffer.TokenMatchingBuffer;
import dev.hanju.adapter.matching.AhoCorasickTrie;
import dev.hanju.adapter.matching.TokenMatchResult;

/**
 * TokenMatchingBuffer 테스트
 *
 * 실제 사용 시나리오:
 * - LLM 스트리밍 응답에서 XML 태그 실시간 감지
 * - 패턴이 아닌 텍스트는 즉시 flush (메모리 효율)
 * - 패턴 가능성이 있는 부분만 버퍼에 유지
 * - 토큰 경계 보존 (원본 분절점 유지)
 *
 * 테스트 구조:
 * 1. 생성 및 초기화
 * 2. 잘못된 입력 처리
 * 3. 패턴 매칭 (즉시 flush 동작 검증)
 * 4. 버퍼 관리 (패턴 가능성에 따른 버퍼링)
 * 5. 엣지 케이스
 * 6. 프로젝트 실제 사용 패턴 (LLM 스트리밍)
 */
@DisplayName("TokenMatchingBuffer 테스트")
class TokenMatchingBufferTest {

  /** 토큰 추가 후 매칭 결과 반환하는 헬퍼 */
  private static List<TokenMatchResult> feedAndMatch(TokenMatchingBuffer matcher, String token) {
    return matcher.accept(token);
  }

  // ==================== 1. 생성 및 초기화 ====================

  @Nested
  @DisplayName("생성 및 초기화")
  class CreationAndInitialization {

    @Test
    @DisplayName("생성자로 Matcher 생성 - 정상")
    void testConstructorCreation() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      assertThat(matcher).isNotNull();
      assertThat(matcher.flush()).isEmpty();
    }

  }

  // ==================== 2. 잘못된 입력 처리 ====================

  @Nested
  @DisplayName("잘못된 입력 처리")
  class InvalidInputHandling {

    @Test
    @DisplayName("생성자에 null Trie 전달 시 예외")
    void testConstructorWithNullTrie() {
      assertThatThrownBy(() -> new TokenMatchingBuffer(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trie는 null일 수 없습니다");
    }


    @Test
    @DisplayName("null 토큰 추가 시 예외")
    void testAddTokenWithNull() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      assertThatThrownBy(() -> feedAndMatch(matcher, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("빈 토큰 추가 시 빈 리스트 반환 (빈 문자열 매칭은 무의미)")
    void testAddTokenWithEmpty() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results = feedAndMatch(matcher, "");

      assertThat(results).isEmpty();
    }
  }

  // ==================== 3. 패턴 매칭 (즉시 flush 동작) ====================

  @Nested
  @DisplayName("패턴 매칭 - 기본 동작")
  class BasicPatternMatching {

    @Test
    @DisplayName("단일 패턴 검출 - 완전 일치 (단일 토큰)")
    void testSinglePatternExactMatch() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results = feedAndMatch(matcher, "hello");

      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.getFirst().tokens())).isEqualTo("hello");
    }

    @Test
    @DisplayName("단일 패턴 검출 - prefix는 즉시 flush됨")
    void testSinglePatternWithPrefixFlushed() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("world"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "hello "는 패턴이 아니므로 즉시 TextTokenResult로 반환
      List<TokenMatchResult> r1 = feedAndMatch(matcher, "hello ");
      assertThat(r1).hasSize(1);
      assertThat(r1.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(r1.getFirst().tokens()).containsExactly("hello ");

      // "world"가 패턴 검출, prefix는 이미 flush됨
      List<TokenMatchResult> r2 = feedAndMatch(matcher, "world");
      assertThat(r2).hasSize(1);
      assertThat(r2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r2.getFirst().tokens())).isEqualTo("world");
    }

    @Test
    @DisplayName("다중 패턴 검출 - 순차적 검출")
    void testMultiplePatternsSequential() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("he", "she"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "he" 검출
      List<TokenMatchResult> results1 = feedAndMatch(matcher, "he");
      assertThat(results1).hasSize(1);
      assertThat(results1.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results1.getFirst().tokens())).isEqualTo("he");

      // "she" 검출
      List<TokenMatchResult> results2 = feedAndMatch(matcher, "she");
      assertThat(results2).hasSize(1);
      assertThat(results2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results2.getFirst().tokens())).isEqualTo("she");
    }

    @Test
    @DisplayName("패턴 검출 - suffix 관계 (she는 he 포함, 더 긴 패턴 우선)")
    void testSuffixPatternDetection() {
      // "she"는 "he"를 suffix로 포함
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("he", "she"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results = feedAndMatch(matcher, "she");

      // "she"가 더 길므로 "she"가 우선 검출
      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.getFirst().tokens())).isEqualTo("she");
    }

    @Test
    @DisplayName("패턴이 여러 토큰에 걸쳐 있을 때 - 버퍼에 유지 후 검출")
    void testPatternAcrossTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "hel"은 "hello"의 prefix일 수 있으므로 버퍼에 유지
      List<TokenMatchResult> r1 = feedAndMatch(matcher, "hel");
      assertThat(r1).isEmpty();

      // "lo" 추가 시 "hello" 패턴 검출
      List<TokenMatchResult> r2 = feedAndMatch(matcher, "lo");
      assertThat(r2).hasSize(1);
      assertThat(r2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r2.getFirst().tokens())).isEqualTo("hello");
    }
  }

  // ==================== 4. 버퍼 관리 ====================

  @Nested
  @DisplayName("버퍼 관리")
  class BufferManagement {

    @Test
    @DisplayName("버퍼 동작 - 패턴 가능성 없으면 즉시 flush")
    void testBufferFlushWhenNoPatternPossibility() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "hello"는 "<tag>"의 prefix가 될 수 없으므로 즉시 flush
      List<TokenMatchResult> results = feedAndMatch(matcher, "hello");

      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results.getFirst().tokens()).containsExactly("hello");
      assertThat(matcher.flush()).isEmpty();
    }

    @Test
    @DisplayName("버퍼 동작 - 패턴 가능성 있으면 버퍼에 유지")
    void testBufferKeepWhenPatternPossible() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "<t"는 "<tag>"의 prefix일 수 있으므로 버퍼에 유지
      List<TokenMatchResult> results = feedAndMatch(matcher, "<t");

      assertThat(results).isEmpty();
      List<TokenMatchResult> remaining = matcher.flush();
      assertThat(remaining).hasSize(1);
      assertThat(remaining.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(remaining.getFirst().tokens()).containsExactly("<t");
    }

    @Test
    @DisplayName("flushRemaining - 남은 버퍼 모두 반환 (토큰 경계 보존)")
    void testFlushRemaining() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<complete>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "<com"은 "<complete>"의 prefix이므로 버퍼에 유지
      feedAndMatch(matcher, "<com");
      feedAndMatch(matcher, "pl"); // "<compl"도 prefix 가능성

      List<TokenMatchResult> remaining = matcher.flush();

      // 패턴 없으므로 TextTokenResult로 반환, 토큰 경계 보존
      assertThat(remaining).hasSize(1);
      assertThat(remaining.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(remaining.get(0).tokens()).containsExactly("<com", "pl");
      assertThat(matcher.flush()).isEmpty();
    }

    @Test
    @DisplayName("flush - 보류된 prefix를 텍스트로 확정")
    void testFlushPendingPrefixAsText() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      feedAndMatch(matcher, "<t");

      List<TokenMatchResult> remaining = matcher.flush();
      assertThat(remaining).hasSize(1);
      assertThat(remaining.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(remaining.getFirst().tokens()).containsExactly("<t");
    }
  }

  // ==================== 5. 엣지 케이스 ====================

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCases {

    @Test
    @DisplayName("연속된 패턴 검출")
    void testConsecutivePatterns() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("aa"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results1 = feedAndMatch(matcher, "aa");
      assertThat(results1).hasSize(1);
      assertThat(results1.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);

      List<TokenMatchResult> results2 = feedAndMatch(matcher, "aa");
      assertThat(results2).hasSize(1);
      assertThat(results2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
    }

    @Test
    @DisplayName("매우 긴 텍스트 처리")
    void testVeryLongText() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("pattern"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // 긴 텍스트 입력 - 패턴 가능성 없으므로 즉시 flush
      String longText = "a".repeat(100);
      List<TokenMatchResult> results = feedAndMatch(matcher, longText);

      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results.getFirst().tokens()).containsExactly(longText);
    }

    @Test
    @DisplayName("한글 패턴")
    void testKoreanPattern() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("안녕"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "안녕하세요" → "안녕" 검출, "하세요"는 패턴 prefix 아니므로 flush
      List<TokenMatchResult> results = feedAndMatch(matcher, "안녕하세요");

      assertThat(results).hasSize(2);
      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(0).tokens())).isEqualTo("안녕");
      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results.get(1).tokens()).containsExactly("하세요");

      assertThat(matcher.flush()).isEmpty();
    }

    @Test
    @DisplayName("특수문자 패턴")
    void testSpecialCharacters() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results = feedAndMatch(matcher, "<tag>");

      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.getFirst().tokens())).isEqualTo("<tag>");
    }
  }

  // ==================== 6. 프로젝트 실제 사용 패턴 (LLM 스트리밍) ====================

  @Nested
  @DisplayName("실제 사용 시나리오 - LLM 스트리밍")
  class LLMStreamingScenarios {

    @Test
    @DisplayName("XML 태그 검출 (단일 토큰)")
    void testXmlTagSingleToken() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<result>", "</result>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results1 = feedAndMatch(matcher, "<result>");
      assertThat(results1).hasSize(1);
      assertThat(results1.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results1.getFirst().tokens())).isEqualTo("<result>");

      feedAndMatch(matcher, "content");

      List<TokenMatchResult> results2 = feedAndMatch(matcher, "</result>");
      assertThat(results2).hasSize(1);
      assertThat(results2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results2.getFirst().tokens())).isEqualTo("</result>");
    }

    @Test
    @DisplayName("XML 태그 검출 (여러 토큰에 걸쳐)")
    void testXmlTagMultipleTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<answer>", "</answer>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // 태그가 여러 토큰에 걸쳐 입력됨 (실제 LLM 스트리밍)
      List<TokenMatchResult> r1 = feedAndMatch(matcher, "<ans");
      assertThat(r1).isEmpty();

      List<TokenMatchResult> r2 = feedAndMatch(matcher, "wer>");
      assertThat(r2).hasSize(1);
      assertThat(r2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r2.getFirst().tokens())).isEqualTo("<answer>");
    }

    @Test
    @DisplayName("실제 시나리오 시뮬레이션")
    void testRealScenario() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<thinking>", "</thinking>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // 실제 LLM 스트리밍 토큰 시뮬레이션
      List<TokenMatchResult> allResults = new ArrayList<>();
      String[] tokens = { "<think", "ing>", "Let me ", "think", "...</", "think", "ing>" };

      for (String token : tokens) {
        allResults.addAll(feedAndMatch(matcher, token));
      }

      // 두 태그 모두 검출되어야 함
      long detectedCount = allResults.stream()
          .filter(r -> r.type() == TokenMatchResult.Type.PATTERN)
          .count();
      assertThat(detectedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("복잡한 응답 (여러 태그)")
    void testComplexResponse() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of(
          "<search>", "</search>",
          "<answer>", "</answer>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // 복잡한 응답 시뮬레이션
      List<TokenMatchResult> r1 = feedAndMatch(matcher, "<search>");
      feedAndMatch(matcher, "query");
      List<TokenMatchResult> r2 = feedAndMatch(matcher, "</search>");
      List<TokenMatchResult> r3 = feedAndMatch(matcher, "<answer>");
      feedAndMatch(matcher, "response");
      List<TokenMatchResult> r4 = feedAndMatch(matcher, "</answer>");

      // 4개 태그 모두 검출 확인
      assertThat(r1).hasSize(1);
      assertThat(r1.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r1.getFirst().tokens())).isEqualTo("<search>");

      assertThat(r2).hasSize(1);
      assertThat(r2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r2.getFirst().tokens())).isEqualTo("</search>");

      assertThat(r3).hasSize(1);
      assertThat(r3.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r3.getFirst().tokens())).isEqualTo("<answer>");

      assertThat(r4).hasSize(1);
      assertThat(r4.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r4.getFirst().tokens())).isEqualTo("</answer>");
    }

    @Test
    @DisplayName("중첩된 태그 처리")
    void testNestedTags() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<outer>", "<inner>", "</inner>", "</outer>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      feedAndMatch(matcher, "<outer>");
      feedAndMatch(matcher, "<inner>");
      feedAndMatch(matcher, "content");
      feedAndMatch(matcher, "</inner>");
      List<TokenMatchResult> results = feedAndMatch(matcher, "</outer>");

      // 마지막 태그 검출 확인
      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.getFirst().tokens())).isEqualTo("</outer>");
    }

    @Test
    @DisplayName("매우 작은 토큰 (한 글자씩)")
    void testSingleCharTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // 한 글자씩 입력
      feedAndMatch(matcher, "<");
      feedAndMatch(matcher, "t");
      feedAndMatch(matcher, "a");
      feedAndMatch(matcher, "g");
      List<TokenMatchResult> results = feedAndMatch(matcher, ">");

      // 마지막 토큰에서 패턴 검출
      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.getFirst().tokens())).isEqualTo("<tag>");
    }

    @Test
    @DisplayName("토큰 경계 보존 확인")
    void testTokenBoundaryPreservation() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<start>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // 일반 텍스트가 여러 토큰으로 입력됨
      List<TokenMatchResult> r1 = feedAndMatch(matcher, "Hello");
      List<TokenMatchResult> r2 = feedAndMatch(matcher, " ");
      List<TokenMatchResult> r3 = feedAndMatch(matcher, "World");

      // 토큰 경계가 보존되어 반환되어야 함
      assertThat(r1).hasSize(1);
      assertThat(r1.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(r1.getFirst().tokens()).containsExactly("Hello");

      assertThat(r2).hasSize(1);
      assertThat(r2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(r2.getFirst().tokens()).containsExactly(" ");

      assertThat(r3).hasSize(1);
      assertThat(r3.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(r3.getFirst().tokens()).containsExactly("World");
    }

    @Test
    @DisplayName("패턴 앞 텍스트 보존 - 패턴 검출 시 prefix 반환")
    void testPrefixPreservation() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "텍스트"와 "<tag>"가 한 토큰에 함께 입력됨 → 두 결과가 한번에 반환
      List<TokenMatchResult> results = feedAndMatch(matcher, "텍스트<tag>");

      assertThat(results).hasSize(2);
      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results.get(0).tokens()).containsExactly("텍스트");
      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(1).tokens())).isEqualTo("<tag>");
    }

    @Test
    @DisplayName("여러 토큰에 걸친 prefix와 패턴")
    void testPrefixAcrossMultipleTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "텍스트1"과 "텍스트2"는 패턴이 아니므로 flush
      List<TokenMatchResult> r1 = feedAndMatch(matcher, "텍스트1");
      assertThat(r1).hasSize(1);
      assertThat(r1.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);

      List<TokenMatchResult> r2 = feedAndMatch(matcher, "텍스트2");
      assertThat(r2).hasSize(1);
      assertThat(r2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);

      // "<tag>" 검출
      List<TokenMatchResult> r3 = feedAndMatch(matcher, "<tag>");
      assertThat(r3).hasSize(1);
      assertThat(r3.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
    }

    @Test
    @DisplayName("패턴 뒤 텍스트도 함께 처리")
    void testSuffixAfterPattern() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "<tag>"와 "텍스트"가 한 토큰에 함께 입력됨 → 두 결과 모두 반환
      List<TokenMatchResult> results = feedAndMatch(matcher, "<tag>텍스트");

      // 패턴과 텍스트 모두 반환
      assertThat(results).hasSize(2);
      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results.get(1).tokens()).containsExactly("텍스트");
    }
  }

  // ==================== 7. Prefix 관계 패턴 (Greedy Matching) ====================

  @Nested
  @DisplayName("Prefix 관계 패턴 - Greedy Matching")
  class PrefixPatternGreedyMatching {

    @Test
    @DisplayName("더 긴 패턴 완성 - 'shedd' 검출")
    void testLongerPatternCompleted() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("she", "shedd"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "shedd" 완성 → 더 긴 패턴 "shedd" 검출
      List<TokenMatchResult> results = feedAndMatch(matcher, "shedd");

      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.getFirst().tokens())).isEqualTo("shedd");
    }

    @Test
    @DisplayName("더 긴 패턴 미완성 - 'she' 검출 후 나머지 flush")
    void testLongerPatternNotCompleted() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("she", "shedd"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "shedg" - "shedd" 미완성, "she" 검출, "dg"는 패턴 prefix 아니므로 flush
      List<TokenMatchResult> results = feedAndMatch(matcher, "shedg");

      assertThat(results).hasSize(2);
      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(0).tokens())).isEqualTo("she");
      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results.get(1).tokens()).containsExactly("dg");

      assertThat(matcher.flush()).isEmpty();
    }

    @Test
    @DisplayName("분할 입력 - 'shed' + 'd' → 'shedd' 검출")
    void testSplitInputCompleted() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("she", "shedd"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "shed" 입력 - "she" 발견하지만 대기
      List<TokenMatchResult> results1 = feedAndMatch(matcher, "shed");
      assertThat(results1).isEmpty();

      // "d" 추가 - "shedd" 완성
      List<TokenMatchResult> results2 = feedAndMatch(matcher, "d");
      assertThat(results2).hasSize(1);
      assertThat(results2.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results2.getFirst().tokens())).isEqualTo("shedd");
    }

    @Test
    @DisplayName("분할 입력 - 'shed' + 'g' → 'she' 검출 + 'dg' flush")
    void testSplitInputNotCompleted() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("she", "shedd"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "shed" 입력 - "she" 발견하지만 대기
      List<TokenMatchResult> results1 = feedAndMatch(matcher, "shed");
      assertThat(results1).isEmpty();

      // "g" 추가 - 전이 실패, "she" 반환, "dg"는 패턴 prefix 아니므로 flush
      List<TokenMatchResult> results2 = feedAndMatch(matcher, "g");
      assertThat(results2).hasSize(2);
      assertThat(results2.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results2.get(0).tokens())).isEqualTo("she");
      assertThat(results2.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results2.get(1).tokens()).containsExactly("d", "g");

      assertThat(matcher.flush()).isEmpty();
    }

    @Test
    @DisplayName("3단계 prefix 관계 - 'a', 'ab', 'abc'")
    void testThreeLevelPrefix() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("a", "ab", "abc"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "abc" 완성 → 가장 긴 패턴 검출
      List<TokenMatchResult> results = feedAndMatch(matcher, "abc");

      assertThat(results).hasSize(1);
      assertThat(results.getFirst()).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.getFirst().tokens())).isEqualTo("abc");
    }

    @Test
    @DisplayName("3단계 prefix 중간 실패 - 'a', 'ab', 'abc'에서 'abx' 입력")
    void testThreeLevelPrefixPartial() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("a", "ab", "abc"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // "abx" → "ab" 검출, "x"는 패턴 prefix 아니므로 flush
      List<TokenMatchResult> results = feedAndMatch(matcher, "abx");

      assertThat(results).hasSize(2);
      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(0).tokens())).isEqualTo("ab");
      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(results.get(1).tokens()).containsExactly("x");

      assertThat(matcher.flush()).isEmpty();
    }
  }

  // ==================== 8. Maximal Munch 버그 재현 테스트 ====================

  @Nested
  @DisplayName("Maximal Munch 버그 재현 테스트")
  class MaximalMunchBugReproduction {

    @Test
    @DisplayName("Bug #1: failure link를 통한 매칭이 이전 매칭을 덮어쓰면 안 됨")
    void testFailureLinkShouldNotOverwriteEarlierMatch() {
      // 패턴: "aa", "aaab"
      // 입력: "aaac"
      //
      // pos=0 'a' → state='a', 매칭 없음
      // pos=1 'a' → state='aa', output="aa" → bestMatch=("aa", 0, 2)
      // pos=2 'a' → state='aaa', failure → 'aa', output="aa"
      //          → 여기서 ("aa", 1, 3)으로 덮어쓰면 안 됨! 위치 0이 더 이름
      // pos=3 'c' → 직접전이 불가, confirm ("aa", 0, 2)
      //
      // 기대: "aa" PATTERN + "ac" TEXT
      // 버그 시: "a" TEXT + "aa" PATTERN + "c" 잔여

      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("aa", "aaab"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results = new ArrayList<>(matcher.accept("aaac"));
      results.addAll(matcher.flush());

      assertThat(results).hasSize(2);

      // 첫 번째: "aa" 패턴
      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(0).tokens())).isEqualTo("aa");

      // 두 번째: "ac" 텍스트
      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(String.join("", results.get(1).tokens())).isEqualTo("ac");
    }

    @Test
    @DisplayName("Bug #1 변형: 더 긴 패턴이 실패할 때 이른 위치의 짧은 패턴 선택")
    void testEarlierPositionWinsOverLaterLongerMatch() {
      // 패턴: "ab", "bcd"
      // 입력: "abce"
      //
      // 기대: "ab" PATTERN + "ce" TEXT
      // (위치 0의 "ab"가 위치 1의 "bcd" 가능성보다 우선)

      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("ab", "bcd"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results = new ArrayList<>(matcher.accept("abce"));
      results.addAll(matcher.flush());

      assertThat(results).hasSize(2);

      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(0).tokens())).isEqualTo("ab");

      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(String.join("", results.get(1).tokens())).isEqualTo("ce");
    }

    @Test
    @DisplayName("Bug #1 변형: 같은 위치면 더 긴 패턴 선택")
    void testSamePositionLongerPatternWins() {
      // 패턴: "a", "ab", "abc"
      // 입력: "abcd"
      //
      // 기대: "abc" PATTERN + "d" TEXT
      // (같은 위치 0에서 시작하는 패턴 중 가장 긴 것 선택)

      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("a", "ab", "abc"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      List<TokenMatchResult> results = new ArrayList<>(matcher.accept("abcd"));
      results.addAll(matcher.flush());

      assertThat(results).hasSize(2);

      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(0).tokens())).isEqualTo("abc");

      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(String.join("", results.get(1).tokens())).isEqualTo("d");
    }

    @Test
    @DisplayName("maxPatternLength 안전장치 테스트")
    void testMaxPatternLengthSafetyGuard() {
      // 패턴: "ab", "abc" (maxPatternLength=3)
      // 입력: "abcx"
      //
      // pos=2 'c' → bestMatch=("abc", 0, 3), scanPos - start = 3 >= maxLen → 확정

      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("ab", "abc"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      assertThat(trie.getMaxPatternLength()).isEqualTo(3);

      List<TokenMatchResult> results = new ArrayList<>(matcher.accept("abcx"));
      results.addAll(matcher.flush());

      assertThat(results).hasSize(2);

      assertThat(results.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", results.get(0).tokens())).isEqualTo("abc");

      assertThat(results.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(String.join("", results.get(1).tokens())).isEqualTo("x");
    }

    @Test
    @DisplayName("스트리밍 환경에서 상태 보존 테스트")
    void testStatePreservationAcrossTokens() {
      // 여러 토큰에 걸쳐 AC 상태가 올바르게 보존되는지 확인

      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc", "abcd"));
      TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

      // 토큰 1: "ab" - 버퍼에 유지
      List<TokenMatchResult> r1 = feedAndMatch(matcher, "ab");
      assertThat(r1).isEmpty();

      // 토큰 2: "c" - "abc" 완성되지만 "abcd" 가능성 대기
      List<TokenMatchResult> r2 = feedAndMatch(matcher, "c");
      assertThat(r2).isEmpty();

      // 토큰 3: "x" - "abcd" 불가, "abc" 확정
      List<TokenMatchResult> r3 = feedAndMatch(matcher, "x");
      assertThat(r3).hasSize(2);

      assertThat(r3.get(0)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", r3.get(0).tokens())).isEqualTo("abc");

      assertThat(r3.get(1)).extracting(TokenMatchResult::type).isEqualTo(TokenMatchResult.Type.TEXT);
      assertThat(String.join("", r3.get(1).tokens())).isEqualTo("x");
    }

    @Test
    @DisplayName("같은 Trie를 여러 버퍼가 독립적으로 공유")
    void testSharedTrieAcrossBuffers() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc"));
      TokenMatchingBuffer first = new TokenMatchingBuffer(trie);
      TokenMatchingBuffer second = new TokenMatchingBuffer(trie);

      assertThat(first.accept("ab")).isEmpty();

      List<TokenMatchResult> secondResults = new ArrayList<>(second.accept("abc"));
      secondResults.addAll(second.flush());

      assertThat(secondResults).hasSize(1);
      assertThat(secondResults.get(0)).extracting(TokenMatchResult::type)
          .isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", secondResults.get(0).tokens())).isEqualTo("abc");

      List<TokenMatchResult> firstResults = new ArrayList<>(first.accept("c"));
      firstResults.addAll(first.flush());

      assertThat(firstResults).hasSize(1);
      assertThat(firstResults.get(0)).extracting(TokenMatchResult::type)
          .isEqualTo(TokenMatchResult.Type.PATTERN);
      assertThat(String.join("", firstResults.get(0).tokens())).isEqualTo("abc");
    }
  }
}
