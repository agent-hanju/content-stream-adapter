package me.hanju.adapter.transition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * TransitionNode 테스트
 *
 * 테스트 구성:
 * 1. 생성 및 초기화
 * 2. 트리 생성 - 다양한 경로 구조
 * 3. 노드 속성 검증
 * 4. 트리 구조 검증
 * 5. 엣지 케이스
 * 6. 실제 사용 시나리오
 */
@DisplayName("TransitionNode 테스트")
class TransitionNodeTest {

    // ==================== 1. 생성 및 초기화 ====================

    @Nested
    @DisplayName("생성 및 초기화")
    class CreationAndInitialization {

        @Test
        @DisplayName("빈 경로로 트리 생성 - 루트만 존재")
        void testCreateTreeWithEmptyPaths() {
            TransitionNode root = TransitionNode.createTree(Set.of());

            assertThat(root.isRoot()).isTrue();
            assertThat(root.getPath()).isEqualTo("/");
            assertThat(root.getParent()).isNull();
            assertThat(root.getTagName()).isNull();
        }

        @Test
        @DisplayName("null 경로로 트리 생성 - 루트만 존재")
        void testCreateTreeWithNullPaths() {
            TransitionNode root = TransitionNode.createTree(null);

            assertThat(root.isRoot()).isTrue();
            assertThat(root.getPath()).isEqualTo("/");
            assertThat(root.getParent()).isNull();
        }
    }

    // ==================== 2. 트리 생성 - 다양한 경로 구조 ====================

    @Nested
    @DisplayName("트리 생성 - 단일 깊이 경로")
    class SingleDepthTreeCreation {

        @Test
        @DisplayName("단일 경로 생성")
        void testSinglePath() {
            TransitionNode root = TransitionNode.createTree(Set.of("/think"));

            // 루트 확인
            assertThat(root.isRoot()).isTrue();
            assertThat(root.getPath()).isEqualTo("/");

            // 자식 노드 확인
            TransitionNode think = root.getChild("think");
            assertThat(think).isNotNull();
            assertThat(think.getPath()).isEqualTo("/think");
            assertThat(think.getTagName()).isEqualTo("think");
            assertThat(think.getParent()).isEqualTo(root);
            assertThat(think.isRoot()).isFalse();
        }

        @Test
        @DisplayName("여러 단일 깊이 경로")
        void testMultipleSingleDepthPaths() {
            TransitionNode root = TransitionNode.createTree(Set.of("/think", "/cite", "/rag"));

            // 루트 확인
            assertThat(root.isRoot()).isTrue();
            assertThat(root.getPath()).isEqualTo("/");

            // 자식 노드 확인
            TransitionNode think = root.getChild("think");
            assertThat(think).isNotNull();
            assertThat(think.getPath()).isEqualTo("/think");
            assertThat(think.getTagName()).isEqualTo("think");
            assertThat(think.getParent()).isEqualTo(root);

            TransitionNode cite = root.getChild("cite");
            assertThat(cite).isNotNull();
            assertThat(cite.getPath()).isEqualTo("/cite");

            TransitionNode rag = root.getChild("rag");
            assertThat(rag).isNotNull();
            assertThat(rag.getPath()).isEqualTo("/rag");
        }
    }

    @Nested
    @DisplayName("트리 생성 - 다중 깊이 경로")
    class MultiDepthTreeCreation {

        @Test
        @DisplayName("2단계 경로")
        void testTwoDepthPaths() {
            TransitionNode root = TransitionNode.createTree(Set.of(
                "/rag",
                "/rag/id",
                "/cite",
                "/cite/id"
            ));

            // /rag 확인
            TransitionNode rag = root.getChild("rag");
            assertThat(rag).isNotNull();
            assertThat(rag.getPath()).isEqualTo("/rag");

            // /rag/id 확인
            TransitionNode ragId = rag.getChild("id");
            assertThat(ragId).isNotNull();
            assertThat(ragId.getPath()).isEqualTo("/rag/id");
            assertThat(ragId.getTagName()).isEqualTo("id");
            assertThat(ragId.getParent()).isEqualTo(rag);

            // /cite/id 확인
            TransitionNode cite = root.getChild("cite");
            TransitionNode citeId = cite.getChild("id");
            assertThat(citeId).isNotNull();
            assertThat(citeId.getPath()).isEqualTo("/cite/id");
        }

