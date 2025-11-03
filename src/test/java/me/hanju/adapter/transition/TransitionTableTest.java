package me.hanju.adapter.transition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * TransitionTable 테스트
 *
 * 테스트 구성:
 * 1. 생성 및 초기화
 * 2. 잘못된 입력 처리
 * 3. tryOpen - 여는 태그 처리
 * 4. tryClose - 닫는 태그 처리 (별칭 호환)
 * 5. 엣지 케이스
 * 6. 별칭 지원 검증
 * 7. 실제 사용 시나리오
 */
@DisplayName("TransitionTable 테스트")
class TransitionTableTest {

    // ==================== 1. 생성 및 초기화 ====================

    @Nested
    @DisplayName("생성 및 초기화")
    class CreationAndInitialization {

        @Test
        @DisplayName("스키마로 테이블 생성")
        void testCreateWithSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think")
                .tag("cite");

            TransitionTable table = new TransitionTable(schema);

            assertThat(table).isNotNull();
            assertThat(table.getRoot()).isNotNull();
            assertThat(table.getRoot().isRoot()).isTrue();
        }

        @Test
        @DisplayName("null 스키마 - 예외")
        void testNullSchema() {
            assertThatThrownBy(() -> new TransitionTable(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema cannot be null");
        }

        @Test
        @DisplayName("빈 스키마 - 루트만 존재")
        void testEmptySchema() {
            TransitionSchema schema = TransitionSchema.root();
            TransitionTable table = new TransitionTable(schema);

            assertThat(table.getRoot()).isNotNull();
            assertThat(table.getAllTagNames()).isEmpty();
        }
    }

    // ==================== 2. tryOpen - 여는 태그 처리 ====================

    @Nested
    @DisplayName("tryOpen - 여는 태그 처리")
    class TryOpenTests {

        @Test
        @DisplayName("유효한 전이 - 다음 상태 반환")
        void testValidTransition() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think")
                .tag("cite");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode think = table.tryOpen(root, "think");

            assertThat(think).isNotNull();
            assertThat(think.getPath()).isEqualTo("/think");
        }

        @Test
        @DisplayName("잘못된 전이 - null 반환")
        void testInvalidTransition() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode cite = table.tryOpen(root, "cite"); // cite는 허용되지 않음

            assertThat(cite).isNull();
        }

        @Test
        @DisplayName("중첩 경로 - 허용되지 않은 경로는 null")
        void testNestedPathNotAllowed() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think")
                .tag("cite");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode think = table.tryOpen(root, "think");
            TransitionNode thinkCite = table.tryOpen(think, "cite"); // /think/cite는 허용되지 않음

