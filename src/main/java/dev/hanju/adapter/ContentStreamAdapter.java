package dev.hanju.adapter;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.hanju.adapter.matching.AhoCorasickTrie;
import dev.hanju.adapter.matching.TokenMatchingResult;
import dev.hanju.adapter.matching.TokenMatchingBuffer;
import dev.hanju.adapter.transition.TransitionTable;
import dev.hanju.adapter.xml.XmlFragment;
import dev.hanju.adapter.xml.XmlTagBuffer;

/**
 * 스트리밍 토큰을 XML-like 태그로 파싱하고 상태 전이를 수행하는 어댑터.
 *
 * <p>바인딩된 태그 시작 패턴만 먼저 감지한 뒤 XML-like 태그로 파싱하고,
 * 정의된 바인딩에 따라 태그 전이를 시도합니다. 전이 불가능한 태그는 일반 텍스트로 처리됩니다.</p>
 */
public class ContentStreamAdapter {

  private static final String[] TAG_NAME_DELIMITERS = {" ", "\t", "\n", "\r", "\f"};

  private final @Nullable TokenMatchingBuffer patternMatcher;
  private final TransitionTable transitionTable;
  private final Map<String, Set<String>> tagToPaths;
  private final Map<String, Map<String, String>> openTargetsByParent;
  private final Map<String, Set<String>> pathToAttrs;
  private final XmlTagBuffer tagBuffer;

  private String currentPath;
  private final StringBuilder rawAccumulator = new StringBuilder();   // raw 문자열 확인 용도

  private ContentStreamAdapter(Set<String> allPaths,
      Map<String, Set<String>> tagToPaths,
      Map<String, Set<String>> pathToAttrs) {
    this.tagToPaths = Collections.unmodifiableMap(deepCopyMapOfSets(tagToPaths));
    this.pathToAttrs = Collections.unmodifiableMap(deepCopyMapOfSets(pathToAttrs));
    this.transitionTable = new TransitionTable(allPaths);
    this.currentPath = transitionTable.getRoot();
    this.tagBuffer = new XmlTagBuffer(this.tagToPaths.keySet());

    // 부모 경로는 바인딩 시점에 고정된 값이라 매 매칭마다 TransitionTable에 되물을 필요가 없음.
    // Builder가 "같은 태그 이름이 같은 부모 아래 두 형제 경로에 바인딩"되는 걸 이미 막아주므로
    // (부모경로 -> 자식경로) 맵은 충돌 없이 유일하게 정해짐
    final Map<String, Map<String, String>> byParent = new HashMap<>();
    for (final Map.Entry<String, Set<String>> entry : this.tagToPaths.entrySet()) {
      final Map<String, String> parentToChild = new HashMap<>();
      for (final String targetPath : entry.getValue()) {
        parentToChild.put(parentPath(targetPath), targetPath);
      }
      byParent.put(entry.getKey(), Collections.unmodifiableMap(parentToChild));
    }
    this.openTargetsByParent = Collections.unmodifiableMap(byParent);

    final Set<String> patterns = new HashSet<>();
    for (final String tag : this.tagToPaths.keySet()) {
      patterns.add("<" + tag + ">");
      patterns.add("<" + tag + "/");
      patterns.add("</" + tag + ">");
      for (final String delimiter : TAG_NAME_DELIMITERS) {
        patterns.add("<" + tag + delimiter);
        patterns.add("</" + tag + delimiter);
      }
    }
    this.patternMatcher = patterns.isEmpty()
        ? null
        : new TokenMatchingBuffer(new AhoCorasickTrie(patterns));
  }

  /**
   * 경로 집합으로부터 어댑터 빌더를 시작합니다.
   *
   * @param allPaths TransitionSchema.toPaths()로 얻은 경로 집합
   * @return 빌더 인스턴스
   */
  public static Builder from(Set<String> allPaths) {
    if (allPaths == null) {
      throw new IllegalArgumentException("allPaths cannot be null");
    }
    return new Builder(allPaths);
  }

