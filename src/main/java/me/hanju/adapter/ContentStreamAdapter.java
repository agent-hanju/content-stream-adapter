package me.hanju.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  private final TransitionSchema schema;

  private TransitionNode currentState;
  private StringBuilder pendingTagBuffer = null; // `<tagname` 매칭 후 `>`까지 버퍼링
  private final StringBuilder rawAccumulator = new StringBuilder(); // 원문 누적

  public ContentStreamAdapter(TransitionSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }

    this.schema = schema;
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

    // 원문 누적
    rawAccumulator.append(token);

    List<TaggedToken> tokens = new ArrayList<>();

    // pending 태그가 있으면 먼저 처리
    if (pendingTagBuffer != null) {
      token = processPendingTag(token, tokens);
      if (token.isEmpty()) {
        return tokens;
      }
    }

    List<MatchResult> matchResults = patternMatcher.addTokenAndGetResult(token);
    processMatchResults(matchResults, tokens);

    return tokens;
  }

  /**
   * 매칭 결과들을 처리 (pending 태그 상태 관리 포함)
   */
  private void processMatchResults(List<MatchResult> results, List<TaggedToken> tokens) {
    for (MatchResult result : results) {
      if (result instanceof MatchResult.TokenMatchResult tokenMatch) {
        if (pendingTagBuffer != null) {
          // pending 상태에서 TokenMatch가 오면 pending 버퍼에 추가
          String combined = String.join("", tokenMatch.tokens());
          String remaining = processPendingTag(combined, tokens);
          if (!remaining.isEmpty()) {
            // '>' 이후 남은 부분은 일반 content로 처리
            // (TokenMatchResult는 이미 "안전한 텍스트"로 판단된 것이므로)
            tokens.add(new TaggedToken(currentState.getPath(), remaining));
          }
        } else {
          for (String t : tokenMatch.tokens()) {
            if (!t.isEmpty()) {
              tokens.add(new TaggedToken(currentState.getPath(), t));
            }
          }
        }
      } else if (result instanceof MatchResult.PatternMatchResult patternMatch) {
        // prevTokens 처리
        if (pendingTagBuffer != null) {
          // pending 상태에서 prevTokens가 오면 pending 버퍼에 추가
          String combined = String.join("", patternMatch.prevTokens());
          if (!combined.isEmpty()) {
            String remaining = processPendingTag(combined, tokens);
            if (!remaining.isEmpty()) {
              // '>' 이후 남은 부분은 일반 content로 처리
              // (prevTokens는 이미 StreamPatternMatcher 버퍼에서 추출된 것이므로)
              tokens.add(new TaggedToken(currentState.getPath(), remaining));
            }
          }
        } else {
          for (String t : patternMatch.prevTokens()) {
            if (!t.isEmpty()) {
              tokens.add(new TaggedToken(currentState.getPath(), t));
            }
          }
        }

        // 패턴 처리
        String pattern = patternMatch.pattern();

        if (!pattern.endsWith(">")) {
          // 열린 태그: '>'까지 버퍼링 필요
          pendingTagBuffer = new StringBuilder(pattern);
        } else {
          // 닫는 태그 처리
          processCloseTag(pattern, tokens);
        }
      }
    }
  }

  /**
   * pending 태그 버퍼에 토큰을 추가하고 '>'를 찾아 완성된 태그를 처리
   * 따옴표 안의 '>'는 무시합니다.
   *
   * @return 남은 토큰 (태그 완성 후 남은 부분)
   */
  private String processPendingTag(String token, List<TaggedToken> tokens) {
    int closeIndex = findTagCloseIndex(pendingTagBuffer.toString(), token);

    if (closeIndex == -1) {
      // '>'가 없으면 전체를 버퍼에 추가
      pendingTagBuffer.append(token);
      return "";
    }

    // '>'까지 포함하여 태그 완성
    pendingTagBuffer.append(token, 0, closeIndex + 1);
    String completeTag = pendingTagBuffer.toString();
    pendingTagBuffer = null;

    // 완성된 태그 처리
    processCompleteOpenTag(completeTag, tokens);

    // '>' 이후 남은 부분 반환
    return token.substring(closeIndex + 1);
  }

  /**
   * 태그를 닫는 '>' 위치를 찾습니다.
   * 따옴표 안의 '>'는 무시합니다.
   *
   * @param buffer 현재까지의 pending 버퍼 내용
   * @param token 새로 들어온 토큰
   * @return token 내에서 태그를 닫는 '>' 위치, 없으면 -1
   */
  private int findTagCloseIndex(String buffer, String token) {
    // 버퍼에서 따옴표 상태 계산
    boolean inQuote = false;
    char quoteChar = 0;

    for (int i = 0; i < buffer.length(); i++) {
      char c = buffer.charAt(i);
      if (!inQuote && (c == '"' || c == '\'')) {
        inQuote = true;
        quoteChar = c;
      } else if (inQuote && c == quoteChar) {
        inQuote = false;
        quoteChar = 0;
      }
    }

    // 토큰에서 '>' 찾기 (따옴표 상태 유지)
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (!inQuote && (c == '"' || c == '\'')) {
        inQuote = true;
        quoteChar = c;
      } else if (inQuote && c == quoteChar) {
        inQuote = false;
        quoteChar = 0;
      } else if (!inQuote && c == '>') {
        return i;
      }
    }

    return -1;
  }

  /**
   * 완성된 열린 태그 처리 (속성 포함)
   */
  private void processCompleteOpenTag(String tagString, List<TaggedToken> tokens) {
    TagInfo tag = TagInfo.parse(tagString);
    String pathBeforeTransition = currentState.getPath();
    boolean transitioned = tryTransition(tag);

    if (transitioned) {
      // 허용된 attribute만 필터링
      Map<String, String> filteredAttrs = filterAttributes(currentState.getPath(), tag.attributes());
      tokens.add(TaggedToken.openEvent(currentState.getPath(), filteredAttrs));
    } else {
      // 전이 실패한 태그는 텍스트로 처리
      tokens.add(new TaggedToken(pathBeforeTransition, tagString));
    }
  }

  /**
   * 닫는 태그 처리
   */
  private void processCloseTag(String tagString, List<TaggedToken> tokens) {
    TagInfo tag = TagInfo.parse(tagString);
    String pathBeforeTransition = currentState.getPath();
    boolean transitioned = tryTransition(tag);

    if (transitioned) {
      tokens.add(TaggedToken.closeEvent(pathBeforeTransition));
    } else {
      tokens.add(new TaggedToken(currentState.getPath(), tagString));
    }
  }

  /**
   * 스키마에 정의된 attribute만 필터링
   */
  private Map<String, String> filterAttributes(String path, Map<String, String> attributes) {
    if (attributes.isEmpty()) {
      return Collections.emptyMap();
    }

    Set<String> allowed = schema.getAllowedAttributes(path);
    if (allowed.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, String> filtered = new HashMap<>();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      if (allowed.contains(entry.getKey())) {
        filtered.put(entry.getKey(), entry.getValue());
      }
    }

    return filtered.isEmpty() ? Collections.emptyMap() : filtered;
  }

  /**
   * 남은 버퍼 텍스트를 모두 flush
   * <p>
   * 불완전한 열린 태그가 있는 경우 (예: "&lt;cite id=\"ref1\"" - '>'가 없음),
   * 태그 형식이 유효하면 OPEN 이벤트로 처리합니다.
   * </p>
   */
  public List<TaggedToken> flush() {
    List<TaggedToken> tokens = new ArrayList<>();

    // pending 태그가 있으면 불완전한 열린 태그로 처리
    if (pendingTagBuffer != null) {
      String incomplete = pendingTagBuffer.toString();
      pendingTagBuffer = null;
      if (!incomplete.isEmpty()) {
        // '>'를 추가하여 완전한 태그로 만들고 처리 시도
        processCompleteOpenTag(incomplete + ">", tokens);
      }
    }

    List<String> remaining = patternMatcher.flushRemaining();
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

  /**
   * 패턴 생성: 열린 태그는 `<tagname`까지만, 닫는 태그는 `</tagname>`까지
   * <p>
   * 열린 태그는 속성이 올 수 있으므로 `>`를 포함하지 않음.
   * 매칭 후 `>`까지 버퍼링하여 전체 태그를 파싱함.
   * </p>
   */
  private Set<String> generatePatterns(Set<String> tagNames) {
    Set<String> patterns = new HashSet<>();
    for (String tag : tagNames) {
      patterns.add("<" + tag);      // 열린 태그: <tagname (속성 가능)
      patterns.add("</" + tag + ">"); // 닫는 태그: </tagname>
    }
    return patterns;
  }

  TransitionNode getCurrentState() {
    return currentState;
  }

  public String getCurrentPath() {
    return currentState.getPath();
  }

  /**
   * 지금까지 받은 모든 토큰의 원문을 반환
   *
   * @return 누적된 원문 문자열
   */
  public String getRaw() {
    return rawAccumulator.toString();
  }
}
