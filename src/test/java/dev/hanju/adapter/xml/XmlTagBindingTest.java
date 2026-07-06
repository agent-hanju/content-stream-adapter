package dev.hanju.adapter.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.xml.XmlTagBinding;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * XmlTagBinding 테스트
 *
 * 테스트 구성:
 * 1. 생성 및 초기화
 * 2. 잘못된 입력 처리
 * 3. 태그 바인딩
 * 4. 별칭 지원
 * 5. 속성 지원
 * 6. 패턴 생성
 * 7. 태그-경로 조회
 * 8. 실제 사용 시나리오
 */
@DisplayName("XmlTagBinding 테스트")
class XmlTagBindingTest {

    // ==================== 1. 생성 및 초기화 ====================

    @Nested
    @DisplayName("생성 및 초기화")
    class CreationAndInitialization {

        @Test
        @DisplayName("스키마로 빌더 시작")
        void testFromSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding.Builder builder = XmlTagBinding.from(schema);

            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("null 스키마 - 예외")
        void testNullSchema() {
            assertThatThrownBy(() -> XmlTagBinding.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema cannot be null");
        }

        @Test
        @DisplayName("빈 바인딩 빌드")
        void testEmptyBinding() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema).build();

            assertThat(binding).isNotNull();
            assertThat(binding.getAllTagNames()).isEmpty();
        }
    }

    // ==================== 2. 잘못된 입력 처리 ====================

    @Nested
    @DisplayName("잘못된 입력 처리")
    class InvalidInputHandling {

        @Test
        @DisplayName("null 경로 바인딩 - 예외")
        void testNullPath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema).bind(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path cannot be null or empty");
        }

        @Test
        @DisplayName("빈 경로 바인딩 - 예외")
        void testEmptyPath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema).bind(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path cannot be null or empty");
        }

        @Test
        @DisplayName("정의되지 않은 경로 - 예외")
        void testUndefinedPath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema).bind("/unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path not defined in schema");
        }

        @Test
        @DisplayName("null 태그 이름 - 예외")
        void testNullTagName() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").tag(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag name cannot be null or empty");
        }

        @Test
        @DisplayName("빈 태그 이름 - 예외")
        void testEmptyTagName() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").tag(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag name cannot be null or empty");
        }

        @Test
        @DisplayName("태그 없이 alias - 예외")
        void testAliasWithoutTag() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").alias("rag"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Call tag() before alias()");
        }

        @Test
        @DisplayName("태그 없이 attr - 예외")
        void testAttrWithoutTag() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").attr("id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Call tag() before attr()");
        }

        @Test
        @DisplayName("태그 없이 and - 예외")
        void testAndWithoutTag() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").and())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Call tag() before and()");
        }

        @Test
        @DisplayName("중복 태그 설정 - 예외")
        void testDuplicateTag() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").tag("rag"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tag already set for path");
        }

        @Test
        @DisplayName("빈 별칭 배열 - 예외")
        void testEmptyAliasArray() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one alias must be provided");
        }

        @Test
        @DisplayName("빈 속성 배열 - 예외")
        void testEmptyAttrArray() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            assertThatThrownBy(() -> XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").attr())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one attribute must be provided");
        }
    }

    // ==================== 3. 태그 바인딩 ====================

    @Nested
    @DisplayName("태그 바인딩")
    class TagBinding {

        @Test
        @DisplayName("단일 태그 바인딩")
        void testSingleTagBinding() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .build();

            assertThat(binding.getPathForTag("cite")).isEqualTo("/cite");
            assertThat(binding.getAllTagNames()).containsExactly("cite");
        }

        @Test
        @DisplayName("여러 경로 태그 바인딩")
        void testMultiplePathBinding() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite")
                .path("thinking");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .bind("/thinking").tag("thinking")
                .and()
                .build();

            assertThat(binding.getPathForTag("cite")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("thinking")).isEqualTo("/thinking");
            assertThat(binding.getAllTagNames()).containsExactlyInAnyOrder("cite", "thinking");
        }

        @Test
        @DisplayName("중첩 경로 태그 바인딩")
        void testNestedPathBinding() {
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

            assertThat(binding.getPathForTag("cite")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("id")).isEqualTo("/cite/id");
            assertThat(binding.getPathForTag("source")).isEqualTo("/cite/source");
        }
    }

    // ==================== 4. 별칭 지원 ====================

    @Nested
    @DisplayName("별칭 지원")
    class AliasSupport {

        @Test
        @DisplayName("단일 별칭 추가")
        void testSingleAlias() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias("rag")
                .and()
                .build();

            assertThat(binding.getPathForTag("cite")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("rag")).isEqualTo("/cite");
            assertThat(binding.getAllTagNames()).containsExactlyInAnyOrder("cite", "rag");
        }

        @Test
        @DisplayName("여러 별칭 추가")
        void testMultipleAliases() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias("rag", "reference", "source")
                .and()
                .build();

            assertThat(binding.getPathForTag("cite")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("rag")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("reference")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("source")).isEqualTo("/cite");
        }

        @Test
        @DisplayName("isTagForPath - 태그와 경로 일치 확인")
        void testIsTagForPath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias("rag")
                .and()
                .build();

            assertThat(binding.isTagForPath("cite", "/cite")).isTrue();
            assertThat(binding.isTagForPath("rag", "/cite")).isTrue();
            assertThat(binding.isTagForPath("cite", "/other")).isFalse();
            assertThat(binding.isTagForPath("unknown", "/cite")).isFalse();
        }
    }

    // ==================== 5. 속성 지원 ====================

    @Nested
    @DisplayName("속성 지원")
    class AttributeSupport {

        @Test
        @DisplayName("단일 속성 추가")
        void testSingleAttribute() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").attr("id")
                .and()
                .build();

            Set<String> attrs = binding.getAllowedAttributes("/cite");
            assertThat(attrs).containsExactly("id");
        }

        @Test
        @DisplayName("여러 속성 추가")
        void testMultipleAttributes() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").attr("id", "source", "type")
                .and()
                .build();

            Set<String> attrs = binding.getAllowedAttributes("/cite");
            assertThat(attrs).containsExactlyInAnyOrder("id", "source", "type");
        }

        @Test
        @DisplayName("속성 없는 경로 - 빈 집합")
        void testNoAttributes() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .build();

            Set<String> attrs = binding.getAllowedAttributes("/cite");
            assertThat(attrs).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 경로 - 빈 집합")
        void testNonExistentPath() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .build();

            Set<String> attrs = binding.getAllowedAttributes("/unknown");
            assertThat(attrs).isEmpty();
        }

        @Test
        @DisplayName("alias와 attr 함께 사용")
        void testAliasAndAttrTogether() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias("rag").attr("id", "source")
                .and()
                .build();

            assertThat(binding.getAllowedAttributes("/cite"))
                .containsExactlyInAnyOrder("id", "source");
            assertThat(binding.getAllTagNames())
                .containsExactlyInAnyOrder("cite", "rag");
        }
    }

    // ==================== 6. 패턴 생성 ====================

    @Nested
    @DisplayName("패턴 생성")
    class PatternGeneration {

        @Test
        @DisplayName("단일 태그 패턴 생성")
        void testSingleTagPattern() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .build();

            Set<String> patterns = binding.generatePatterns();
            assertThat(patterns).containsExactlyInAnyOrder("<cite", "</cite>");
        }

        @Test
        @DisplayName("별칭 포함 패턴 생성")
        void testAliasPatterns() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias("rag")
                .and()
                .build();

            Set<String> patterns = binding.generatePatterns();
            assertThat(patterns).containsExactlyInAnyOrder(
                "<cite", "</cite>",
                "<rag", "</rag>"
            );
        }

        @Test
        @DisplayName("여러 경로 패턴 생성")
        void testMultiplePathPatterns() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite")
                .path("thinking");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .bind("/thinking").tag("thinking")
                .and()
                .build();

            Set<String> patterns = binding.generatePatterns();
            assertThat(patterns).containsExactlyInAnyOrder(
                "<cite", "</cite>",
                "<thinking", "</thinking>"
            );
        }
    }

    // ==================== 7. 태그-경로 조회 ====================

    @Nested
    @DisplayName("태그-경로 조회")
    class TagPathLookup {

        @Test
        @DisplayName("바인딩된 태그 조회")
        void testBoundTagLookup() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .build();

            assertThat(binding.getPathForTag("cite")).isEqualTo("/cite");
        }

        @Test
        @DisplayName("바인딩되지 않은 태그 - null")
        void testUnboundTagLookup() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .build();

            assertThat(binding.getPathForTag("unknown")).isNull();
        }

        @Test
        @DisplayName("getSchema - 스키마 반환")
        void testGetSchema() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite");

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite")
                .and()
                .build();

            assertThat(binding.getSchema()).isSameAs(schema);
        }
    }

    // ==================== 8. 실제 사용 시나리오 ====================

    @Nested
    @DisplayName("실제 사용 시나리오")
    class RealWorldScenarios {

        @Test
        @DisplayName("RAG 응답 구조")
        void testRagResponseStructure() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite", cite -> cite
                    .path("id")
                    .path("source"));

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias("rag").attr("id", "source")
                .and()
                .bind("/cite/id").tag("id")
                .and()
                .bind("/cite/source").tag("source")
                .and()
                .build();

            // 태그 조회
            assertThat(binding.getPathForTag("cite")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("rag")).isEqualTo("/cite");
            assertThat(binding.getPathForTag("id")).isEqualTo("/cite/id");

            // 속성 조회
            assertThat(binding.getAllowedAttributes("/cite"))
                .containsExactlyInAnyOrder("id", "source");
            assertThat(binding.getAllowedAttributes("/cite/id")).isEmpty();

            // 패턴 생성
            Set<String> patterns = binding.generatePatterns();
            assertThat(patterns).contains("<cite", "</cite>", "<rag", "</rag>");
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

            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/section").tag("section").attr("id")
                .and()
                .bind("/section/title").tag("title")
                .and()
                .bind("/section/content").tag("content")
                .and()
                .bind("/section/subsection").tag("subsection")
                .and()
                .bind("/section/subsection/title").tag("title")
                .and()
                .bind("/section/subsection/content").tag("content")
                .and()
                .bind("/metadata").tag("metadata")
                .and()
                .build();

            assertThat(binding.getAllTagNames()).contains(
                "section", "title", "content", "subsection", "metadata"
            );
            assertThat(binding.getAllowedAttributes("/section")).containsExactly("id");
        }

        @Test
        @DisplayName("Fluent API 체이닝")
        void testFluentAPIChaining() {
            TransitionSchema schema = TransitionSchema.root()
                .path("cite")
                .path("thinking");

            // 한 줄로 체이닝
            XmlTagBinding binding = XmlTagBinding.from(schema)
                .bind("/cite").tag("cite").alias("rag").attr("id")
                .and()
                .bind("/thinking").tag("thinking")
                .and()
                .build();

            assertThat(binding.getAllTagNames()).hasSize(3); // cite, rag, thinking
        }
    }
}
