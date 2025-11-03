package me.hanju.adapter.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import me.hanju.adapter.payload.MatchResult;

/**
 * StreamPatternMatcher 테스트
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
@DisplayName("StreamPatternMatcher 테스트")
class StreamPatternMatcherTest {

  // ==================== 1. 생성 및 초기화 ====================

  @Nested
  @DisplayName("생성 및 초기화")
  class CreationAndInitialization {

    @Test
    @DisplayName("생성자로 Matcher 생성 - 정상")
    void testConstructorCreation() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      assertThat(matcher).isNotNull();
      assertThat(matcher.getTrie()).isEqualTo(trie);
      assertThat(matcher.getBufferSize()).isZero();
    }

    @Test
    @DisplayName("생성자로 Matcher 생성 - 버퍼 크기 제한 지정")
    void testConstructorWithBufferLimit() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie, 100);

      assertThat(matcher).isNotNull();
      assertThat(matcher.getTrie()).isEqualTo(trie);
    }
  }

  // ==================== 2. 잘못된 입력 처리 ====================

  @Nested
  @DisplayName("잘못된 입력 처리")
  class InvalidInputHandling {

    @Test
    @DisplayName("생성자에 null Trie 전달 시 예외")
    void testConstructorWithNullTrie() {
      assertThatThrownBy(() -> new StreamPatternMatcher(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trie는 null일 수 없습니다");
    }

    @Test
    @DisplayName("생성자에 null Trie와 버퍼 크기 전달 시 예외")
    void testConstructorWithNullTrieAndBufferLimit() {
      assertThatThrownBy(() -> new StreamPatternMatcher(null, 100))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Trie는 null일 수 없습니다");
    }

    @Test
    @DisplayName("null 토큰 추가 시 예외")
    void testAddTokenWithNull() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      assertThatThrownBy(() -> matcher.addTokenAndGetResult(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("토큰은 null일 수 없습니다");
    }

    @Test
    @DisplayName("빈 토큰 추가 시 NoMatch 반환 (빈 문자열 매칭은 무의미)")
    void testAddTokenWithEmpty() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      MatchResult result = matcher.addTokenAndGetResult("");

      assertThat(result).isInstanceOf(MatchResult.NoMatchResult.class);
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
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      MatchResult result = matcher.addTokenAndGetResult("hello");

      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("hello");
      assertThat(detected.prevTokens()).isEmpty();
    }

    @Test
    @DisplayName("단일 패턴 검출 - prefix는 즉시 flush됨")
    void testSinglePatternWithPrefixFlushed() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("world"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "hello "는 패턴이 아니므로 즉시 TextChunks로 반환
      MatchResult r1 = matcher.addTokenAndGetResult("hello ");
      assertThat(r1).isInstanceOf(MatchResult.TokenMatchResult.class);
      assertThat(((MatchResult.TokenMatchResult) r1).tokens()).containsExactly("hello ");

      // "world"가 패턴 검출, prefix는 이미 flush됨
      MatchResult r2 = matcher.addTokenAndGetResult("world");
      assertThat(r2).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) r2;
      assertThat(detected.pattern()).isEqualTo("world");
      assertThat(detected.prevTokens()).isEmpty(); // 이미 flush됨
    }

    @Test
    @DisplayName("다중 패턴 검출 - 순차적 검출")
    void testMultiplePatternsSequential() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("he", "she"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "he" 검출
      MatchResult result1 = matcher.addTokenAndGetResult("he");
      assertThat(result1).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) result1).pattern()).isEqualTo("he");

      // "she" 검출
      MatchResult result2 = matcher.addTokenAndGetResult("she");
      assertThat(result2).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) result2).pattern()).isEqualTo("she");
    }

    @Test
    @DisplayName("패턴 검출 - suffix 관계 (she는 he 포함, 더 긴 패턴 우선)")
    void testSuffixPatternDetection() {
      // "she"는 "he"를 suffix로 포함
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("he", "she"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      MatchResult result = matcher.addTokenAndGetResult("she");

      // "she"가 더 길므로 "she"가 우선 검출
      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("she");
    }

    @Test
    @DisplayName("패턴이 여러 토큰에 걸쳐 있을 때 - 버퍼에 유지 후 검출")
    void testPatternAcrossTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "hel"은 "hello"의 prefix일 수 있으므로 버퍼에 유지
      MatchResult r1 = matcher.addTokenAndGetResult("hel");
      assertThat(r1).isInstanceOf(MatchResult.NoMatchResult.class);
      assertThat(matcher.getBufferContent()).isEqualTo("hel");

      // "lo" 추가 시 "hello" 패턴 검출
      MatchResult r2 = matcher.addTokenAndGetResult("lo");
      assertThat(r2).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) r2;
      assertThat(detected.pattern()).isEqualTo("hello");
      assertThat(detected.prevTokens()).isEmpty();
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
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "hello"는 "<tag>"의 prefix가 될 수 없으므로 즉시 flush
      MatchResult result = matcher.addTokenAndGetResult("hello");

      assertThat(result).isInstanceOf(MatchResult.TokenMatchResult.class);
      assertThat(((MatchResult.TokenMatchResult) result).tokens()).containsExactly("hello");
      assertThat(matcher.getBufferSize()).isZero();
    }

    @Test
    @DisplayName("버퍼 동작 - 패턴 가능성 있으면 버퍼에 유지")
    void testBufferKeepWhenPatternPossible() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "<t"는 "<tag>"의 prefix일 수 있으므로 버퍼에 유지
      MatchResult result = matcher.addTokenAndGetResult("<t");

      assertThat(result).isInstanceOf(MatchResult.NoMatchResult.class);
      assertThat(matcher.getBufferSize()).isGreaterThan(0);
      assertThat(matcher.getBufferContent()).isEqualTo("<t");
    }

    @Test
    @DisplayName("flushRemaining - 남은 버퍼 모두 반환 (토큰 경계 보존)")
    void testFlushRemaining() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<complete>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "<com"은 "<complete>"의 prefix이므로 버퍼에 유지
      matcher.addTokenAndGetResult("<com");
      matcher.addTokenAndGetResult("pl"); // "<compl"도 prefix 가능성

      assertThat(matcher.getBufferSize()).isGreaterThan(0);

      List<String> remaining = matcher.flushRemaining();

      // 토큰 경계가 보존되어 반환
      assertThat(remaining).containsExactly("<com", "pl");
      assertThat(matcher.getBufferSize()).isZero();
    }

    @Test
    @DisplayName("reset - 버퍼 초기화")
    void testReset() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      matcher.addTokenAndGetResult("<t");
      assertThat(matcher.getBufferSize()).isGreaterThan(0);

      matcher.reset();

      assertThat(matcher.getBufferSize()).isZero();
      assertThat(matcher.getBufferContent()).isEmpty();
    }

    @Test
    @DisplayName("getBufferContent / getBufferSize - 버퍼 상태 확인")
    void testBufferStateQuery() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      assertThat(matcher.getBufferSize()).isZero();
      assertThat(matcher.getBufferContent()).isEmpty();

      matcher.addTokenAndGetResult("<t");

      assertThat(matcher.getBufferSize()).isEqualTo(2);
      assertThat(matcher.getBufferContent()).isEqualTo("<t");
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
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      MatchResult result1 = matcher.addTokenAndGetResult("aa");
      assertThat(result1).isInstanceOf(MatchResult.PatternMatchResult.class);

      MatchResult result2 = matcher.addTokenAndGetResult("aa");
      assertThat(result2).isInstanceOf(MatchResult.PatternMatchResult.class);
    }

    @Test
    @DisplayName("매우 긴 텍스트 (버퍼 오버플로우 방지)")
    void testVeryLongText() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("pattern"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie, 50); // 작은 버퍼

      // 버퍼 크기보다 훨씬 큰 텍스트 입력
      String longText = "a".repeat(100);
      MatchResult result = matcher.addTokenAndGetResult(longText);

      // 오버플로우 방지를 위해 일부가 flush되어야 함
      assertThat(result).isInstanceOf(MatchResult.TokenMatchResult.class);
    }

    @Test
    @DisplayName("한글 패턴")
    void testKoreanPattern() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("안녕"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      MatchResult result = matcher.addTokenAndGetResult("안녕하세요");

      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("안녕");
    }

    @Test
    @DisplayName("특수문자 패턴")
    void testSpecialCharacters() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      MatchResult result = matcher.addTokenAndGetResult("<tag>");

      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("<tag>");
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
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      MatchResult result1 = matcher.addTokenAndGetResult("<result>");
      assertThat(result1).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) result1).pattern()).isEqualTo("<result>");

      matcher.addTokenAndGetResult("content");

      MatchResult result2 = matcher.addTokenAndGetResult("</result>");
      assertThat(result2).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) result2).pattern()).isEqualTo("</result>");
    }

    @Test
    @DisplayName("XML 태그 검출 (여러 토큰에 걸쳐)")
    void testXmlTagMultipleTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<answer>", "</answer>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // 태그가 여러 토큰에 걸쳐 입력됨 (실제 LLM 스트리밍)
      MatchResult r1 = matcher.addTokenAndGetResult("<ans");
      assertThat(r1).isInstanceOf(MatchResult.NoMatchResult.class);

      MatchResult r2 = matcher.addTokenAndGetResult("wer>");
      assertThat(r2).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) r2).pattern()).isEqualTo("<answer>");
    }

    @Test
    @DisplayName("실제 시나리오 시뮬레이션")
    void testRealScenario() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<thinking>", "</thinking>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // 실제 LLM 스트리밍 토큰 시뮬레이션
      List<MatchResult> results = new ArrayList<>();
      String[] tokens = { "<think", "ing>", "Let me ", "think", "...</", "think", "ing>" };

      for (String token : tokens) {
        results.add(matcher.addTokenAndGetResult(token));
      }

      // 두 태그 모두 검출되어야 함
      long detectedCount = results.stream()
          .filter(r -> r instanceof MatchResult.PatternMatchResult)
          .count();
      assertThat(detectedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("복잡한 응답 (여러 태그)")
    void testComplexResponse() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of(
          "<search>", "</search>",
          "<answer>", "</answer>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // 복잡한 응답 시뮬레이션
      MatchResult r1 = matcher.addTokenAndGetResult("<search>");
      matcher.addTokenAndGetResult("query");
      MatchResult r2 = matcher.addTokenAndGetResult("</search>");
      MatchResult r3 = matcher.addTokenAndGetResult("<answer>");
      matcher.addTokenAndGetResult("response");
      MatchResult r4 = matcher.addTokenAndGetResult("</answer>");

      // 4개 태그 모두 검출 확인
      assertThat(r1).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) r1).pattern()).isEqualTo("<search>");

      assertThat(r2).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) r2).pattern()).isEqualTo("</search>");

      assertThat(r3).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) r3).pattern()).isEqualTo("<answer>");

      assertThat(r4).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) r4).pattern()).isEqualTo("</answer>");
    }

    @Test
    @DisplayName("중첩된 태그 처리")
    void testNestedTags() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<outer>", "<inner>", "</inner>", "</outer>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      matcher.addTokenAndGetResult("<outer>");
      matcher.addTokenAndGetResult("<inner>");
      matcher.addTokenAndGetResult("content");
      matcher.addTokenAndGetResult("</inner>");
      MatchResult result = matcher.addTokenAndGetResult("</outer>");

      // 마지막 태그 검출 확인
      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) result).pattern()).isEqualTo("</outer>");
    }

    @Test
    @DisplayName("매우 작은 토큰 (한 글자씩)")
    void testSingleCharTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // 한 글자씩 입력
      matcher.addTokenAndGetResult("<");
      matcher.addTokenAndGetResult("t");
      matcher.addTokenAndGetResult("a");
      matcher.addTokenAndGetResult("g");
      MatchResult result = matcher.addTokenAndGetResult(">");

      // 마지막 토큰에서 패턴 검출
      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) result).pattern()).isEqualTo("<tag>");
    }

    @Test
    @DisplayName("토큰 경계 보존 확인")
    void testTokenBoundaryPreservation() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<start>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // 일반 텍스트가 여러 토큰으로 입력됨
      MatchResult r1 = matcher.addTokenAndGetResult("Hello");
      MatchResult r2 = matcher.addTokenAndGetResult(" ");
      MatchResult r3 = matcher.addTokenAndGetResult("World");

      // 토큰 경계가 보존되어 반환되어야 함
      assertThat(r1).isInstanceOf(MatchResult.TokenMatchResult.class);
      assertThat(((MatchResult.TokenMatchResult) r1).tokens()).containsExactly("Hello");

      assertThat(r2).isInstanceOf(MatchResult.TokenMatchResult.class);
      assertThat(((MatchResult.TokenMatchResult) r2).tokens()).containsExactly(" ");

      assertThat(r3).isInstanceOf(MatchResult.TokenMatchResult.class);
      assertThat(((MatchResult.TokenMatchResult) r3).tokens()).containsExactly("World");
    }

    @Test
    @DisplayName("패턴 앞 텍스트 보존 - 패턴 검출 시 prefix 반환")
    void testPrefixPreservation() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "텍스트"와 "<tag>"가 한 토큰에 함께 입력됨
      MatchResult result = matcher.addTokenAndGetResult("텍스트<tag>");

      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("<tag>");
      assertThat(detected.prevTokens()).containsExactly("텍스트");
    }

    @Test
    @DisplayName("여러 토큰에 걸친 prefix와 패턴")
    void testPrefixAcrossMultipleTokens() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "텍스트1"과 "텍스트2"는 패턴이 아니므로 flush
      MatchResult r1 = matcher.addTokenAndGetResult("텍스트1");
      assertThat(r1).isInstanceOf(MatchResult.TokenMatchResult.class);

      MatchResult r2 = matcher.addTokenAndGetResult("텍스트2");
      assertThat(r2).isInstanceOf(MatchResult.TokenMatchResult.class);

      // "<tag>" 검출
      MatchResult r3 = matcher.addTokenAndGetResult("<tag>");
      assertThat(r3).isInstanceOf(MatchResult.PatternMatchResult.class);
      assertThat(((MatchResult.PatternMatchResult) r3).prevTokens()).isEmpty();
    }

    @Test
    @DisplayName("패턴 뒤 텍스트는 다음 호출에서 처리")
    void testSuffixAfterPattern() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "<tag>"와 "텍스트"가 한 토큰에 함께 입력됨
      MatchResult r1 = matcher.addTokenAndGetResult("<tag>텍스트");

      // 패턴만 먼저 반환
      assertThat(r1).isInstanceOf(MatchResult.PatternMatchResult.class);

      // "텍스트"는 다음 입력 시 처리됨
      // 추가 입력이 없으면 flushRemaining으로 가져와야 함
      List<String> remaining = matcher.flushRemaining();
      assertThat(remaining).containsExactly("텍스트");
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
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "shedd" 완성 → 더 긴 패턴 "shedd" 검출
      MatchResult result = matcher.addTokenAndGetResult("shedd");

      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("shedd");
      assertThat(detected.prevTokens()).isEmpty();
    }

    @Test
    @DisplayName("더 긴 패턴 미완성 - 'she' 검출 후 나머지 처리")
    void testLongerPatternNotCompleted() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("she", "shedd"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "shedg" - "shedd" 미완성, "she" 검출
      MatchResult result1 = matcher.addTokenAndGetResult("shedg");

      assertThat(result1).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result1;
      assertThat(detected.pattern()).isEqualTo("she");

      // "dg"는 버퍼에 남음
      assertThat(matcher.getBufferContent()).isEqualTo("dg");
    }

    @Test
    @DisplayName("분할 입력 - 'shed' + 'd' → 'shedd' 검출")
    void testSplitInputCompleted() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("she", "shedd"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "shed" 입력 - "she" 발견하지만 대기
      MatchResult result1 = matcher.addTokenAndGetResult("shed");
      assertThat(result1).isInstanceOf(MatchResult.NoMatchResult.class);
      assertThat(matcher.getBufferContent()).isEqualTo("shed");

      // "d" 추가 - "shedd" 완성
      MatchResult result2 = matcher.addTokenAndGetResult("d");
      assertThat(result2).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result2;
      assertThat(detected.pattern()).isEqualTo("shedd");
    }

    @Test
    @DisplayName("분할 입력 - 'shed' + 'g' → 'she' 검출 + 'dg' 남음")
    void testSplitInputNotCompleted() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("she", "shedd"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "shed" 입력 - "she" 발견하지만 대기
      MatchResult result1 = matcher.addTokenAndGetResult("shed");
      assertThat(result1).isInstanceOf(MatchResult.NoMatchResult.class);

      // "g" 추가 - 전이 실패, "she" 반환
      MatchResult result2 = matcher.addTokenAndGetResult("g");
      assertThat(result2).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result2;
      assertThat(detected.pattern()).isEqualTo("she");

      // "dg"는 버퍼에 남음
      assertThat(matcher.getBufferContent()).isEqualTo("dg");
    }

    @Test
    @DisplayName("3단계 prefix 관계 - 'a', 'ab', 'abc'")
    void testThreeLevelPrefix() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("a", "ab", "abc"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "abc" 완성 → 가장 긴 패턴 검출
      MatchResult result = matcher.addTokenAndGetResult("abc");

      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("abc");
    }

    @Test
    @DisplayName("3단계 prefix 중간 실패 - 'a', 'ab', 'abc'에서 'abx' 입력")
    void testThreeLevelPrefixPartial() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("a", "ab", "abc"));
      StreamPatternMatcher matcher = new StreamPatternMatcher(trie);

      // "abx" → "ab" 검출
      MatchResult result = matcher.addTokenAndGetResult("abx");

      assertThat(result).isInstanceOf(MatchResult.PatternMatchResult.class);
      MatchResult.PatternMatchResult detected = (MatchResult.PatternMatchResult) result;
      assertThat(detected.pattern()).isEqualTo("ab");

      // "x"는 버퍼에 남음
      assertThat(matcher.getBufferContent()).isEqualTo("x");
    }
  }
}
