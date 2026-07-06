package dev.hanju.adapter.transition;

/**
 * 상태 전이 결과를 나타내는 sealed interface
 */
public sealed interface TransitionResult {

  /**
   * 상태 진입 결과
   *
   * @param path 진입한 FSM 경로
   */
  record Enter(String path) implements TransitionResult {
    public Enter {
      if (path == null) {
        throw new IllegalArgumentException("path cannot be null");
      }
    }
  }

  /**
   * 상태 이탈 결과
   *
   * @param path 이탈하는 FSM 경로
   */
  record Exit(String path) implements TransitionResult {
    public Exit {
      if (path == null) {
        throw new IllegalArgumentException("path cannot be null");
      }
    }
  }
}
