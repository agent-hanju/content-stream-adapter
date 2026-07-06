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
import dev.hanju.adapter.xml.XmlStreamOutput;
import dev.hanju.adapter.xml.XmlTagBinding;

/**
 * ContentStreamAdapter 통합 테스트
 *
 * 실제 LLM 스트리밍 시나리오를 중심으로 검증합니다.
 */
@DisplayName("ContentStreamAdapter 통합 테스트")
class ContentStreamAdapterTest {

  // ==================== Helper Methods ====================

  private XmlTagBinding createBinding(TransitionSchema schema, String... paths) {
    XmlTagBinding.Builder builder = XmlTagBinding.from(schema);
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
      XmlTagBinding binding = createBinding(schema, "/thinking", "/answer");

      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      assertThat(adapter).isNotNull();
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("null 바인딩 - 예외")
    void testNullBinding() {
      assertThatThrownBy(() -> new ContentStreamAdapter(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Binding cannot be null");
    }

    @Test
    @DisplayName("초기 상태는 루트")
    void testInitialStateIsRoot() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

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
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      assertThatThrownBy(() -> adapter.feedToken(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 토큰 - 빈 리스트 반환")
    void testEmptyToken() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> result = adapter.feedToken("");

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
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("Hello world");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs.get(0)).content()).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("유효한 태그 - 상태 전이 발생")
    void testValidTag() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      XmlTagBinding binding = createBinding(schema, "/answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("<answer>");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(((XmlStreamOutput.Enter) outputs.get(0)).path()).isEqualTo("/answer");
      assertThat(adapter.getCurrentPath()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("잘못된 태그 - 일반 텍스트로 처리")
    void testInvalidTag() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      XmlTagBinding binding = createBinding(schema, "/answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("<invalid>");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs.get(0)).content()).isEqualTo("<invalid>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("태그와 텍스트 혼합 - 한 토큰에")
    void testTagAndTextMixed() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      XmlTagBinding binding = createBinding(schema, "/answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("텍스트<answer>");

      assertThat(outputs).hasSize(2);
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs.get(0)).content()).isEqualTo("텍스트");
      assertThat(outputs.get(1)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(((XmlStreamOutput.Enter) outputs.get(1)).path()).isEqualTo("/answer");
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
      XmlTagBinding binding = createBinding(schema, "/thinking");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      // <thinking>
      List<XmlStreamOutput> openOutputs = adapter.feedToken("<thinking>");
      assertThat(openOutputs).hasSize(1);
      assertThat(openOutputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking");

      // 내용
      List<XmlStreamOutput> outputs = adapter.feedToken("content");
      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs.get(0)).content()).isEqualTo("content");

      // </thinking>
      List<XmlStreamOutput> closeOutputs = adapter.feedToken("</thinking>");
      assertThat(closeOutputs).hasSize(1);
      assertThat(closeOutputs.get(0)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) closeOutputs.get(0)).path()).isEqualTo("/thinking");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("중첩 태그 전이")
    void testNestedTagTransition() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite", cite -> cite
              .path("id"));
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite")
          .and()
          .bind("/cite/id").tag("id")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      // <cite>
      List<XmlStreamOutput> citeOpen = adapter.feedToken("<cite>");
      assertThat(citeOpen).hasSize(1);
      assertThat(citeOpen.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // <id>
      List<XmlStreamOutput> idOpen = adapter.feedToken("<id>");
      assertThat(idOpen).hasSize(1);
      assertThat(idOpen.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite/id");

      // 내용
      List<XmlStreamOutput> outputs = adapter.feedToken("123");
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Text.class);

      // </id>
      List<XmlStreamOutput> idClose = adapter.feedToken("</id>");
      assertThat(idClose).hasSize(1);
      assertThat(idClose.get(0)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) idClose.get(0)).path()).isEqualTo("/cite/id");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </cite>
      List<XmlStreamOutput> citeClose = adapter.feedToken("</cite>");
      assertThat(citeClose).hasSize(1);
      assertThat(citeClose.get(0)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) citeClose.get(0)).path()).isEqualTo("/cite");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("잘못된 닫기 태그 - 무시")
    void testInvalidCloseTag() {
      TransitionSchema schema = TransitionSchema.root()
          .path("thinking");
      XmlTagBinding binding = createBinding(schema, "/thinking");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      adapter.feedToken("<thinking>");
      assertThat(adapter.getCurrentPath()).isEqualTo("/thinking");

      // 잘못된 닫기 태그
      List<XmlStreamOutput> outputs = adapter.feedToken("</answer>");
      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs.get(0)).content()).isEqualTo("</answer>");
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
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite").alias("rag")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("<rag>");

      assertThat(outputs).hasSize(1);
      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");
    }

    @Test
    @DisplayName("별칭으로 닫기")
    void testCloseWithAlias() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite").alias("rag")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      // <cite>로 열고
      List<XmlStreamOutput> openOutputs = adapter.feedToken("<cite>");
      assertThat(openOutputs).hasSize(1);
      assertThat(openOutputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </rag>로 닫기
      List<XmlStreamOutput> closeOutputs = adapter.feedToken("</rag>");
      assertThat(closeOutputs).hasSize(1);
      assertThat(closeOutputs.get(0)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) closeOutputs.get(0)).path()).isEqualTo("/cite");
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("별칭 혼용")
    void testMixedAliasUsage() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite").alias("rag")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      // <rag>로 열고
      adapter.feedToken("<rag>");
      List<XmlStreamOutput> outputs = adapter.feedToken("content");
      assertThat(adapter.getCurrentPath()).isEqualTo("/cite");

      // </cite>로 닫기
      List<XmlStreamOutput> closeOutputs = adapter.feedToken("</cite>");
      assertThat(closeOutputs).hasSize(1);
      assertThat(closeOutputs.get(0)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) closeOutputs.get(0)).path()).isEqualTo("/cite");
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
      XmlTagBinding binding = createBinding(schema, "/answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      // "<ans"와 "wer>"로 분할
      List<XmlStreamOutput> outputs1 = adapter.feedToken("<ans");
      assertThat(outputs1).isEmpty();

      List<XmlStreamOutput> outputs2 = adapter.feedToken("wer>");
      assertThat(outputs2).hasSize(1);
      assertThat(outputs2.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(adapter.getCurrentPath()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("flush - 남은 버퍼 처리")
    void testFlush() {
      TransitionSchema schema = TransitionSchema.root()
          .path("answer");
      XmlTagBinding binding = createBinding(schema, "/answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      adapter.feedToken("<ans");

      List<XmlStreamOutput> flushed = adapter.flush();
      assertThat(flushed).hasSize(1);
      assertThat(flushed.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) flushed.get(0)).content()).isEqualTo("<ans");
    }

    @Test
    @DisplayName("빈 컨텐츠 토큰 무시")
    void testEmptyContentIgnored() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("");

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
      XmlTagBinding binding = createBinding(schema, "/thinking", "/answer");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> allOutputs = new ArrayList<>();

      allOutputs.addAll(adapter.feedToken("<thinking>"));
      allOutputs.addAll(adapter.feedToken("법률 검토 중..."));
      allOutputs.addAll(adapter.feedToken("</thinking>"));
      allOutputs.addAll(adapter.feedToken("<answer>"));
      allOutputs.addAll(adapter.feedToken("결론은 A입니다."));
      allOutputs.addAll(adapter.feedToken("</answer>"));

      assertThat(allOutputs).hasSize(6);
      assertThat(allOutputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(((XmlStreamOutput.Enter) allOutputs.get(0)).path()).isEqualTo("/thinking");
      assertThat(allOutputs.get(1)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) allOutputs.get(1)).content()).isEqualTo("법률 검토 중...");
      assertThat(allOutputs.get(2)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) allOutputs.get(2)).path()).isEqualTo("/thinking");
      assertThat(allOutputs.get(3)).isInstanceOf(XmlStreamOutput.Enter.class);
      assertThat(((XmlStreamOutput.Enter) allOutputs.get(3)).path()).isEqualTo("/answer");
      assertThat(allOutputs.get(4)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) allOutputs.get(4)).content()).isEqualTo("결론은 A입니다.");
      assertThat(allOutputs.get(5)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) allOutputs.get(5)).path()).isEqualTo("/answer");
    }

    @Test
    @DisplayName("RAG 응답 - cite 구조")
    void testRagResponse() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite", cite -> cite
              .path("id")
              .path("source"));
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite")
          .and()
          .bind("/cite/id").tag("id")
          .and()
          .bind("/cite/source").tag("source")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> allOutputs = new ArrayList<>();

      allOutputs.addAll(adapter.feedToken("<cite>"));
      allOutputs.addAll(adapter.feedToken("<id>"));
      allOutputs.addAll(adapter.feedToken("doc-123"));
      allOutputs.addAll(adapter.feedToken("</id>"));
      allOutputs.addAll(adapter.feedToken("<source>"));
      allOutputs.addAll(adapter.feedToken("법률 문서"));
      allOutputs.addAll(adapter.feedToken("</source>"));
      allOutputs.addAll(adapter.feedToken("</cite>"));

      assertThat(allOutputs).hasSize(8);

      List<XmlStreamOutput.Text> textOutputs = allOutputs.stream()
          .filter(t -> t instanceof XmlStreamOutput.Text)
          .map(t -> (XmlStreamOutput.Text) t)
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
      XmlTagBinding binding = createBinding(schema, "/thinking");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> allOutputs = new ArrayList<>();

      allOutputs.addAll(adapter.feedToken("<thi"));
      allOutputs.addAll(adapter.feedToken("nking>"));
      allOutputs.addAll(adapter.feedToken("Let me "));
      allOutputs.addAll(adapter.feedToken("think"));
      allOutputs.addAll(adapter.feedToken("..."));
      allOutputs.addAll(adapter.feedToken("</"));
      allOutputs.addAll(adapter.feedToken("thi"));
      allOutputs.addAll(adapter.feedToken("nking>"));

      assertThat(allOutputs).hasSize(5);
      assertThat(allOutputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);

      List<XmlStreamOutput.Text> textOutputs = allOutputs.stream()
          .filter(t -> t instanceof XmlStreamOutput.Text)
          .map(t -> (XmlStreamOutput.Text) t)
          .toList();
      assertThat(textOutputs).extracting(XmlStreamOutput.Text::content)
          .containsExactly("Let me ", "think", "...");

      assertThat(allOutputs.get(4)).isInstanceOf(XmlStreamOutput.Exit.class);
      assertThat(((XmlStreamOutput.Exit) allOutputs.get(4)).path()).isEqualTo("/thinking");
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
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs1 = adapter.feedToken("Hello");
      List<XmlStreamOutput> outputs2 = adapter.feedToken(" ");
      List<XmlStreamOutput> outputs3 = adapter.feedToken("World");

      assertThat(outputs1).hasSize(1);
      assertThat(outputs1.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs1.get(0)).content()).isEqualTo("Hello");

      assertThat(outputs2).hasSize(1);
      assertThat(outputs2.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs2.get(0)).content()).isEqualTo(" ");

      assertThat(outputs3).hasSize(1);
      assertThat(outputs3.get(0)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs3.get(0)).content()).isEqualTo("World");
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
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      adapter.feedToken("Hello ");
      adapter.feedToken("World");

      assertThat(adapter.getRaw()).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("태그 포함 원문 누적")
    void testRawWithTags() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      XmlTagBinding binding = createBinding(schema, "/cite");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

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
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      assertThat(adapter.getRaw()).isEmpty();
    }

    @Test
    @DisplayName("flush 후 getRaw()는 초기화되어 빈 문자열")
    void testGetRawResetAfterFlush() {
      TransitionSchema schema = TransitionSchema.root()
          .path("test");
      XmlTagBinding binding = createBinding(schema, "/test");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

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
      XmlTagBinding binding = createBinding(schema, "/cite");
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      // 첫 번째 스트림 - 태그 안에서 끝남 (불균형)
      adapter.feedToken("first <cite>open");
      adapter.flush();

      // flush 후 초기 상태로 복귀
      assertThat(adapter.getCurrentPath()).isEqualTo("/");
      assertThat(adapter.getRaw()).isEmpty();

      // 두 번째 스트림 - 이전 스트림 흔적 없이 독립 동작
      List<XmlStreamOutput> outputs = adapter.feedToken("<cite>second</cite>");

      assertThat(adapter.getRaw()).isEqualTo("<cite>second</cite>");
      assertThat(outputs).anyMatch(o -> o instanceof XmlStreamOutput.Enter e && e.path().equals("/cite"));
      assertThat(outputs).anyMatch(o -> o instanceof XmlStreamOutput.Exit e && e.path().equals("/cite"));
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
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite").attr("id", "source")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">content</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs).hasSize(3);

      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      XmlStreamOutput.Enter enter = (XmlStreamOutput.Enter) outputs.get(0);
      assertThat(enter.path()).isEqualTo("/cite");
      assertThat(enter.attributes()).containsEntry("id", "ref1");
      assertThat(enter.attributes()).containsEntry("source", "wiki");

      assertThat(outputs.get(1)).isInstanceOf(XmlStreamOutput.Text.class);
      assertThat(((XmlStreamOutput.Text) outputs.get(1)).content()).isEqualTo("content");

      assertThat(outputs.get(2)).isInstanceOf(XmlStreamOutput.Exit.class);
    }

    @Test
    @DisplayName("속성이 스키마에 정의되지 않으면 필터링됨")
    void testAttributeFiltering() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite").attr("id")  // source는 허용 안 함
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">x</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      XmlStreamOutput.Enter enter = (XmlStreamOutput.Enter) outputs.get(0);
      assertThat(enter.attributes()).containsEntry("id", "ref1");
      assertThat(enter.attributes()).doesNotContainKey("source");
    }

    @Test
    @DisplayName("속성 없는 태그도 정상 동작")
    void testTagWithoutAttributes() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite").attr("id")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("<cite>content</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      XmlStreamOutput.Enter enter = (XmlStreamOutput.Enter) outputs.get(0);
      assertThat(enter.attributes()).isEmpty();
    }

    @Test
    @DisplayName("속성이 토큰 경계에 걸침")
    void testAttributeSplitAcrossTokens() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      XmlTagBinding binding = XmlTagBinding.from(schema)
          .bind("/cite").tag("cite").attr("id")
          .and()
          .build();
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> allOutputs = new ArrayList<>();
      allOutputs.addAll(adapter.feedToken("<cite id=\"ref"));
      allOutputs.addAll(adapter.feedToken("1\">content</cite>"));
      allOutputs.addAll(adapter.flush());

      assertThat(allOutputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      XmlStreamOutput.Enter enter = (XmlStreamOutput.Enter) allOutputs.get(0);
      assertThat(enter.attributes()).containsEntry("id", "ref1");
    }

    @Test
    @DisplayName("스키마에 attr 정의 안 하면 모든 속성 필터링")
    void testNoAttrDefinedFiltersAll() {
      TransitionSchema schema = TransitionSchema.root()
          .path("cite");
      XmlTagBinding binding = createBinding(schema, "/cite");  // attr 없음
      ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

      List<XmlStreamOutput> outputs = adapter.feedToken("<cite id=\"ref1\">content</cite>");
      outputs.addAll(adapter.flush());

      assertThat(outputs.get(0)).isInstanceOf(XmlStreamOutput.Enter.class);
      XmlStreamOutput.Enter enter = (XmlStreamOutput.Enter) outputs.get(0);
      assertThat(enter.attributes()).isEmpty();
    }
  }
}
