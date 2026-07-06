package dev.hanju.adapter.transition;

import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** path 문자열을 커서로 받아 O(1) 상태 전이를 제공하는 stateless 상태 전이 테이블. */
public class TransitionTable {
  private final Map<String, Map<String, String>> childOf; // currentPath -> (segment -> nextPath)
  private final Map<String, String> parentOf;              // currentPath -> parentPath

  /**
   * 경로 집합으로부터 전이 테이블을 생성합니다.
   *
   * @param allPaths TransitionSchema.getAllPaths()로 얻은 경로 집합
   * @throws IllegalArgumentException allPaths가 null인 경우
   */
  public TransitionTable(Set<String> allPaths) {
    if (allPaths == null) {
      throw new IllegalArgumentException("allPaths cannot be null");
    }

    final Map<String, Map<String, String>> childOf = new HashMap<>();
    final Map<String, String> parentOf = new HashMap<>();
    childOf.put("/", new HashMap<>());

    for (final String path : allPaths) {
      if (path == null || path.isEmpty() || path.equals("/")) {
        continue;
      }

      // 경로 하나를 세그먼트별로 쪼개 루트부터 걸어 내려가며, 없는 중간 관계를 그때그때 등록.
      // 처리 순서와 무관하게 항상 루트부터 다시 걷기 때문에 경로를 깊이순으로 정렬할 필요가 없음
      final String[] segments = path.substring(1).split("/");
      final StringBuilder currentPath = new StringBuilder();
      String parentPath = "/";

      for (final String segment : segments) {
        currentPath.append("/").append(segment);
        final String pathStr = currentPath.toString();

        // 다른 경로를 처리하다가 같은 중간 관계가 이미 등록됐을 수 있으므로 없을 때만 등록
        if (!parentOf.containsKey(pathStr)) {
          parentOf.put(pathStr, parentPath);
          childOf.computeIfAbsent(parentPath, key -> new HashMap<>()).put(segment, pathStr);
          childOf.putIfAbsent(pathStr, new HashMap<>());
        }

        parentPath = pathStr;
      }
    }

    this.childOf = childOf;
    this.parentOf = parentOf;
  }

  /**
   * 여는 전이를 시도합니다.
   *
   * @param currentPath 현재 경로
   * @param segmentName 이동할 자식 세그먼트 이름
   * @return 전이된 경로 (전이 불가 시 null)
   */
  public @Nullable String tryOpen(String currentPath, @Nullable String segmentName) {
    final Map<String, String> children = childOf.get(currentPath);
    if (children == null || segmentName == null) {
      return null;
    }
    return children.get(segmentName);
  }

  /**
   * 닫는 전이를 시도합니다 (부모 경로로 이동).
   *
   * @param currentPath 현재 경로
   * @return 부모 경로 (루트거나 알 수 없는 경로면 null)
   */
  public @Nullable String tryClose(String currentPath) {
    return parentOf.get(currentPath);
  }

  /**
   * 루트 경로를 반환합니다.
   *
   * @return 루트 경로 ("/")
   */
  public static String getRoot() {
    return "/";
  }
}
