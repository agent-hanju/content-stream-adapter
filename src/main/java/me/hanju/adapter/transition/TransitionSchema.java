package me.hanju.adapter.transition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 계층적 태그 스키마 빌더.
 *
 * <p>Fluent API로 태그 구조를 정의하고 별칭을 지원합니다.</p>
 *
 * <p><b>사용 예시:</b></p>
 * <pre>{@code
 * TransitionSchema schema = TransitionSchema.root()
 *     .tag("section", section -> section
 *         .tag("subsection", subsection -> subsection
 *             .tag("content"))
 *         .tag("metadata"))
 *     .tag("cite").alias("rag");
 *
 * ContentStreamAdapter adapter = new ContentStreamAdapter(schema);
 * }</pre>
 */
public class TransitionSchema {
  private final String currentPath;
  private final Map<String, List<String>> pathToTags;
  private final Map<String, Set<String>> pathToAttributes;
  private String lastAddedPath;

  private TransitionSchema(String currentPath, Map<String, List<String>> pathToTags,
      Map<String, Set<String>> pathToAttributes) {
    this.currentPath = currentPath;
    this.pathToTags = pathToTags;
    this.pathToAttributes = pathToAttributes;
  }

  /**
   * 루트 스키마를 생성합니다.
   *
   * @return 새로운 루트 스키마
   */
  public static TransitionSchema root() {
    return new TransitionSchema("/", new HashMap<>(), new HashMap<>());
  }

  /**
   * 현재 레벨에 태그를 추가합니다.
   *
   * @param name 태그 이름
   * @return 이 스키마 (체이닝용)
   */
  public TransitionSchema tag(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Tag name cannot be null or empty");
    }

    String childPath = buildPath(name);
    pathToTags.put(childPath, new ArrayList<>(List.of(name)));
    lastAddedPath = childPath;

    return this;
  }

  /**
   * 현재 레벨에 중첩 태그를 추가합니다.
   *
   * @param name 태그 이름
   * @param builder 하위 태그를 정의하는 빌더
   * @return 이 스키마 (체이닝용)
   */
  public TransitionSchema tag(String name, Consumer<TransitionSchema> builder) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Tag name cannot be null or empty");
    }
    if (builder == null) {
      throw new IllegalArgumentException("Builder cannot be null");
    }

    String childPath = buildPath(name);
    pathToTags.put(childPath, new ArrayList<>(List.of(name)));

    TransitionSchema childContext = new TransitionSchema(childPath, pathToTags, pathToAttributes);
    builder.accept(childContext);

    lastAddedPath = childPath;
    return this;
  }

  /**
   * 마지막 태그에 별칭을 추가합니다.
   *
   * @param aliases 별칭 이름들
   * @return 이 스키마 (체이닝용)
   */
  public TransitionSchema alias(String... aliases) {
    if (lastAddedPath == null) {
      throw new IllegalStateException("No tag to add alias to. Call tag() before alias()");
    }
    if (aliases == null || aliases.length == 0) {
      throw new IllegalArgumentException("At least one alias must be provided");
    }

    List<String> tags = pathToTags.get(lastAddedPath);
    for (String aliasName : aliases) {
      if (aliasName == null || aliasName.isEmpty()) {
        throw new IllegalArgumentException("Alias name cannot be null or empty");
      }
      tags.add(aliasName);
    }

    return this;
  }

  /**
   * 마지막 태그에 허용할 속성을 추가합니다.
   *
   * @param attributes 허용할 속성 이름들
   * @return 이 스키마 (체이닝용)
   */
  public TransitionSchema attr(String... attributes) {
    if (lastAddedPath == null) {
      throw new IllegalStateException("No tag to add attributes to. Call tag() before attr()");
    }
    if (attributes == null || attributes.length == 0) {
      throw new IllegalArgumentException("At least one attribute must be provided");
    }

    Set<String> attrs = pathToAttributes.computeIfAbsent(lastAddedPath, k -> new HashSet<>());
    for (String attrName : attributes) {
      if (attrName == null || attrName.isEmpty()) {
        throw new IllegalArgumentException("Attribute name cannot be null or empty");
      }
      attrs.add(attrName);
    }

    return this;
  }

  /**
   * 경로별 태그 매핑을 반환합니다.
   *
   * @return 경로를 키로, 태그 이름 리스트를 값으로 갖는 불변 맵
   */
  public Map<String, List<String>> getPathToTagsMapping() {
    Map<String, List<String>> result = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : pathToTags.entrySet()) {
      result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * 경로별 허용 속성 매핑을 반환합니다.
   *
   * @return 경로를 키로, 허용 속성 이름 집합을 값으로 갖는 불변 맵
   */
  public Map<String, Set<String>> getPathToAttributesMapping() {
    Map<String, Set<String>> result = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : pathToAttributes.entrySet()) {
      result.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * 모든 경로를 반환합니다.
   *
   * @return 모든 경로의 불변 집합
   */
  public Set<String> getAllPaths() {
    return Collections.unmodifiableSet(pathToTags.keySet());
  }

  /**
   * 모든 태그 이름(별칭 포함)을 반환합니다.
   *
   * @return 모든 태그 이름의 불변 집합
   */
  public Set<String> getAllTagNames() {
    Set<String> allTags = new HashSet<>();
    for (List<String> tags : pathToTags.values()) {
      allTags.addAll(tags);
    }
    return Collections.unmodifiableSet(allTags);
  }

  private String buildPath(String tagName) {
    if (currentPath.equals("/")) {
      return "/" + tagName;
    } else {
      return currentPath + "/" + tagName;
    }
  }

  String getCurrentPath() {
    return currentPath;
  }

  int getPathCount() {
    return pathToTags.size();
  }
}
