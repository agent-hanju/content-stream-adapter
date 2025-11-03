package me.hanju.adapter.matcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Aho-Corasick Trie 자료구조
 * 패턴들을 저장하고 실패 링크를 구축합니다.
 * 여러 검출기가 동일한 Trie 인스턴스를 공유할 수 있습니다.
 */
public class AhoCorasickTrie {

  /**
   * Trie 노드
   */
  public static class TrieNode {
    Map<Character, TrieNode> children = new HashMap<>();
    TrieNode failureLink = null;
    List<String> outputs = new ArrayList<>();
    int depth = 0;

    // 패키지 접근: Trie와 Matcher만 접근 가능
    TrieNode() {
    }
  }

  private final TrieNode root;
  private final Set<String> patterns;
  private final int maxPatternLength;

  /**
   * 생성자: 패턴들로 Trie 구축
   *
   * @param patterns 검출할 패턴 목록
   * @throws IllegalArgumentException patterns가 null, 비어있음, 또는 null/빈 문자열 패턴 포함 시
   */
  public AhoCorasickTrie(Collection<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      throw new IllegalArgumentException("패턴이 최소 하나 이상 필요합니다");
    }

    // 패턴 내부에 null이나 빈 문자열이 있는지 검증
    for (String pattern : patterns) {
      if (pattern == null) {
        throw new IllegalArgumentException("패턴 목록에 null이 포함되어 있습니다");
      }
      if (pattern.isEmpty()) {
        throw new IllegalArgumentException("패턴 목록에 빈 문자열이 포함되어 있습니다");
      }
    }

    this.patterns = new HashSet<>(patterns);
    this.root = new TrieNode();
    this.maxPatternLength = patterns.stream()
        .mapToInt(String::length)
        .max()
        .orElse(0);

    buildTrie();
    buildFailureLinks();
  }

  /**
   * [1단계] Trie 구조 생성
   */
  private void buildTrie() {
    for (String pattern : patterns) {
      TrieNode node = root;

      for (int i = 0; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        final int parentDepth = node.depth;

        node = node.children.computeIfAbsent(c, k -> {
          TrieNode newNode = new TrieNode();
          newNode.depth = parentDepth + 1;
          return newNode;
        });
      }

      node.outputs.add(pattern);
    }
  }

  /**
   * [2단계] 실패 링크 생성
   */
  private void buildFailureLinks() {
    Queue<TrieNode> queue = new LinkedList<>();

    for (TrieNode child : root.children.values()) {
      child.failureLink = root;
      queue.offer(child);
    }

    while (!queue.isEmpty()) {
      TrieNode current = queue.poll();

      for (Map.Entry<Character, TrieNode> entry : current.children.entrySet()) {
        char c = entry.getKey();
        TrieNode child = entry.getValue();
        queue.offer(child);

        TrieNode failNode = current.failureLink;

        while (failNode != null && !failNode.children.containsKey(c)) {
          failNode = failNode.failureLink;
        }

        child.failureLink = (failNode != null)
            ? failNode.children.get(c)
            : root;

        child.outputs.addAll(child.failureLink.outputs);
      }
    }
  }

  /**
   * Trie의 루트 노드 반환
   *
   * @return 루트 노드
   */
  public TrieNode getRoot() {
    return root;
  }

  /**
   * 등록된 패턴 목록 반환
   *
   * @return 패턴 집합 (수정 불가)
   */
  public Set<String> getPatterns() {
    return Collections.unmodifiableSet(patterns);
  }

  /**
   * 가장 긴 패턴의 길이 반환
   *
   * @return 최대 패턴 길이
   */
  public int getMaxPatternLength() {
    return maxPatternLength;
  }

  /**
   * 패턴 수 반환
   *
   * @return 패턴 개수
   */
  public int getPatternCount() {
    return patterns.size();
  }

  /**
   * Builder 패턴으로 Trie 생성
   */
  public static class Builder {
    private final Set<String> patterns = new HashSet<>();

    /**
     * 패턴 추가
     *
     * @param pattern 추가할 패턴
     * @return this
     * @throws IllegalArgumentException pattern이 null이거나 빈 문자열인 경우
     */
    public Builder addPattern(String pattern) {
      if (pattern == null) {
        throw new IllegalArgumentException("패턴은 null일 수 없습니다");
      }
      if (pattern.isEmpty()) {
        throw new IllegalArgumentException("패턴은 빈 문자열일 수 없습니다");
      }
      patterns.add(pattern);
      return this;
    }

    /**
     * 여러 패턴 추가
     *
     * @param patterns 추가할 패턴들
     * @return this
     */
    public Builder addPatterns(Collection<String> patterns) {
      if (patterns != null) {
        patterns.forEach(this::addPattern);
      }
      return this;
    }

    /**
     * Trie 빌드
     *
     * @return 생성된 Trie
     */
    public AhoCorasickTrie build() {
      return new AhoCorasickTrie(patterns);
    }
  }

  /**
   * Builder 인스턴스 생성
   *
   * @return Builder
   */
  public static Builder builder() {
    return new Builder();
  }
}
