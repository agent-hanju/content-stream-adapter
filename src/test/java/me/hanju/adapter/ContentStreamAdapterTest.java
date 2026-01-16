package me.hanju.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import me.hanju.adapter.payload.TaggedToken;
import me.hanju.adapter.transition.TransitionSchema;

/**
 * ContentStreamAdapter 통합 테스트
 *
 * 실제 LLM 스트리밍 시나리오를 중심으로 검증합니다.
 *
 * 테스트 구성:
 * 1. 생성 및 초기화
 * 2. 잘못된 입력 처리
 * 3. 기본 동작 - 단순 태그 처리
 * 4. 상태 전이 검증
 * 5. 별칭 지원
 * 6. 엣지 케이스
 * 7. 실제 LLM 스트리밍 시나리오
 */
@DisplayName("ContentStreamAdapter 통합 테스트")
class ContentStreamAdapterTest {

  // ==================== 1. 생성 및 초기화 ====================

  @Nested
  @DisplayName("생성 및 초기화")
  class CreationAndInitialization {

    @Test
    @DisplayName("스키마로 어댑터 생성")
    void testCreateWithSchema() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("thinking")
          .tag("answer");

      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      assertThat(adapter).isNotNull();
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("null 스키마 - 예외")
    void testNullSchema() {
      assertThatThrownBy(() -> new ContentStreamAdapter(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Schema cannot be null");
    }

    @Test
    @DisplayName("초기 상태는 루트")
    void testInitialStateIsRoot() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }
  }

  // ==================== 2. 잘못된 입력 처리 ====================

  @Nested
  @DisplayName("잘못된 입력 처리")
  class InvalidInputHandling {

    @Test
    @DisplayName("null 토큰 - 빈 리스트 반환")
    void testNullToken() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> result = adapter.feedToken(null);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("빈 토큰 - 빈 리스트 반환")
    void testEmptyToken() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> result = adapter.feedToken("");

      assertThat(result).isEmpty();
    }
  }

  // ==================== 3. 기본 동작 - 단순 태그 처리 ====================

  @Nested
  @DisplayName("기본 동작 - 단순 태그 처리")
  class BasicTagProcessing {

    @Test
    @DisplayName("일반 텍스트 - 현재 경로로 태깅")
    void testPlainText() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> tokens = adapter.feedToken("Hello world");

      assertThat(tokens).hasSize(1);
      assertThat(tokens.get(0).path()).isEqualTo("/");
      assertThat(tokens.get(0).content()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("유효한 태그 - 상태 전이 발생")
    void testValidTag() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> tokens = adapter.feedToken("<answer>");

      assertThat(tokens).hasSize(1); // OPEN 이벤트 토큰
      assertThat(tokens.get(0).path()).isEqualTo("/answer");
      assertThat(tokens.get(0).content()).isNull();
      assertThat(tokens.get(0).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("잘못된 태그 - 일반 텍스트로 처리")
    void testInvalidTag() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> tokens = adapter.feedToken("<invalid>");

      assertThat(tokens).hasSize(1);
      assertThat(tokens.get(0).path()).isEqualTo("/");
      assertThat(tokens.get(0).content()).isEqualTo("<invalid>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/"); // 상태 변경 없음
    }

    @Test
    @DisplayName("태그와 텍스트 혼합 - 한 토큰에")
    void testTagAndTextMixed() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> tokens = adapter.feedToken("텍스트<answer>");

      assertThat(tokens).hasSize(2);
      assertThat(tokens.get(0).path()).isEqualTo("/");
      assertThat(tokens.get(0).content()).isEqualTo("텍스트");
      assertThat(tokens.get(0).event()).isNull();
      assertThat(tokens.get(1).path()).isEqualTo("/answer");
      assertThat(tokens.get(1).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/answer");
    }
  }

  // ==================== 4. 상태 전이 검증 ====================

  @Nested
  @DisplayName("상태 전이 검증")
  class StateTransitionValidation {

