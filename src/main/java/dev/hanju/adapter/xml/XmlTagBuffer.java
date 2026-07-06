package dev.hanju.adapter.xml;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스트리밍 문자열에서 XML-like 태그를 토큰화하는 상태ful 버퍼.
 *
 * <p>일반 텍스트와 완성된 태그 문자열을 모두 {@link XmlFragment}로 반환합니다.
 * 태그의 짝, 중첩 관계, 스키마 유효성은 검증하지 않습니다.</p>
 */
public class XmlTagBuffer {

  private final StringBuilder textBuffer = new StringBuilder();
  private final StringBuilder tagBuffer = new StringBuilder();
  private final @Nullable Set<String> targetTags;
  private boolean parsingTag;
  private char quoteChar;

  /**
   * 모든 XML-like 태그를 토큰화하는 버퍼를 생성합니다.
   */
  public XmlTagBuffer() {
    this.targetTags = null;
  }

  /**
   * 지정된 태그 이름만 태그 출력으로 반환하는 버퍼를 생성합니다.
   *
   * @param targetTags 태그 출력으로 인정할 이름 집합
   */
  public XmlTagBuffer(Set<String> targetTags) {
    if (targetTags == null) {
      throw new IllegalArgumentException("targetTags must not be null");
    }
    this.targetTags = Collections.unmodifiableSet(new LinkedHashSet<>(targetTags));
  }

  /**
   * 문자열 조각을 추가하고 이번 호출에서 확정된 출력을 반환합니다.
   *
   * @param token 추가할 문자열 조각
   * @return 텍스트와 태그 출력 목록
   */
  public List<XmlFragment> feed(String token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    if (token.isEmpty()) {
      return Collections.emptyList();
    }

    final List<XmlFragment> outputs = new ArrayList<>();
    for (int i = 0; i < token.length(); i++) {
      final char c = token.charAt(i);
      // 상태 1: 태그 밖. '<'를 만나면 태그 후보 파싱으로 전환하고, 그 외 문자는 그대로 텍스트로 누적.
      if (!parsingTag) {
        if (c == '<') {
          parsingTag = true;
          quoteChar = 0;
          tagBuffer.append(c);
        } else {
          textBuffer.append(c);
        }
        continue;
      }

      // 따옴표 밖에서 또 다른 '<'가 나오면 지금까지의 태그 후보는 무효한 것으로 보고
      // 텍스트로 되돌린 뒤(rollback), 새로 만난 '<'부터 다시 태그 후보 파싱을 시작한다.
      if (quoteChar == 0 && c == '<') {
        textBuffer.append(tagBuffer);
        flushText(outputs);
        tagBuffer.setLength(0);
        tagBuffer.append(c);
        continue;
      }

      // 상태 2: 태그 후보 파싱 중. 따옴표 안에서는 '>'가 나와도 태그를 닫지 않도록
      // quoteChar로 따옴표 열림/닫힘만 추적하고, 따옴표 밖의 '>'에서만 태그 후보를 확정(commit)한다.
      tagBuffer.append(c);
      if (quoteChar != 0) {
        if (c == quoteChar) {
          quoteChar = 0;
        }
      } else if (c == '"' || c == '\'') {
        quoteChar = c;
      } else if (c == '>') {
        final String rawTag = tagBuffer.toString();
        XmlFragment fragment = null;

        // 여기서부터는 "완성된 태그 후보" 하나를 해석한다. targetTags에 없거나
        // XML-like 태그로 보기 어려우면 fragment를 만들지 않고 raw text로 되돌린다.
        if (rawTag.length() >= 3
            && rawTag.charAt(0) == '<'
            && rawTag.charAt(rawTag.length() - 1) == '>') {
          if (rawTag.charAt(1) == '/') {
            final String closeName = rawTag.substring(2, rawTag.length() - 1).trim();
            if (isTagName(closeName) && (targetTags == null || targetTags.contains(closeName))) {
              fragment = XmlFragment.close(rawTag, closeName);
            }
          } else {
            // 여는/자체 닫힘 태그 후보: 끝의 '/'로 자체 닫힘 여부를 먼저 판별하고 제거한다.
            final String body = rawTag.substring(1, rawTag.length() - 1);
            String trimmedBody = body.trim();
            final boolean selfClosing = trimmedBody.endsWith("/");
            if (selfClosing) {
              trimmedBody = trimmedBody.substring(0, trimmedBody.length() - 1).trim();
            }

            // "<foo ...>"에서 태그 이름은 첫 공백 전까지. "< foo>"처럼 이름 앞에 공백이 있으면
            // 유효한 태그로 보지 않는다(body.charAt(0) 검사로 원본 기준 앞공백 여부 확인).
            if (!trimmedBody.isEmpty() && !Character.isWhitespace(body.charAt(0))) {
              int nameEnd = 0;
              while (nameEnd < trimmedBody.length()
                  && !Character.isWhitespace(trimmedBody.charAt(nameEnd))) {
                nameEnd++;
              }

              final String tagName = trimmedBody.substring(0, nameEnd);
              if (isTagName(tagName) && (targetTags == null || targetTags.contains(tagName))) {
                final Map<String, String> attributes = new HashMap<>();
                final String attributeText = trimmedBody.substring(nameEnd);
                boolean validAttributes = true;
                int index = 0;

                // 속성은 quoted value만 의미 있게 캡처한다. unquoted value는 XML 비적합
                // 입력으로 보고 기존 정책처럼 값 없는 속성으로만 보존한다.
                // 아래 while 하나가 "속성 이름=값" 한 쌍을 매 반복마다 소비하는 서브파서다.
                while (index < attributeText.length()) {
                  // 1) 다음 속성 이름 앞의 공백을 건너뛴다.
                  while (index < attributeText.length()
                      && Character.isWhitespace(attributeText.charAt(index))) {
                    index++;
                  }
                  if (index >= attributeText.length()) {
                    break;
                  }

                  // 2) 공백 또는 '='을 만날 때까지 읽어 속성 이름을 확정한다.
                  final int attrNameStart = index;
                  while (index < attributeText.length()
                      && !Character.isWhitespace(attributeText.charAt(index))
                      && attributeText.charAt(index) != '=') {
                    index++;
                  }
                  final String attrName = attributeText.substring(attrNameStart, index);
                  if (!isTagName(attrName)) {
                    validAttributes = false;
                    break;
                  }

                  // 3) 이름 뒤 공백을 건너뛴 다음 '='이 없으면 값 없는 속성으로 확정하고 다음 속성으로.
                  while (index < attributeText.length()
                      && Character.isWhitespace(attributeText.charAt(index))) {
                    index++;
                  }
                  if (index >= attributeText.length() || attributeText.charAt(index) != '=') {
                    attributes.put(attrName, "");
                    continue;
                  }

                  // 4) '=' 뒤 공백을 건너뛴다. 값 자체가 없으면 역시 값 없는 속성으로 확정.
                  index++;
                  while (index < attributeText.length()
                      && Character.isWhitespace(attributeText.charAt(index))) {
                    index++;
                  }
                  if (index >= attributeText.length()) {
                    attributes.put(attrName, "");
                    break;
                  }

                  // 5) 값이 따옴표로 시작하지 않으면(unquoted) 값 없는 속성으로 취급하고,
                  // 다음 공백까지를 통째로 건너뛴 뒤 다음 속성 파싱으로 넘어간다.
                  final char quote = attributeText.charAt(index);
                  if (quote != '"' && quote != '\'') {
                    attributes.put(attrName, "");
                    while (index < attributeText.length()
                        && !Character.isWhitespace(attributeText.charAt(index))) {
                      index++;
                    }
                    continue;
                  }

                  // 6) 같은 종류의 닫는 따옴표까지 값으로 읽는다. 닫는 따옴표 없이 끝나면 태그 전체를 무효 처리.
                  final int valueStart = ++index;
                  while (index < attributeText.length() && attributeText.charAt(index) != quote) {
                    index++;
                  }
                  if (index >= attributeText.length()) {
                    validAttributes = false;
                    break;
                  }
                  attributes.put(attrName, attributeText.substring(valueStart, index));
                  index++;
                }

                if (validAttributes) {
                  final Map<String, String> finalAttributes = attributes.isEmpty()
                      ? Collections.emptyMap()
                      : Collections.unmodifiableMap(attributes);
                  fragment = selfClosing
                      ? XmlFragment.selfClosing(rawTag, tagName, finalAttributes)
                      : XmlFragment.open(rawTag, tagName, finalAttributes);
                }
              }
            }
          }
        }

        if (fragment == null) {
          textBuffer.append(rawTag);
        } else {
          flushText(outputs);
          outputs.add(fragment);
        }
        tagBuffer.setLength(0);
        parsingTag = false;
        quoteChar = 0;
      }
    }

    flushText(outputs);
    return outputs;
  }

