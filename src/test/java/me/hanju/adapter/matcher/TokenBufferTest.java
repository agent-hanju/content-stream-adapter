package me.hanju.adapter.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * TokenBuffer 테스트
 *
 * 토큰 경계 보존 버퍼의 기본 동작을 검증합니다.
 * 버퍼는 항상 앞(시작)부터 데이터를 비워냅니다.
 */
@DisplayName("TokenBuffer 테스트")
class TokenBufferTest {

  private TokenBuffer buffer;

  @BeforeEach
  void setUp() {
    buffer = new TokenBuffer();
  }

  // ==================== 1. 생성 및 초기화 ====================

  @Nested
  @DisplayName("생성 및 초기화")
  class CreationAndInitialization {

    @Test
    @DisplayName("새 버퍼는 비어있음")
    void testNewBufferIsEmpty() {
      assertThat(buffer.isEmpty()).isTrue();
      assertThat(buffer.getTokenCount()).isZero();
      assertThat(buffer.getTotalLength()).isZero();
      assertThat(buffer.getContentAsString()).isEmpty();
    }
  }

  // ==================== 2. 잘못된 입력 처리 ====================

  @Nested
  @DisplayName("잘못된 입력 처리")
  class InvalidInputHandling {

    @Test
    @DisplayName("null 토큰 추가 시 예외 발생")
    void testAddNullToken() {
      assertThatThrownBy(() -> buffer.addToken(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("빈 문자열 토큰 추가 시 무시됨 (WARN 로그)")
    void testAddEmptyToken() {
      // LLM (e.g., DeepSeek)에서 실제로 빈 토큰이 올 수 있음
      buffer.addToken("");

      assertThat(buffer.isEmpty()).isTrue();
      assertThat(buffer.getTokenCount()).isZero();
    }

    @Test
    @DisplayName("extractUpTo - 음수 위치로 추출 시 예외 발생")
    void testExtractUpToNegative() {
      buffer.addToken("Hello");

      assertThatThrownBy(() -> buffer.extractUpTo(-5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be non-negative");
    }

    @Test
    @DisplayName("extractAsString - 음수 길이 시 예외 발생")
    void testExtractAsStringNegativeLength() {
      buffer.addToken("Hello");

      assertThatThrownBy(() -> buffer.extractAsString(-5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be non-negative");
    }
  }

  // ==================== 3. 기본 기능 ====================

  @Nested
  @DisplayName("기본 동작 - 토큰 추가")
  class BasicTokenAdditionTests {

    @Test
    @DisplayName("단일 토큰 추가")
    void testAddSingleToken() {
      buffer.addToken("Hello");

      assertThat(buffer.getTokenCount()).isEqualTo(1);
      assertThat(buffer.getTotalLength()).isEqualTo(5);
      assertThat(buffer.getContentAsString()).isEqualTo("Hello");
      assertThat(buffer.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("여러 토큰 순차 추가")
    void testAddMultipleTokens() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      assertThat(buffer.getTokenCount()).isEqualTo(3);
      assertThat(buffer.getTotalLength()).isEqualTo(11);
      assertThat(buffer.getContentAsString()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("한글 토큰 추가")
    void testAddKoreanTokens() {
      buffer.addToken("안녕");
      buffer.addToken("하세요");

      assertThat(buffer.getTokenCount()).isEqualTo(2);
      assertThat(buffer.getTotalLength()).isEqualTo(5);
      assertThat(buffer.getContentAsString()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("혼합 언어 토큰 추가")
    void testAddMixedLanguageTokens() {
      buffer.addToken("Hello ");
      buffer.addToken("세계");

      assertThat(buffer.getTokenCount()).isEqualTo(2);
      assertThat(buffer.getTotalLength()).isEqualTo(8);
      assertThat(buffer.getContentAsString()).isEqualTo("Hello 세계");
    }

    @Test
    @DisplayName("빈 문자열과 유효 토큰 혼합")
    void testMixedEmptyAndValidTokens() {
      buffer.addToken("valid");
      buffer.addToken(""); // 무시됨
      buffer.addToken("token");

      assertThat(buffer.getTokenCount()).isEqualTo(2);
      assertThat(buffer.flushAll()).containsExactly("valid", "token");
    }
  }

  @Nested
  @DisplayName("기본 동작 - extractUpTo")
  class BasicExtractUpToTests {

    @Test
    @DisplayName("정확한 토큰 경계에서 추출")
    void testExtractAtTokenBoundary() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      // "Hello" 전체 추출 (5글자)
      List<String> extracted = buffer.extractUpTo(5);

      assertThat(extracted).containsExactly("Hello");
      assertThat(buffer.getContentAsString()).isEqualTo(" world");
      assertThat(buffer.getTokenCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("여러 토큰에 걸친 추출")
    void testExtractSpanningMultipleTokens() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      // "Hello " 추출 (6글자)
      List<String> extracted = buffer.extractUpTo(6);

      assertThat(extracted).containsExactly("Hello", " ");
      assertThat(buffer.getContentAsString()).isEqualTo("world");
    }

    @Test
    @DisplayName("토큰 중간에서 분할 추출")
    void testExtractSplittingToken() {
      buffer.addToken("HelloWorld");

      // "Hello" 부분만 추출 (5글자)
      List<String> extracted = buffer.extractUpTo(5);

      assertThat(extracted).containsExactly("Hello");
      assertThat(buffer.getContentAsString()).isEqualTo("World");
      assertThat(buffer.getTokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("순차적 extractUpTo")
    void testSequentialExtractUpTo() {
      buffer.addToken("Hello world");

      // 첫 번째 추출
      List<String> first = buffer.extractUpTo(5);
      assertThat(first).containsExactly("Hello");
      assertThat(buffer.getContentAsString()).isEqualTo(" world");

      // 두 번째 추출
      List<String> second = buffer.extractUpTo(6);
      assertThat(second).containsExactly(" world");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("위치 0으로 추출 시 아무것도 추출 안 됨")
    void testExtractUpToZero() {
      buffer.addToken("Hello");

      List<String> extracted = buffer.extractUpTo(0);

      assertThat(extracted).isEmpty();
      assertThat(buffer.getContentAsString()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("버퍼 길이를 초과하는 위치로 추출")
    void testExtractUpToBeyondLength() {
      buffer.addToken("Hello");

      List<String> extracted = buffer.extractUpTo(100);

      assertThat(extracted).containsExactly("Hello");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("빈 버퍼에서 extractUpTo")
    void testExtractUpToOnEmptyBuffer() {
      List<String> extracted = buffer.extractUpTo(5);

      assertThat(extracted).isEmpty();
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("첫 글자만 추출")
    void testExtractUpToFirstCharacter() {
      buffer.addToken("Hello");

      List<String> extracted = buffer.extractUpTo(1);

      assertThat(extracted).containsExactly("H");
      assertThat(buffer.getContentAsString()).isEqualTo("ello");
    }
  }

  @Nested
  @DisplayName("기본 동작 - extractAsString")
  class BasicExtractAsStringTests {

    @Test
    @DisplayName("버퍼 앞부터 지정 길이만큼 추출")
    void testExtractAsStringFromStart() {
      buffer.addToken("Hello");
      buffer.addToken("world");

      // 앞에서부터 5글자 추출
      String extracted = buffer.extractAsString(5);

      assertThat(extracted).isEqualTo("Hello");
      assertThat(buffer.getContentAsString()).isEqualTo("world");
    }

    @Test
    @DisplayName("extractAsString 여러 번 호출")
    void testMultipleExtractAsString() {
      buffer.addToken("Hello world");

      // 첫 번째 추출
      String first = buffer.extractAsString(5);
      assertThat(first).isEqualTo("Hello");

      // 두 번째 추출 (항상 버퍼 앞부터)
      String second = buffer.extractAsString(6);
      assertThat(second).isEqualTo(" world");
    }

    @Test
    @DisplayName("0 길이")
    void testExtractAsStringZeroLength() {
      buffer.addToken("Hello");

      String extracted = buffer.extractAsString(0);

      assertThat(extracted).isEmpty();
      assertThat(buffer.getContentAsString()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("버퍼를 초과하는 길이")
    void testExtractAsStringBeyondBuffer() {
      buffer.addToken("Hello");

      String extracted = buffer.extractAsString(100);

      assertThat(extracted).isEqualTo("Hello");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("빈 버퍼에서 extractAsString")
    void testExtractAsStringOnEmptyBuffer() {
      String extracted = buffer.extractAsString(5);

      assertThat(extracted).isEmpty();
    }
  }

  @Nested
  @DisplayName("기본 동작 - flushAll")
  class BasicFlushAllTests {

    @Test
    @DisplayName("전체 버퍼 flush")
    void testFlushAll() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      List<String> flushed = buffer.flushAll();

      assertThat(flushed).containsExactly("Hello", " ", "world");
      assertThat(buffer.isEmpty()).isTrue();
      assertThat(buffer.getTotalLength()).isZero();
    }

    @Test
    @DisplayName("부분 추출 후 flush")
    void testFlushAfterPartialExtraction() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      buffer.extractUpTo(6); // "Hello " 추출

      List<String> flushed = buffer.flushAll();
      assertThat(flushed).containsExactly("world");
    }

    @Test
    @DisplayName("빈 버퍼 flush")
    void testFlushEmptyBuffer() {
      List<String> flushed = buffer.flushAll();

      assertThat(flushed).isEmpty();
    }

    @Test
    @DisplayName("여러 번 flush")
    void testMultipleFlush() {
      buffer.addToken("Hello");

      List<String> first = buffer.flushAll();
      List<String> second = buffer.flushAll();

      assertThat(first).containsExactly("Hello");
      assertThat(second).isEmpty();
    }
  }

  // ==================== 4. 엣지 케이스 ====================

  @Nested
  @DisplayName("엣지 케이스 - DeepSeek 빈 토큰 시나리오")
  class EdgeCaseDeepSeekEmptyTokens {

    @Test
    @DisplayName("연속된 빈 문자열 토큰 (DeepSeek 시나리오)")
    void testConsecutiveEmptyTokens() {
      // DeepSeek에서 첫 출력 후 5~6개 빈 문자열 전송
      buffer.addToken("first");
      buffer.addToken("");
      buffer.addToken("");
      buffer.addToken("");
      buffer.addToken("");
      buffer.addToken("");
      buffer.addToken("second");

      assertThat(buffer.getTokenCount()).isEqualTo(2);
      assertThat(buffer.flushAll()).containsExactly("first", "second");
    }
  }

  @Nested
  @DisplayName("엣지 케이스 - 한글 경계")
  class EdgeCaseKoreanBoundary {

    @Test
    @DisplayName("한글 토큰 중간에서 분할")
    void testKoreanTokenSplit() {
      // "안녕하세요" (5글자)
      buffer.addToken("안녕하세요");

      List<String> extracted = buffer.extractUpTo(3);

      assertThat(extracted).containsExactly("안녕하");
      assertThat(buffer.getContentAsString()).isEqualTo("세요");
    }

    @Test
    @DisplayName("한글 토큰 경계에서 정확히 추출")
    void testKoreanTokenBoundaryExtract() {
      buffer.addToken("안녕");
      buffer.addToken("하세요");

      List<String> extracted = buffer.extractUpTo(2);

      assertThat(extracted).containsExactly("안녕");
      assertThat(buffer.getContentAsString()).isEqualTo("하세요");
    }
  }

  // ==================== 5. 실제 사용 시나리오 ====================

  @Nested
  @DisplayName("실제 사용 시나리오 - LLM 스트리밍")
  class RealWorldLlmStreamingTests {

    @Test
    @DisplayName("vLLM 스타일 토큰 분할 - 한글")
    void testVllmStyleTokenSplitsKorean() {
      // vLLM이 한글을 토큰 단위로 분할하여 전송하는 시나리오
      buffer.addToken("안녕");
      buffer.addToken("하세요");
      buffer.addToken(" ");
      buffer.addToken("세계");

      // "안녕하세요" 추출 (5글자)
      List<String> extracted = buffer.extractUpTo(5);

      assertThat(extracted).containsExactly("안녕", "하세요");
      assertThat(buffer.getContentAsString()).isEqualTo(" 세계");
    }

    @Test
    @DisplayName("XML 태그가 여러 토큰에 분할되어 도착")
    void testXmlTagSplitAcrossTokens() {
      // XML 태그가 여러 토큰으로 쪼개져서 들어오는 시나리오
      buffer.addToken("일반 텍스트 ");
      buffer.addToken("<thi");
      buffer.addToken("nking");
      buffer.addToken(">");

      // "일반 텍스트 " 추출 (7글자)
      List<String> extracted = buffer.extractUpTo(7);

      assertThat(extracted).containsExactly("일반 텍스트 ");
      assertThat(buffer.getContentAsString()).isEqualTo("<thinking>");
    }

    @Test
    @DisplayName("패턴 검출 시나리오 - 버퍼링 후 부분 추출")
    void testPatternDetectionScenario() {
      // StreamPatternMatcher에서 패턴 검출 전까지 버퍼링하다가
      // 패턴 검출 시 패턴 이전 텍스트 추출하는 시나리오

      buffer.addToken("텍스트 ");
      buffer.addToken("내용");
      buffer.addToken("<thinking>");

      // 패턴 "<thinking>" 감지 → 패턴 이전 텍스트 추출
      // "텍스트 " (4글자) + "내용" (2글자) = 6글자
      List<String> beforePattern = buffer.extractUpTo(6);

      assertThat(beforePattern).containsExactly("텍스트 ", "내용");

      // 패턴 자체 추출
      String pattern = buffer.extractAsString(10);

      assertThat(pattern).isEqualTo("<thinking>");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("긴 컨텐츠의 점진적 처리")
    void testIncrementalProcessingOfLongContent() {
      // 긴 컨텐츠를 토큰 단위로 받으면서 점진적으로 처리
      for (int i = 0; i < 10; i++) {
        buffer.addToken("토큰" + i + " ");
      }

      int initialLength = buffer.getTotalLength();
      assertThat(buffer.getTokenCount()).isEqualTo(10);

      // 절반 추출
      buffer.extractUpTo(initialLength / 2);

      assertThat(buffer.getTokenCount()).isLessThan(10);
      assertThat(buffer.getTotalLength()).isLessThan(initialLength);
    }

    @Test
    @DisplayName("혼합 언어 스트리밍 - 영어/한글/XML")
    void testMixedLanguageStreamingWithXml() {
      // 실제 LLM 응답: 영어 + 한글 + XML 태그 혼합
      buffer.addToken("Hello ");
      buffer.addToken("안녕");
      buffer.addToken("하세요");
      buffer.addToken(" <tag");
      buffer.addToken(">");

      // "Hello " (6) + "안녕" (2) + "하세요" (3) = 11글자
      List<String> extracted = buffer.extractUpTo(11);

      assertThat(extracted).containsExactly("Hello ", "안녕", "하세요");
      assertThat(buffer.getContentAsString()).isEqualTo(" <tag>");
    }

    @Test
    @DisplayName("토큰 경계 보존 - 큰 텍스트 블록")
    void testTokenBoundaryPreservationLargeBlock() {
      // 많은 작은 토큰들이 들어와도 경계가 보존되는지 확인
      for (int i = 0; i < 100; i++) {
        buffer.addToken("토큰");
      }

      // 일부 추출 - 각 토큰은 2글자이므로 25개 토큰이 추출되어야 함
      List<String> extracted = buffer.extractUpTo(50);

      assertThat(extracted).hasSize(25);
      assertThat(extracted).allMatch(token -> token.equals("토큰"));
    }
  }

  @Nested
  @DisplayName("실제 사용 시나리오 - HybridStreamProcessor 통합")
  class RealWorldHybridProcessorIntegrationTests {

    @Test
    @DisplayName(
      "HybridStreamProcessor 사용 패턴 - extractUpTo 후 extractAsString"
    )
    void testHybridProcessorUsagePattern() {
      // HybridStreamProcessor에서 실제로 사용하는 패턴:
      // 1. 패턴 이전 텍스트를 extractUpTo로 추출
      // 2. 패턴 자체를 extractAsString(patternLength)로 추출

      buffer.addToken("일반 텍스트");
      buffer.addToken("<thinking>");
      buffer.addToken("추론 내용");

      // 1. 패턴 이전 텍스트 추출
      List<String> beforePattern = buffer.extractUpTo(6); // "일반 텍스트".length()
      assertThat(beforePattern).containsExactly("일반 텍스트");

      // 2. 패턴 추출 (버퍼 앞부터 10글자)
      String pattern = buffer.extractAsString(10);
      assertThat(pattern).isEqualTo("<thinking>");

      // 3. 남은 내용
      assertThat(buffer.getContentAsString()).isEqualTo("추론 내용");
    }

    @Test
    @DisplayName("다단계 패턴 검출 시나리오")
    void testMultiPatternDetectionScenario() {
      // 여러 패턴이 순차적으로 나타나는 시나리오
      buffer.addToken("텍스트1");
      buffer.addToken("<tag1>");
      buffer.addToken("내용1<");
      buffer.addToken("/tag1>");
      buffer.addToken("텍스트2");

      // 첫 번째 패턴 이전 텍스트
      List<String> text1 = buffer.extractUpTo(4);
      assertThat(text1).containsExactly("텍스트1");

      // 첫 번째 패턴
      String tag1Open = buffer.extractAsString(6);
      assertThat(tag1Open).isEqualTo("<tag1>");

      // 패턴 사이 텍스트
      List<String> content = buffer.extractUpTo(3);
      assertThat(content).containsExactly("내용1");

      // 두 번째 패턴
      String tag1Close = buffer.extractAsString(7);
      assertThat(tag1Close).isEqualTo("</tag1>");

      // 남은 텍스트
      assertThat(buffer.getContentAsString()).isEqualTo("텍스트2");
    }

    @Test
    @DisplayName("버퍼 오버플로우 방지 - 긴 텍스트 처리")
    void testBufferOverflowPrevention() {
      // 매우 긴 텍스트가 들어와도 토큰 단위로 처리 가능한지 확인
      for (int i = 0; i < 1000; i++) {
        buffer.addToken("긴");
      }

      // 점진적 추출
      while (!buffer.isEmpty()) {
        int currentLength = buffer.getTotalLength();
        if (currentLength > 10) {
          buffer.extractUpTo(10);
        } else {
          buffer.flushAll();
          break;
        }
      }

      assertThat(buffer.isEmpty()).isTrue();
    }
  }

  // ==================== 상태 검증 ====================

  @Nested
  @DisplayName("상태 검증")
  class StateValidationTests {

    @Test
    @DisplayName("getTotalLength - 토큰 추가 후 정확성")
    void testGetTotalLengthAccuracy() {
      buffer.addToken("Hello");
      assertThat(buffer.getTotalLength()).isEqualTo(5);

      buffer.addToken("World");
      assertThat(buffer.getTotalLength()).isEqualTo(10);
    }

    @Test
    @DisplayName("getTotalLength - extractUpTo 후 업데이트")
    void testGetTotalLengthAfterExtractUpTo() {
      buffer.addToken("HelloWorld");
      assertThat(buffer.getTotalLength()).isEqualTo(10);

      buffer.extractUpTo(5);
      assertThat(buffer.getTotalLength()).isEqualTo(5);
    }

    @Test
    @DisplayName("getTokenCount - 추가/제거 후 정확성")
    void testGetTokenCountAccuracy() {
      assertThat(buffer.getTokenCount()).isZero();

      buffer.addToken("a");
      buffer.addToken("b");
      buffer.addToken("c");
      assertThat(buffer.getTokenCount()).isEqualTo(3);

      buffer.extractUpTo(1);
      assertThat(buffer.getTokenCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("isEmpty - 초기 상태")
    void testIsEmptyInitialState() {
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("isEmpty - 추가 및 flush 후")
    void testIsEmptyAfterAddAndFlush() {
      buffer.addToken("test");
      assertThat(buffer.isEmpty()).isFalse();

      buffer.flushAll();
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("getContentAsString - 토큰 연결 확인")
    void testGetContentAsStringConcatenation() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      assertThat(buffer.getContentAsString()).isEqualTo("Hello world");
    }
  }
}
