package dev.hanju.adapter.transition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.transition.TransitionNode;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.transition.TransitionTable;

import static org.assertj.core.api.Assertions.*;

/**
 * TransitionTable 테스트
 *
 * 테스트 구성:
 * 1. 생성 및 초기화
 * 2. tryOpen - 열기 전이 처리
 * 3. tryClose - 닫기 전이 처리
 * 4. 엣지 케이스
 * 5. 실제 사용 시나리오
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
                .path("think")
                .path("cite");

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
            assertThat(table.getRoot().isRoot()).isTrue();
        }
    }

    // ==================== 2. tryOpen - 열기 전이 처리 ====================

    @Nested
    @DisplayName("tryOpen - 열기 전이 처리")
    class TryOpenTests {

        @Test
        @DisplayName("유효한 전이 - 다음 상태 반환")
        void testValidTransition() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think")
                .path("cite");
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
                .path("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode cite = table.tryOpen(root, "cite"); // cite는 허용되지 않음

            assertThat(cite).isNull();
        }

        @Test
        @DisplayName("중첩 경로 - 허용되지 않은 경로는 null")
        void testNestedPathNotAllowed() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think")
                .path("cite");
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
                .path("rag", rag -> rag
                    .path("id"));
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
                .path("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode result = table.tryOpen(null, "think");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null 세그먼트 이름 - null 반환")
        void testNullSegmentName() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode result = table.tryOpen(root, null);

            assertThat(result).isNull();
        }
    }

    // ==================== 3. tryClose - 닫기 전이 처리 ====================

    @Nested
    @DisplayName("tryClose - 닫기 전이 처리")
    class TryCloseTests {

        @Test
        @DisplayName("일반 닫기 - 부모 반환")
        void testNormalClose() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode cite = table.tryOpen(root, "cite");
            TransitionNode back = table.tryClose(cite);

            assertThat(back).isNotNull();
            assertThat(back).isEqualTo(root);
            assertThat(back.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("루트에서 닫기 - null 반환")
        void testCloseOnRoot() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();
            TransitionNode result = table.tryClose(root);

            assertThat(result).isNull(); // ROOT는 닫을 수 없음
        }

        @Test
        @DisplayName("다단계 닫기")
        void testMultiLevelClose() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("subsection", subsection -> subsection
                        .path("content")));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode root = table.getRoot();

            // <section><subsection><content>
            TransitionNode section = table.tryOpen(root, "section");
            TransitionNode subsection = table.tryOpen(section, "subsection");
            TransitionNode content = table.tryOpen(subsection, "content");

            // </content>
            TransitionNode backToSubsection = table.tryClose(content);
            assertThat(backToSubsection).isEqualTo(subsection);

            // </subsection>
            TransitionNode backToSection = table.tryClose(backToSubsection);
            assertThat(backToSection).isEqualTo(section);

            // </section>
            TransitionNode backToRoot = table.tryClose(backToSection);
            assertThat(backToRoot).isEqualTo(root);
        }

        @Test
        @DisplayName("null 현재 상태 - null 반환")
        void testNullCurrentState() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode result = table.tryClose(null);

            assertThat(result).isNull();
        }
    }

    // ==================== 4. 엣지 케이스 ====================

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("깊은 중첩 구조")
        void testDeepNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .path("a", a -> a
                    .path("b", b -> b
                        .path("c", c -> c
                            .path("d"))));
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
        @DisplayName("동일 이름 경로 다른 부모")
        void testSameNameDifferentPaths() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("title"))
                .path("article", article -> article
                    .path("title"));
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

    // ==================== 5. 실제 사용 시나리오 ====================

    @Nested
    @DisplayName("실제 사용 시나리오 - 상태 전이")
    class RealWorldScenarios {

        @Test
        @DisplayName("기본 전이 시퀀스")
        void testBasicTransitionSequence() {
            TransitionSchema schema = TransitionSchema.root()
                .path("thinking")
                .path("answer");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // open thinking
            current = table.tryOpen(current, "thinking");
            assertThat(current.getPath()).isEqualTo("/thinking");

            // close thinking
            current = table.tryClose(current);
            assertThat(current.getPath()).isEqualTo("/");

            // open answer
            current = table.tryOpen(current, "answer");
            assertThat(current.getPath()).isEqualTo("/answer");

            // close answer
            current = table.tryClose(current);
            assertThat(current.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("RAG 응답 처리")
        void testRagResponseHandling() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite", cite -> cite
                    .path("id")
                    .path("source"));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // open cite
            current = table.tryOpen(current, "cite");
            assertThat(current.getPath()).isEqualTo("/cite");

            // open id
            current = table.tryOpen(current, "id");
            assertThat(current.getPath()).isEqualTo("/cite/id");

            // close id
            current = table.tryClose(current);
            assertThat(current.getPath()).isEqualTo("/cite");

            // open source
            current = table.tryOpen(current, "source");
            assertThat(current.getPath()).isEqualTo("/cite/source");

            // close source
            current = table.tryClose(current);
            assertThat(current.getPath()).isEqualTo("/cite");

            // close cite
            current = table.tryClose(current);
            assertThat(current.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("잘못된 전이 무시 시나리오")
        void testInvalidTransitionIgnored() {
            TransitionSchema schema = TransitionSchema.root()
                .path("answer");
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // 허용되지 않은 전이 시도
            TransitionNode invalid = table.tryOpen(current, "invalid");
            assertThat(invalid).isNull();

            // 상태 변경 없음
            assertThat(current.getPath()).isEqualTo("/");

            // 유효한 전이는 여전히 동작
            TransitionNode answer = table.tryOpen(current, "answer");
            assertThat(answer).isNotNull();
            assertThat(answer.getPath()).isEqualTo("/answer");
        }

        @Test
        @DisplayName("복잡한 중첩 문서")
        void testComplexNestedDocument() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("title")
                    .path("content")
                    .path("subsection", subsection -> subsection
                        .path("title")
                        .path("content")));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // open section
            current = table.tryOpen(current, "section");
            assertThat(current.getPath()).isEqualTo("/section");

            // open title
            current = table.tryOpen(current, "title");
            assertThat(current.getPath()).isEqualTo("/section/title");

            // close title
            current = table.tryClose(current);

            // open subsection
            current = table.tryOpen(current, "subsection");
            assertThat(current.getPath()).isEqualTo("/section/subsection");

            // open content
            current = table.tryOpen(current, "content");
            assertThat(current.getPath()).isEqualTo("/section/subsection/content");

            // close content
            current = table.tryClose(current);

            // close subsection
            current = table.tryClose(current);

            // close section
            current = table.tryClose(current);

            assertThat(current.getPath()).isEqualTo("/");
        }

        @Test
        @DisplayName("전체 전이 시퀀스 - 여러 경로 왕복")
        void testFullTransitionSequence() {
            TransitionSchema schema = TransitionSchema.root()
                .path("rag", rag -> rag
                    .path("id"));
            TransitionTable table = new TransitionTable(schema);

            TransitionNode current = table.getRoot();

            // open rag
            current = table.tryOpen(current, "rag");
            assertThat(current.getPath()).isEqualTo("/rag");

            // open id
            current = table.tryOpen(current, "id");
            assertThat(current.getPath()).isEqualTo("/rag/id");

            // close id
            current = table.tryClose(current);
            assertThat(current.getPath()).isEqualTo("/rag");

            // close rag
            current = table.tryClose(current);
            assertThat(current.getPath()).isEqualTo("/");
        }
    }
}