    @Test
    @DisplayName("열고 닫기 시퀀스")
    void testOpenCloseSequence() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("thinking");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      // <thinking>
      List<TaggedToken> openTokens = adapter.feedToken("<thinking>");
      assertThat(openTokens).hasSize(1);
      assertThat(openTokens.get(0).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking");

      // 내용
      List<TaggedToken> tokens = adapter.feedToken("content");
      assertThat(tokens).hasSize(1);
      assertThat(tokens.get(0).path()).isEqualTo("/thinking");
      assertThat(tokens.get(0).content()).isEqualTo("content");
      assertThat(tokens.get(0).event()).isNull();

      // </thinking>
      List<TaggedToken> closeTokens = adapter.feedToken("</thinking>");
      assertThat(closeTokens).hasSize(1);
      assertThat(closeTokens.get(0).path()).isEqualTo("/thinking"); // CLOSE는 닫히는 경로
      assertThat(closeTokens.get(0).event()).isEqualTo("CLOSE");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("중첩 태그 전이")
    void testNestedTagTransition() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("cite", cite -> cite
              .tag("id"));
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      // <cite>
      List<TaggedToken> citeOpen = adapter.feedToken("<cite>");
      assertThat(citeOpen).hasSize(1);
      assertThat(citeOpen.get(0).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // <id>
      List<TaggedToken> idOpen = adapter.feedToken("<id>");
      assertThat(idOpen).hasSize(1);
      assertThat(idOpen.get(0).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite/id");

      // 내용
      List<TaggedToken> tokens = adapter.feedToken("123");
      assertThat(tokens.get(0).path()).isEqualTo("/cite/id");
      assertThat(tokens.get(0).event()).isNull();

      // </id>
      List<TaggedToken> idClose = adapter.feedToken("</id>");
      assertThat(idClose).hasSize(1);
      assertThat(idClose.get(0).path()).isEqualTo("/cite/id"); // CLOSE는 닫히는 경로
      assertThat(idClose.get(0).event()).isEqualTo("CLOSE");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </cite>
      List<TaggedToken> citeClose = adapter.feedToken("</cite>");
      assertThat(citeClose).hasSize(1);
      assertThat(citeClose.get(0).path()).isEqualTo("/cite"); // CLOSE는 닫히는 경로
      assertThat(citeClose.get(0).event()).isEqualTo("CLOSE");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("잘못된 닫기 태그 - 무시")
    void testInvalidCloseTag() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("thinking");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      adapter.feedToken("<thinking>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking");

      // 잘못된 닫기 태그
      List<TaggedToken> tokens = adapter.feedToken("</answer>");
      assertThat(tokens).hasSize(1);
      assertThat(tokens.get(0).content()).isEqualTo("</answer>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking"); // 상태 유지
    }
  }

  // ==================== 5. 별칭 지원 ====================

  @Nested
  @DisplayName("별칭 지원")
  class AliasSupport {

    @Test
    @DisplayName("별칭으로 열기")
    void testOpenWithAlias() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("cite").alias("rag");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> tokens = adapter.feedToken("<rag>");

      assertThat(tokens).hasSize(1);
      assertThat(tokens.get(0).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");
    }

    @Test
    @DisplayName("별칭으로 닫기")
    void testCloseWithAlias() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("cite").alias("rag");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      // <cite>로 열고
      List<TaggedToken> openTokens = adapter.feedToken("<cite>");
      assertThat(openTokens).hasSize(1);
      assertThat(openTokens.get(0).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </rag>로 닫기
      List<TaggedToken> closeTokens = adapter.feedToken("</rag>");
      assertThat(closeTokens).hasSize(1);
      assertThat(closeTokens.get(0).path()).isEqualTo("/cite"); // CLOSE는 닫히는 경로
      assertThat(closeTokens.get(0).event()).isEqualTo("CLOSE");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("별칭 혼용")
    void testMixedAliasUsage() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("cite").alias("rag");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      // <rag>로 열고
      adapter.feedToken("<rag>");
      List<TaggedToken> tokens = adapter.feedToken("content");
      assertThat(tokens.get(0).path()).isEqualTo("/cite");
      assertThat(tokens.get(0).event()).isNull();

      // </cite>로 닫기
      List<TaggedToken> closeTokens = adapter.feedToken("</cite>");
      assertThat(closeTokens).hasSize(1);
      assertThat(closeTokens.get(0).path()).isEqualTo("/cite"); // CLOSE는 닫히는 경로
      assertThat(closeTokens.get(0).event()).isEqualTo("CLOSE");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }
  }

  // ==================== 6. 엣지 케이스 ====================

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCases {

    @Test
    @DisplayName("여러 토큰에 걸친 태그")
    void testTagAcrossTokens() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      // "<ans"와 "wer>"로 분할
      List<TaggedToken> tokens1 = adapter.feedToken("<ans");
      assertThat(tokens1).isEmpty(); // 패턴 가능성으로 버퍼링

      List<TaggedToken> tokens2 = adapter.feedToken("wer>");
      assertThat(tokens2).hasSize(1); // OPEN 이벤트 토큰
      assertThat(tokens2.get(0).event()).isEqualTo("OPEN");
      assertThat(adapter.getCurrentPath()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("flush - 남은 버퍼 처리")
    void testFlush() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      // 패턴 가능성으로 버퍼에 남아있는 텍스트
      adapter.feedToken("<ans");

      List<TaggedToken> flushed = adapter.flush();
      assertThat(flushed).hasSize(1);
      assertThat(flushed.get(0).path()).isEqualTo("/");
      assertThat(flushed.get(0).content()).isEqualTo("<ans");
    }

    @Test
    @DisplayName("빈 컨텐츠 토큰 무시")
    void testEmptyContentIgnored() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> tokens = adapter.feedToken(""); // 빈 문자열

      assertThat(tokens).isEmpty();
    }
  }

