package dev.hanju.adapter.transition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

            TransitionTable table = new TransitionTable(schema.toPaths());

            assertThat(table).isNotNull();
            assertThat(table.getRoot()).isEqualTo("/");
        }

        @Test
        @DisplayName("null 스키마 - 예외")
        void testNullSchema() {
            assertThatThrownBy(() -> new TransitionTable(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allPaths cannot be null");
        }

        @Test
        @DisplayName("빈 스키마 - 루트만 존재")
        void testEmptySchema() {
            TransitionSchema schema = TransitionSchema.root();
            TransitionTable table = new TransitionTable(schema.toPaths());

            assertThat(table.getRoot()).isEqualTo("/");
        }
    }

    // ==================== 2. tryOpen - 열기 전이 처리 ====================

    @Nested
    @DisplayName("tryOpen - 열기 전이 처리")
    class TryOpenTests {

        @Test
        @DisplayName("유효한 전이 - 다음 경로 반환")
        void testValidTransition() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think")
                .path("cite");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();
            String think = table.tryOpen(root, "think");

            assertThat(think).isEqualTo("/think");
        }

        @Test
        @DisplayName("잘못된 전이 - null 반환")
        void testInvalidTransition() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();
            String cite = table.tryOpen(root, "cite"); // cite는 허용되지 않음

            assertThat(cite).isNull();
        }

        @Test
        @DisplayName("중첩 경로 - 허용되지 않은 경로는 null")
        void testNestedPathNotAllowed() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think")
                .path("cite");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();
            String think = table.tryOpen(root, "think");
            String thinkCite = table.tryOpen(think, "cite"); // /think/cite는 허용되지 않음

            assertThat(thinkCite).isNull();
        }

        @Test
        @DisplayName("다단계 전이 - 성공")
        void testMultiLevelTransition() {
            TransitionSchema schema = TransitionSchema.root()
                .path("rag", rag -> rag
                    .path("id"));
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();
            String rag = table.tryOpen(root, "rag");
            String ragId = table.tryOpen(rag, "id");

            assertThat(ragId).isEqualTo("/rag/id");
        }

        @Test
        @DisplayName("null 현재 경로 - null 반환")
        void testNullCurrentState() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String result = table.tryOpen(null, "think");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("null 세그먼트 이름 - null 반환")
        void testNullSegmentName() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();
            String result = table.tryOpen(root, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("알 수 없는 현재 경로 - null 반환")
        void testUnknownCurrentPath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String result = table.tryOpen("/unknown", "think");

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
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();
            String cite = table.tryOpen(root, "cite");
            String back = table.tryClose(cite);

            assertThat(back).isEqualTo(root);
            assertThat(back).isEqualTo("/");
        }

        @Test
        @DisplayName("루트에서 닫기 - null 반환")
        void testCloseOnRoot() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();
            String result = table.tryClose(root);

            assertThat(result).isNull(); // ROOT는 닫을 수 없음
        }

        @Test
        @DisplayName("다단계 닫기")
        void testMultiLevelClose() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("subsection", subsection -> subsection
                        .path("content")));
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();

            // <section><subsection><content>
            String section = table.tryOpen(root, "section");
            String subsection = table.tryOpen(section, "subsection");
            String content = table.tryOpen(subsection, "content");

            // </content>
            String backToSubsection = table.tryClose(content);
            assertThat(backToSubsection).isEqualTo(subsection);

            // </subsection>
            String backToSection = table.tryClose(backToSubsection);
            assertThat(backToSection).isEqualTo(section);

            // </section>
            String backToRoot = table.tryClose(backToSection);
            assertThat(backToRoot).isEqualTo(root);
        }

        @Test
        @DisplayName("null 현재 경로 - null 반환")
        void testNullCurrentState() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String result = table.tryClose(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("알 수 없는 현재 경로 - null 반환")
        void testUnknownCurrentPath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String result = table.tryClose("/unknown");

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
            TransitionTable table = new TransitionTable(schema.toPaths());

            String current = table.getRoot();

            // 순차적으로 열기
            current = table.tryOpen(current, "a");
            assertThat(current).isEqualTo("/a");

            current = table.tryOpen(current, "b");
            assertThat(current).isEqualTo("/a/b");

            current = table.tryOpen(current, "c");
            assertThat(current).isEqualTo("/a/b/c");

            current = table.tryOpen(current, "d");
            assertThat(current).isEqualTo("/a/b/c/d");
        }

        @Test
        @DisplayName("동일 이름 경로 다른 부모")
        void testSameNameDifferentPaths() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("title"))
                .path("article", article -> article
                    .path("title"));
            TransitionTable table = new TransitionTable(schema.toPaths());

            String root = table.getRoot();

            // /section/title
            String section = table.tryOpen(root, "section");
            String sectionTitle = table.tryOpen(section, "title");
            assertThat(sectionTitle).isEqualTo("/section/title");

            // /article/title
            String article = table.tryOpen(root, "article");
            String articleTitle = table.tryOpen(article, "title");
            assertThat(articleTitle).isEqualTo("/article/title");

            // 다른 경로
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
            TransitionTable table = new TransitionTable(schema.toPaths());

            String current = table.getRoot();

            // open thinking
            current = table.tryOpen(current, "thinking");
            assertThat(current).isEqualTo("/thinking");

            // close thinking
            current = table.tryClose(current);
            assertThat(current).isEqualTo("/");

            // open answer
            current = table.tryOpen(current, "answer");
            assertThat(current).isEqualTo("/answer");

            // close answer
            current = table.tryClose(current);
            assertThat(current).isEqualTo("/");
        }

        @Test
        @DisplayName("RAG 응답 처리")
        void testRagResponseHandling() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite", cite -> cite
                    .path("id")
                    .path("source"));
            TransitionTable table = new TransitionTable(schema.toPaths());

            String current = table.getRoot();

            // open cite
            current = table.tryOpen(current, "cite");
            assertThat(current).isEqualTo("/cite");

            // open id
            current = table.tryOpen(current, "id");
            assertThat(current).isEqualTo("/cite/id");

            // close id
            current = table.tryClose(current);
            assertThat(current).isEqualTo("/cite");

            // open source
            current = table.tryOpen(current, "source");
            assertThat(current).isEqualTo("/cite/source");

            // close source
            current = table.tryClose(current);
            assertThat(current).isEqualTo("/cite");

            // close cite
            current = table.tryClose(current);
            assertThat(current).isEqualTo("/");
        }

        @Test
        @DisplayName("잘못된 전이 무시 시나리오")
        void testInvalidTransitionIgnored() {
            TransitionSchema schema = TransitionSchema.root()
                .path("answer");
            TransitionTable table = new TransitionTable(schema.toPaths());

            String current = table.getRoot();

            // 허용되지 않은 전이 시도
            String invalid = table.tryOpen(current, "invalid");
            assertThat(invalid).isNull();

            // 상태 변경 없음
            assertThat(current).isEqualTo("/");

            // 유효한 전이는 여전히 동작
            String answer = table.tryOpen(current, "answer");
            assertThat(answer).isEqualTo("/answer");
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
            TransitionTable table = new TransitionTable(schema.toPaths());

            String current = table.getRoot();

            // open section
            current = table.tryOpen(current, "section");
            assertThat(current).isEqualTo("/section");

            // open title
            current = table.tryOpen(current, "title");
            assertThat(current).isEqualTo("/section/title");

            // close title
            current = table.tryClose(current);

            // open subsection
            current = table.tryOpen(current, "subsection");
            assertThat(current).isEqualTo("/section/subsection");

            // open content
            current = table.tryOpen(current, "content");
            assertThat(current).isEqualTo("/section/subsection/content");

            // close content
            current = table.tryClose(current);

            // close subsection
            current = table.tryClose(current);

            // close section
            current = table.tryClose(current);

            assertThat(current).isEqualTo("/");
        }

        @Test
        @DisplayName("전체 전이 시퀀스 - 여러 경로 왕복")
        void testFullTransitionSequence() {
            TransitionSchema schema = TransitionSchema.root()
                .path("rag", rag -> rag
                    .path("id"));
            TransitionTable table = new TransitionTable(schema.toPaths());

            String current = table.getRoot();

            // open rag
            current = table.tryOpen(current, "rag");
            assertThat(current).isEqualTo("/rag");

            // open id
            current = table.tryOpen(current, "id");
            assertThat(current).isEqualTo("/rag/id");

            // close id
            current = table.tryClose(current);
            assertThat(current).isEqualTo("/rag");

            // close rag
            current = table.tryClose(current);
            assertThat(current).isEqualTo("/");
        }
    }
}