            assertThat(thinkCite).isNull();
        }

        @Test
        @DisplayName("다단계 전이 - 성공")
        void testMultiLevelTransition() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("rag", rag -> rag
                    .tag("id"));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode rag = table.tryOpen(root, "rag");
            TransitionNode ragId = table.tryOpen(rag, "id");

            assertThat(ragId).isNotNull();
            assertThat(ragId.getPath()).isEqualTo("/rag/id");
        }

        @Test
        @DisplayName("null 현재 상태 - null 반환")
        void testNullCurrentState() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode result = table.tryOpen(null, "think");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null 태그 이름 - null 반환")
        void testNullTagName() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode result = table.tryOpen(root, null);

            assertThat(result).isNull();
        }
    }

    // ==================== 3. tryClose - 닫는 태그 처리 ====================

    @Nested
    @DisplayName("tryClose - 닫는 태그 처리")
    class TryCloseTests {

        @Test
        @DisplayName("일치하는 태그 - 부모 반환")
        void testMatchingTag() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode cite = table.tryOpen(root, "cite");
            TransitionNode back = table.tryClose(cite, "cite");

            assertThat(back).isNotNull();
            assertThat(back).isEqualTo(root);
            assertThat(back.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("일치하지 않는 태그 - null 반환")
        void testNonMatchingTag() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode think = table.tryOpen(root, "think");
            TransitionNode result = table.tryClose(think, "cite"); // </cite>는 /think과 매칭 안됨

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("루트에서 닫기 - null 반환")
        void testCloseOnRoot() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode result = table.tryClose(root, "cite");

            assertThat(result).isNull(); // ROOT는 닫을 수 없음
        }

        @Test
        @DisplayName("다단계 닫기")
        void testMultiLevelClose() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("subsection", subsection -> subsection
                        .tag("content")));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();

            // <section><subsection><content>
            TransitionNode section = table.tryOpen(root, "section");
            TransitionNode subsection = table.tryOpen(section, "subsection");
            TransitionNode content = table.tryOpen(subsection, "content");

            // </content>
            TransitionNode backToSubsection = table.tryClose(content, "content");
            assertThat(backToSubsection).isEqualTo(subsection);

            // </subsection>
            TransitionNode backToSection = table.tryClose(backToSubsection, "subsection");
            assertThat(backToSection).isEqualTo(section);

            // </section>
            TransitionNode backToRoot = table.tryClose(backToSection, "section");
            assertThat(backToRoot).isEqualTo(root);
        }

        @Test
        @DisplayName("null 현재 상태 - null 반환")
        void testNullCurrentState() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode result = table.tryClose(null, "think");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null 태그 이름 - null 반환")
        void testNullTagName() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode think = table.tryOpen(root, "think");
            TransitionNode result = table.tryClose(think, null);

            assertThat(result).isNull();
        }
    }

    // ==================== 4. 별칭 지원 검증 ====================

    @Nested
    @DisplayName("별칭 지원")
    class AliasSupport {

        @Test
        @DisplayName("별칭으로 열기")
        void testOpenWithAlias() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();

            // "cite"로 열기
            TransitionNode cite = table.tryOpen(root, "cite");
            assertThat(cite).isNotNull();
            assertThat(cite.getPath()).isEqualTo("/cite");

            table.tryClose(cite, "cite"); // 닫고 다시 테스트

            // "rag" 별칭으로 열기 - 같은 노드로 전이
            TransitionNode rag = table.tryOpen(root, "rag");
            assertThat(rag).isNotNull();
            assertThat(rag.getPath()).isEqualTo("/cite");
        }

        @Test
        @DisplayName("별칭으로 닫기")
        void testCloseWithAlias() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();

            // "cite"로 열고 "rag"로 닫기 (별칭 호환)
            TransitionNode cite = table.tryOpen(root, "cite");
            TransitionNode back = table.tryClose(cite, "rag");

            assertThat(back).isNotNull();
            assertThat(back).isEqualTo(root);
        }

        @Test
        @DisplayName("별칭으로 열고 원본 이름으로 닫기")
        void testOpenWithAliasCloseWithOriginal() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();

            // "rag"로 열고 "cite"로 닫기
            TransitionNode rag = table.tryOpen(root, "rag");
            TransitionNode back = table.tryClose(rag, "cite");

            assertThat(back).isNotNull();
            assertThat(back).isEqualTo(root);
        }

        @Test
        @DisplayName("여러 별칭 지원")
        void testMultipleAliases() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag", "reference", "source");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();

            // 모든 별칭으로 같은 경로 전이
            TransitionNode cite = table.tryOpen(root, "cite");
            TransitionNode rag = table.tryOpen(root, "rag");
            TransitionNode reference = table.tryOpen(root, "reference");
            TransitionNode source = table.tryOpen(root, "source");

            assertThat(cite.getPath()).isEqualTo("/cite");
            assertThat(rag.getPath()).isEqualTo("/cite");
            assertThat(reference.getPath()).isEqualTo("/cite");
            assertThat(source.getPath()).isEqualTo("/cite");
        }

        @Test
        @DisplayName("getAllTagNames - 별칭 포함")
        void testGetAllTagNamesWithAliases() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag")
                .tag("think");
            TransitionTable table = new TransitionTable(schema);

            Set<String> tagNames = table.getAllTagNames();

            assertThat(tagNames).containsExactlyInAnyOrder("cite", "rag", "think");
        }
    }

    // ==================== 5. 엣지 케이스 ====================

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("깊은 중첩 구조")
        void testDeepNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("a", a -> a
                    .tag("b", b -> b
                        .tag("c", c -> c
                            .tag("d"))));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // 순차적으로 열기
            current = table.tryOpen(current, "a");
            assertThat(current.getPath()).isEqualTo("/a");

            current = table.tryOpen(current, "b");
            assertThat(current.getPath()).isEqualTo("/a/b");

            current = table.tryOpen(current, "c");
            assertThat(current.getPath()).isEqualTo("/a/b/c");

            current = table.tryOpen(current, "d");
            assertThat(current.getPath()).isEqualTo("/a/b/c/d");
        }

        @Test
        @DisplayName("동일 이름 태그 다른 경로")
        void testSameNameDifferentPaths() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("title"))
                .tag("article", article -> article
                    .tag("title"));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();

            // /section/title
            TransitionNode section = table.tryOpen(root, "section");
            TransitionNode sectionTitle = table.tryOpen(section, "title");
            assertThat(sectionTitle.getPath()).isEqualTo("/section/title");

            // /article/title
            TransitionNode article = table.tryOpen(root, "article");
            TransitionNode articleTitle = table.tryOpen(article, "title");
            assertThat(articleTitle.getPath()).isEqualTo("/article/title");

            // 다른 노드
            assertThat(sectionTitle).isNotEqualTo(articleTitle);
        }
    }

    // ==================== 6. 실제 사용 시나리오 ====================

    @Nested
    @DisplayName("실제 사용 시나리오 - LLM 응답 처리")
    class RealWorldLLMScenarios {

        @Test
        @DisplayName("기본 전이 시퀀스")
        void testBasicTransitionSequence() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("thinking")
                .tag("answer");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // <thinking>
            current = table.tryOpen(current, "thinking");
            assertThat(current.getPath()).isEqualTo("/thinking");

            // </thinking>
            current = table.tryClose(current, "thinking");
            assertThat(current.getPath()).isEqualTo("/");

            // <answer>
            current = table.tryOpen(current, "answer");
            assertThat(current.getPath()).isEqualTo("/answer");

            // </answer>
            current = table.tryClose(current, "answer");
            assertThat(current.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("RAG 응답 처리")
        void testRagResponseHandling() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite", cite -> cite
                    .tag("id")
                    .tag("source"));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // <cite>
            current = table.tryOpen(current, "cite");
            assertThat(current.getPath()).isEqualTo("/cite");

            // <id>
            current = table.tryOpen(current, "id");
            assertThat(current.getPath()).isEqualTo("/cite/id");

            // </id>
            current = table.tryClose(current, "id");
            assertThat(current.getPath()).isEqualTo("/cite");

            // <source>
            current = table.tryOpen(current, "source");
            assertThat(current.getPath()).isEqualTo("/cite/source");

            // </source>
            current = table.tryClose(current, "source");
            assertThat(current.getPath()).isEqualTo("/cite");

            // </cite>
            current = table.tryClose(current, "cite");
            assertThat(current.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("잘못된 태그 무시 시나리오")
        void testInvalidTagIgnored() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("answer");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // 허용되지 않은 태그 시도
            TransitionNode invalid = table.tryOpen(current, "invalid");
            assertThat(invalid).isNull();

            // 상태 변경 없음
            assertThat(current.getPath()).isEqualTo("/");

            // 유효한 태그는 여전히 동작
            TransitionNode answer = table.tryOpen(current, "answer");
            assertThat(answer).isNotNull();
            assertThat(answer.getPath()).isEqualTo("/answer");
        }

        @Test
        @DisplayName("별칭 혼용 시나리오")
        void testMixedAliasUsage() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // <rag>로 열고
            current = table.tryOpen(current, "rag");
            assertThat(current.getPath()).isEqualTo("/cite");

            // </cite>로 닫기 - 별칭 호환
            current = table.tryClose(current, "cite");
            assertThat(current).isNotNull();
            assertThat(current.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("복잡한 중첩 문서")
        void testComplexNestedDocument() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("title")
                    .tag("content")
                    .tag("subsection", subsection -> subsection
                        .tag("title")
                        .tag("content")));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // <section>
            current = table.tryOpen(current, "section");
            assertThat(current.getPath()).isEqualTo("/section");

            // <title>
            current = table.tryOpen(current, "title");
            assertThat(current.getPath()).isEqualTo("/section/title");

            // </title>
            current = table.tryClose(current, "title");

            // <subsection>
            current = table.tryOpen(current, "subsection");
            assertThat(current.getPath()).isEqualTo("/section/subsection");

            // <content>
            current = table.tryOpen(current, "content");
            assertThat(current.getPath()).isEqualTo("/section/subsection/content");

            // </content>
            current = table.tryClose(current, "content");

            // </subsection>
            current = table.tryClose(current, "subsection");

            // </section>
            current = table.tryClose(current, "section");

            assertThat(current.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("전체 전이 시퀀스 - 여러 경로 왕복")
        void testFullTransitionSequence() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("rag", rag -> rag
                    .tag("id"));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // <rag>
            current = table.tryOpen(current, "rag");
            assertEquals("/rag", current.getPath());

            // <id>
            current = table.tryOpen(current, "id");
            assertEquals("/rag/id", current.getPath());

            // </id>
            current = table.tryClose(current, "id");
            assertEquals("/rag", current.getPath());

            // </rag>
            current = table.tryClose(current, "rag");
            assertEquals("/", current.getPath());
        }
    }

    // Helper method for cleaner assertions
    private void assertEquals(String expected, String actual) {
        assertThat(actual).isEqualTo(expected);
    }
}
