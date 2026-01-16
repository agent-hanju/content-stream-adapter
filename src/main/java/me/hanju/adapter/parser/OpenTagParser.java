package me.hanju.adapter.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 열린 태그를 스트리밍 방식으로 파싱하는 파서
 * <p>
 * {@code <tagname} 패턴이 감지된 후, {@code >}까지 스트리밍으로 파싱하여
 * 태그 이름과 속성을 추출합니다.
 * 따옴표 안의 {@code >}는 태그 종료로 처리하지 않습니다.
 * </p>
 *
 * <h3>사용 예시:</h3>
 * <pre>
 * OpenTagParser parser = new OpenTagParser();
 * parser.start("&lt;cite");
 *
 * ParsedTag result = parser.feed(" id=\"ref\"&gt;content");
 * // result != null (완성됨)
 * // result.tagName() == "cite"
 * // result.attributes() == {id: "ref"}
 * // result.rawTag() == "&lt;cite id=\"ref\"&gt;"
 * // parser.getRemaining() == "content"
 * </pre>
 */
public class OpenTagParser {

  private String tagName;
  private Map<String, String> attributes;
  private StringBuilder rawBuffer;
  private String remaining;

  // 파싱 상태
  private StringBuilder currentToken;
  private ParserState state;
  private String currentAttrName;
  private char quoteChar;

  private enum ParserState {
    AFTER_TAG_NAME,
    ATTR_NAME,
    AFTER_ATTR_NAME,
    BEFORE_ATTR_VALUE,
    ATTR_VALUE_QUOTED,
    ATTR_VALUE_UNQUOTED
  }

  /**
   * 새 태그 파싱 시작
   *
   * @param startPattern Aho-Corasick이 감지한 시작 패턴 (예: "&lt;cite")
   */
  public void start(String startPattern) {
    tagName = startPattern.substring(1); // '<' 제거
    attributes = new HashMap<>();
    rawBuffer = new StringBuilder(startPattern);
    remaining = null;
    currentToken = new StringBuilder();
    state = ParserState.AFTER_TAG_NAME;
    currentAttrName = null;
    quoteChar = 0;
  }

  /**
   * 토큰을 추가하고 파싱 결과 반환
   *
   * @param token 추가할 토큰
   * @return 완성된 태그 정보 (미완성이면 null)
   */
  public ParsedTag feed(String token) {
    if (tagName == null) {
      throw new IllegalStateException("Parser not started. Call start() first.");
    }

    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      rawBuffer.append(c);

      switch (state) {
        case AFTER_TAG_NAME:
          if (c == '>') {
            return complete(token.substring(i + 1));
          } else if (!Character.isWhitespace(c)) {
            state = ParserState.ATTR_NAME;
            currentToken.append(c);
          }
          break;

        case ATTR_NAME:
          if (c == '=') {
            currentAttrName = currentToken.toString();
            currentToken.setLength(0);
            state = ParserState.BEFORE_ATTR_VALUE;
          } else if (c == '>') {
            if (!currentToken.isEmpty()) {
              attributes.put(currentToken.toString(), "");
            }
            return complete(token.substring(i + 1));
          } else if (Character.isWhitespace(c)) {
            currentAttrName = currentToken.toString();
            currentToken.setLength(0);
            state = ParserState.AFTER_ATTR_NAME;
          } else {
            currentToken.append(c);
          }
          break;

        case AFTER_ATTR_NAME:
          if (c == '=') {
            state = ParserState.BEFORE_ATTR_VALUE;
          } else if (c == '>') {
            if (currentAttrName != null && !currentAttrName.isEmpty()) {
              attributes.put(currentAttrName, "");
            }
            return complete(token.substring(i + 1));
          } else if (!Character.isWhitespace(c)) {
            if (currentAttrName != null && !currentAttrName.isEmpty()) {
              attributes.put(currentAttrName, "");
            }
            state = ParserState.ATTR_NAME;
            currentToken.append(c);
          }
          break;

        case BEFORE_ATTR_VALUE:
          if (c == '"' || c == '\'') {
            quoteChar = c;
            state = ParserState.ATTR_VALUE_QUOTED;
          } else if (c == '>') {
            if (currentAttrName != null) {
              attributes.put(currentAttrName, "");
            }
            return complete(token.substring(i + 1));
          } else if (!Character.isWhitespace(c)) {
            state = ParserState.ATTR_VALUE_UNQUOTED;
            currentToken.append(c);
          }
          break;

        case ATTR_VALUE_QUOTED:
          if (c == quoteChar) {
            if (currentAttrName != null) {
              attributes.put(currentAttrName, currentToken.toString());
            }
            currentToken.setLength(0);
            currentAttrName = null;
            quoteChar = 0;
            state = ParserState.AFTER_TAG_NAME;
          } else {
            currentToken.append(c);
          }
          break;

        case ATTR_VALUE_UNQUOTED:
          if (c == '>') {
            if (currentAttrName != null) {
              attributes.put(currentAttrName, currentToken.toString());
            }
            return complete(token.substring(i + 1));
          } else if (Character.isWhitespace(c)) {
            if (currentAttrName != null) {
              attributes.put(currentAttrName, currentToken.toString());
            }
            currentToken.setLength(0);
            currentAttrName = null;
            state = ParserState.AFTER_TAG_NAME;
          } else {
            currentToken.append(c);
          }
          break;
      }
    }

    return null; // 미완성
  }

  private ParsedTag complete(String rem) {
    String name = tagName;
    String raw = rawBuffer.toString();
    Map<String, String> attrs = attributes.isEmpty()
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(new HashMap<>(attributes));
    this.remaining = rem;
    reset();
    return new ParsedTag(name, attrs, raw);
  }

  /**
   * 파싱 중인지 확인
   */
  public boolean isParsing() {
    return tagName != null;
  }

  /**
   * 태그 완성 후 남은 문자열 반환
   */
  public String getRemaining() {
    return remaining;
  }

  /**
   * 파서 상태 초기화
   */
  public void reset() {
    tagName = null;
    attributes = null;
    rawBuffer = null;
    currentToken = null;
    state = null;
    currentAttrName = null;
    quoteChar = 0;
  }

  /**
   * 불완전한 태그를 강제로 완성 (flush 시 사용)
   * <p>
   * 현재까지 파싱된 태그 이름과 완성된 속성만 반환합니다.
   * 불완전한 속성(닫는 따옴표가 없는 등)은 무시됩니다.
   * </p>
   */
  public ParsedTag forceComplete() {
    if (tagName == null) {
      return null;
    }
    return complete("");
  }

  /**
   * 파싱된 태그 정보
   */
  public record ParsedTag(String tagName, Map<String, String> attributes, String rawTag) {
  }
}
