package dev.hanju.adapter.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * StreamingBuffer 테스트
 *
 * 토큰 경계 보존 버퍼의 기본 동작을 검증합니다.
 * 버퍼는 항상 앞(시작)부터 데이터를 비워냅니다.
 */
@DisplayName("StreamingBuffer 테스트")
class StreamingBufferTest {

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
      assertThat(buffer.getContent()).isEmpty();
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
    }

    @Test
    @DisplayName("extract - 음수 위치로 추출 시 예외 발생")
    void testExtractNegative() {
      buffer.addToken("Hello");

      assertThatThrownBy(() -> buffer.extract(-5))
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

      assertThat(buffer.getContent()).isEqualTo("Hello");
      assertThat(buffer.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("여러 토큰 순차 추가")
    void testAddMultipleTokens() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      assertThat(buffer.getContent()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("한글 토큰 추가")
    void testAddKoreanTokens() {
      buffer.addToken("안녕");
      buffer.addToken("하세요");

      assertThat(buffer.getContent()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("혼합 언어 토큰 추가")
    void testAddMixedLanguageTokens() {
      buffer.addToken("Hello ");
      buffer.addToken("세계");

      assertThat(buffer.getContent()).isEqualTo("Hello 세계");
    }

    @Test
    @DisplayName("빈 문자열과 유효 토큰 혼합")
    void testMixedEmptyAndValidTokens() {
      buffer.addToken("valid");
      buffer.addToken(""); // 무시됨
      buffer.addToken("token");

      assertThat(buffer.flush()).containsExactly("valid", "token");
    }
  }

  @Nested
  @DisplayName("기본 동작 - extract")
  class BasicExtractTests {

    @Test
    @DisplayName("정확한 토큰 경계에서 추출")
    void testExtractAtTokenBoundary() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      List<String> extracted = buffer.extract(5);

      assertThat(extracted).containsExactly("Hello");
      assertThat(buffer.getContent()).isEqualTo(" world");
    }

    @Test
    @DisplayName("여러 토큰에 걸친 추출")
    void testExtractSpanningMultipleTokens() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      List<String> extracted = buffer.extract(6);

      assertThat(extracted).containsExactly("Hello", " ");
      assertThat(buffer.getContent()).isEqualTo("world");
    }

    @Test
    @DisplayName("토큰 중간에서 분할 추출")
    void testExtractSplittingToken() {
      buffer.addToken("HelloWorld");

      List<String> extracted = buffer.extract(5);

      assertThat(extracted).containsExactly("Hello");
      assertThat(buffer.getContent()).isEqualTo("World");
    }

    @Test
    @DisplayName("순차적 extract")
    void testSequentialExtract() {
      buffer.addToken("Hello world");

      List<String> first = buffer.extract(5);
      assertThat(first).containsExactly("Hello");
      assertThat(buffer.getContent()).isEqualTo(" world");

      List<String> second = buffer.extract(6);
      assertThat(second).containsExactly(" world");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("위치 0으로 추출 시 아무것도 추출 안 됨")
    void testExtractZero() {
      buffer.addToken("Hello");

      List<String> extracted = buffer.extract(0);

      assertThat(extracted).isEmpty();
      assertThat(buffer.getContent()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("버퍼 길이를 초과하는 위치로 추출")
    void testExtractBeyondLength() {
      buffer.addToken("Hello");

      List<String> extracted = buffer.extract(100);

      assertThat(extracted).containsExactly("Hello");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("빈 버퍼에서 extract")
    void testExtractOnEmptyBuffer() {
      List<String> extracted = buffer.extract(5);

      assertThat(extracted).isEmpty();
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("첫 글자만 추출")
    void testExtractFirstCharacter() {
      buffer.addToken("Hello");

      List<String> extracted = buffer.extract(1);

      assertThat(extracted).containsExactly("H");
      assertThat(buffer.getContent()).isEqualTo("ello");
    }
  }

  @Nested
  @DisplayName("기본 동작 - flush")
  class BasicFlushTests {

    @Test
    @DisplayName("전체 버퍼 flush")
    void testFlushAll() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      List<String> flushed = buffer.flush();

      assertThat(flushed).containsExactly("Hello", " ", "world");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("부분 추출 후 flush")
    void testFlushAfterPartialExtraction() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      buffer.extract(6); // "Hello " 추출

      List<String> flushed = buffer.flush();
      assertThat(flushed).containsExactly("world");
    }

    @Test
    @DisplayName("빈 버퍼 flush")
    void testFlushEmptyBuffer() {
      assertThat(buffer.flush()).isEmpty();
    }

    @Test
    @DisplayName("여러 번 flush")
    void testMultipleFlush() {
      buffer.addToken("Hello");

      assertThat(buffer.flush()).containsExactly("Hello");
      assertThat(buffer.flush()).isEmpty();
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

      assertThat(buffer.flush()).containsExactly("first", "second");
    }
  }

  @Nested
  @DisplayName("엣지 케이스 - 한글 경계")
  class EdgeCaseKoreanBoundary {

    @Test
    @DisplayName("한글 토큰 중간에서 분할")
    void testKoreanTokenSplit() {
      buffer.addToken("안녕하세요");

      List<String> extracted = buffer.extract(3);

      assertThat(extracted).containsExactly("안녕하");
      assertThat(buffer.getContent()).isEqualTo("세요");
    }

    @Test
    @DisplayName("한글 토큰 경계에서 정확히 추출")
    void testKoreanTokenBoundaryExtract() {
      buffer.addToken("안녕");
      buffer.addToken("하세요");

      List<String> extracted = buffer.extract(2);

      assertThat(extracted).containsExactly("안녕");
      assertThat(buffer.getContent()).isEqualTo("하세요");
    }
  }

  // ==================== 5. 실제 사용 시나리오 ====================

  @Nested
  @DisplayName("실제 사용 시나리오 - LLM 스트리밍")
  class RealWorldLlmStreamingTests {

    @Test
    @DisplayName("vLLM 스타일 토큰 분할 - 한글")
    void testVllmStyleTokenSplitsKorean() {
      buffer.addToken("안녕");
      buffer.addToken("하세요");
      buffer.addToken(" ");
      buffer.addToken("세계");

      List<String> extracted = buffer.extract(5);

      assertThat(extracted).containsExactly("안녕", "하세요");
      assertThat(buffer.getContent()).isEqualTo(" 세계");
    }

    @Test
    @DisplayName("XML 태그가 여러 토큰에 분할되어 도착")
    void testXmlTagSplitAcrossTokens() {
      buffer.addToken("일반 텍스트 ");
      buffer.addToken("<thi");
      buffer.addToken("nking");
      buffer.addToken(">");

      List<String> extracted = buffer.extract(7);

      assertThat(extracted).containsExactly("일반 텍스트 ");
      assertThat(buffer.getContent()).isEqualTo("<thinking>");
    }

    @Test
    @DisplayName("패턴 검출 시나리오 - 버퍼링 후 부분 추출")
    void testPatternDetectionScenario() {
      buffer.addToken("텍스트 ");
      buffer.addToken("내용");
      buffer.addToken("<thinking>");

      List<String> beforePattern = buffer.extract(6);
      assertThat(beforePattern).containsExactly("텍스트 ", "내용");

      assertThat(String.join("", buffer.extract(10))).isEqualTo("<thinking>");
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("긴 컨텐츠의 점진적 처리")
    void testIncrementalProcessingOfLongContent() {
      for (int i = 0; i < 10; i++) {
        buffer.addToken("토큰" + i + " ");
      }

      int initialLength = buffer.getContent().length();

      buffer.extract(initialLength / 2);

      assertThat(buffer.getContent().length()).isLessThan(initialLength);
    }

    @Test
    @DisplayName("혼합 언어 스트리밍 - 영어/한글/XML")
    void testMixedLanguageStreamingWithXml() {
      buffer.addToken("Hello ");
      buffer.addToken("안녕");
      buffer.addToken("하세요");
      buffer.addToken(" <tag");
      buffer.addToken(">");

      List<String> extracted = buffer.extract(11);

      assertThat(extracted).containsExactly("Hello ", "안녕", "하세요");
      assertThat(buffer.getContent()).isEqualTo(" <tag>");
    }

    @Test
    @DisplayName("토큰 경계 보존 - 큰 텍스트 블록")
    void testTokenBoundaryPreservationLargeBlock() {
      for (int i = 0; i < 100; i++) {
        buffer.addToken("토큰");
      }

      List<String> extracted = buffer.extract(50);

      assertThat(extracted).hasSize(25);
      assertThat(extracted).allMatch(token -> token.equals("토큰"));
    }
  }

  @Nested
  @DisplayName("실제 사용 시나리오 - 패턴 전후 추출")
  class RealWorldPatternExtractionTests {

    @Test
    @DisplayName("패턴 이전 텍스트 추출 후 패턴 추출")
    void testExtractBeforeAndAfterPattern() {
      buffer.addToken("일반 텍스트");
      buffer.addToken("<thinking>");
      buffer.addToken("추론 내용");

      List<String> beforePattern = buffer.extract(6);
      assertThat(beforePattern).containsExactly("일반 텍스트");

      assertThat(String.join("", buffer.extract(10))).isEqualTo("<thinking>");
      assertThat(buffer.getContent()).isEqualTo("추론 내용");
    }

    @Test
    @DisplayName("다단계 패턴 검출 시나리오")
    void testMultiPatternDetectionScenario() {
      buffer.addToken("텍스트1");
      buffer.addToken("<tag1>");
      buffer.addToken("내용1<");
      buffer.addToken("/tag1>");
      buffer.addToken("텍스트2");

      assertThat(buffer.extract(4)).containsExactly("텍스트1");
      assertThat(String.join("", buffer.extract(6))).isEqualTo("<tag1>");
      assertThat(buffer.extract(3)).containsExactly("내용1");
      assertThat(String.join("", buffer.extract(7))).isEqualTo("</tag1>");
      assertThat(buffer.getContent()).isEqualTo("텍스트2");
    }

    @Test
    @DisplayName("버퍼 오버플로우 방지 - 긴 텍스트 처리")
    void testBufferOverflowPrevention() {
      for (int i = 0; i < 1000; i++) {
        buffer.addToken("긴");
      }

      while (!buffer.isEmpty()) {
        if (buffer.getContent().length() > 10) {
          buffer.extract(10);
        } else {
          buffer.flush();
          break;
        }
      }

      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("compact 이후에도 토큰 경계 보존")
    void testTokenBoundariesAfterCompaction() {
      buffer.addToken("a".repeat(9000));
      buffer.addToken("tail");

      assertThat(buffer.extract(8500)).containsExactly("a".repeat(8500));
      assertThat(buffer.getContent()).isEqualTo("a".repeat(500) + "tail");

      assertThat(buffer.extract(500)).containsExactly("a".repeat(500));
      assertThat(buffer.flush()).containsExactly("tail");
    }
  }

  // ==================== 상태 검증 ====================

  @Nested
  @DisplayName("상태 검증")
  class StateValidationTests {

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

      buffer.flush();
      assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("getContent - 토큰 연결 확인")
    void testGetContentConcatenation() {
      buffer.addToken("Hello");
      buffer.addToken(" ");
      buffer.addToken("world");

      assertThat(buffer.getContent()).isEqualTo("Hello world");
    }
  }
}
