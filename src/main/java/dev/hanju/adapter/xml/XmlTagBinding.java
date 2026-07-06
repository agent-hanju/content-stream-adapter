package dev.hanju.adapter.xml;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.hanju.adapter.transition.TransitionSchema;

/**
 * XML 태그와 경로 간의 바인딩을 정의합니다.
 *
 * <p>TransitionSchema의 경로 구조에 XML 태그 이름, 별칭, 속성을 매핑합니다.</p>
 *
 * <p><b>사용 예시:</b></p>
 * <p>{@code /cite}의 {@code id}/{@code url}은 태그 자체의 attribute(예: {@code <cite id="1">}),
 * {@code /cite/source}는 그 안에 중첩되는 별개의 자식 태그(예: {@code <cite><source>...</source></cite>})입니다.
 * 서로 다른 메커니즘이라 이름이 겹칠 필요가 없습니다.</p>
 * <pre>{@code
 * TransitionSchema schema = TransitionSchema.root()
 *     .path("cite", cite -> cite.path("source"))
 *     .path("thinking");
 *
 * XmlTagBinding binding = XmlTagBinding.from(schema)
 *     .bind("/cite").tag("cite").alias("rag").attr("id", "url")
 *     .and()
 *     .bind("/cite/source").tag("source")
 *     .and()
 *     .bind("/thinking").tag("thinking")
 *     .build();
 * }</pre>
 */
public class XmlTagBinding {
  private final TransitionSchema schema;
  private final Map<String, String> tagToPath;
  private final Map<String, Set<String>> pathToAttrs;

  private XmlTagBinding(TransitionSchema schema,
      Map<String, String> tagToPath,
      Map<String, Set<String>> pathToAttrs) {
    this.schema = schema;
    this.tagToPath = Collections.unmodifiableMap(new HashMap<>(tagToPath));
    this.pathToAttrs = Collections.unmodifiableMap(deepCopyPathToAttrs(pathToAttrs));
  }

  private static Map<String, Set<String>> deepCopyPathToAttrs(Map<String, Set<String>> original) {
    Map<String, Set<String>> copy = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : original.entrySet()) {
      copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }
    return copy;
  }

  /**
   * 스키마로부터 바인딩 빌더를 시작합니다.
   *
   * @param schema 경로 스키마
   * @return 빌더 인스턴스
   */
  public static Builder from(TransitionSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }
    return new Builder(schema);
  }

  /**
   * XML 패턴을 생성합니다 (열림/닫힘 태그).
   *
   * @return 패턴 집합
   */
  public Set<String> generatePatterns() {
    Set<String> patterns = new HashSet<>();
    for (String tag : tagToPath.keySet()) {
      patterns.add("<" + tag);
      patterns.add("</" + tag + ">");
    }
    return patterns;
  }

  /**
   * 태그 이름으로 매핑된 경로를 조회합니다.
   *
   * @param tagName 태그 이름
   * @return 매핑된 경로 (없으면 null)
   */
  public String getPathForTag(String tagName) {
    return tagToPath.get(tagName);
  }

  /**
   * 태그가 해당 경로의 태그인지 확인합니다 (닫힘 태그 검증용).
   *
   * @param tagName 태그 이름
   * @param path 경로
   * @return 해당 경로의 태그면 true
   */
  public boolean isTagForPath(String tagName, String path) {
    String mappedPath = tagToPath.get(tagName);
    return mappedPath != null && mappedPath.equals(path);
  }

  /**
   * 경로에서 허용된 속성 이름을 반환합니다.
   *
   * @param path 경로
   * @return 허용된 속성 집합 (없으면 빈 집합)
   */
  public Set<String> getAllowedAttributes(String path) {
    Set<String> attrs = pathToAttrs.get(path);
    return attrs != null ? Collections.unmodifiableSet(attrs) : Collections.emptySet();
  }

  /**
   * 스키마를 반환합니다.
   *
   * @return TransitionSchema 인스턴스
   */
  public TransitionSchema getSchema() {
    return schema;
  }

  /**
   * 모든 태그 이름을 반환합니다.
   *
   * @return 태그 이름 집합
   */
  public Set<String> getAllTagNames() {
    return Collections.unmodifiableSet(tagToPath.keySet());
  }

  /**
   * XmlTagBinding 빌더.
   */
  public static class Builder {
    private final TransitionSchema schema;
    private final Map<String, String> tagToPath = new HashMap<>();
    private final Map<String, Set<String>> pathToAttrs = new HashMap<>();

    private Builder(TransitionSchema schema) {
      this.schema = schema;
    }

    /**
     * 경로에 바인딩을 시작합니다.
     *
     * @param path 바인딩할 경로
     * @return PathBinder 인스턴스
     */
    public PathBinder bind(String path) {
      if (path == null || path.isEmpty()) {
        throw new IllegalArgumentException("Path cannot be null or empty");
      }
      if (!schema.getAllPaths().contains(path)) {
        throw new IllegalArgumentException("Path not defined in schema: " + path);
      }
      return new PathBinder(this, path);
    }

    /**
     * 바인딩을 완료하고 XmlTagBinding을 생성합니다.
     *
     * @return XmlTagBinding 인스턴스
     */
    public XmlTagBinding build() {
      return new XmlTagBinding(schema, tagToPath, pathToAttrs);
    }

    void addTag(String path, String tagName) {
      tagToPath.put(tagName, path);
    }

    void addAlias(String path, String aliasName) {
      tagToPath.put(aliasName, path);
    }

    void addAttr(String path, String attrName) {
      pathToAttrs.computeIfAbsent(path, k -> new HashSet<>()).add(attrName);
    }
  }

  /**
   * 경로별 바인딩 빌더.
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
      builder.addTag(path, tagName);
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
      for (String alias : aliases) {
        if (alias == null || alias.isEmpty()) {
          throw new IllegalArgumentException("Alias name cannot be null or empty");
        }
        builder.addAlias(path, alias);
      }
      return this;
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
      for (String attrName : attributes) {
        if (attrName == null || attrName.isEmpty()) {
          throw new IllegalArgumentException("Attribute name cannot be null or empty");
        }
        builder.addAttr(path, attrName);
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
     * 현재 바인딩을 완료하고 XmlTagBinding을 생성합니다.
     * {@code and().build()}의 편의 메서드입니다.
     *
     * @return XmlTagBinding 인스턴스
     */
    public XmlTagBinding build() {
      return and().build();
    }
  }
}
