package dev.hanju.adapter.xml;

import java.util.Collections;
import java.util.Map;

/**
 * XmlTagBuffer가 확정한 XML-like 조각.
 *
 * <p>타입별로 유효한 필드만 갖는 별도 record로 나뉘어 있어, TEXT에 name을 묻거나
 * CLOSE에 attributes를 묻는 등 타입에 안 맞는 접근 자체가 컴파일 에러가 됩니다.</p>
 */
public sealed interface XmlFragment {

  /**
   * 원본 조각 문자열. TEXT에서는 텍스트 내용, 태그에서는 원본 태그 문자열.
   *
   * @return 원본 조각 문자열
   */
  String raw();

  /**
   * 일반 텍스트 조각.
   *
   * @param raw 텍스트 내용
   */
  record Text(String raw) implements XmlFragment {
    public Text {
      if (raw == null) {
        throw new IllegalArgumentException("raw cannot be null");
      }
    }
  }

  /**
   * 여는 태그 조각.
   *
   * @param raw        원본 태그 문자열
   * @param name       태그 이름
   * @param attributes 태그 속성 맵
   */
  record Open(String raw, String name, Map<String, String> attributes)
      implements XmlFragment {
    public Open {
      if (raw == null) {
        throw new IllegalArgumentException("raw cannot be null");
      }
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("tag name cannot be null or empty");
      }
      attributes = attributes == null
          ? Collections.emptyMap()
          : Collections.unmodifiableMap(attributes);
    }
  }

  /**
   * 자체 닫힘 태그 조각.
   *
   * @param raw        원본 태그 문자열
   * @param name       태그 이름
   * @param attributes 태그 속성 맵
   */
  record SelfClosing(String raw, String name, Map<String, String> attributes)
      implements XmlFragment {
    public SelfClosing {
      if (raw == null) {
        throw new IllegalArgumentException("raw cannot be null");
      }
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("tag name cannot be null or empty");
      }
      attributes = attributes == null
          ? Collections.emptyMap()
          : Collections.unmodifiableMap(attributes);
    }
  }

  /**
   * 닫는 태그 조각.
   *
   * @param raw  원본 태그 문자열
   * @param name 태그 이름
   */
  record Close(String raw, String name) implements XmlFragment {
    public Close {
      if (raw == null) {
        throw new IllegalArgumentException("raw cannot be null");
      }
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("tag name cannot be null or empty");
      }
    }
  }

  /**
   * 텍스트 조각을 생성합니다.
   *
   * @param content 텍스트 내용
   * @return 텍스트 조각
   */
  static XmlFragment text(String content) {
    return new Text(content);
  }

  /**
   * 속성 없는 여는 태그 조각을 생성합니다.
   *
   * @param name 태그 이름
   * @return 여는 태그 조각
   */
  static XmlFragment open(String name) {
    return new Open("<" + name + ">", name, Collections.emptyMap());
  }

  /**
   * 여는 태그 조각을 생성합니다.
   *
   * @param raw        원본 태그 문자열
   * @param name       태그 이름
   * @param attributes 태그 속성 맵
   * @return 여는 태그 조각
   */
  static XmlFragment open(String raw, String name, Map<String, String> attributes) {
    return new Open(raw, name, attributes);
  }

  /**
   * 닫는 태그 조각을 생성합니다.
   *
   * @param name 태그 이름
   * @return 닫는 태그 조각
   */
  static XmlFragment close(String name) {
    return new Close("</" + name + ">", name);
  }

  /**
   * 닫는 태그 조각을 생성합니다.
   *
   * @param raw  원본 태그 문자열
   * @param name 태그 이름
   * @return 닫는 태그 조각
   */
  static XmlFragment close(String raw, String name) {
    return new Close(raw, name);
  }

  /**
   * 자체 닫힘 태그 조각을 생성합니다.
   *
   * @param raw        원본 태그 문자열
   * @param name       태그 이름
   * @param attributes 태그 속성 맵
   * @return 자체 닫힘 태그 조각
   */
  static XmlFragment selfClosing(String raw, String name, Map<String, String> attributes) {
    return new SelfClosing(raw, name, attributes);
  }

  /**
   * 자체 닫힘 태그 조각을 생성합니다.
   *
   * @param name 태그 이름
   * @return 자체 닫힘 태그 조각
   */
  static XmlFragment selfClosing(String name) {
    return new SelfClosing("<" + name + " />", name, Collections.emptyMap());
  }
}
