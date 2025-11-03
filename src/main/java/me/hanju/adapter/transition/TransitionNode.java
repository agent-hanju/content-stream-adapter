package me.hanju.adapter.transition;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 경로 기반 상태 전이를 위한 트리 노드 (불변)
 * <p>
 * Flyweight 패턴으로 초기화 시 모든 노드를 생성하고 재사용합니다.
 * </p>
 *
 * <pre>
 * Set&lt;String&gt; paths = Set.of("/section", "/section/subsection", "/section/subsection/content");
 * TransitionNode root = TransitionNode.createTree(paths);
 *
 * // ROOT (/)
 * // └─ section (/section)
 *    └─ subsection (/section/subsection)
 *       └─ content (/section/subsection/content)
 * </pre>
 */
public class TransitionNode {
  private final String path;
  private final String tagName;
  private final TransitionNode parent;
  private final Map<String, TransitionNode> children;

  private TransitionNode(String path, String tagName, TransitionNode parent) {
    this.path = path;
    this.tagName = tagName;
    this.parent = parent;
    this.children = new HashMap<>();
  }

  public String getPath() {
    return path;
  }

  public String getTagName() {
    return tagName;
  }

  public TransitionNode getParent() {
    return parent;
  }

  public boolean isRoot() {
    return parent == null;
  }

  public TransitionNode getChild(String tagName) {
    return children.get(tagName);
  }

  void addChild(String tagName, TransitionNode child) {
    children.put(tagName, child);
  }

  @Override
  public String toString() {
    return path;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    TransitionNode node = (TransitionNode) o;
    return Objects.equals(path, node.path);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path);
  }

  /**
   * 허용 경로 목록으로부터 트리 생성
   */
  public static TransitionNode createTree(Set<String> allowedPaths) {
    TransitionNode root = new TransitionNode("/", null, null);

    if (allowedPaths == null || allowedPaths.isEmpty()) {
      return root;
    }

    List<String> sortedPaths = allowedPaths.stream()
        .filter(p -> p != null && !p.isEmpty())
        .filter(p -> !p.equals("/"))
        .sorted(Comparator.comparingInt(TransitionNode::countDepth))
        .collect(Collectors.toList());

    Map<String, TransitionNode> pathToNode = new HashMap<>();
    pathToNode.put("/", root);

    for (String path : sortedPaths) {
      createNode(path, root, pathToNode);
    }

    return root;
  }

  private static void createNode(String path, TransitionNode root, Map<String, TransitionNode> pathToNode) {
    String[] segments = path.substring(1).split("/");

    StringBuilder currentPath = new StringBuilder();
    TransitionNode currentNode = root;

    for (String segment : segments) {
      currentPath.append("/").append(segment);
      String pathStr = currentPath.toString();

      TransitionNode node = pathToNode.get(pathStr);
      if (node == null) {
        node = new TransitionNode(pathStr, segment, currentNode);
        pathToNode.put(pathStr, node);
        currentNode.addChild(segment, node);
      }

      currentNode = node;
    }
  }

  private static int countDepth(String path) {
    if (path.equals("/")) {
      return 0;
    }
    return path.split("/").length - 1;
  }
}
