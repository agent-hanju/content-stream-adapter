package dev.hanju.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.hanju.adapter.buffer.TokenMatchingBuffer;
import dev.hanju.adapter.matching.AhoCorasickTrie;
import dev.hanju.adapter.matching.TokenMatchResult;
import dev.hanju.adapter.transition.TransitionNode;
import dev.hanju.adapter.transition.TransitionTable;
import dev.hanju.adapter.xml.OpenTagParser;
import dev.hanju.adapter.xml.TagInfo;
import dev.hanju.adapter.xml.XmlStreamOutput;
import dev.hanju.adapter.xml.XmlTagBinding;
import dev.hanju.adapter.xml.OpenTagParser.ParsedTag;
import dev.hanju.adapter.xml.TagInfo.TagType;

/**
 * 스트리밍 토큰을 XML-like 태그로 파싱하고 상태 전이를 수행하는 어댑터.
 *
 * <p>Aho-Corasick 알고리즘으로 다중 패턴 매칭(O(n))을 수행하고,
 * 정의된 바인딩에 따라 태그 전이를 시도합니다.
 * 전이 불가능한 태그는 일반 텍스트로 처리됩니다.</p>
 */
public class ContentStreamAdapter {

  private final TokenMatchingBuffer patternMatcher;
  private final TransitionTable transitionTable;
  private final XmlTagBinding xmlBinding;
  private final OpenTagParser openTagParser;

  private TransitionNode currentState;
  private final StringBuilder rawAccumulator = new StringBuilder();   // raw 문자열 확인 용도

  /**
   * XmlTagBinding을 기반으로 어댑터를 생성합니다.
   *
   * @param binding XML 태그 바인딩
   * @throws IllegalArgumentException binding이 null인 경우
   */
  public ContentStreamAdapter(XmlTagBinding binding) {
    if (binding == null) {
      throw new IllegalArgumentException("Binding cannot be null");
    }

    this.xmlBinding = binding;
    this.transitionTable = new TransitionTable(binding.getSchema());
    this.currentState = transitionTable.getRoot();
    this.openTagParser = new OpenTagParser();

    this.patternMatcher = new TokenMatchingBuffer(
        new AhoCorasickTrie(binding.generatePatterns()));
  }

  /**
   * 현재 FSM 경로를 반환합니다.
   *
   * @return 현재 경로 (예: "/", "/cite")
   */
  public String getCurrentPath() {
    return currentState.getPath();
  }

  /**
   * 지금까지 입력된 모든 토큰의 원본 문자열을 반환합니다.
   *
   * <p>{@link #flush()} 호출 시 누적기가 초기화되므로 flush 이후에는 빈 문자열을 반환합니다.</p>
   *
   * @return 누적된 원본 문자열 (flush 이후에는 빈 문자열)
   */
  public String getRaw() {
    return rawAccumulator.toString();
  }

  /**
   * 토큰을 처리하여 XmlStreamOutput 리스트를 반환합니다.
   *
   * <p>빈 문자열은 무시하고 빈 리스트를 반환합니다 (일부 LLM 제공자가 스트림 도중
   * 빈 델타를 보내는 경우가 있어 이를 허용합니다). null은 호출자 오류로 간주해 예외를 던집니다.</p>
   *
   * @param token 처리할 토큰
   * @return 처리된 XmlStreamOutput 리스트
   * @throws IllegalArgumentException token이 null인 경우
   */
  public List<XmlStreamOutput> feedToken(String token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    if (token.isEmpty()) {
      return Collections.emptyList();
    }

    rawAccumulator.append(token);

    List<XmlStreamOutput> outputs = new ArrayList<>();

    // 이전 호출에서 태그가 미완성으로 남았다면, 이 토큰을 AC로 보내기 전에
    // 먼저 OpenTagParser로 보낸다 (속성값 문자를 패턴으로 오인하는 것을 방지)
    if (openTagParser.isParsing()) {
      token = processOpenTagParsing(token, outputs);
      if (token.isEmpty()) {
        return outputs;
      }
    }

    for (TokenMatchResult result : patternMatcher.accept(token)) {
      processMatchResult(result, outputs);
    }

    return outputs;
  }