        @Test
        @DisplayName("3단계 경로")
        void testThreeDepthPath() {
            TransitionNode root = TransitionNode.createTree(Set.of(
                "/rag",
                "/rag/id",
                "/rag/id/source"
            ));

            TransitionNode rag = root.getChild("rag");
            TransitionNode ragId = rag.getChild("id");
            TransitionNode ragIdSource = ragId.getChild("source");

            assertThat(ragIdSource).isNotNull();
            assertThat(ragIdSource.getPath()).isEqualTo("/rag/id/source");
            assertThat(ragIdSource.getTagName()).isEqualTo("source");
            assertThat(ragIdSource.getParent()).isEqualTo(ragId);
        }

        @Test
        @DisplayName("중간 경로 누락 - 자동 생성")
        void testIntermediatePathAutoCreation() {
            // /rag/id/source만 제공, /rag와 /rag/id는 자동 생성되어야 함
            TransitionNode root = TransitionNode.createTree(Set.of("/rag/id/source"));

            TransitionNode rag = root.getChild("rag");
            assertThat(rag).isNotNull();
            assertThat(rag.getPath()).isEqualTo("/rag");

            TransitionNode ragId = rag.getChild("id");
            assertThat(ragId).isNotNull();
            assertThat(ragId.getPath()).isEqualTo("/rag/id");

            TransitionNode ragIdSource = ragId.getChild("source");
            assertThat(ragIdSource).isNotNull();
            assertThat(ragIdSource.getPath()).isEqualTo("/rag/id/source");
        }
    }

    // ==================== 3. 노드 속성 검증 ====================

    @Nested
    @DisplayName("노드 속성 검증")
    class NodePropertyValidation {

        @Test
        @DisplayName("루트 노드 속성")
        void testRootProperties() {
            TransitionNode root = TransitionNode.createTree(Set.of("/test"));

            assertThat(root.getPath()).isEqualTo("/");
            assertThat(root.getTagName()).isNull();
            assertThat(root.getParent()).isNull();
            assertThat(root.isRoot()).isTrue();
        }

