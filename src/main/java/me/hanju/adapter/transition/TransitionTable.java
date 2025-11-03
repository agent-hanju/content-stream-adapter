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

  public TransitionTable(TransitionSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }

    Map<String, List<String>> pathToTags = schema.getPathToTagsMapping();
    this.root = buildTree(pathToTags);
    this.allTagNames = Collections.unmodifiableSet(schema.getAllTagNames());
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
   * 여는 태그 처리
   */
  public TransitionNode tryOpen(TransitionNode current, String tagName) {
    if (current == null || tagName == null) {
      return null;
    }
    return current.getChild(tagName);
  }

  /**
   * 닫는 태그 처리 (별칭 호환)
   * <p>
   * 예: &lt;rag&gt;로 열고 &lt;/cite&gt;로 닫아도 OK (둘 다 /cite 경로)
   * </p>
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

  public TransitionNode getRoot() {
    return root;
  }

  public Set<String> getAllTagNames() {
    return allTagNames;
  }

}