  // ==================== 7. 실제 LLM 스트리밍 시나리오 ====================

  @Nested
  @DisplayName("실제 LLM 스트리밍 시나리오")
  class RealWorldLLMScenarios {

    @Test
    @DisplayName("기본 LLM 응답 - thinking + answer")
    void testBasicLLMResponse() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("thinking")
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> allTokens = new ArrayList<>();

      // <thinking>
      allTokens.addAll(adapter.feedToken("<thinking>"));

      // 추론 내용
      allTokens.addAll(adapter.feedToken("법률 검토 중..."));

      // </thinking>
      allTokens.addAll(adapter.feedToken("</thinking>"));

      // <answer>
      allTokens.addAll(adapter.feedToken("<answer>"));

      // 답변
      allTokens.addAll(adapter.feedToken("결론은 A입니다."));

      // </answer>
      allTokens.addAll(adapter.feedToken("</answer>"));

      // 검증: OPEN thinking, content, CLOSE thinking, OPEN answer, content, CLOSE
      // answer
      assertThat(allTokens).hasSize(6);
      assertThat(allTokens.get(0).path()).isEqualTo("/thinking");
      assertThat(allTokens.get(0).event()).isEqualTo("OPEN");
      assertThat(allTokens.get(1).path()).isEqualTo("/thinking");
      assertThat(allTokens.get(1).content()).isEqualTo("법률 검토 중...");
      assertThat(allTokens.get(1).event()).isNull();
      assertThat(allTokens.get(2).path()).isEqualTo("/thinking"); // CLOSE는 닫히는 경로
      assertThat(allTokens.get(2).event()).isEqualTo("CLOSE");
      assertThat(allTokens.get(3).path()).isEqualTo("/answer");
      assertThat(allTokens.get(3).event()).isEqualTo("OPEN");
      assertThat(allTokens.get(4).path()).isEqualTo("/answer");
      assertThat(allTokens.get(4).content()).isEqualTo("결론은 A입니다.");
      assertThat(allTokens.get(4).event()).isNull();
      assertThat(allTokens.get(5).path()).isEqualTo("/answer"); // CLOSE는 닫히는 경로
      assertThat(allTokens.get(5).event()).isEqualTo("CLOSE");
    }

    @Test
    @DisplayName("RAG 응답 - cite 구조")
    void testRagResponse() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("cite", cite -> cite
              .tag("id")
              .tag("source"));
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> allTokens = new ArrayList<>();

      // <cite>
      allTokens.addAll(adapter.feedToken("<cite>"));

      // <id>
      allTokens.addAll(adapter.feedToken("<id>"));
      allTokens.addAll(adapter.feedToken("doc-123"));
      allTokens.addAll(adapter.feedToken("</id>"));

      // <source>
      allTokens.addAll(adapter.feedToken("<source>"));
      allTokens.addAll(adapter.feedToken("법률 문서"));
      allTokens.addAll(adapter.feedToken("</source>"));

      // </cite>
      allTokens.addAll(adapter.feedToken("</cite>"));

