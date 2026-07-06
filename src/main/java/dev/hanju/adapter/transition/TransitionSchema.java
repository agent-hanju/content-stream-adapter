package dev.hanju.adapter.transition;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 계층적 경로 스키마 빌더.
 *
 * <p>Fluent API로 경로 구조를 정의합니다.</p>
 *
 * <p><b>사용 예시:</b></p>
 * <pre>{@code
 * TransitionSchema schema = TransitionSchema.root()
 *     .path("section", section -> section
 *         .path("subsection", subsection -> subsection
 *             .path("content"))
 *         .path("metadata"))
 *     .path("cite");
 * }</pre>
 */
public class TransitionSchema {
  private final String currentPath;
  private final Set<String> paths;

  private TransitionSchema(String currentPath, Set<String> paths) {
    this.currentPath = currentPath;
    this.paths = paths;
  }

  /**
   * 루트 스키마를 생성합니다.
   *
   * @return 새로운 루트 스키마
   */
  public static TransitionSchema root() {
    return new TransitionSchema("/", new HashSet<>());
  }

  /**
   * 현재 레벨에 경로를 추가합니다.
   *
   * @param name 경로 세그먼트 이름
   * @return 이 스키마 (체이닝용)
   */
  public TransitionSchema path(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Path name cannot be null or empty");
    }

    final String childPath = buildPath(name);
    paths.add(childPath);

    return this;
  }

  /**
   * 현재 레벨에 중첩 경로를 추가합니다.
   *
   * @param name 경로 세그먼트 이름
   * @param builder 하위 경로를 정의하는 빌더
   * @return 이 스키마 (체이닝용)
   */
  public TransitionSchema path(String name, Consumer<TransitionSchema> builder) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Path name cannot be null or empty");
    }
    if (builder == null) {
      throw new IllegalArgumentException("Builder cannot be null");
    }

    final String childPath = buildPath(name);
    paths.add(childPath);

    final TransitionSchema childContext = new TransitionSchema(childPath, paths);
    builder.accept(childContext);

    return this;
  }

  /**
   * 모든 자식 경로를 반환합니다.
   *
   * @return 모든 자식 경로의 불변 집합
   */
  public Set<String> toPaths() {
    return Collections.unmodifiableSet(paths);
  }

  /**
   * 자식 경로 생성용 메서드
   *
   * @param segmentName 생성할 하위 경로의 이름
   * @return 루트부터 하위 경로까지의 전체 경로 문자열
   */
  private String buildPath(String segmentName) {
    if (currentPath.equals("/")) {
      return "/" + segmentName;
    } else {
      return currentPath + "/" + segmentName;
    }
  }
}
