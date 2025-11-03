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
 * 계층적 태그 스키마 빌더
 * <p>
 * Fluent API로 태그 구조를 정의하고 별칭을 지원합니다.
 * </p>
 *
 * <h3>사용 예시:</h3>
 * <pre>
 * TransitionSchema schema = TransitionSchema.root()
 *     .tag("section", section -> section
 *         .tag("subsection", subsection -> subsection
 *             .tag("content"))
 *         .tag("metadata"))
 *     .tag("cite").alias("rag");
 *
 * ContentStreamAdapter adapter = new ContentStreamAdapter(schema);
 * </pre>
 */
public class TransitionSchema {
  private final String currentPath;
  private final Map<String, List<String>> pathToTags;
  private String lastAddedPath;

  private TransitionSchema(String currentPath, Map<String, List<String>> pathToTags) {
    this.currentPath = currentPath;
    this.pathToTags = pathToTags;
  }

  public static TransitionSchema root() {
    return new TransitionSchema("/", new HashMap<>());
  }

  /**
   * 현재 레벨에 태그 추가
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
   * 현재 레벨에 중첩 태그 추가
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

    TransitionSchema childContext = new TransitionSchema(childPath, pathToTags);
    builder.accept(childContext);

    lastAddedPath = childPath;
    return this;
  }

  /**
   * 마지막 태그에 별칭 추가
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

  public Map<String, List<String>> getPathToTagsMapping() {
    Map<String, List<String>> result = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : pathToTags.entrySet()) {
      result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  public Set<String> getAllPaths() {
    return Collections.unmodifiableSet(pathToTags.keySet());
  }

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
