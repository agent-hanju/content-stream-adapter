package dev.hanju.adapter.transition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.transition.TransitionSchema;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * TransitionSchema 테스트
 *
 * 테스트 구성:
 * 1. 생성 및 초기화
 * 2. 잘못된 입력 처리
 * 3. 기본 경로 추가
 * 4. 중첩 경로 추가
 * 5. 스키마 조회 메서드
 * 6. 엣지 케이스
 * 7. 실제 사용 시나리오
 */
@DisplayName("TransitionSchema 테스트")
class TransitionSchemaTest {

    // ==================== 1. 생성 및 초기화 ====================

    @Nested
    @DisplayName("생성 및 초기화")
    class CreationAndInitialization {

        @Test
        @DisplayName("root()로 스키마 생성")
        void testRootCreation() {
            TransitionSchema schema = TransitionSchema.root();

            assertThat(schema).isNotNull();
            assertThat(schema.getCurrentPath()).isEqualTo("/");
            assertThat(schema.getPathCount()).isZero();
        }
    }

    // ==================== 2. 잘못된 입력 처리 ====================

    @Nested
    @DisplayName("잘못된 입력 처리")
    class InvalidInputHandling {

        @Test
        @DisplayName("null 경로 이름 - 예외")
        void testNullPathName() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.path(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path name cannot be null or empty");
        }

        @Test
        @DisplayName("빈 경로 이름 - 예외")
        void testEmptyPathName() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.path(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path name cannot be null or empty");
        }

        @Test
        @DisplayName("중첩 경로에 null 이름 - 예외")
        void testNestedPathWithNullName() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.path(null, nested -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path name cannot be null or empty");
        }

        @Test
        @DisplayName("중첩 경로에 null builder - 예외")
        void testNestedPathWithNullBuilder() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.path("section", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Builder cannot be null");
        }
    }

    // ==================== 3. 기본 경로 추가 ====================

    @Nested
    @DisplayName("기본 경로 추가")
    class BasicPathAddition {

        @Test
        @DisplayName("단일 경로 추가")
        void testSinglePath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactly("/think");
        }

