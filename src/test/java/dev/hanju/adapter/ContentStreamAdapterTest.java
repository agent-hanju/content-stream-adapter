package dev.hanju.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.ContentStreamResult;

/**
 * ContentStreamAdapter 통합 테스트
 *
 * 실제 LLM 스트리밍 시나리오를 중심으로 검증합니다.
 */
@DisplayName("ContentStreamAdapter 통합 테스트")
class ContentStreamAdapterTest {

  // ==================== Helper Methods ====================

  private ContentStreamAdapter createAdapter(TransitionSchema schema, String... paths) {
    ContentStreamAdapter.Builder builder = ContentStreamAdapter.from(schema.toPaths());
    for (String path : paths) {
      String segment = path.substring(path.lastIndexOf('/') + 1);
      builder.bind(path).tag(segment).and();
    }
    return builder.build();
  }

  // ==================== 1. 생성 및 초기화 ====================

  @Nested
  @DisplayName("생성 및 초기화")
  class CreationAndInitialization {

    @Test
    @DisplayName("바인딩으로 어댑터 생성")
    void testCreateWithBinding() {
      TransitionSchema schema = TransitionSchema.root()
          .path("thinking")
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/thinking", "/answer");

      assertThat(adapter).isNotNull();
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("null 바인딩 - 예외")
    void testNullBinding() {
      assertThatThrownBy(() -> ContentStreamAdapter.from(null).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("allPaths cannot be null");
    }

    @Test
    @DisplayName("초기 상태는 루트")
    void testInitialStateIsRoot() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }
  }

  // ==================== 2. 잘못된 입력 처리 ====================

  @Nested
  @DisplayName("잘못된 입력 처리")
  class InvalidInputHandling {

    @Test
    @DisplayName("null 토큰 - 예외 발생")
    void testNullToken() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      assertThatThrownBy(() -> adapter.feedToken(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 토큰 - 빈 리스트 반환")
    void testEmptyToken() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      List<ContentStreamResult> result = adapter.feedToken("");

      assertThat(result).isEmpty();
    }
  }

  // ==================== 3. 기본 동작 - 단순 태그 처리 ====================

  @Nested
  @DisplayName("기본 동작 - 단순 태그 처리")
  class BasicTagProcessing {

    @Test
    @DisplayName("일반 텍스트 - Text 타입으로 반환")
    void testPlainText() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      List<ContentStreamResult> outputs = adapter.feedToken("Hello world");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs.get(0)).content()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("유효한 태그 - 상태 전이 발생")
    void testValidTag() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/answer");

      List<ContentStreamResult> outputs = adapter.feedToken("<answer>");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(((ContentStreamResult.Enter) outputs.get(0)).path()).isEqualTo("/answer");
      assertThat(adapter.getCurrentPath()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("잘못된 태그 - 일반 텍스트로 처리")
    void testInvalidTag() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/answer");

      List<ContentStreamResult> outputs = adapter.feedToken("<invalid>");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs.get(0)).content()).isEqualTo("<invalid>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("관심 없는 XML-like 태그는 토큰 경계 보존")
    void testNonTargetXmlLikeTextPreservesTokenBoundaries() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/answer");

      List<ContentStreamResult> outputs1 = adapter.feedToken("<invalid");
      List<ContentStreamResult> outputs2 = adapter.feedToken(">text");

      assertThat(outputs1).containsExactly(new ContentStreamResult.Text("<invalid"));
      assertThat(outputs2).containsExactly(new ContentStreamResult.Text(">text"));
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("관심 태그 이름의 prefix여도 이름 경계가 다르면 토큰 경계 보존")
    void testNonTargetPrefixPreservesTokenBoundaries() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/answer");

      List<ContentStreamResult> outputs1 = adapter.feedToken("<answer");
      List<ContentStreamResult> outputs2 = adapter.feedToken("X>text");

      assertThat(outputs1).isEmpty();
      assertThat(outputs2).containsExactly(
          new ContentStreamResult.Text("<answer"),
          new ContentStreamResult.Text("X>text"));
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("자가 닫힘 태그 - Enter 후 Exit 연속 출력")
    void testSelfClosingTag() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").attr("id")
          .build();

      List<ContentStreamResult> outputs = adapter.feedToken("<cite id=\"ref\"/>tail");

      assertThat(outputs).hasSize(3);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      ContentStreamResult.Enter enter = (ContentStreamResult.Enter) outputs.get(0);
      assertThat(enter.path()).isEqualTo("/cite");
      assertThat(enter.attributes()).containsEntry("id", "ref");

      assertThat(outputs.get(1)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) outputs.get(1)).path()).isEqualTo("/cite");

