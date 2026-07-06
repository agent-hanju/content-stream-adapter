package dev.hanju.adapter;

import java.util.Collections;
import java.util.Map;

/**
 * 콘텐츠 스트림 어댑터가 확정한 출력 단위.
 */
public sealed interface ContentStreamResult {

  /**
   * 변환 대상이 아닌 원문 텍스트.
   *
   * @param content 텍스트 내용
   */
  record Text(String content) implements ContentStreamResult {
    public Text {
      if (content == null) {
        throw new IllegalArgumentException("content cannot be null");
      }
    }
  }

  /**
   * 바인딩된 콘텐츠 구간으로 진입했음을 나타내는 출력.
   *
   * @param path       진입한 콘텐츠 경로
   * @param attributes 진입 구간에 연결된 속성 맵
   */
  record Enter(String path, Map<String, String> attributes) implements ContentStreamResult {
    public Enter {
      if (path == null) {
        throw new IllegalArgumentException("path cannot be null");
      }
      if (attributes == null) {
        attributes = Collections.emptyMap();
      }
    }

    /**
     * 속성 없는 진입 출력을 생성합니다.
     *
     * @param path 진입한 콘텐츠 경로
     */
    public Enter(String path) {
      this(path, Collections.emptyMap());
    }
  }

  /**
   * 현재 콘텐츠 구간에서 이탈했음을 나타내는 출력.
   *
   * @param path 이탈한 콘텐츠 경로
   */
  record Exit(String path) implements ContentStreamResult {
    public Exit {
      if (path == null) {
        throw new IllegalArgumentException("path cannot be null");
      }
    }
  }
}
