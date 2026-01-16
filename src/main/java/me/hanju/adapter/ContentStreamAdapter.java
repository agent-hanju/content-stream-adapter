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
import me.hanju.adapter.parser.OpenTagParser;
import me.hanju.adapter.parser.OpenTagParser.ParsedTag;
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
 */
public class ContentStreamAdapter {

  private final StreamPatternMatcher patternMatcher;
  private final TransitionTable transitionTable;
  private final OpenTagParser openTagParser;

  private TransitionNode currentState;
  private final StringBuilder rawAccumulator = new StringBuilder();

  public ContentStreamAdapter(TransitionSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }

    this.transitionTable = new TransitionTable(schema);
    this.currentState = transitionTable.getRoot();
    this.openTagParser = new OpenTagParser();

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

    rawAccumulator.append(token);

    List<TaggedToken> tokens = new ArrayList<>();

    // OpenTagParser가 파싱 중이면 먼저 처리
    if (openTagParser.isParsing()) {
      token = processOpenTagParsing(token, tokens);
      if (token.isEmpty()) {
        return tokens;
      }
    }

    for (MatchResult result : patternMatcher.addTokenAndGetResult(token)) {
      processMatchResult(result, tokens);
    }

    return tokens;
  }

  /**
   * OpenTagParser로 열린 태그 파싱 처리
   *
   * @return 남은 토큰 (태그 완성 후 남은 부분)
   */
  private String processOpenTagParsing(String token, List<TaggedToken> tokens) {
    ParsedTag parsedTag = openTagParser.feed(token);

    if (parsedTag != null) {
      processCompleteOpenTag(parsedTag, tokens);
      return openTagParser.getRemaining();
    }

    return "";
  }

  /**
   * 완성된 열린 태그 처리
   */
  private void processCompleteOpenTag(ParsedTag parsedTag, List<TaggedToken> tokens) {
    TagInfo tag = TagInfo.open(parsedTag.tagName(), parsedTag.attributes());
    String pathBeforeTransition = currentState.getPath();
    boolean transitioned = tryTransition(tag);

    if (transitioned) {
      Map<String, String> filteredAttrs = filterAttributes(currentState.getPath(),
          tag.attributes());
      tokens.add(TaggedToken.openEvent(currentState.getPath(), filteredAttrs));
    } else {
      tokens.add(new TaggedToken(pathBeforeTransition, parsedTag.rawTag()));
    }
  }

  /**
   * 스키마에 정의된 attribute만 필터링
   */
  private Map<String, String> filterAttributes(String path, Map<String, String> attributes) {
    if (attributes.isEmpty()) {
      return Collections.emptyMap();
    }

    Set<String> allowed = transitionTable.getAllowedAttributes(path);
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

  private void processMatchResult(MatchResult result, List<TaggedToken> tokens) {
    if (result instanceof MatchResult.TokenMatchResult(List<String> matchedTokens)) {
      if (openTagParser.isParsing()) {
        String combined = String.join("", matchedTokens);
        String remaining = processOpenTagParsing(combined, tokens);
        if (!remaining.isEmpty()) {
          tokens.add(new TaggedToken(currentState.getPath(), remaining));
        }
      } else {
        for (String t : matchedTokens) {
          if (!t.isEmpty()) {
            tokens.add(new TaggedToken(currentState.getPath(), t));
          }
        }
      }
    } else if (result instanceof MatchResult.PatternMatchResult(List<String> prevTokens,
        String pattern)) {
      if (openTagParser.isParsing()) {
        String combined = String.join("", prevTokens);
        if (!combined.isEmpty()) {
          String remaining = processOpenTagParsing(combined, tokens);
          if (!remaining.isEmpty()) {
            tokens.add(new TaggedToken(currentState.getPath(), remaining));
          }
        }
      } else {
        for (String t : prevTokens) {
          if (!t.isEmpty()) {
            tokens.add(new TaggedToken(currentState.getPath(), t));
          }
        }
      }

      if (pattern.endsWith(">")) {
        processCloseTag(pattern, tokens);
      } else {
        openTagParser.start(pattern);
      }
    }
  }

  /**
   * 닫는 태그 처리
   */
  private void processCloseTag(String tagString, List<TaggedToken> tokens) {
    String tagName = tagString.substring(2, tagString.length() - 1);
    TagInfo tag = TagInfo.close(tagName);
    String pathBeforeTransition = currentState.getPath();
    boolean transitioned = tryTransition(tag);

    if (transitioned) {
      tokens.add(TaggedToken.closeEvent(pathBeforeTransition));
    } else {
      tokens.add(new TaggedToken(currentState.getPath(), tagString));
    }
  }

  /**
   * 남은 버퍼 텍스트를 모두 flush
   */
  public List<TaggedToken> flush() {
    List<TaggedToken> tokens = new ArrayList<>();

    if (openTagParser.isParsing()) {
      ParsedTag parsedTag = openTagParser.forceComplete();
      if (parsedTag != null) {
        processCompleteOpenTag(parsedTag, tokens);
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

  private Set<String> generatePatterns(Set<String> tagNames) {
    Set<String> patterns = new HashSet<>();
    for (String tag : tagNames) {
      patterns.add("<" + tag);
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

  public String getRaw() {
    return rawAccumulator.toString();
  }
}
