package me.hanju.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.hanju.adapter.matcher.AhoCorasickTrie;
import me.hanju.adapter.matcher.StreamPatternMatcher;
import me.hanju.adapter.payload.MatchResult;
import me.hanju.adapter.payload.TagInfo;
import me.hanju.adapter.payload.TagInfo.TagType;
import me.hanju.adapter.payload.TaggedToken;
import me.hanju.adapter.transition.TransitionNode;
import me.hanju.adapter.transition.TransitionSchema;
import me.hanju.adapter.transition.TransitionTable;

/**
 * 스트리밍 토큰을 XML-like 태그로 파싱하고 상태 전이를 수행하는 어댑터
 * <p>
 * Aho-Corasick 알고리즘으로 다중 패턴 매칭(O(n))을 수행하고,
 * 정의된 스키마에 따라 태그 전이를 시도합니다.
 * 전이 불가능한 태그는 일반 텍스트로 처리됩니다.
 * </p>
 *
 * <h3>사용 예시:</h3>
 *
 * <pre>
 * TransitionSchema schema = TransitionSchema.root()
 *     .tag("section", section -> section
 *         .tag("subsection", subsection -> subsection
 *             .tag("content"))
 *         .tag("metadata"))
 *     .tag("cite").alias("rag")
 *     .tag("result");
 *
 * ContentStreamAdapter adapter = new ContentStreamAdapter(schema);
 *
 * List&lt;TaggedToken&gt; tokens = adapter.feedToken("Hello &lt;cite&gt;ref&lt;/cite&gt; world");
 * // → TaggedToken("/", "Hello ")
 * // → TaggedToken("/cite", "ref")
 * // → TaggedToken("/", " world")
 * </pre>
 */
public class ContentStreamAdapter {

  private final StreamPatternMatcher patternMatcher;
  private final TransitionTable transitionTable;

  private TransitionNode currentState;

  public ContentStreamAdapter(TransitionSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }

    this.transitionTable = new TransitionTable(schema);
    this.currentState = transitionTable.getRoot();

    Set<String> patterns = generatePatterns(transitionTable.getAllTagNames());
    this.patternMatcher = new StreamPatternMatcher(
        new AhoCorasickTrie(patterns));
  }

  /**
   * 토큰을 처리하여 현재 상태 기준의 TaggedToken 리스트 반환
   */
  public List<TaggedToken> feedToken(String token) {
    if (token == null || token.isEmpty()) {
      return Collections.emptyList();
    }

    List<TaggedToken> tokens = new ArrayList<>();

    MatchResult result = patternMatcher.addTokenAndGetResult(token);

    if (result instanceof MatchResult.TokenMatchResult tokenMatch) {
      for (String t : tokenMatch.tokens()) {
        if (!t.isEmpty()) {
          tokens.add(new TaggedToken(currentState.getPath(), t));
        }
      }
    } else if (result instanceof MatchResult.PatternMatchResult patternMatch) {
      for (String t : patternMatch.prevTokens()) {
        if (!t.isEmpty()) {
          tokens.add(new TaggedToken(currentState.getPath(), t));
        }
      }

      TagInfo tag = TagInfo.parse(patternMatch.pattern());
      String pathBeforeTransition = currentState.getPath();
      boolean transitioned = tryTransition(tag);

      if (transitioned) {
        // 전이 성공: OPEN 또는 CLOSE 이벤트 추가
        if (tag.type() == TagType.OPEN) {
          tokens.add(TaggedToken.openEvent(currentState.getPath()));
        } else {
          tokens.add(TaggedToken.closeEvent(pathBeforeTransition));
        }
      } else {
        // 전이 실패한 태그는 텍스트로 처리
        tokens.add(new TaggedToken(currentState.getPath(), patternMatch.pattern()));
      }
    }

    return tokens;
  }

  /**
   * 남은 버퍼 텍스트를 모두 flush
   */
  public List<TaggedToken> flush() {
    List<String> remaining = patternMatcher.flushRemaining();
    List<TaggedToken> tokens = new ArrayList<>();
    for (String chunk : remaining) {
      if (!chunk.isEmpty()) {
        tokens.add(new TaggedToken(currentState.getPath(), chunk));
      }
    }
    return tokens;
  }

  private boolean tryTransition(TagInfo tag) {
    if (tag.type() == TagType.OPEN) {
      TransitionNode nextState = transitionTable.tryOpen(currentState, tag.name());
      if (nextState != null) {
        currentState = nextState;
        return true;
      }
      return false;

    } else {
      TransitionNode nextState = transitionTable.tryClose(currentState, tag.name());
      if (nextState != null) {
        currentState = nextState;
        return true;
      }
      return false;
    }
  }

  private Set<String> generatePatterns(Set<String> tagNames) {
    Set<String> patterns = new HashSet<>();
    for (String tag : tagNames) {
      patterns.add("<" + tag + ">");
      patterns.add("</" + tag + ">");
    }
    return patterns;
  }

  TransitionNode getCurrentState() {
    return currentState;
  }

  public String getCurrentPath() {
    return currentState.getPath();
  }
}
