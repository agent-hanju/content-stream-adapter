package dev.hanju.adapter.xml;

import java.util.Collections;
import java.util.Map;

/**
 * XML 스트리밍 파서의 출력을 나타내는 sealed interface
 */
public sealed interface XmlStreamOutput {

  /**
   * 일반 텍스트 출력
   *
   * @param content 텍스트 컨텐츠
   */
  record Text(String content) implements XmlStreamOutput {
    public Text {
      if (content == null) {
        throw new IllegalArgumentException("content cannot be null");
      }
    }
  }

  /**
   * XML 태그 진입 출력
   *
   * @param path       진입한 FSM 경로
   * @param attributes 태그 속성 맵
   */
  record Enter(String path, Map<String, String> attributes) implements XmlStreamOutput {
    public Enter {
      if (path == null) {
        throw new IllegalArgumentException("path cannot be null");
      }
      if (attributes == null) {
        attributes = Collections.emptyMap();
      }
    }

    public Enter(String path) {
      this(path, Collections.emptyMap());
    }
  }

  /**
   * XML 태그 이탈 출력
   *
   * @param path 이탈하는 FSM 경로
   */
  record Exit(String path) implements XmlStreamOutput {
    public Exit {
      if (path == null) {
        throw new IllegalArgumentException("path cannot be null");
      }
    }
  }
}