        @Test
        @DisplayName("일반 노드 속성")
        void testNormalNodeProperties() {
            TransitionNode root = TransitionNode.createTree(Set.of("/cite/id"));

            TransitionNode cite = root.getChild("cite");
            assertThat(cite.getPath()).isEqualTo("/cite");
            assertThat(cite.getTagName()).isEqualTo("cite");
            assertThat(cite.getParent()).isEqualTo(root);
            assertThat(cite.isRoot()).isFalse();

            TransitionNode id = cite.getChild("id");
            assertThat(id.getPath()).isEqualTo("/cite/id");
            assertThat(id.getTagName()).isEqualTo("id");
            assertThat(id.getParent()).isEqualTo(cite);
            assertThat(id.isRoot()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 자식 - null 반환")
        void testNonExistentChild() {
            TransitionNode root = TransitionNode.createTree(Set.of("/think"));

            assertThat(root.getChild("nonexistent")).isNull();

            TransitionNode think = root.getChild("think");
            assertThat(think.getChild("cite")).isNull(); // /think/cite는 허용되지 않음
        }
    }

    // ==================== 4. 트리 구조 검증 ====================

    @Nested
    @DisplayName("트리 구조 검증")
    class TreeStructureValidation {

        @Test
        @DisplayName("부모-자식 관계 검증")
        void testParentChildRelationship() {
            TransitionNode root = TransitionNode.createTree(Set.of("/section/subsection/content"));

            TransitionNode section = root.getChild("section");
            assertThat(section.getParent()).isEqualTo(root);

            TransitionNode subsection = section.getChild("subsection");
            assertThat(subsection.getParent()).isEqualTo(section);

            TransitionNode content = subsection.getChild("content");
            assertThat(content.getParent()).isEqualTo(subsection);
        }

        @Test
        @DisplayName("복잡한 트리 구조")
        void testComplexTreeStructure() {
            TransitionNode root = TransitionNode.createTree(Set.of(
                "/section",
                "/section/subsection",
                "/section/subsection/content",
                "/section/metadata",
                "/cite",
                "/cite/id"
            ));

            // /section 브랜치
            TransitionNode section = root.getChild("section");
            assertThat(section).isNotNull();

            TransitionNode subsection = section.getChild("subsection");
            assertThat(subsection).isNotNull();

            TransitionNode content = subsection.getChild("content");
            assertThat(content).isNotNull();

            TransitionNode metadata = section.getChild("metadata");
            assertThat(metadata).isNotNull();

            // /cite 브랜치
            TransitionNode cite = root.getChild("cite");
            assertThat(cite).isNotNull();

            TransitionNode id = cite.getChild("id");
            assertThat(id).isNotNull();
        }
    }

    // ==================== 5. equals / hashCode ====================

    @Nested
    @DisplayName("equals 및 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("같은 경로의 노드는 equals")
        void testEqualsSamePath() {
            TransitionNode root1 = TransitionNode.createTree(Set.of("/think"));
            TransitionNode root2 = TransitionNode.createTree(Set.of("/cite"));

            // 같은 경로면 equals
            assertThat(root1).isEqualTo(root2);

            TransitionNode think = root1.getChild("think");
            TransitionNode think2 = TransitionNode.createTree(Set.of("/think")).getChild("think");
            assertThat(think).isEqualTo(think2);
        }

        @Test
        @DisplayName("다른 경로의 노드는 not equals")
        void testNotEqualsDifferentPath() {
            TransitionNode root = TransitionNode.createTree(Set.of("/think", "/cite"));

            TransitionNode think = root.getChild("think");
            TransitionNode cite = root.getChild("cite");

            assertThat(think).isNotEqualTo(cite);
        }

        @Test
        @DisplayName("같은 경로는 같은 hashCode")
        void testHashCodeSamePath() {
            TransitionNode root1 = TransitionNode.createTree(Set.of("/think"));
            TransitionNode root2 = TransitionNode.createTree(Set.of("/think"));

            assertThat(root1.hashCode()).isEqualTo(root2.hashCode());
        }
    }

    // ==================== 6. 엣지 케이스 ====================

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("루트 경로(/) 포함 - 무시됨")
        void testRootPathIgnored() {
            TransitionNode root = TransitionNode.createTree(Set.of("/", "/think"));

            assertThat(root.getPath()).isEqualTo("/");
            assertThat(root.getChild("think")).isNotNull();
        }

        @Test
        @DisplayName("null 경로 포함 - 무시됨")
        void testNullPathIgnored() {
            Set<String> paths = new HashSet<>();
            paths.add("/think");
            paths.add(null);
            paths.add("/cite");

            TransitionNode root = TransitionNode.createTree(paths);

            assertThat(root.getChild("think")).isNotNull();
            assertThat(root.getChild("cite")).isNotNull();
        }

        @Test
        @DisplayName("빈 문자열 경로 포함 - 무시됨")
        void testEmptyPathIgnored() {
            Set<String> paths = Set.of("/think", "", "/cite");
            TransitionNode root = TransitionNode.createTree(paths);

            assertThat(root.getChild("think")).isNotNull();
            assertThat(root.getChild("cite")).isNotNull();
        }

        @Test
        @DisplayName("단일 세그먼트 경로")
        void testSingleSegmentPath() {
            TransitionNode root = TransitionNode.createTree(Set.of("/a"));

            TransitionNode a = root.getChild("a");
            assertThat(a).isNotNull();
            assertThat(a.getPath()).isEqualTo("/a");
            assertThat(a.getTagName()).isEqualTo("a");
        }

        @Test
        @DisplayName("매우 긴 경로")
        void testVeryLongPath() {
            TransitionNode root = TransitionNode.createTree(Set.of("/a/b/c/d/e/f/g"));

            TransitionNode current = root;
            String[] segments = {"a", "b", "c", "d", "e", "f", "g"};

            for (String segment : segments) {
                current = current.getChild(segment);
                assertThat(current).isNotNull();
            }

            assertThat(current.getPath()).isEqualTo("/a/b/c/d/e/f/g");
            assertThat(current.getTagName()).isEqualTo("g");
        }
    }