  /**
   * 현재 FSM 경로를 반환합니다.
   *
   * @return 현재 경로 (예: "/", "/cite")
   */
  public String getCurrentPath() {
    return currentPath;
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
   * 토큰을 처리하여 ContentStreamResult 리스트를 반환합니다.
   *
   * <p>빈 문자열은 무시하고 빈 리스트를 반환합니다 (일부 LLM 제공자가 스트림 도중
   * 빈 델타를 보내는 경우가 있어 이를 허용합니다). null은 호출자 오류로 간주해 예외를 던집니다.</p>
   *
   * @param token 처리할 토큰
   * @return 처리된 ContentStreamResult 리스트
   * @throws IllegalArgumentException token이 null인 경우
   */
  public List<ContentStreamResult> feedToken(String token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    if (token.isEmpty()) {
      return Collections.emptyList();
    }

    rawAccumulator.append(token);

    final List<ContentStreamResult> outputs = new ArrayList<>();
    if (patternMatcher == null) {
      outputs.add(new ContentStreamResult.Text(token));
      return outputs;
    }

    for (final TokenMatchingResult result : patternMatcher.accept(token)) {
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
   * @return flush된 ContentStreamResult 리스트
   */
  public List<ContentStreamResult> flush() {
    final List<ContentStreamResult> outputs = new ArrayList<>();

    if (patternMatcher != null) {
      for (final TokenMatchingResult result : patternMatcher.flush()) {
        processMatchResult(result, outputs);
      }
    }

    for (final XmlFragment fragment : tagBuffer.flush()) {
      appendFragment(fragment, outputs);
    }

    // 스트림 종료 — 재사용을 위해 초기 상태로 리셋
    currentPath = transitionTable.getRoot();
    rawAccumulator.setLength(0);
    return outputs;
  }

  /**
   * TokenMatchingBuffer의 출력 중 관심 태그 후보만 XmlTagBuffer로 전달합니다.
   */
  private void processMatchResult(TokenMatchingResult result, List<ContentStreamResult> outputs) {
    final String raw = String.join("", result.tokens());
    if (tagBuffer.isParsing()) {
      for (final XmlFragment fragment : tagBuffer.feed(raw)) {
        appendFragment(fragment, outputs);
      }
      return;
    }

    switch (result.type()) {
      case TEXT -> {
        for (final String token : result.tokens()) {
          if (!token.isEmpty()) {
            outputs.add(new ContentStreamResult.Text(token));
          }
        }
      }
      case PATTERN -> {
        // 태그 이름만으로 전이 가능 여부를 미리 확인(peek)한다. 전이가 불가능하면 태그로
        // 파싱할 이유가 없으므로 XmlTagBuffer를 거치지 않고 매치 문자열을 그대로 텍스트로 낸다.
        final boolean close = raw.startsWith("</");
        // 패턴은 항상 "<"/"</" + 이름 + 구분자(>, /, 공백) 한 글자로 생성되므로
        // (ContentStreamAdapter 생성자의 patterns.add 참고), 이름은 그 사이 전부
        final int nameStart = close ? 2 : 1;
        final String tagName = raw.substring(nameStart, raw.length() - 1);

        final boolean transitionable = close
            ? resolveCloseTarget(tagName) != null
            : resolveOpenTarget(tagName) != null;
        if (transitionable) {
          for (final XmlFragment fragment : tagBuffer.feed(raw)) {
            appendFragment(fragment, outputs);
          }
        } else {
          outputs.add(new ContentStreamResult.Text(raw));
        }
      }
    }
  }

  /**
   * 여는 태그가 현재 경로에서 전이 가능하면 도달 경로를, 아니면 null을 반환합니다.
   *
   * <p>peek(라우팅 판정, {@link #processMatchResult})와 commit(실제 전이, {@link #appendFragment})
   * 양쪽에서 호출됩니다. 대상 경로의 부모는 바인딩 시점에 고정되는 값이라({@code openTargetsByParent}로
   * 미리 인덱싱) {@link TransitionTable}을 다시 조회할 필요가 없으며, 호출 자체가 상태를 바꾸지
   * 않으므로 같은 판정을 두 시점에서 안전하게 재사용합니다. Builder가 같은 태그 이름을 같은 부모의
   * 두 형제 경로에 바인딩하는 것을 막아주므로 후보는 항상 최대 1개입니다.</p>
   *
   * @param tagName 태그 이름
   * @return 도달 경로 (전이 불가 시 null)
   */
  private @Nullable String resolveOpenTarget(String tagName) {
    final Map<String, String> byParent = openTargetsByParent.get(tagName);
    return byParent == null ? null : byParent.get(currentPath);
  }

  /**
   * 닫는 태그가 현재 경로에서 유효하면 부모 경로를, 아니면 null을 반환합니다.
   *
   * <p>alias로 열렸더라도({@code <rag>}) 같은 경로를 가리키는 다른 이름({@code </cite>})으로 닫을
   * 수 있어야 하므로, 이름 일치가 아니라 현재 경로가 그 태그의 바인딩 경로인지로 검증합니다.
   * peek와 commit 양쪽에서 호출되는 것은 {@link #resolveOpenTarget}과 동일합니다.</p>
   *
   * @param tagName 태그 이름
   * @return 부모 경로 (닫기 불가 시 null)
   */
  private @Nullable String resolveCloseTarget(String tagName) {
    final Set<String> targetPaths = tagToPaths.get(tagName);
    if (targetPaths == null || !targetPaths.contains(currentPath)) {
      return null;
    }
    return transitionTable.tryClose(currentPath);
  }

  /**
   * XmlTagBuffer의 조각을 콘텐츠 스트림 출력으로 변환합니다.
   */
  private void appendFragment(XmlFragment fragment, List<ContentStreamResult> outputs) {
    switch (fragment) {
      case XmlFragment.Text text -> {
        if (!text.raw().isEmpty()) {
          outputs.add(new ContentStreamResult.Text(text.raw()));
        }
      }
      case XmlFragment.Open open -> enterPath(open.name(), open.attributes(), false, outputs);
      case XmlFragment.SelfClosing selfClosing ->
          enterPath(selfClosing.name(), selfClosing.attributes(), true, outputs);
      case XmlFragment.Close close -> {
        // peek(processMatchResult)에서 유효성이 이미 보장된 조각만 도달하므로 non-null
        final String pathBeforeClose = currentPath;
        currentPath = Objects.requireNonNull(resolveCloseTarget(close.name()));
        outputs.add(new ContentStreamResult.Exit(pathBeforeClose));
      }
    }
  }

  /**
   * OPEN/SELF_CLOSING 공통 처리: 경로 전이, 속성 필터링, Enter 출력, 자체 닫힘이면 곧이은 Exit까지.
   *
   * <p>peek(processMatchResult)에서 전이 가능함이 이미 보장된 이름만 도달하므로
   * {@code resolveOpenTarget}/{@code tryClose}는 non-null입니다.</p>
   */
  private void enterPath(String tagName, Map<String, String> attributes, boolean selfClosing,
      List<ContentStreamResult> outputs) {
    currentPath = Objects.requireNonNull(resolveOpenTarget(tagName));

    Map<String, String> filtered = Collections.emptyMap();
    final Set<String> allowedAttributes = pathToAttrs.get(currentPath);
    if (!attributes.isEmpty() && allowedAttributes != null && !allowedAttributes.isEmpty()) {
      final Map<String, String> filteredAttributes = new HashMap<>();
      for (final Map.Entry<String, String> entry : attributes.entrySet()) {
        if (allowedAttributes.contains(entry.getKey())) {
          filteredAttributes.put(entry.getKey(), entry.getValue());
        }
      }
      if (!filteredAttributes.isEmpty()) {
        filtered = filteredAttributes;
      }
    }
    outputs.add(new ContentStreamResult.Enter(currentPath, filtered));

    if (selfClosing) {
      final String pathBeforeClose = currentPath;
      currentPath = Objects.requireNonNull(transitionTable.tryClose(currentPath));
      outputs.add(new ContentStreamResult.Exit(pathBeforeClose));
    }
  }

  /**
   * 경로 문자열의 부모 경로를 계산합니다 ("/section/title" → "/section", "/cite" → "/").
   *
   * @param path 자식 경로
   * @return 부모 경로
   */
  private static String parentPath(String path) {
    final int lastSlash = path.lastIndexOf('/');
    return lastSlash <= 0 ? "/" : path.substring(0, lastSlash);
  }

  /**
   * Set을 값으로 갖는 Map을 깊은 복사합니다.
   *
   * @param original 원본 맵
   * @return 깊은 복사된 맵
   */
  private static Map<String, Set<String>> deepCopyMapOfSets(Map<String, Set<String>> original) {
    final Map<String, Set<String>> copy = new HashMap<>();
    for (final Map.Entry<String, Set<String>> entry : original.entrySet()) {
      copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
    }
    return copy;
  }

  /**
   * ContentStreamAdapter 빌더.
   */
  public static class Builder {
    private final Set<String> allPaths;
    private final Map<String, Set<String>> tagToPaths = new HashMap<>();
    private final Map<String, Set<String>> pathToAttrs = new HashMap<>();

    private Builder(Set<String> allPaths) {
      this.allPaths = allPaths;
    }

    /**
     * 경로에 태그 규칙을 바인딩합니다.
     *
     * @param path 바인딩할 경로
     * @return PathBinder 인스턴스
     */
    public PathBinder bind(String path) {
      if (path == null || path.isEmpty()) {
        throw new IllegalArgumentException("Path cannot be null or empty");
      }
      if (!allPaths.contains(path)) {
        throw new IllegalArgumentException("Path not defined in schema: " + path);
      }
      return new PathBinder(this, path);
    }

    /**
     * 바인딩을 완료하고 ContentStreamAdapter를 생성합니다.
     *
     * @return ContentStreamAdapter 인스턴스
     */
    public ContentStreamAdapter build() {
      return new ContentStreamAdapter(allPaths, tagToPaths, pathToAttrs);
    }

  }

  /**
   * 경로별 태그 규칙 빌더.
   */
  public static class PathBinder {
    private final Builder builder;
    private final String path;
    private boolean hasTag = false;

    private PathBinder(Builder builder, String path) {
      this.builder = builder;
      this.path = path;
    }

    /**
     * 경로에 태그 이름을 바인딩합니다.
     *
     * @param tagName 태그 이름
     * @return 이 PathBinder (체이닝용)
     */
    public PathBinder tag(String tagName) {
      if (tagName == null || tagName.isEmpty()) {
        throw new IllegalArgumentException("Tag name cannot be null or empty");
      }
      if (hasTag) {
        throw new IllegalStateException("Tag already set for path: " + path);
      }
      bindTagName(tagName);
      hasTag = true;
      return this;
    }

    /**
     * 경로에 별칭을 추가합니다.
     *
     * @param aliases 별칭 이름들
     * @return 이 PathBinder (체이닝용)
     */
    public PathBinder alias(String... aliases) {
      if (!hasTag) {
        throw new IllegalStateException("Call tag() before alias()");
      }
      if (aliases == null || aliases.length == 0) {
        throw new IllegalArgumentException("At least one alias must be provided");
      }
      for (final String alias : aliases) {
        if (alias == null || alias.isEmpty()) {
          throw new IllegalArgumentException("Alias name cannot be null or empty");
        }
        bindTagName(alias);
      }
      return this;
    }

    /**
     * 태그 이름(또는 별칭)을 이 경로에 등록합니다.
     *
     * <p>같은 태그 이름이 같은 부모 아래 두 형제 경로에 바인딩되면, 어떤 현재 경로에서 열려도
     * 어느 경로로 전이해야 할지 구분할 수 없으므로 예외로 거부합니다. 이 보장 덕분에
     * {@link #resolveOpenTarget}은 (부모경로 → 자식경로) 맵 하나로 후보를 유일하게 찾을 수 있습니다.</p>
     *
     * @param tagName 등록할 태그 이름(또는 별칭)
     */
    private void bindTagName(String tagName) {
      final String parent = parentPath(path);
      final Set<String> existing = builder.tagToPaths.get(tagName);
      if (existing != null) {
        for (final String existingPath : existing) {
          if (parentPath(existingPath).equals(parent)) {
            throw new IllegalStateException(
                "Tag name '" + tagName + "' is already bound to a sibling path under " + parent
                    + ": " + existingPath);
          }
        }
      }
      builder.tagToPaths.computeIfAbsent(tagName, k -> new HashSet<>()).add(path);
    }

    /**
     * 경로에 허용할 속성을 추가합니다.
     *
     * @param attributes 속성 이름들
     * @return 이 PathBinder (체이닝용)
     */
    public PathBinder attr(String... attributes) {
      if (!hasTag) {
        throw new IllegalStateException("Call tag() before attr()");
      }
      if (attributes == null || attributes.length == 0) {
        throw new IllegalArgumentException("At least one attribute must be provided");
      }
      for (final String attrName : attributes) {
        if (attrName == null || attrName.isEmpty()) {
          throw new IllegalArgumentException("Attribute name cannot be null or empty");
        }
        builder.pathToAttrs.computeIfAbsent(path, k -> new HashSet<>()).add(attrName);
      }
      return this;
    }

    /**
     * 현재 바인딩을 완료하고 빌더로 돌아갑니다.
     *
     * @return Builder 인스턴스
     */
    public Builder and() {
      if (!hasTag) {
        throw new IllegalStateException("Call tag() before and()");
      }
      return builder;
    }

    /**
     * 현재 바인딩을 완료하고 ContentStreamAdapter를 생성합니다.
     * {@code and().build()}의 편의 메서드입니다.
     *
     * @return ContentStreamAdapter 인스턴스
     */
    public ContentStreamAdapter build() {
      return and().build();
    }
  }
}