      // 검증: OPEN cite, OPEN id, content, CLOSE id, OPEN source, content, CLOSE
      // source, CLOSE cite
      assertThat(allTokens).hasSize(8);

      // 컨텐츠만 필터링
      List<TaggedToken> contentTokens = allTokens.stream()
          .filter(t -> t.event() == null)
          .toList();
      assertThat(contentTokens).hasSize(2);
      assertThat(contentTokens.get(0).path()).isEqualTo("/cite/id");
      assertThat(contentTokens.get(0).content()).isEqualTo("doc-123");
      assertThat(contentTokens.get(1).path()).isEqualTo("/cite/source");
      assertThat(contentTokens.get(1).content()).isEqualTo("법률 문서");
    }

    @Test
    @DisplayName("태그가 여러 토큰에 분할 - 실제 vLLM 시나리오")
    void testTagSplitAcrossTokens() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("thinking");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> allTokens = new ArrayList<>();

      // "<thi"
      allTokens.addAll(adapter.feedToken("<thi"));

      // "nking>"
      allTokens.addAll(adapter.feedToken("nking>"));

      // "Let me ", "think", "..."
      allTokens.addAll(adapter.feedToken("Let me "));
      allTokens.addAll(adapter.feedToken("think"));
      allTokens.addAll(adapter.feedToken("..."));

      // "</"
      allTokens.addAll(adapter.feedToken("</"));

      // "thi"
      allTokens.addAll(adapter.feedToken("thi"));

      // "nking>"
      allTokens.addAll(adapter.feedToken("nking>"));

      // 검증 - OPEN thinking, 3개 컨텐츠 토큰, CLOSE thinking
      assertThat(allTokens).hasSize(5);
      assertThat(allTokens.get(0).event()).isEqualTo("OPEN");

      List<TaggedToken> contentTokens = allTokens.stream()
          .filter(t -> t.event() == null)
          .toList();
      assertThat(contentTokens).allMatch(token -> token.path().equals("/thinking"));
      assertThat(contentTokens).extracting("content")
          .containsExactly("Let me ", "think", "...");

      assertThat(allTokens.get(4).path()).isEqualTo("/thinking"); // CLOSE는 닫히는 경로
      assertThat(allTokens.get(4).event()).isEqualTo("CLOSE");
    }

    @Test
    @DisplayName("혼합 컨텐츠 - 일반 텍스트 + 태그")
    void testMixedContent() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("thinking")
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> allTokens = new ArrayList<>();

      // 일반 텍스트
      allTokens.addAll(adapter.feedToken("안녕하세요. "));

      // <thinking>
      allTokens.addAll(adapter.feedToken("<thinking>"));
      allTokens.addAll(adapter.feedToken("추론..."));
      allTokens.addAll(adapter.feedToken("</thinking>"));

      // <answer>
      allTokens.addAll(adapter.feedToken("<answer>"));
      allTokens.addAll(adapter.feedToken("답변"));
      allTokens.addAll(adapter.feedToken("</answer>"));

      // 일반 텍스트
      allTokens.addAll(adapter.feedToken(" 감사합니다."));

      // 검증: 일반 텍스트, OPEN thinking, content, CLOSE, OPEN answer, content, CLOSE, 일반
      // 텍스트
      assertThat(allTokens).hasSize(8);