  /**
   * 남은 버퍼를 텍스트로 확정하고 초기 상태로 되돌립니다.
   *
   * @return flush된 출력 목록
   */
  public List<XmlFragment> flush() {
    final List<XmlFragment> outputs = new ArrayList<>();
    if (parsingTag) {
      textBuffer.append(tagBuffer);
      tagBuffer.setLength(0);
      parsingTag = false;
      quoteChar = 0;
    }
    flushText(outputs);
    return outputs;
  }

  /**
   * 버퍼 상태를 초기화합니다.
   */
  public void reset() {
    textBuffer.setLength(0);
    tagBuffer.setLength(0);
    parsingTag = false;
    quoteChar = 0;
  }

  /**
   * 현재 태그 후보를 버퍼링 중인지 반환합니다.
   *
   * @return 태그 후보를 버퍼링 중이면 true
   */
  public boolean isParsing() {
    return parsingTag;
  }

  /**
   * 누적된 텍스트 버퍼를 {@link XmlFragment.Text}로 확정합니다.
   *
   * @param outputs 확정된 조각을 추가할 출력 목록
   */
  private void flushText(List<XmlFragment> outputs) {
    if (!textBuffer.isEmpty()) {
      outputs.add(XmlFragment.text(textBuffer.toString()));
      textBuffer.setLength(0);
    }
  }

  /**
   * 문자열이 XML 태그/속성 이름으로 유효한지 확인합니다.
   *
   * @param name 검사할 이름
   * @return 유효한 태그/속성 이름이면 true
   */
  private boolean isTagName(@Nullable String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    for (int i = 0; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != ':' && c != '.') {
        return false;
      }
    }
    return true;
  }
}