  /**
   * 남은 버퍼 텍스트를 모두 flush하고 어댑터를 초기 상태로 되돌립니다.
   *
   * <p>스트림 종료 처리입니다. 호출 후 어댑터는 새 스트림을 위해 재사용할 수 있으며,
   * FSM 상태와 원문 누적기가 모두 초기화됩니다. 따라서 완전한 원문이 필요하면
   * {@link #getRaw()}를 flush 이전에 호출해야 합니다 (flush 이후에는 빈 문자열).</p>
   *
   * @return flush된 XmlStreamOutput 리스트
   */
  public List<XmlStreamOutput> flush() {
    List<XmlStreamOutput> outputs = new ArrayList<>();

    if (openTagParser.isParsing()) {
      ParsedTag parsedTag = openTagParser.forceComplete();
      if (parsedTag != null) {
        processCompleteOpenTag(parsedTag, outputs);
      }
    }

    for (TokenMatchResult result : patternMatcher.flush()) {
      processMatchResult(result, outputs);
    }

    // 스트림 종료 — 재사용을 위해 초기 상태로 리셋
    // (patternMatcher는 자체 flush()에서, openTagParser는 forceComplete()에서 이미 리셋됨)
    currentState = transitionTable.getRoot();
    rawAccumulator.setLength(0);
    return outputs;
  }

  /**
   * OpenTagParser로 열린 태그 파싱을 이어갑니다.
   *
   * <p>{@code feedToken}에서 이전 호출로부터 파싱이 이어지는 경우, 그리고
   * {@code processMatchResult}에서 같은 배치 안에 태그 시작 직후 도착한 TEXT를
   * 처리하는 경우 양쪽에서 호출됩니다. 태그가 완성되면 {@link #processCompleteOpenTag}로
   * 결과를 위임합니다.</p>
   *
   * @param token 파서에 먹일 토큰
   * @param outputs 완성된 태그 결과를 누적할 출력 리스트
   * @return 태그 종료(&gt;) 이후 남은 토큰 (미완성이면 빈 문자열)
   */
  private String processOpenTagParsing(String token, List<XmlStreamOutput> outputs) {
    ParsedTag parsedTag = openTagParser.feed(token);

    if (parsedTag != null) {
      processCompleteOpenTag(parsedTag, outputs);
      return openTagParser.getRemaining();
    }

    return "";
  }