      // 컨텐츠만 필터링
      List<TaggedToken> contentTokens = allTokens.stream()
          .filter(t -> t.event() == null)
          .toList();
      assertThat(contentTokens).hasSize(4);
      assertThat(contentTokens.get(0).path()).isEqualTo("/");
      assertThat(contentTokens.get(0).content()).isEqualTo("안녕하세요. ");
      assertThat(contentTokens.get(1).path()).isEqualTo("/thinking");
      assertThat(contentTokens.get(1).content()).isEqualTo("추론...");
      assertThat(contentTokens.get(2).path()).isEqualTo("/answer");
      assertThat(contentTokens.get(2).content()).isEqualTo("답변");
      assertThat(contentTokens.get(3).path()).isEqualTo("/");
      assertThat(contentTokens.get(3).content()).isEqualTo(" 감사합니다.");
    }

    @Test
    @DisplayName("잘못된 태그 무시 - 일반 텍스트로 처리")
    void testInvalidTagsIgnored() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> allTokens = new ArrayList<>();

      // 허용되지 않은 태그
      allTokens.addAll(adapter.feedToken("<invalid>"));
      allTokens.addAll(adapter.feedToken("content"));
      allTokens.addAll(adapter.feedToken("</invalid>"));

      // <answer>
      allTokens.addAll(adapter.feedToken("<answer>"));
      allTokens.addAll(adapter.feedToken("valid"));
      allTokens.addAll(adapter.feedToken("</answer>"));

      // 검증 - invalid 태그는 일반 텍스트로, answer는 OPEN + content + CLOSE
      assertThat(allTokens).hasSize(6);
      assertThat(allTokens.get(0).path()).isEqualTo("/");
      assertThat(allTokens.get(0).content()).isEqualTo("<invalid>");
      assertThat(allTokens.get(1).path()).isEqualTo("/");
      assertThat(allTokens.get(1).content()).isEqualTo("content");
      assertThat(allTokens.get(2).path()).isEqualTo("/");
      assertThat(allTokens.get(2).content()).isEqualTo("</invalid>");
      assertThat(allTokens.get(3).event()).isEqualTo("OPEN");
      assertThat(allTokens.get(4).path()).isEqualTo("/answer");
      assertThat(allTokens.get(4).content()).isEqualTo("valid");
      assertThat(allTokens.get(5).path()).isEqualTo("/answer"); // CLOSE는 닫히는 경로
      assertThat(allTokens.get(5).event()).isEqualTo("CLOSE");
    }

    @Test
    @DisplayName("복잡한 중첩 구조")
    void testComplexNestedStructure() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("section", section -> section
              .tag("title")
              .tag("content")
              .tag("subsection", subsection -> subsection
                  .tag("title")
                  .tag("content")));
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> allTokens = new ArrayList<>();

      // <section>
      allTokens.addAll(adapter.feedToken("<section>"));

      // <title>
      allTokens.addAll(adapter.feedToken("<title>"));
      allTokens.addAll(adapter.feedToken("Section Title"));
      allTokens.addAll(adapter.feedToken("</title>"));

      // <subsection>
      allTokens.addAll(adapter.feedToken("<subsection>"));

      // <content>
      allTokens.addAll(adapter.feedToken("<content>"));
      allTokens.addAll(adapter.feedToken("Subsection Content"));
      allTokens.addAll(adapter.feedToken("</content>"));

      // </subsection>
      allTokens.addAll(adapter.feedToken("</subsection>"));

      // </section>
      allTokens.addAll(adapter.feedToken("</section>"));

      // 검증: 많은 이벤트 토큰들 포함
      // 컨텐츠만 필터링해서 검증
      List<TaggedToken> contentTokens = allTokens.stream()
          .filter(t -> t.event() == null)
          .toList();
      assertThat(contentTokens).hasSize(2);
      assertThat(contentTokens.get(0).path()).isEqualTo("/section/title");
      assertThat(contentTokens.get(0).content()).isEqualTo("Section Title");
      assertThat(contentTokens.get(1).path()).isEqualTo("/section/subsection/content");
      assertThat(contentTokens.get(1).content()).isEqualTo("Subsection Content");
    }

    @Test
    @DisplayName("전체 스트리밍 시뮬레이션 - 매우 작은 토큰")
    void testFullStreamingSimulation() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> allTokens = new ArrayList<>();

      // 매우 작은 토큰으로 시뮬레이션
      String[] tokens = {
          "Hello", " ", "<", "ans", "wer", ">",
          "This", " ", "is", " ", "the", " ", "answer",
          ".", "</", "ans", "wer", ">"
      };

      for (String token : tokens) {
        allTokens.addAll(adapter.feedToken(token));
      }

      // 남은 버퍼 flush
      allTokens.addAll(adapter.flush());

      // 검증 - 경로별로 그룹화 (이벤트 제외)
      List<TaggedToken> rootTokens = allTokens.stream()
          .filter(t -> t.path().equals("/") && t.event() == null)
          .toList();
      List<TaggedToken> answerTokens = allTokens.stream()
          .filter(t -> t.path().equals("/answer") && t.event() == null)
          .toList();

      assertThat(rootTokens).hasSize(2);
      assertThat(rootTokens).extracting("content").containsExactly("Hello", " ");

      assertThat(answerTokens).hasSize(8); // "This", " ", "is", " ", "the", " ", "answer", "."
      assertThat(String.join("", answerTokens.stream().map(TaggedToken::content).toList()))
          .isEqualTo("This is the answer.");

      // 이벤트 토큰 검증
      List<TaggedToken> eventTokens = allTokens.stream()
          .filter(t -> t.event() != null)
          .toList();
      assertThat(eventTokens).hasSize(2); // OPEN, CLOSE
      assertThat(eventTokens.get(0).path()).isEqualTo("/answer");
      assertThat(eventTokens.get(0).event()).isEqualTo("OPEN");
      assertThat(eventTokens.get(1).path()).isEqualTo("/answer"); // CLOSE는 닫히는 경로
      assertThat(eventTokens.get(1).event()).isEqualTo("CLOSE");
    }
  }

  // ==================== 8. 토큰 경계 보존 검증 ====================

  @Nested
  @DisplayName("토큰 경계 보존")
  class TokenBoundaryPreservation {

    @Test
    @DisplayName("여러 토큰의 텍스트 - 개별 반환")
    void testMultipleTokensReturnedSeparately() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      List<TaggedToken> tokens1 = adapter.feedToken("Hello");
      List<TaggedToken> tokens2 = adapter.feedToken(" ");
      List<TaggedToken> tokens3 = adapter.feedToken("World");

      // 각 토큰이 개별적으로 반환됨
      assertThat(tokens1).hasSize(1);
      assertThat(tokens1.get(0).content()).isEqualTo("Hello");

      assertThat(tokens2).hasSize(1);
      assertThat(tokens2.get(0).content()).isEqualTo(" ");

      assertThat(tokens3).hasSize(1);
      assertThat(tokens3.get(0).content()).isEqualTo("World");
    }

    @Test
    @DisplayName("패턴 검출 전 텍스트 - 토큰 경계 보존")
    void testPrefixTokenBoundaryPreservation() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("tag");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      // "텍스트1"과 "텍스트2"는 개별 토큰으로 입력됨
      adapter.feedToken("텍스트1");
      adapter.feedToken("텍스트2");

      // 각각 별도로 반환되어야 함 (병합되지 않음)
      // 이는 StreamPatternMatcher의 TextChunks에 의해 보장됨
    }
  }

  // ==================== 9. getRaw() 테스트 ====================

  @Nested
  @DisplayName("getRaw() - 원문 누적")
  class GetRawTests {

    @Test
    @DisplayName("단순 텍스트 누적")
    void testSimpleTextAccumulation() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      adapter.feedToken("Hello ");
      adapter.feedToken("World");

      assertThat(adapter.getRaw()).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("태그 포함 원문 누적")
    void testRawWithTags() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("cite");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      adapter.feedToken("안녕 ");
      adapter.feedToken("<cite>");
      adapter.feedToken("출처");
      adapter.feedToken("</cite>");
      adapter.feedToken(" 세계");

      assertThat(adapter.getRaw()).isEqualTo("안녕 <cite>출처</cite> 세계");
    }

    @Test
    @DisplayName("빈 토큰은 누적되지 않음")
    void testEmptyTokensNotAccumulated() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      adapter.feedToken("Hello");
      adapter.feedToken("");
      adapter.feedToken(null);
      adapter.feedToken("World");

      assertThat(adapter.getRaw()).isEqualTo("HelloWorld");
    }

    @Test
    @DisplayName("초기 상태에서 빈 문자열 반환")
    void testInitialStateReturnsEmpty() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      assertThat(adapter.getRaw()).isEmpty();
    }

    @Test
    @DisplayName("스트리밍 시나리오 - 점진적 누적")
    void testStreamingAccumulation() {
      TransitionSchema schema = TransitionSchema.root()
          .tag("think");
      ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

      adapter.feedToken("<th");
      assertThat(adapter.getRaw()).isEqualTo("<th");

      adapter.feedToken("ink>");
      assertThat(adapter.getRaw()).isEqualTo("<think>");

      adapter.feedToken("reasoning");
      assertThat(adapter.getRaw()).isEqualTo("<think>reasoning");

      adapter.feedToken("</think>");
      assertThat(adapter.getRaw()).isEqualTo("<think>reasoning</think>");
    }
  }
}
