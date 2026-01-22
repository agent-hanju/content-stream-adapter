package me.hanju.adapter.transition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 상태 전이 테이블
 * <p>
 * TransitionNode 트리로 O(1) 상태 전이를 제공하며 별칭을 지원합니다.
 * </p>
 */
public class TransitionTable {
  private final TransitionNode root;
  private final Set<String> allTagNames;
  private final Map<String, Set<String>> pathToAttributes;

  /**
   * 스키마로부터 전이 테이블을 생성합니다.
   *
   * @param schema 태그 전이 스키마
   * @throws IllegalArgumentException schema가 null인 경우
   */
  public TransitionTable(TransitionSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }

    Map<String, List<String>> pathToTags = schema.getPathToTagsMapping();
    this.root = buildTree(pathToTags);
    this.allTagNames = Collections.unmodifiableSet(schema.getAllTagNames());
    this.pathToAttributes = schema.getPathToAttributesMapping();
  }

  private TransitionNode buildTree(Map<String, List<String>> pathToTags) {
    TransitionNode root = TransitionNode.createTree(pathToTags.keySet());

    for (String path : pathToTags.keySet()) {
      if (path.equals("/"))
        continue;

      String[] segments = path.substring(1).split("/");
      TransitionNode current = root;
      StringBuilder currentPath = new StringBuilder();

      for (String segment : segments) {
        currentPath.append("/").append(segment);
        TransitionNode child = findNodeByPath(root, currentPath.toString());

        if (child != null && current != null) {
          current.addChild(segment, child);
        }

        current = child;
      }
    }

    for (Map.Entry<String, List<String>> entry : pathToTags.entrySet()) {
      String path = entry.getKey();
      List<String> tagNames = entry.getValue();

      TransitionNode target = findNodeByPath(root, path);
      if (target == null) {
        continue;
      }

      TransitionNode parent = target.getParent();
      if (parent == null) {
        continue;
      }

      for (String tagName : tagNames) {
        parent.addChild(tagName, target);
      }
    }

    return root;
  }

  private TransitionNode findNodeByPath(TransitionNode root, String path) {
    if (path.equals("/")) {
      return root;
    }

    String[] segments = path.substring(1).split("/");
    TransitionNode current = root;

    for (String segment : segments) {
      current = current.getChild(segment);
      if (current == null) {
        return null;
      }
    }

    return current;
  }

  /**
   * 여는 태그를 처리하여 전이를 시도합니다.
   *
   * @param current 현재 상태 노드
   * @param tagName 여는 태그 이름
   * @return 전이된 노드 (전이 불가 시 null)
   */
  public TransitionNode tryOpen(TransitionNode current, String tagName) {
    if (current == null || tagName == null) {
      return null;
    }
    return current.getChild(tagName);
  }

  /**
   * 닫는 태그를 처리하여 전이를 시도합니다 (별칭 호환).
   *
   * <p>예: {@code <rag>}로 열고 {@code </cite>}로 닫아도 OK (둘 다 /cite 경로)</p>
   *
   * @param current 현재 상태 노드
   * @param tagName 닫는 태그 이름
   * @return 전이된 노드 (전이 불가 시 null)
   */
  public TransitionNode tryClose(TransitionNode current, String tagName) {
    if (current == null || tagName == null) {
      return null;
    }

    if (current.isRoot()) {
      return null;
    }

    TransitionNode parent = current.getParent();
    if (parent != null && parent.getChild(tagName) == current) {
      return parent;
    }

    return null;
  }

  /**
   * 루트 노드를 반환합니다.
   *
   * @return 루트 노드
   */
  public TransitionNode getRoot() {
    return root;
  }

  /**
   * 모든 태그 이름(별칭 포함)을 반환합니다.
   *
   * @return 태그 이름 집합
   */
  public Set<String> getAllTagNames() {
    return allTagNames;
  }

  /**
   * 지정한 경로에서 허용된 속성 이름들을 반환합니다.
   *
   * @param path 경로
   * @return 허용된 속성 이름 집합 (없으면 빈 집합)
   */
  public Set<String> getAllowedAttributes(String path) {
    Set<String> attrs = pathToAttributes.get(path);
    return attrs != null ? attrs : Collections.emptySet();
  }

}
