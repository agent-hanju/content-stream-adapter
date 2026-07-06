package dev.hanju.adapter.matching;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/** Aho-Corasick Trie 자료구조 */
public class AhoCorasickTrie {

  /** Aho-Corasick automaton 상태 */
  private static final class State {
    private final int id;
    private final Map<Character, State> children = new HashMap<>();
    private @Nullable State failureLink = null;
    private String matchedPattern = "";
    private int matchingLength = 0;

    private State(final int id) {
      this.id = id;
    }
  }

  private final State root;
  private final List<State> states = new ArrayList<>();
  private final Set<String> patterns;
  private final int maxPatternLength;

  /**
   * 생성자: 패턴들로 Trie 구축
   *
   * @param patterns 검출할 패턴 목록
   * @throws IllegalArgumentException patterns가 null, 비어있음, 또는 null/빈 문자열 패턴 포함 시
   */
  public AhoCorasickTrie(final Collection<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      throw new IllegalArgumentException("패턴이 최소 하나 이상 필요합니다");
    }
    for (final String pattern : patterns) {
      if (pattern == null) {
        throw new IllegalArgumentException("패턴 목록에 null이 포함되어 있습니다");
      }
      if (pattern.isEmpty()) {
        throw new IllegalArgumentException("패턴 목록에 빈 문자열이 포함되어 있습니다");
      }
    }

    this.patterns = new HashSet<>(patterns);
    this.root = new State(states.size());
    states.add(this.root);
    this.maxPatternLength = patterns.stream()
        .mapToInt(String::length)
        .max()
        .orElse(0);

    // [1단계] Trie 구조 생성
    for (final String pattern : patterns) {
      State state = root;

      for (int i = 0; i < pattern.length(); i++) {
        final char c = pattern.charAt(i);
        final State parent = state;
        state = parent.children.computeIfAbsent(c, key -> {
          final State child = new State(states.size());
          states.add(child);
          child.matchingLength = parent.matchingLength + 1;
          return child;
        });
      }

      if (pattern.length() > state.matchedPattern.length()) {
        state.matchedPattern = pattern;
      }
    }

    // [2단계] 실패 링크 생성
    final Queue<State> queue = new LinkedList<>();

    for (final State child : root.children.values()) {
      child.failureLink = root;
      queue.offer(child);
    }

    while (!queue.isEmpty()) {
      final State current = queue.poll();

      for (final Map.Entry<Character, State> entry : current.children.entrySet()) {
        final char c = entry.getKey();
        final State child = entry.getValue();
        queue.offer(child);

        State failNode = current.failureLink;

        while (failNode != null && !failNode.children.containsKey(c)) {
          failNode = failNode.failureLink;
        }

        // failNode.children.containsKey(c)가 true인 상태로 루프를 빠져나왔으므로 get(c)는 non-null
        final State resolvedFailureLink = (failNode != null)
            ? Objects.requireNonNull(failNode.children.get(c))
            : root;
        child.failureLink = resolvedFailureLink;

        // 같은 끝 위치의 여러 패턴 중 가장 긴 것만 report합니다.
        final String suffixPattern = resolvedFailureLink.matchedPattern;
        if (suffixPattern.length() > child.matchedPattern.length()) {
          child.matchedPattern = suffixPattern;
        }
      }
    }
  }

  /**
   * 등록된 패턴 목록
   *
   * @return 패턴 집합 (수정 불가)
   */
  public Set<String> getPatterns() {
    return Collections.unmodifiableSet(this.patterns);
  }

  /**
   * 가장 긴 패턴의 길이
   *
   * @return 가장 긴 패턴의 길이
   */
  public int getMaxPatternLength() {
    return this.maxPatternLength;
  }

  /**
   * 최초 상태 id
   *
   * @return root 상태 id
   */
  public int getInitialState() {
    return root.id;
  }

  /**
   * 지정한 상태에서 문자 하나를 바로 소비할 수 있는지 확인합니다.
   *
   * @param stateId 현재 상태 id
   * @param c 입력 문자
   * @return failure link를 타지 않고 바로 전이할 수 있으면 true
   */
  public boolean hasNextState(final int stateId, final char c) {
    return getState(stateId).children.containsKey(c);
  }

  /**
   * 지정한 상태에서 Aho-Corasick 전이를 수행하고 다음 상태 id를 반환합니다.
   *
   * <p>지정한 상태에서 직접 전이가 없으면 failure link를 따라가고,
   * 가능한 전이가 발견되면 해당 문자를 소비한 다음 상태로 이동합니다.
   * 어디에서도 전이할 수 없으면 root 상태로 이동합니다.</p>
   *
   * @param stateId 현재 상태 id
   * @param c 입력 문자
   * @return 다음 상태 id
   */
  public int nextState(final int stateId, final char c) {
    State current = getState(stateId);
    while (current != root && !current.children.containsKey(c)) {
      // root가 아닌 모든 state는 failureLink가 non-null이도록 생성 시 보장됨
      current = Objects.requireNonNull(current.failureLink);
    }

    if (current.children.containsKey(c)) {
      return Objects.requireNonNull(current.children.get(c)).id;
    }
    return root.id;
  }

  /**
   * 지정한 상태에서 매칭된 패턴을 반환합니다.
   *
   * <p>빈 문자열 패턴은 생성자에서 거부하므로, 빈 문자열은 매칭 없음만 의미합니다.</p>
   *
   * @param stateId 현재 상태 id
   * @return 매칭된 패턴. 매칭이 없으면 빈 문자열
   */
  public String getMatchedPattern(final int stateId) {
    return getState(stateId).matchedPattern;
  }

  /**
   * 지정한 상태에서 더 긴 입력으로 확장 가능한지 확인합니다.
   *
   * @param stateId 현재 상태 id
   * @return 자식 노드가 있으면 true
   */
  public boolean canContinue(final int stateId) {
    return !getState(stateId).children.isEmpty();
  }

  /**
   * 지정한 상태가 나타내는 매칭 진행 길이를 반환합니다.
   *
   * <p>스트리밍 매칭 중 버퍼 끝에 도달했을 때, 앞으로 들어올 입력과 합쳐져
   * 패턴이 될 수 있는 접미사의 길이를 구하는 데 사용됩니다.</p>
   *
   * @param stateId 현재 상태 id
   * @return 현재 매칭 진행 길이
   */
  public int getMatchingLength(final int stateId) {
    return getState(stateId).matchingLength;
  }

  private State getState(final int stateId) {
    if (stateId < 0 || stateId >= states.size()) {
      throw new IllegalArgumentException("유효하지 않은 상태 id입니다: " + stateId);
    }
    return states.get(stateId);
  }
}
