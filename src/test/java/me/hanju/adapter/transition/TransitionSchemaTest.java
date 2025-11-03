package me.hanju.adapter.transition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * TransitionSchema 테스트
 *
 * 테스트 구성:
 * 1. 생성 및 초기화
 * 2. 잘못된 입력 처리
 * 3. 기본 태그 추가
 * 4. 중첩 태그 추가
 * 5. 별칭 추가
 * 6. 스키마 조회 메서드
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
        @DisplayName("null 태그 이름 - 예외")
        void testNullTagName() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.tag(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag name cannot be null or empty");
        }

        @Test
        @DisplayName("빈 태그 이름 - 예외")
        void testEmptyTagName() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.tag(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag name cannot be null or empty");
        }

        @Test
        @DisplayName("중첩 태그에 null 이름 - 예외")
        void testNestedTagWithNullName() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.tag(null, nested -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag name cannot be null or empty");
        }

        @Test
        @DisplayName("중첩 태그에 null builder - 예외")
        void testNestedTagWithNullBuilder() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.tag("section", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Builder cannot be null");
        }

        @Test
        @DisplayName("태그 없이 alias 호출 - 예외")
        void testAliasWithoutTag() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.alias("alias1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tag to add alias to");
        }

        @Test
        @DisplayName("null 별칭 - 예외")
        void testNullAlias() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.tag("cite").alias((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alias name cannot be null or empty");
        }

        @Test
        @DisplayName("빈 별칭 - 예외")
        void testEmptyAlias() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.tag("cite").alias(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alias name cannot be null or empty");
        }

        @Test
        @DisplayName("빈 별칭 배열 - 예외")
        void testEmptyAliasArray() {
            TransitionSchema schema = TransitionSchema.root();

            assertThatThrownBy(() -> schema.tag("cite").alias())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one alias must be provided");
        }
    }

    // ==================== 3. 기본 태그 추가 ====================

    @Nested
    @DisplayName("기본 태그 추가")
    class BasicTagAddition {

        @Test
        @DisplayName("단일 태그 추가")
        void testSingleTag() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKey("/think");
            assertThat(mapping.get("/think")).containsExactly("think");
        }

        @Test
        @DisplayName("여러 태그 추가")
        void testMultipleTags() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think")
                .tag("cite")
                .tag("rag");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys("/think", "/cite", "/rag");
        }

        @Test
        @DisplayName("태그 추가 후 경로 확인")
        void testPathAfterTagAddition() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactly("/section");
        }

        @Test
        @DisplayName("태그 추가 후 태그 이름 확인")
        void testTagNamesAfterAddition() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think")
                .tag("cite");

            Set<String> tagNames = schema.getAllTagNames();
            assertThat(tagNames).containsExactlyInAnyOrder("think", "cite");
        }
    }

    // ==================== 4. 중첩 태그 추가 ====================

    @Nested
    @DisplayName("중첩 태그 추가")
    class NestedTagAddition {

        @Test
        @DisplayName("1단계 중첩")
        void testSingleLevelNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("content"));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys("/section", "/section/content");
            assertThat(mapping.get("/section")).containsExactly("section");
            assertThat(mapping.get("/section/content")).containsExactly("content");
        }

        @Test
        @DisplayName("2단계 중첩")
        void testTwoLevelNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("subsection", subsection -> subsection
                        .tag("content")));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys(
                "/section",
                "/section/subsection",
                "/section/subsection/content"
            );
        }

        @Test
        @DisplayName("형제 태그")
        void testSiblingTags() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("title")
                    .tag("content")
                    .tag("metadata"));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys(
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
                .tag("section", section -> section
                    .tag("title")
                    .tag("subsection", subsection -> subsection
                        .tag("title")
                        .tag("content"))
                    .tag("metadata"))
                .tag("cite");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys(
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

    // ==================== 5. 별칭 추가 ====================

    @Nested
    @DisplayName("별칭 추가")
    class AliasAddition {

        @Test
        @DisplayName("단일 별칭 추가")
        void testSingleAlias() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping.get("/cite")).containsExactlyInAnyOrder("cite", "rag");
        }

        @Test
        @DisplayName("여러 별칭 추가")
        void testMultipleAliases() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag", "reference", "source");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping.get("/cite")).containsExactlyInAnyOrder(
                "cite", "rag", "reference", "source"
            );
        }

        @Test
        @DisplayName("별칭 추가 후 태그 이름 목록 확인")
        void testAllTagNamesWithAliases() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag")
                .tag("think");

            Set<String> tagNames = schema.getAllTagNames();
            assertThat(tagNames).containsExactlyInAnyOrder("cite", "rag", "think");
        }

        @Test
        @DisplayName("중첩 태그에 별칭 추가")
        void testAliasOnNestedTag() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("content").alias("body", "text"));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping.get("/section/content")).containsExactlyInAnyOrder(
                "content", "body", "text"
            );
        }

        @Test
        @DisplayName("마지막 태그에만 별칭 추가됨")
        void testAliasAppliedToLastTag() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think")
                .tag("cite").alias("rag");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping.get("/think")).containsExactly("think");
            assertThat(mapping.get("/cite")).containsExactlyInAnyOrder("cite", "rag");
        }
    }

    // ==================== 6. 스키마 조회 메서드 ====================

    @Nested
    @DisplayName("스키마 조회 메서드")
    class SchemaQueryMethods {

        @Test
        @DisplayName("getPathToTagsMapping - 수정 불가")
        void testPathToTagsMappingUnmodifiable() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();

            assertThatThrownBy(() -> mapping.put("/new", List.of("new")))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getAllPaths - 수정 불가")
        void testAllPathsUnmodifiable() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite");

            Set<String> paths = schema.getAllPaths();

            assertThatThrownBy(() -> paths.add("/new"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getAllTagNames - 수정 불가")
        void testAllTagNamesUnmodifiable() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite");

            Set<String> tagNames = schema.getAllTagNames();

            assertThatThrownBy(() -> tagNames.add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getPathCount - 정확한 경로 수 반환")
        void testPathCount() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("think")
                .tag("cite")
                .tag("section", section -> section
                    .tag("content"));

            assertThat(schema.getPathCount()).isEqualTo(4); // /think, /cite, /section, /section/content
        }
    }

    // ==================== 7. 엣지 케이스 ====================

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("빈 스키마")
        void testEmptySchema() {
            TransitionSchema schema = TransitionSchema.root();

            assertThat(schema.getAllPaths()).isEmpty();
            assertThat(schema.getAllTagNames()).isEmpty();
            assertThat(schema.getPathCount()).isZero();
        }

        @Test
        @DisplayName("매우 긴 중첩")
        void testVeryDeepNesting() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("a", a -> a
                    .tag("b", b -> b
                        .tag("c", c -> c
                            .tag("d", d -> d
                                .tag("e")))));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKey("/a/b/c/d/e");
        }

        @Test
        @DisplayName("동일 이름 태그 다른 경로")
        void testSameNameDifferentPaths() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("title"))
                .tag("article", article -> article
                    .tag("title"));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys("/section/title", "/article/title");
            assertThat(mapping.get("/section/title")).containsExactly("title");
            assertThat(mapping.get("/article/title")).containsExactly("title");
        }
    }

    // ==================== 8. 실제 사용 시나리오 ====================

    @Nested
    @DisplayName("실제 사용 시나리오 - LLM 응답 구조")
    class RealWorldLLMScenarios {

        @Test
        @DisplayName("기본 LLM 스키마")
        void testBasicLLMSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("thinking")
                .tag("answer");

            Set<String> paths = schema.getAllPaths();
            assertThat(paths).containsExactlyInAnyOrder("/thinking", "/answer");
        }

        @Test
        @DisplayName("RAG 스키마")
        void testRagSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite", cite -> cite
                    .tag("id")
                    .tag("source"));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys("/cite", "/cite/id", "/cite/source");
        }

        @Test
        @DisplayName("RAG 스키마 with 별칭")
        void testRagSchemaWithAlias() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("cite").alias("rag")
                .tag("think");

            Set<String> tagNames = schema.getAllTagNames();
            assertThat(tagNames).containsExactlyInAnyOrder("cite", "rag", "think");
        }

        @Test
        @DisplayName("복잡한 문서 구조")
        void testComplexDocumentStructure() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("title")
                    .tag("content")
                    .tag("subsection", subsection -> subsection
                        .tag("title")
                        .tag("content")))
                .tag("metadata");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys(
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
            // Fluent API로 복잡한 구조를 읽기 쉽게 작성
            TransitionSchema schema = TransitionSchema.root()
                .tag("search", search -> search
                    .tag("query")
                    .tag("results"))
                .tag("thinking")
                .tag("answer", answer -> answer
                    .tag("summary")
                    .tag("details"));

            assertThat(schema.getAllPaths()).hasSize(7);
            assertThat(schema.getAllTagNames()).containsExactlyInAnyOrder(
                "search", "query", "results", "thinking", "answer", "summary", "details"
            );
        }

        @Test
        @DisplayName("tool_use 스키마")
        void testToolUseSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .tag("function_calls", calls -> calls
                    .tag("invoke", invoke -> invoke
                        .tag("tool_name")
                        .tag("parameters", params -> params
                            .tag("parameter"))));

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys(
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
            // Javadoc 예제와 동일한 스키마
            TransitionSchema schema = TransitionSchema.root()
                .tag("section", section -> section
                    .tag("subsection", subsection -> subsection
                        .tag("content"))
                    .tag("metadata"))
                .tag("cite").alias("rag");

            Map<String, List<String>> mapping = schema.getPathToTagsMapping();
            assertThat(mapping).containsKeys(
                "/section",
                "/section/subsection",
                "/section/subsection/content",
                "/section/metadata",
                "/cite"
            );

            assertThat(mapping.get("/cite")).containsExactlyInAnyOrder("cite", "rag");
        }
    }

    // ==================== 9. Fluent API 동작 검증 ====================

    @Nested
    @DisplayName("Fluent API 동작 검증")
    class FluentAPIBehavior {

        @Test
        @DisplayName("tag() 반환값으로 체이닝")
        void testTagReturnsSchema() {
            TransitionSchema schema = TransitionSchema.root();
            TransitionSchema result = schema.tag("test");

            assertThat(result).isSameAs(schema);
        }

        @Test
        @DisplayName("alias() 반환값으로 체이닝")
        void testAliasReturnsSchema() {
            TransitionSchema schema = TransitionSchema.root();
            TransitionSchema result = schema.tag("cite").alias("rag");

            assertThat(result).isSameAs(schema);
        }

        @Test
        @DisplayName("중첩 tag() 반환값으로 체이닝")
        void testNestedTagReturnsSchema() {
            TransitionSchema schema = TransitionSchema.root();
            TransitionSchema result = schema.tag("section", section -> {
                section.tag("content");
            });

            assertThat(result).isSameAs(schema);
        }
    }
}