      assertThat(outputs.get(2)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs.get(2)).content()).isEqualTo("tail");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("태그와 텍스트 혼합 - 한 토큰에")
    void testTagAndTextMixed() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/answer");

      List<ContentStreamResult> outputs = adapter.feedToken("텍스트<answer>");

      assertThat(outputs).hasSize(2);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs.get(0)).content()).isEqualTo("텍스트");
      assertThat(outputs.get(1)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(((ContentStreamResult.Enter) outputs.get(1)).path()).isEqualTo("/answer");
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
          .path("thinking");
      ContentStreamAdapter adapter = createAdapter(schema, "/thinking");

      // <thinking>
      List<ContentStreamResult> openOutputs = adapter.feedToken("<thinking>");
      assertThat(openOutputs).hasSize(1);
      assertThat(openOutputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking");

      // 내용
      List<ContentStreamResult> outputs = adapter.feedToken("content");
      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs.get(0)).content()).isEqualTo("content");

      // </thinking>
      List<ContentStreamResult> closeOutputs = adapter.feedToken("</thinking>");
      assertThat(closeOutputs).hasSize(1);
      assertThat(closeOutputs.get(0)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) closeOutputs.get(0)).path()).isEqualTo("/thinking");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("중첩 태그 전이")
    void testNestedTagTransition() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite", cite -> cite
              .path("id"));
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite")
          .and()
          .bind("/cite/id").tag("id")
          .and()
          .build();

      // <cite>
      List<ContentStreamResult> citeOpen = adapter.feedToken("<cite>");
      assertThat(citeOpen).hasSize(1);
      assertThat(citeOpen.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // <id>
      List<ContentStreamResult> idOpen = adapter.feedToken("<id>");
      assertThat(idOpen).hasSize(1);
      assertThat(idOpen.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite/id");

      // 내용
      List<ContentStreamResult> outputs = adapter.feedToken("123");
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Text.class);

      // </id>
      List<ContentStreamResult> idClose = adapter.feedToken("</id>");
      assertThat(idClose).hasSize(1);
      assertThat(idClose.get(0)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) idClose.get(0)).path()).isEqualTo("/cite/id");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </cite>
      List<ContentStreamResult> citeClose = adapter.feedToken("</cite>");
      assertThat(citeClose).hasSize(1);
      assertThat(citeClose.get(0)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) citeClose.get(0)).path()).isEqualTo("/cite");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("잘못된 닫기 태그 - 무시")
    void testInvalidCloseTag() {
      TransitionSchema schema = TransitionSchema.root()
          .path("thinking");
      ContentStreamAdapter adapter = createAdapter(schema, "/thinking");

      adapter.feedToken("<thinking>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking");

      // 잘못된 닫기 태그
      List<ContentStreamResult> outputs = adapter.feedToken("</answer>");
      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs.get(0)).content()).isEqualTo("</answer>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking");
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
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").alias("rag")
          .and()
          .build();

      List<ContentStreamResult> outputs = adapter.feedToken("<rag>");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");
    }

    @Test
    @DisplayName("별칭으로 닫기")
    void testCloseWithAlias() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").alias("rag")
          .and()
          .build();

      // <cite>로 열고
      List<ContentStreamResult> openOutputs = adapter.feedToken("<cite>");
      assertThat(openOutputs).hasSize(1);
      assertThat(openOutputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </rag>로 닫기
      List<ContentStreamResult> closeOutputs = adapter.feedToken("</rag>");
      assertThat(closeOutputs).hasSize(1);
      assertThat(closeOutputs.get(0)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) closeOutputs.get(0)).path()).isEqualTo("/cite");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("별칭 혼용")
    void testMixedAliasUsage() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").alias("rag")
          .and()
          .build();

      // <rag>로 열고
      adapter.feedToken("<rag>");
      List<ContentStreamResult> outputs = adapter.feedToken("content");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </cite>로 닫기
      List<ContentStreamResult> closeOutputs = adapter.feedToken("</cite>");
      assertThat(closeOutputs).hasSize(1);
      assertThat(closeOutputs.get(0)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) closeOutputs.get(0)).path()).isEqualTo("/cite");
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
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/answer");

      // "<ans"와 "wer>"로 분할
      List<ContentStreamResult> outputs1 = adapter.feedToken("<ans");
      assertThat(outputs1).isEmpty();

      List<ContentStreamResult> outputs2 = adapter.feedToken("wer>");
      assertThat(outputs2).hasSize(1);
      assertThat(outputs2.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("flush - 남은 버퍼 처리")
    void testFlush() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/answer");

      adapter.feedToken("<ans");

      List<ContentStreamResult> flushed = adapter.flush();
      assertThat(flushed).hasSize(1);
      assertThat(flushed.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) flushed.get(0)).content()).isEqualTo("<ans");
    }

    @Test
    @DisplayName("빈 컨텐츠 토큰 무시")
    void testEmptyContentIgnored() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      List<ContentStreamResult> outputs = adapter.feedToken("");

      assertThat(outputs).isEmpty();
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
          .path("thinking")
          .path("answer");
      ContentStreamAdapter adapter = createAdapter(schema, "/thinking", "/answer");

      List<ContentStreamResult> allOutputs = new ArrayList<>();

      allOutputs.addAll(adapter.feedToken("<thinking>"));
      allOutputs.addAll(adapter.feedToken("법률 검토 중..."));
      allOutputs.addAll(adapter.feedToken("</thinking>"));
      allOutputs.addAll(adapter.feedToken("<answer>"));
      allOutputs.addAll(adapter.feedToken("결론은 A입니다."));
      allOutputs.addAll(adapter.feedToken("</answer>"));

      assertThat(allOutputs).hasSize(6);
      assertThat(allOutputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(((ContentStreamResult.Enter) allOutputs.get(0)).path()).isEqualTo("/thinking");
      assertThat(allOutputs.get(1)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) allOutputs.get(1)).content()).isEqualTo("법률 검토 중...");
      assertThat(allOutputs.get(2)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) allOutputs.get(2)).path()).isEqualTo("/thinking");
      assertThat(allOutputs.get(3)).isInstanceOf(ContentStreamResult.Enter.class);
      assertThat(((ContentStreamResult.Enter) allOutputs.get(3)).path()).isEqualTo("/answer");
      assertThat(allOutputs.get(4)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) allOutputs.get(4)).content()).isEqualTo("결론은 A입니다.");
      assertThat(allOutputs.get(5)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) allOutputs.get(5)).path()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("RAG 응답 - cite 구조")
    void testRagResponse() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite", cite -> cite
              .path("id")
              .path("source"));
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite")
          .and()
          .bind("/cite/id").tag("id")
          .and()
          .bind("/cite/source").tag("source")
          .and()
          .build();

      List<ContentStreamResult> allOutputs = new ArrayList<>();

      allOutputs.addAll(adapter.feedToken("<cite>"));
      allOutputs.addAll(adapter.feedToken("<id>"));
      allOutputs.addAll(adapter.feedToken("doc-123"));
      allOutputs.addAll(adapter.feedToken("</id>"));
      allOutputs.addAll(adapter.feedToken("<source>"));
      allOutputs.addAll(adapter.feedToken("법률 문서"));
      allOutputs.addAll(adapter.feedToken("</source>"));
      allOutputs.addAll(adapter.feedToken("</cite>"));

      assertThat(allOutputs).hasSize(8);

      List<ContentStreamResult.Text> textOutputs = allOutputs.stream()
          .filter(t -> t instanceof ContentStreamResult.Text)
          .map(t -> (ContentStreamResult.Text) t)
          .toList();
      assertThat(textOutputs).hasSize(2);
      assertThat(textOutputs.get(0).content()).isEqualTo("doc-123");
      assertThat(textOutputs.get(1).content()).isEqualTo("법률 문서");
    }

    @Test
    @DisplayName("태그가 여러 토큰에 분할 - 실제 vLLM 시나리오")
    void testTagSplitAcrossTokens() {
      TransitionSchema schema = TransitionSchema.root()
          .path("thinking");
      ContentStreamAdapter adapter = createAdapter(schema, "/thinking");

      List<ContentStreamResult> allOutputs = new ArrayList<>();

      allOutputs.addAll(adapter.feedToken("<thi"));
      allOutputs.addAll(adapter.feedToken("nking>"));
      allOutputs.addAll(adapter.feedToken("Let me "));
      allOutputs.addAll(adapter.feedToken("think"));
      allOutputs.addAll(adapter.feedToken("..."));
      allOutputs.addAll(adapter.feedToken("</"));
      allOutputs.addAll(adapter.feedToken("thi"));
      allOutputs.addAll(adapter.feedToken("nking>"));

      assertThat(allOutputs).hasSize(5);
      assertThat(allOutputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);

      List<ContentStreamResult.Text> textOutputs = allOutputs.stream()
          .filter(t -> t instanceof ContentStreamResult.Text)
          .map(t -> (ContentStreamResult.Text) t)
          .toList();
      assertThat(textOutputs).extracting(ContentStreamResult.Text::content)
          .containsExactly("Let me ", "think", "...");

      assertThat(allOutputs.get(4)).isInstanceOf(ContentStreamResult.Exit.class);
      assertThat(((ContentStreamResult.Exit) allOutputs.get(4)).path()).isEqualTo("/thinking");
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
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      List<ContentStreamResult> outputs1 = adapter.feedToken("Hello");
      List<ContentStreamResult> outputs2 = adapter.feedToken(" ");
      List<ContentStreamResult> outputs3 = adapter.feedToken("World");

      assertThat(outputs1).hasSize(1);
      assertThat(outputs1.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs1.get(0)).content()).isEqualTo("Hello");

      assertThat(outputs2).hasSize(1);
      assertThat(outputs2.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs2.get(0)).content()).isEqualTo(" ");

      assertThat(outputs3).hasSize(1);
      assertThat(outputs3.get(0)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs3.get(0)).content()).isEqualTo("World");
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
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      adapter.feedToken("Hello ");
      adapter.feedToken("World");

      assertThat(adapter.getRaw()).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("태그 포함 원문 누적")
    void testRawWithTags() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = createAdapter(schema, "/cite");

      adapter.feedToken("안녕 ");
      adapter.feedToken("<cite>");
      adapter.feedToken("출처");
      adapter.feedToken("</cite>");
      adapter.feedToken(" 세계");

      assertThat(adapter.getRaw()).isEqualTo("안녕 <cite>출처</cite> 세계");
    }

    @Test
    @DisplayName("초기 상태에서 빈 문자열 반환")
    void testInitialStateReturnsEmpty() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      assertThat(adapter.getRaw()).isEmpty();
    }

    @Test
    @DisplayName("flush 후 getRaw()는 초기화되어 빈 문자열")
    void testGetRawResetAfterFlush() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      ContentStreamAdapter adapter = createAdapter(schema, "/test");

      adapter.feedToken("Hello ");
      adapter.feedToken("World");
      assertThat(adapter.getRaw()).isEqualTo("Hello World");

      adapter.flush();

      assertThat(adapter.getRaw()).isEmpty();
    }

    @Test
    @DisplayName("flush 후 어댑터 재사용 - 상태·원문이 독립적으로 초기화")
    void testAdapterReusableAfterFlush() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = createAdapter(schema, "/cite");

      // 첫 번째 스트림 - 태그 안에서 끝남 (불균형)
      adapter.feedToken("first <cite>open");
      adapter.flush();

      // flush 후 초기 상태로 복귀
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
      assertThat(adapter.getRaw()).isEmpty();

      // 두 번째 스트림 - 이전 스트림 흔적 없이 독립 동작
      List<ContentStreamResult> outputs = adapter.feedToken("<cite>second</cite>");

      assertThat(adapter.getRaw()).isEqualTo("<cite>second</cite>");
      assertThat(outputs).anyMatch(o -> o instanceof ContentStreamResult.Enter e && e.path().equals("/cite"));
      assertThat(outputs).anyMatch(o -> o instanceof ContentStreamResult.Exit e && e.path().equals("/cite"));
    }
  }

  // ==================== 10. 속성(Attribute) 지원 ====================

  @Nested
  @DisplayName("속성 지원")
  class AttributeSupport {

    @Test
    @DisplayName("속성 있는 태그 - 한 번에 전송")
    void testTagWithAttributes() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").attr("id", "source")
          .and()
          .build();

      List<ContentStreamResult> outputs = adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">content</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs).hasSize(3);

      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      ContentStreamResult.Enter enter = (ContentStreamResult.Enter) outputs.get(0);
      assertThat(enter.path()).isEqualTo("/cite");
      assertThat(enter.attributes()).containsEntry("id", "ref1");
      assertThat(enter.attributes()).containsEntry("source", "wiki");

      assertThat(outputs.get(1)).isInstanceOf(ContentStreamResult.Text.class);
      assertThat(((ContentStreamResult.Text) outputs.get(1)).content()).isEqualTo("content");

      assertThat(outputs.get(2)).isInstanceOf(ContentStreamResult.Exit.class);
    }

    @Test
    @DisplayName("속성이 스키마에 정의되지 않으면 필터링됨")
    void testAttributeFiltering() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").attr("id")  // source는 허용 안 함
          .and()
          .build();

      List<ContentStreamResult> outputs = adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">x</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      ContentStreamResult.Enter enter = (ContentStreamResult.Enter) outputs.get(0);
      assertThat(enter.attributes()).containsEntry("id", "ref1");
      assertThat(enter.attributes()).doesNotContainKey("source");
    }

    @Test
    @DisplayName("속성 없는 태그도 정상 동작")
    void testTagWithoutAttributes() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").attr("id")
          .and()
          .build();

      List<ContentStreamResult> outputs = adapter.feedToken("<cite>content</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      ContentStreamResult.Enter enter = (ContentStreamResult.Enter) outputs.get(0);
      assertThat(enter.attributes()).isEmpty();
    }

    @Test
    @DisplayName("속성이 토큰 경계에 걸침")
    void testAttributeSplitAcrossTokens() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/cite").tag("cite").attr("id")
          .and()
          .build();

      List<ContentStreamResult> allOutputs = new ArrayList<>();
      allOutputs.addAll(adapter.feedToken("<cite id=\"ref"));
      allOutputs.addAll(adapter.feedToken("1\">content</cite>"));
      allOutputs.addAll(adapter.flush());

      assertThat(allOutputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      ContentStreamResult.Enter enter = (ContentStreamResult.Enter) allOutputs.get(0);
      assertThat(enter.attributes()).containsEntry("id", "ref1");
    }

    @Test
    @DisplayName("스키마에 attr 정의 안 하면 모든 속성 필터링")
    void testNoAttrDefinedFiltersAll() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      ContentStreamAdapter adapter = createAdapter(schema, "/cite");  // attr 없음

      List<ContentStreamResult> outputs = adapter.feedToken("<cite id=\"ref1\">content</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs.get(0)).isInstanceOf(ContentStreamResult.Enter.class);
      ContentStreamResult.Enter enter = (ContentStreamResult.Enter) outputs.get(0);
      assertThat(enter.attributes()).isEmpty();
    }

    @Test
    @DisplayName("같은 태그 이름을 현재 경로에 맞는 자식 경로로 해석")
    void testSameTagNameUnderDifferentParents() {
      TransitionSchema schema = TransitionSchema.root()
          .path("section", section -> section
              .path("title")
              .path("subsection", subsection -> subsection
                  .path("title")));
      ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
          .bind("/section").tag("section")
          .and()
          .bind("/section/title").tag("title")
          .and()
          .bind("/section/subsection").tag("subsection")
          .and()
          .bind("/section/subsection/title").tag("title")
          .and()
          .build();

      List<ContentStreamResult> outputs = adapter.feedToken(
          "<section><title>A</title><subsection><title>B</title></subsection></section>");
      outputs.addAll(adapter.flush());

      assertThat(outputs)
          .filteredOn(ContentStreamResult.Enter.class::isInstance)
          .extracting(output -> ((ContentStreamResult.Enter) output).path())
          .containsExactly(
              "/section",
              "/section/title",
              "/section/subsection",
              "/section/subsection/title");
    }
  }
}