        @Test
        @DisplayName("여러 경로 추가")
        void testMultiplePaths() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think")
                .path("cite")
                .path("rag");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder("/think", "/cite", "/rag");
        }

        @Test
        @DisplayName("경로 추가 후 경로 수 확인")
        void testPathCountAfterAddition() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section");

            assertThat(schema.getPathCount()).isEqualTo(1);
        }
    }

    // ==================== 4. 중첩 경로 추가 ====================

    @Nested
    @DisplayName("중첩 경로 추가")
    class NestedPathAddition {

        @Test
        @DisplayName("1단계 중첩")
        void testSingleLevelNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("content"));

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder("/section", "/section/content");
        }

        @Test
        @DisplayName("2단계 중첩")
        void testTwoLevelNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("subsection", subsection -> subsection
                        .path("content")));

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder(
                "/section",
                "/section/subsection",
                "/section/subsection/content"
            );
        }

        @Test
        @DisplayName("형제 경로")
        void testSiblingPaths() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("title")
                    .path("content")
                    .path("metadata"));

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder(
                "/section",
                "/section/title",
                "/section/content",
                "/section/metadata"
            );
        }

        @Test
        @DisplayName("복잡한 중첩 구조")
        void testComplexNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("title")
                    .path("subsection", subsection -> subsection
                        .path("title")
                        .path("content"))
                    .path("metadata"))
                .path("cite");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder(
                "/section",
                "/section/title",
                "/section/subsection",
                "/section/subsection/title",
                "/section/subsection/content",
                "/section/metadata",
                "/cite"
            );
        }
    }

    // ==================== 5. 스키마 조회 메서드 ====================

    @Nested
    @DisplayName("스키마 조회 메서드")
    class SchemaQueryMethods {

        @Test
        @DisplayName("getAllPaths - 수정 불가")
        void testAllPathsUnmodifiable() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            Set<String> paths = schema.getAllPaths();

            assertThatThrownBy(() -> paths.add("/new"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getPathCount - 정확한 경로 수 반환")
        void testPathCount() {
            TransitionSchema schema = TransitionSchema.root()
                .path("think")
                .path("cite")
                .path("section", section -> section
                    .path("content"));

            assertThat(schema.getPathCount()).isEqualTo(4);
        }
    }

    // ==================== 6. 엣지 케이스 ====================

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("빈 스키마")
        void testEmptySchema() {
            TransitionSchema schema = TransitionSchema.root();

            assertThat(schema.getAllPaths()).isEmpty();
            assertThat(schema.getPathCount()).isZero();
        }

        @Test
        @DisplayName("매우 긴 중첩")
        void testVeryDeepNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .path("a", a -> a
                    .path("b", b -> b
                        .path("c", c -> c
                            .path("d", d -> d
                                .path("e")))));

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).contains("/a/b/c/d/e");
        }

        @Test
        @DisplayName("동일 이름 경로 다른 부모")
        void testSameNameDifferentParents() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("title"))
                .path("article", article -> article
                    .path("title"));

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder(
                "/section", "/section/title",
                "/article", "/article/title"
            );
        }
    }

    // ==================== 7. 실제 사용 시나리오 ====================

    @Nested
    @DisplayName("실제 사용 시나리오 - LLM 응답 구조")
    class RealWorldLLMScenarios {

        @Test
        @DisplayName("기본 LLM 스키마")
        void testBasicLLMSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .path("thinking")
                .path("answer");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder("/thinking", "/answer");
        }

        @Test
        @DisplayName("RAG 스키마")
        void testRagSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite", cite -> cite
                    .path("id")
                    .path("source"));

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder("/cite", "/cite/id", "/cite/source");
        }

        @Test
        @DisplayName("복잡한 문서 구조")
        void testComplexDocumentStructure() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("title")
                    .path("content")
                    .path("subsection", subsection -> subsection
                        .path("title")
                        .path("content")))
                .path("metadata");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder(
                "/section",
                "/section/title",
                "/section/content",
                "/section/subsection",
                "/section/subsection/title",
                "/section/subsection/content",
                "/metadata"
            );
        }

        @Test
        @DisplayName("Fluent API 체이닝")
        void testFluentAPIChaining() {
            TransitionSchema schema = TransitionSchema.root()
                .path("search", search -> search
                    .path("query")
                    .path("results"))
                .path("thinking")
                .path("answer", answer -> answer
                    .path("summary")
                    .path("details"));

            assertThat(schema.getAllPaths()).hasSize(7);
        }

        @Test
        @DisplayName("tool_use 스키마")
        void testToolUseSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .path("function_calls", calls -> calls
                    .path("invoke", invoke -> invoke
                        .path("tool_name")
                        .path("parameters", params -> params
                            .path("parameter"))));

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder(
                "/function_calls",
                "/function_calls/invoke",
                "/function_calls/invoke/tool_name",
                "/function_calls/invoke/parameters",
                "/function_calls/invoke/parameters/parameter"
            );
        }

        @Test
        @DisplayName("예제 코드와 동일한 스키마")
        void testExampleFromJavadoc() {
            TransitionSchema schema = TransitionSchema.root()
                .path("section", section -> section
                    .path("subsection", subsection -> subsection
                        .path("content"))
                    .path("metadata"))
                .path("cite");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder(
                "/section",
                "/section/subsection",
                "/section/subsection/content",
                "/section/metadata",
                "/cite"
            );
        }
    }

    // ==================== 8. Fluent API 동작 검증 ====================

    @Nested
    @DisplayName("Fluent API 동작 검증")
    class FluentAPIBehavior {

        @Test
        @DisplayName("path() 반환값으로 체이닝")
        void testPathReturnsSchema() {
            TransitionSchema schema = TransitionSchema.root();
            TransitionSchema result = schema.path("test");

            assertThat(result).isSameAs(schema);
        }

        @Test
        @DisplayName("중첩 path() 반환값으로 체이닝")
        void testNestedPathReturnsSchema() {
            TransitionSchema schema = TransitionSchema.root();
            TransitionSchema result = schema.path("section", section -> {
                section.path("content");
            });

            assertThat(result).isSameAs(schema);
        }
    }
}
