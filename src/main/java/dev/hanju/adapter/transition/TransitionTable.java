package dev.hanju.adapter.transition;

/**
 * 상태 전이 테이블
 * <p>
 * TransitionNode 트리로 O(1) 상태 전이를 제공합니다.
 * </p>
 */
public class TransitionTable {
  private final TransitionNode root;

  /**
   * 스키마로부터 전이 테이블을 생성합니다.
   *
   * @param schema 경로 스키마
   * @throws IllegalArgumentException schema가 null인 경우
   */
  public TransitionTable(TransitionSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }

    this.root = TransitionNode.createTree(schema.getAllPaths());
  }

  /**
   * 여는 전이를 시도합니다.
   *
   * @param current 현재 상태 노드
   * @param segmentName 이동할 자식 세그먼트 이름
   * @return 전이된 노드 (전이 불가 시 null)
   */
  public TransitionNode tryOpen(TransitionNode current, String segmentName) {
    if (current == null || segmentName == null) {
      return null;
    }
    return current.getChild(segmentName);
  }

  /**
   * 닫는 전이를 시도합니다 (부모로 이동).
   *
   * @param current 현재 상태 노드
   * @return 부모 노드 (루트면 null)
   */
  public TransitionNode tryClose(TransitionNode current) {
    if (current == null || current.isRoot()) {
      return null;
    }
    return current.getParent();
  }

  /**
   * 루트 노드를 반환합니다.
   *
   * @return 루트 노드
   */
  public TransitionNode getRoot() {
    return root;
  }
}
