package me.hanju.adapter.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * AhoCorasickTrie 클래스에 대한 포괄적인 단위 테스트
 *
 * 테스트 구성:
 * 1. 일반적인 입력 케이스 - Trie 생성 및 속성 검증
 * 2. 엣지 케이스 - 잘못된 입력 처리
 * 3. 프로젝트 사용 패턴 - XML 태그, 다국어 패턴
 * 4. Trie 구조 검증 - 내부 구조 및 실패 링크
 */
@DisplayName("AhoCorasickTrie 테스트")
class AhoCorasickTrieTest {

  // ============================================================
  // 1. 일반적인 입력 케이스
  // ============================================================

  @Nested
  @DisplayName("Trie 생성 - 생성자 사용")
  class TrieCreationWithConstructor {

    @Test
    @DisplayName("단일 패턴으로 Trie 생성")
    void testSinglePattern() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));

      assertThat(trie.getPatternCount()).isEqualTo(1);
      assertThat(trie.getPatterns()).containsExactly("hello");
      assertThat(trie.getMaxPatternLength()).isEqualTo(5);
      assertThat(trie.getRoot()).isNotNull();
    }

    @Test
    @DisplayName("다중 패턴으로 Trie 생성")
    void testMultiplePatterns() {
      List<String> patterns = List.of("he", "she", "his", "hers");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(4);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "he",
        "she",
        "his",
        "hers"
      );
      assertThat(trie.getMaxPatternLength()).isEqualTo(4);
    }

    @Test
    @DisplayName("중복 패턴 제거")
    void testDuplicatePatterns() {
      List<String> patterns = List.of("hello", "world", "hello", "world");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(2);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "hello",
        "world"
      );
    }

    @Test
    @DisplayName("한글 패턴으로 Trie 생성")
    void testKoreanPatterns() {
      List<String> patterns = List.of("안녕", "하세요", "반갑습니다");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(3);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "안녕",
        "하세요",
        "반갑습니다"
      );
      assertThat(trie.getMaxPatternLength()).isEqualTo(5);
    }

    @Test
    @DisplayName("혼합 언어 패턴으로 Trie 생성")
    void testMixedLanguagePatterns() {
      List<String> patterns = List.of("Hello", "안녕", "World", "세계");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(4);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "Hello",
        "안녕",
        "World",
        "세계"
      );
    }
  }

  @Nested
  @DisplayName("Trie 생성 - Builder 패턴 사용")
  class TrieCreationWithBuilder {

    @Test
    @DisplayName("Builder로 단일 패턴 추가")
    void testBuilderSinglePattern() {
      AhoCorasickTrie trie = AhoCorasickTrie.builder()
        .addPattern("test")
        .build();

      assertThat(trie.getPatternCount()).isEqualTo(1);
      assertThat(trie.getPatterns()).containsExactly("test");
    }

    @Test
    @DisplayName("Builder로 다중 패턴 추가")
    void testBuilderMultiplePatterns() {
      AhoCorasickTrie trie = AhoCorasickTrie.builder()
        .addPattern("abc")
        .addPattern("bcd")
        .addPattern("cde")
        .build();

      assertThat(trie.getPatternCount()).isEqualTo(3);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "abc",
        "bcd",
        "cde"
      );
    }

    @Test
    @DisplayName("Builder로 패턴 컬렉션 추가")
    void testBuilderAddPatterns() {
      List<String> patterns = List.of("one", "two", "three");
      AhoCorasickTrie trie = AhoCorasickTrie.builder()
        .addPatterns(patterns)
        .build();

      assertThat(trie.getPatternCount()).isEqualTo(3);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "one",
        "two",
        "three"
      );
    }

    @Test
    @DisplayName("Builder로 혼합 추가 (개별 + 컬렉션)")
    void testBuilderMixedAdd() {
      AhoCorasickTrie trie = AhoCorasickTrie.builder()
        .addPattern("first")
        .addPatterns(List.of("second", "third"))
        .addPattern("fourth")
        .build();

      assertThat(trie.getPatternCount()).isEqualTo(4);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "first",
        "second",
        "third",
        "fourth"
      );
    }

    @Test
    @DisplayName("Builder로 중복 패턴 추가 시 자동 제거")
    void testBuilderDuplicates() {
      AhoCorasickTrie trie = AhoCorasickTrie.builder()
        .addPattern("duplicate")
        .addPattern("unique")
        .addPattern("duplicate")
        .build();

      assertThat(trie.getPatternCount()).isEqualTo(2);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "duplicate",
        "unique"
      );
    }
  }

  @Nested
  @DisplayName("Trie 속성 조회")
  class TrieProperties {

    @Test
    @DisplayName("패턴 개수 정확히 반환")
    void testPatternCount() {
      AhoCorasickTrie trie = new AhoCorasickTrie(
        List.of("a", "ab", "abc", "abcd")
      );

      assertThat(trie.getPatternCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("최대 패턴 길이 정확히 반환")
    void testMaxPatternLength() {
      AhoCorasickTrie trie = new AhoCorasickTrie(
        List.of("x", "hello", "world")
      );

      assertThat(trie.getMaxPatternLength()).isEqualTo(5);
    }

    @Test
    @DisplayName("패턴 목록 수정 불가")
    void testPatternsUnmodifiable() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));
      Set<String> patterns = trie.getPatterns();

      assertThatThrownBy(() -> patterns.add("new")).isInstanceOf(
        UnsupportedOperationException.class
      );
    }

    @Test
    @DisplayName("루트 노드 반환")
    void testRootNode() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));

      assertThat(trie.getRoot()).isNotNull();
      assertThat(trie.getRoot().depth).isEqualTo(0);
    }
  }

  // ============================================================
  // 2. 엣지 케이스
  // ============================================================

  @Nested
  @DisplayName("잘못된 입력 - 생성자")
  class InvalidInputsConstructor {

    @Test
    @DisplayName("null 패턴 목록 - 예외 발생")
    void testNullPatterns() {
      assertThatThrownBy(() -> new AhoCorasickTrie(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴이 최소 하나 이상 필요합니다");
    }

    @Test
    @DisplayName("빈 패턴 목록 - 예외 발생")
    void testEmptyPatterns() {
      assertThatThrownBy(() -> new AhoCorasickTrie(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴이 최소 하나 이상 필요합니다");
    }

    @Test
    @DisplayName("패턴 목록 내 null 포함 - 예외 발생")
    void testPatternsContainingNull() {
      List<String> patterns = Arrays.asList("hello", null, "world");

      assertThatThrownBy(() -> new AhoCorasickTrie(patterns))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴 목록에 null이 포함되어 있습니다");
    }

    @Test
    @DisplayName("패턴 목록 내 빈 문자열 포함 - 예외 발생")
    void testPatternsContainingEmpty() {
      List<String> patterns = List.of("hello", "", "world");

      assertThatThrownBy(() -> new AhoCorasickTrie(patterns))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴 목록에 빈 문자열이 포함되어 있습니다");
    }

    @Test
    @DisplayName("패턴 목록 내 null과 빈 문자열 모두 포함 - 예외 발생")
    void testPatternsContainingNullAndEmpty() {
      List<String> patterns = Arrays.asList("hello", null, "", "world");

      assertThatThrownBy(() -> new AhoCorasickTrie(patterns)).isInstanceOf(
        IllegalArgumentException.class
      );
    }
  }

  @Nested
  @DisplayName("잘못된 입력 - Builder")
  class InvalidInputsBuilder {

    @Test
    @DisplayName("Builder에 null 패턴 추가 - 예외 발생")
    void testBuilderAddNullPattern() {
      assertThatThrownBy(() ->
        AhoCorasickTrie.builder().addPattern(null).build()
      )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴은 null일 수 없습니다");
    }

    @Test
    @DisplayName("Builder에 빈 문자열 패턴 추가 - 예외 발생")
    void testBuilderAddEmptyPattern() {
      assertThatThrownBy(() -> AhoCorasickTrie.builder().addPattern("").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴은 빈 문자열일 수 없습니다");
    }

    @Test
    @DisplayName("Builder에 패턴 추가 없이 build - 예외 발생")
    void testBuilderBuildWithoutPatterns() {
      assertThatThrownBy(() -> AhoCorasickTrie.builder().build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴이 최소 하나 이상 필요합니다");
    }

    @Test
    @DisplayName("Builder addPatterns에 null 컬렉션 전달 - 정상 동작")
    void testBuilderAddPatternsNull() {
      // addPatterns(null)은 조용히 무시됨
      assertThatThrownBy(() ->
        AhoCorasickTrie.builder().addPatterns(null).build()
      )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("패턴이 최소 하나 이상 필요합니다");
    }
  }

  @Nested
  @DisplayName("특수한 패턴 케이스")
  class SpecialPatternCases {

    @Test
    @DisplayName("단일 문자 패턴")
    void testSingleCharacterPattern() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("a", "b", "c"));

      assertThat(trie.getPatternCount()).isEqualTo(3);
      assertThat(trie.getMaxPatternLength()).isEqualTo(1);
    }

    @Test
    @DisplayName("매우 긴 패턴")
    void testVeryLongPattern() {
      String longPattern = "a".repeat(1000);
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of(longPattern));

      assertThat(trie.getPatternCount()).isEqualTo(1);
      assertThat(trie.getMaxPatternLength()).isEqualTo(1000);
    }

    @Test
    @DisplayName("중첩 패턴 (prefix 관계)")
    void testNestedPatterns() {
      // "abc"는 "abcd"의 접두사
      List<String> patterns = List.of("abc", "abcd", "abcde");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(3);
      assertThat(trie.getMaxPatternLength()).isEqualTo(5);
    }

    @Test
    @DisplayName("겹치는 패턴 (overlapping)")
    void testOverlappingPatterns() {
      // "abc", "bcd", "cde"는 서로 겹침
      List<String> patterns = List.of("abc", "bcd", "cde");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("공백 포함 패턴")
    void testPatternsWithWhitespace() {
      List<String> patterns = List.of("hello world", "foo bar", "test ");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(3);
      assertThat(trie.getPatterns()).containsExactlyInAnyOrder(
        "hello world",
        "foo bar",
        "test "
      );
    }

    @Test
    @DisplayName("특수 문자 포함 패턴")
    void testPatternsWithSpecialCharacters() {
      List<String> patterns = List.of(
        "<tag>",
        "</tag>",
        "[bracket]",
        "{brace}"
      );
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("줄바꿈 문자 포함 패턴")
    void testPatternsWithNewlines() {
      List<String> patterns = List.of("line1\nline2", "text\r\nmore");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(2);
    }
  }

  // ============================================================
  // 3. 프로젝트 사용 패턴
  // ============================================================

  @Nested
  @DisplayName("프로젝트 사용 패턴 - XML 태그 검출")
  class XmlTagPatterns {

    @Test
    @DisplayName("기본 XML 태그 패턴")
    void testBasicXmlTags() {
      List<String> patterns = List.of(
        "<tag>",
        "</tag>",
        "<another>",
        "</another>"
      );
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("한글 XML 태그 패턴")
    void testKoreanXmlTags() {
      List<String> patterns = List.of("<제목>", "</제목>", "<내용>", "</내용>");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("혼합 언어 XML 태그 패턴")
    void testMixedLanguageXmlTags() {
      List<String> patterns = List.of(
        "<title>",
        "</title>",
        "<제목>",
        "</제목>",
        "<content>",
        "</content>",
        "<내용>",
        "</내용>"
      );
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("LLM 응답 패턴 (tool_use 시나리오)")
    void testLlmToolUsePatterns() {
      // 실제 LLM tool_use에서 검출하는 패턴
      List<String> patterns = List.of(
        "<function_calls>",
        "</function_calls>",
        "<invoke>",
        "</invoke>",
        "<parameter>",
        "</parameter>"
      );
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(6);
      assertThat(trie.getMaxPatternLength()).isEqualTo(17); // "</function_calls>"
    }

    @Test
    @DisplayName("대소문자 구분 패턴")
    void testCaseSensitivePatterns() {
      List<String> patterns = List.of("<Tag>", "<tag>", "<TAG>");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      // 대소문자를 구분하므로 3개 모두 별도 패턴
      assertThat(trie.getPatternCount()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("프로젝트 사용 패턴 - 실제 LLM 응답")
  class RealWorldLlmPatterns {

    @Test
    @DisplayName("한글/영어 혼합 응답 패턴")
    void testMixedKoreanEnglishResponse() {
      List<String> patterns = List.of(
        "안녕하세요",
        "Hello",
        "<thinking>",
        "</thinking>",
        "감사합니다",
        "Thank you"
      );
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("코드 블록 패턴")
    void testCodeBlockPatterns() {
      List<String> patterns = List.of("```java", "```python", "```", "```\n");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("마크다운 패턴")
    void testMarkdownPatterns() {
      List<String> patterns = List.of("**", "*", "##", "#", "- ", "1. ");
      AhoCorasickTrie trie = new AhoCorasickTrie(patterns);

      assertThat(trie.getPatternCount()).isEqualTo(6);
    }
  }

  // ============================================================
  // 4. Trie 구조 검증
  // ============================================================

  @Nested
  @DisplayName("Trie 내부 구조 검증")
  class TrieStructureValidation {

    @Test
    @DisplayName("루트 노드는 depth 0")
    void testRootDepth() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));

      assertThat(trie.getRoot().depth).isEqualTo(0);
    }

    @Test
    @DisplayName("루트 노드는 실패 링크가 null")
    void testRootFailureLink() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));

      assertThat(trie.getRoot().failureLink).isNull();
    }

    @Test
    @DisplayName("루트의 자식 노드들은 실패 링크가 루트")
    void testRootChildrenFailureLinks() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc", "bcd"));

      for (AhoCorasickTrie.TrieNode child : trie.getRoot().children.values()) {
        assertThat(child.failureLink).isEqualTo(trie.getRoot());
      }
    }

    @Test
    @DisplayName("패턴 종료 노드는 outputs에 해당 패턴 포함")
    void testPatternOutputs() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "abc" 경로 탐색
      AhoCorasickTrie.TrieNode node = root.children.get('a');
      assertThat(node).isNotNull();
      assertThat(node.outputs).isEmpty(); // 중간 노드

      node = node.children.get('b');
      assertThat(node).isNotNull();
      assertThat(node.outputs).isEmpty(); // 중간 노드

      node = node.children.get('c');
      assertThat(node).isNotNull();
      assertThat(node.outputs).containsExactly("abc"); // 패턴 종료 노드
    }

    @Test
    @DisplayName("중첩 패턴의 outputs 검증")
    void testNestedPatternOutputs() {
      // "he"와 "she" - "he"는 "she"의 suffix
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("he", "she"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "she" 경로의 마지막 'e' 노드는 "he"와 "she" 모두 포함해야 함
      AhoCorasickTrie.TrieNode node = root.children.get('s');
      node = node.children.get('h');
      node = node.children.get('e');

      assertThat(node.outputs).containsExactlyInAnyOrder("he", "she");
    }

    @Test
    @DisplayName("노드 depth 검증")
    void testNodeDepths() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      AhoCorasickTrie.TrieNode nodeA = root.children.get('a');
      assertThat(nodeA.depth).isEqualTo(1);

      AhoCorasickTrie.TrieNode nodeB = nodeA.children.get('b');
      assertThat(nodeB.depth).isEqualTo(2);

      AhoCorasickTrie.TrieNode nodeC = nodeB.children.get('c');
      assertThat(nodeC.depth).isEqualTo(3);
    }

    @Test
    @DisplayName("겹치는 패턴의 실패 링크 구조")
    void testOverlappingPatternsFailureLinks() {
      // "abc", "bc", "c" - 서로 suffix 관계
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc", "bc", "c"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "abc" 경로의 'c' 노드 확인
      AhoCorasickTrie.TrieNode nodeA = root.children.get('a');
      AhoCorasickTrie.TrieNode nodeB = nodeA.children.get('b');
      AhoCorasickTrie.TrieNode nodeC = nodeB.children.get('c');

      // 이 노드는 "abc", "bc", "c" 모두 출력해야 함
      assertThat(nodeC.outputs).containsExactlyInAnyOrder("abc", "bc", "c");
    }
  }

  @Nested
  @DisplayName("Trie 불변성 검증")
  class TrieImmutability {

    @Test
    @DisplayName("생성 후 패턴 목록 수정 불가")
    void testPatternsImmutable() {
      List<String> originalPatterns = new ArrayList<>(List.of("test"));
      AhoCorasickTrie trie = new AhoCorasickTrie(originalPatterns);

      // 원본 리스트 수정
      originalPatterns.add("new");

      // Trie는 영향받지 않음
      assertThat(trie.getPatternCount()).isEqualTo(1);
      assertThat(trie.getPatterns()).containsExactly("test");
    }

    @Test
    @DisplayName("getPatterns() 반환값 수정 불가")
    void testGetPatternsImmutable() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("test"));
      Set<String> patterns = trie.getPatterns();

      assertThatThrownBy(() -> patterns.add("new")).isInstanceOf(
        UnsupportedOperationException.class
      );
    }
  }

  // ============================================================
  // 5. 실패 링크 구조 검증
  // ============================================================

  @Nested
  @DisplayName("실패 링크(Failure Link) 구조 검증")
  class FailureLinkValidation {

    @Test
    @DisplayName("단순 패턴의 실패 링크 - 모두 루트")
    void testSimplePatternFailureLinks() {
      // 겹치지 않는 패턴들
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc", "def"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "abc" 경로의 실패 링크
      AhoCorasickTrie.TrieNode nodeA = root.children.get('a');
      assertThat(nodeA.failureLink).isEqualTo(root);

      AhoCorasickTrie.TrieNode nodeB = nodeA.children.get('b');
      assertThat(nodeB.failureLink).isEqualTo(root);

      AhoCorasickTrie.TrieNode nodeC = nodeB.children.get('c');
      assertThat(nodeC.failureLink).isEqualTo(root);
    }

    @Test
    @DisplayName("suffix 관계 패턴의 실패 링크 - he/she")
    void testSuffixPatternFailureLinks() {
      // "he"는 "she"의 suffix
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("he", "she"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "he" 경로
      AhoCorasickTrie.TrieNode nodeH_he = root.children.get('h');
      AhoCorasickTrie.TrieNode nodeE_he = nodeH_he.children.get('e');

      // "she" 경로
      AhoCorasickTrie.TrieNode nodeS = root.children.get('s');
      AhoCorasickTrie.TrieNode nodeH_she = nodeS.children.get('h');
      AhoCorasickTrie.TrieNode nodeE_she = nodeH_she.children.get('e');

      // "she"의 'h'는 "he"의 'h'를 실패 링크로
      assertThat(nodeH_she.failureLink).isEqualTo(nodeH_he);

      // "she"의 'e'는 "he"의 'e'를 실패 링크로
      assertThat(nodeE_she.failureLink).isEqualTo(nodeE_he);

      // "she"의 마지막 'e'는 "he"와 "she" 모두 출력
      assertThat(nodeE_she.outputs).containsExactlyInAnyOrder("he", "she");
    }

    @Test
    @DisplayName("다중 suffix 관계 - abc/bc/c")
    void testMultipleSuffixFailureLinks() {
      // "c"는 "bc"의 suffix, "bc"는 "abc"의 suffix
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc", "bc", "c"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "c" 경로
      AhoCorasickTrie.TrieNode nodeC_c = root.children.get('c');

      // "bc" 경로
      AhoCorasickTrie.TrieNode nodeB_bc = root.children.get('b');
      AhoCorasickTrie.TrieNode nodeC_bc = nodeB_bc.children.get('c');

      // "abc" 경로
      AhoCorasickTrie.TrieNode nodeA = root.children.get('a');
      AhoCorasickTrie.TrieNode nodeB_abc = nodeA.children.get('b');
      AhoCorasickTrie.TrieNode nodeC_abc = nodeB_abc.children.get('c');

      // "bc"의 'c'는 단독 "c"를 실패 링크로
      assertThat(nodeC_bc.failureLink).isEqualTo(nodeC_c);

      // "abc"의 'b'는 "bc"의 'b'를 실패 링크로
      assertThat(nodeB_abc.failureLink).isEqualTo(nodeB_bc);

      // "abc"의 'c'는 "bc"의 'c'를 실패 링크로
      assertThat(nodeC_abc.failureLink).isEqualTo(nodeC_bc);

      // "abc"의 마지막 'c'는 세 패턴 모두 출력
      assertThat(nodeC_abc.outputs).containsExactlyInAnyOrder("abc", "bc", "c");
    }

    @Test
    @DisplayName("겹치는 패턴의 실패 링크 - aba/bab")
    void testOverlappingPatternFailureLinks() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("aba", "bab"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "aba" 경로
      AhoCorasickTrie.TrieNode nodeA1 = root.children.get('a');
      AhoCorasickTrie.TrieNode nodeB1 = nodeA1.children.get('b');
      AhoCorasickTrie.TrieNode nodeA2 = nodeB1.children.get('a');

      // "bab" 경로
      AhoCorasickTrie.TrieNode nodeB2 = root.children.get('b');
      AhoCorasickTrie.TrieNode nodeA3 = nodeB2.children.get('a');
      AhoCorasickTrie.TrieNode nodeB3 = nodeA3.children.get('b');

      // "aba"의 'b'는 "bab"의 'b'를 실패 링크로
      assertThat(nodeB1.failureLink).isEqualTo(nodeB2);

      // "bab"의 'a'는 "aba"의 'a'를 실패 링크로
      assertThat(nodeA3.failureLink).isEqualTo(nodeA1);
    }

    @Test
    @DisplayName("한글 패턴의 실패 링크")
    void testKoreanPatternFailureLinks() {
      // "녕"은 "안녕"의 suffix
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("안녕", "녕"));
      AhoCorasickTrie.TrieNode root = trie.getRoot();

      // "녕" 단독 경로
      AhoCorasickTrie.TrieNode node_녕 = root.children.get('녕');

      // "안녕" 경로
      AhoCorasickTrie.TrieNode node_안 = root.children.get('안');
      AhoCorasickTrie.TrieNode node_녕2 = node_안.children.get('녕');

      // "안녕"의 '녕'은 단독 "녕"을 실패 링크로
      assertThat(node_녕2.failureLink).isEqualTo(node_녕);

      // "안녕"의 마지막 노드는 두 패턴 모두 출력
      assertThat(node_녕2.outputs).containsExactlyInAnyOrder("녕", "안녕");
    }
  }

  // ============================================================
  // 6. 실제 매칭 시뮬레이션
  // ============================================================

  @Nested
  @DisplayName("실제 매칭 시뮬레이션")
  class MatchingSimulation {

    /**
     * Aho-Corasick 알고리즘으로 텍스트에서 패턴 찾기
     */
    private List<MatchResult> findPatterns(AhoCorasickTrie trie, String text) {
      List<MatchResult> results = new ArrayList<>();
      AhoCorasickTrie.TrieNode state = trie.getRoot();

      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);

        // 상태 전이
        while (state != trie.getRoot() && !state.children.containsKey(c)) {
          state = state.failureLink;
        }

        if (state.children.containsKey(c)) {
          state = state.children.get(c);
        }

        // 매칭 확인
        if (!state.outputs.isEmpty()) {
          for (String pattern : state.outputs) {
            int startPos = i - pattern.length() + 1;
            results.add(new MatchResult(pattern, startPos, i + 1));
          }
        }
      }

      return results;
    }

    /**
     * 매칭 결과
     */
    record MatchResult(String pattern, int startPos, int endPos) {}

    @Test
    @DisplayName("단일 패턴 매칭 - 'hello' in 'hello world'")
    void testSinglePatternMatch() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      List<MatchResult> results = findPatterns(trie, "hello world");

      assertThat(results).hasSize(1);
      assertThat(results.get(0).pattern()).isEqualTo("hello");
      assertThat(results.get(0).startPos()).isEqualTo(0);
      assertThat(results.get(0).endPos()).isEqualTo(5);
    }

    @Test
    @DisplayName("다중 패턴 매칭 - 'he', 'she' in 'ushers'")
    void testMultiplePatternMatch() {
      // "ushers" = u(0) s(1) h(2) e(3) r(4) s(5)
      // "she" at index 1-4, "he" at index 2-4, "hers" at index 2-6
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("he", "she", "hers"));
      List<MatchResult> results = findPatterns(trie, "ushers");

      assertThat(results).hasSize(3);

      assertThat(results)
        .extracting("pattern")
        .containsExactlyInAnyOrder("she", "he", "hers");

      // "she" 위치 확인
      MatchResult sheMatch = results
        .stream()
        .filter(r -> r.pattern().equals("she"))
        .findFirst()
        .orElseThrow();
      assertThat(sheMatch.startPos()).isEqualTo(1);
      assertThat(sheMatch.endPos()).isEqualTo(4);
    }

    @Test
    @DisplayName("겹치는 패턴 매칭 - 'abc', 'bc', 'c' in 'abcd'")
    void testOverlappingPatternMatch() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("abc", "bc", "c"));
      List<MatchResult> results = findPatterns(trie, "abcd");

      // "abc" (0-3), "bc" (1-3), "c" (2-3)
      assertThat(results).hasSize(3);
      assertThat(results)
        .extracting("pattern")
        .containsExactlyInAnyOrder("abc", "bc", "c");
    }

    @Test
    @DisplayName("패턴 반복 매칭 - 'a' in 'aaaa'")
    void testRepeatedPatternMatch() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("a"));
      List<MatchResult> results = findPatterns(trie, "aaaa");

      assertThat(results).hasSize(4);
      assertThat(results).allMatch(r -> r.pattern().equals("a"));
    }

    @Test
    @DisplayName("한글 패턴 매칭")
    void testKoreanPatternMatch() {
      // "안녕하세요" = 안(0) 녕(1) 하(2) 세(3) 요(4)
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("안녕", "하세요"));
      List<MatchResult> results = findPatterns(trie, "안녕하세요");

      assertThat(results).hasSize(2);
      assertThat(results)
        .extracting("pattern")
        .containsExactlyInAnyOrder("안녕", "하세요");

      // "안녕" 위치
      MatchResult match1 = results
        .stream()
        .filter(r -> r.pattern().equals("안녕"))
        .findFirst()
        .orElseThrow();
      assertThat(match1.startPos()).isEqualTo(0);
      assertThat(match1.endPos()).isEqualTo(2);

      // "하세요" 위치
      MatchResult match2 = results
        .stream()
        .filter(r -> r.pattern().equals("하세요"))
        .findFirst()
        .orElseThrow();
      assertThat(match2.startPos()).isEqualTo(2);
      assertThat(match2.endPos()).isEqualTo(5);
    }

    @Test
    @DisplayName("XML 태그 패턴 매칭")
    void testXmlTagPatternMatch() {
      // "<tag>content</tag>" 길이 계산:
      // <tag> = 0-5, content = 5-12, </tag> = 12-18
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>", "</tag>"));
      List<MatchResult> results = findPatterns(trie, "<tag>content</tag>");

      assertThat(results).hasSize(2);

      MatchResult openTag = results
        .stream()
        .filter(r -> r.pattern().equals("<tag>"))
        .findFirst()
        .orElseThrow();
      assertThat(openTag.startPos()).isEqualTo(0);

      MatchResult closeTag = results
        .stream()
        .filter(r -> r.pattern().equals("</tag>"))
        .findFirst()
        .orElseThrow();
      assertThat(closeTag.startPos()).isEqualTo(12);
    }

    @Test
    @DisplayName("매칭 없음")
    void testNoMatch() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      List<MatchResult> results = findPatterns(trie, "world");

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("부분 매칭 실패 - 'hello' not in 'hell'")
    void testPartialMatchFail() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("hello"));
      List<MatchResult> results = findPatterns(trie, "hell");

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("대소문자 구분 매칭")
    void testCaseSensitiveMatch() {
      AhoCorasickTrie trie = new AhoCorasickTrie(List.of("Hello"));

      List<MatchResult> results1 = findPatterns(trie, "Hello world");
      assertThat(results1).hasSize(1);

      List<MatchResult> results2 = findPatterns(trie, "hello world");
      assertThat(results2).isEmpty();
    }

    @Test
    @DisplayName("복잡한 실제 시나리오 - LLM 응답 파싱")
    void testComplexLlmResponse() {
      AhoCorasickTrie trie = new AhoCorasickTrie(
        List.of(
          "<function_calls>",
          "</function_calls>",
          "<invoke>",
          "</invoke>"
        )
      );

      String llmResponse =
        "Some text<function_calls><invoke>tool</invoke></function_calls>more text";
      List<MatchResult> results = findPatterns(trie, llmResponse);

      assertThat(results).hasSize(4);
      assertThat(results)
        .extracting("pattern")
        .containsExactlyInAnyOrder(
          "<function_calls>",
          "<invoke>",
          "</invoke>",
          "</function_calls>"
        );

      // 순서 확인
      List<String> orderedPatterns = results
        .stream()
        .sorted(Comparator.comparingInt(MatchResult::startPos))
        .map(MatchResult::pattern)
        .toList();

      assertThat(orderedPatterns).containsExactly(
        "<function_calls>",
        "<invoke>",
        "</invoke>",
        "</function_calls>"
      );
    }
  }
}