  /**
   * 완성된 열린 태그를 처리합니다.
   *
   * <p>{@code processOpenTagParsing}(스트림 도중 정상 완성)과 {@code flush}
   * (스트림 종료 시 강제 완성) 양쪽에서 공유하는 로직입니다. 전이 성공 시 바인딩에
   * 정의된 attribute만 필터링해 Enter로, 실패 시 원본 텍스트로 출력합니다.</p>
   *
   * @param parsedTag 파싱이 완료된 태그 정보
   * @param outputs 결과를 누적할 출력 리스트
   */
  private void processCompleteOpenTag(ParsedTag parsedTag, List<XmlStreamOutput> outputs) {
    TagInfo tag = TagInfo.open(parsedTag.tagName(), parsedTag.attributes());
    if (!tryTransition(tag)) {
      outputs.add(new XmlStreamOutput.Text(parsedTag.rawTag()));
      return;
    }

    Map<String, String> attributes = tag.attributes();
    // 화이트리스트에 없는 태그(allowed 없음)나 필터 후 남는 게 없으면 emptyMap 유지 —
    // 매 Enter마다 빈 HashMap을 새로 할당하지 않기 위함
    Map<String, String> filtered = Collections.emptyMap();
    if (!attributes.isEmpty()) {
      Set<String> allowed = xmlBinding.getAllowedAttributes(currentState.getPath());
      if (!allowed.isEmpty()) {
        filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
          if (allowed.contains(entry.getKey())) {
            filtered.put(entry.getKey(), entry.getValue());
          }
        }
        if (filtered.isEmpty()) {
          filtered = Collections.emptyMap();
        }
      }
    }
    outputs.add(new XmlStreamOutput.Enter(currentState.getPath(), filtered));
  }

  /**
   * Aho-Corasick 매칭 결과 하나를 출력으로 변환합니다.
   *
   * <p>TEXT는 파싱 중이면 {@link #processOpenTagParsing}으로 우회시키고,
   * 아니면 토큰 경계를 보존한 채 그대로 출력합니다. PATTERN은 닫는 태그({@code >}로
   * 끝남)면 {@link #processCloseTag}로, 여는 태그 시작이면 OpenTagParser를 시작시킵니다.</p>
   *
   * @param result 처리할 매칭 결과
   * @param outputs 결과를 누적할 출력 리스트
   */
  private void processMatchResult(TokenMatchResult result, List<XmlStreamOutput> outputs) {
    switch (result.type()) {
      case TEXT -> {
        // 같은 accept() 배치 안에서 방금 시작된 태그 파싱이 있으면(직전 결과가 PATTERN
        // 이었던 경우), 이 TEXT는 사실 속성 구간이므로 그대로 출력하면 안 됨
        if (openTagParser.isParsing()) {
          String combined = String.join("", result.tokens());
          String remaining = processOpenTagParsing(combined, outputs);
          if (!remaining.isEmpty()) {
            outputs.add(new XmlStreamOutput.Text(remaining));
          }
        } else {
          for (String t : result.tokens()) {
            if (!t.isEmpty()) {
              outputs.add(new XmlStreamOutput.Text(t));
            }
          }
        }
      }
      case PATTERN -> {
        String pattern = String.join("", result.tokens());
        if (pattern.endsWith(">")) {
          processCloseTag(pattern, outputs);
        } else {
          openTagParser.start(pattern);
        }
      }
    }
  }

  /**
   * 닫는 태그를 처리합니다.
   *
   * <p>전이 성공 시 전이 이전 경로를 Exit로 출력하고(전이 이후 경로가 아님에 주의),
   * 실패 시 원본 태그 문자열을 텍스트로 출력합니다.</p>
   *
   * @param tagString 닫는 태그 원본 문자열 (예: {@code "</cite>"})
   * @param outputs 결과를 누적할 출력 리스트
   */
  private void processCloseTag(String tagString, List<XmlStreamOutput> outputs) {
    String tagName = tagString.substring(2, tagString.length() - 1);
    TagInfo tag = TagInfo.close(tagName);
    // tryTransition이 성공하면 currentState가 부모로 바뀌므로, Exit에 실릴
    // "닫힌 경로"는 전이 전에 미리 저장해둬야 함
    String pathBeforeTransition = currentState.getPath();
    boolean transitioned = tryTransition(tag);

    if (transitioned) {
      outputs.add(new XmlStreamOutput.Exit(pathBeforeTransition));
    } else {
      outputs.add(new XmlStreamOutput.Text(tagString));
    }
  }

  /**
   * 태그 정보를 바탕으로 FSM 전이를 시도합니다.
   *
   * <p>태그 이름을 바인딩을 통해 경로로 번역한 뒤, OPEN이면 해당 경로로의 전이를,
   * CLOSE면 alias 호환 여부를 확인한 뒤 부모로의 전이를 시도합니다. 전이가 성공하면
   * {@code currentState}를 갱신합니다.</p>
   *
   * @param tag 전이를 시도할 태그 정보
   * @return 전이에 성공하면 true
   */
  private boolean tryTransition(TagInfo tag) {
    String tagName = tag.name();
    String targetPath = xmlBinding.getPathForTag(tagName);

    if (targetPath == null) {
      return false;
    }

    if (tag.type() == TagType.OPEN) {
      String segment = targetPath.substring(targetPath.lastIndexOf('/') + 1);
      TransitionNode nextState = transitionTable.tryOpen(currentState, segment);
      // targetPath와 실제 도달 경로가 다르면, 현재 상태에서는 스키마상 유효하지 않은
      // 위치의 태그라는 뜻 (예: 부모 경로가 다른 곳에서 같은 이름의 자식을 참조)
      if (nextState != null && nextState.getPath().equals(targetPath)) {
        currentState = nextState;
        return true;
      }
      return false;
    } else {
      // alias로 열렸더라도(<rag>) 같은 경로를 가리키는 다른 이름(</cite>)으로
      // 닫을 수 있어야 하므로, 이름 일치가 아니라 경로 일치로 검증
      if (xmlBinding.isTagForPath(tagName, currentState.getPath())) {
        TransitionNode nextState = transitionTable.tryClose(currentState);
        if (nextState != null) {
          currentState = nextState;
          return true;
        }
      }
      return false;
    }
  }
}