    // ==================== 7. 실제 사용 시나리오 ====================

    @Nested
    @DisplayName("실제 사용 시나리오 - LLM 응답 구조")
    class RealWorldLLMScenarios {

        @Test
        @DisplayName("기본 LLM 응답 구조")
        void testBasicLLMStructure() {
            TransitionNode root = TransitionNode.createTree(Set.of(
                "/thinking",
                "/answer"
            ));

            TransitionNode thinking = root.getChild("thinking");
            assertThat(thinking).isNotNull();
            assertThat(thinking.getPath()).isEqualTo("/thinking");

            TransitionNode answer = root.getChild("answer");
            assertThat(answer).isNotNull();
            assertThat(answer.getPath()).isEqualTo("/answer");
        }

        @Test
        @DisplayName("RAG 응답 구조")
        void testRagStructure() {
            TransitionNode root = TransitionNode.createTree(Set.of(
                "/cite",
                "/cite/id",
                "/cite/source"
            ));

            TransitionNode cite = root.getChild("cite");
            assertThat(cite).isNotNull();

            TransitionNode id = cite.getChild("id");
            assertThat(id).isNotNull();
            assertThat(id.getPath()).isEqualTo("/cite/id");

            TransitionNode source = cite.getChild("source");
            assertThat(source).isNotNull();
            assertThat(source.getPath()).isEqualTo("/cite/source");
        }

        @Test
        @DisplayName("복잡한 문서 구조")
        void testComplexDocumentStructure() {
            TransitionNode root = TransitionNode.createTree(Set.of(
                "/section",
                "/section/title",
                "/section/content",
                "/section/subsection",
                "/section/subsection/title",
                "/section/subsection/content"
            ));

            TransitionNode section = root.getChild("section");
            assertThat(section.getChild("title")).isNotNull();
            assertThat(section.getChild("content")).isNotNull();

            TransitionNode subsection = section.getChild("subsection");
            assertThat(subsection).isNotNull();
            assertThat(subsection.getChild("title")).isNotNull();
            assertThat(subsection.getChild("content")).isNotNull();
        }

        @Test
        @DisplayName("경로 순회 - 루트부터 리프까지")
        void testPathTraversal() {
            TransitionNode root = TransitionNode.createTree(Set.of("/a/b/c"));

            // 루트부터 리프까지 순회
            TransitionNode current = root;
            assertThat(current.getPath()).isEqualTo("/");

            current = current.getChild("a");
            assertThat(current.getPath()).isEqualTo("/a");

            current = current.getChild("b");
            assertThat(current.getPath()).isEqualTo("/a/b");

            current = current.getChild("c");
            assertThat(current.getPath()).isEqualTo("/a/b/c");
        }

        @Test
        @DisplayName("역방향 순회 - 리프부터 루트까지")
        void testReverseTraversal() {
            TransitionNode root = TransitionNode.createTree(Set.of("/a/b/c"));

            TransitionNode leaf = root.getChild("a").getChild("b").getChild("c");
            assertThat(leaf.getPath()).isEqualTo("/a/b/c");

            TransitionNode b = leaf.getParent();
            assertThat(b.getPath()).isEqualTo("/a/b");

            TransitionNode a = b.getParent();
            assertThat(a.getPath()).isEqualTo("/a");

            TransitionNode rootAgain = a.getParent();
            assertThat(rootAgain.getPath()).isEqualTo("/");
            assertThat(rootAgain.isRoot()).isTrue();
        }
    }

    // ==================== 8. toString ====================

    @Nested
    @DisplayName("toString 검증")
    class ToStringValidation {

        @Test
        @DisplayName("toString은 경로 반환")
        void testToString() {
            TransitionNode root = TransitionNode.createTree(Set.of("/think", "/cite/id"));

            assertThat(root.toString()).isEqualTo("/");

            TransitionNode think = root.getChild("think");
            assertThat(think.toString()).isEqualTo("/think");

            TransitionNode cite = root.getChild("cite");
            TransitionNode id = cite.getChild("id");
            assertThat(id.toString()).isEqualTo("/cite/id");
        }
    }
}
