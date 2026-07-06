# Content Stream Adapter

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

LLM 스트리밍 응답에서 XML-like 태그를 파싱하여 경로별로 구조화된 이벤트를 출력하는 zero-dependency Java 라이브러리.

[English Document](README.md)

## 개요

토큰 단위로 스트리밍되는 XML-like 스타일의 섹셔닝 텍스트를 파싱하여, 검출어(태그)를 제외한 텍스트를 현재 경로 상태와 함께 실시간으로 구조화된 이벤트로 출력하는 어댑터입니다.

## 주요 특징

- **O(1) 상태 전이**: HashMap 기반 빠른 전이 테이블
- **토큰 경계 보존**: 원본 토큰의 분절점 유지
- **Aho-Corasick 알고리즘**: O(n) 다중 패턴 매칭
- **Multi-depth 경로 지원**: `/section/subsection/content` 등의 계층 구조
- **별칭(Alias) 지원**: 여러 태그 이름을 같은 경로로 매핑
- **속성(Attribute) 지원**: 태그 속성 파싱 및 필터링 (예: `<cite id="ref">`)
- **Fault-tolerant**: 인식된 태그라도 전이 불가하면 텍스트로 처리
- **재사용 가능한 어댑터**: `flush()`가 어댑터를 초기화하여 새 스트림을 이어서 처리 가능

## 의존성

- Java 21 이상
- 외부 런타임 의존성 없음 (zero-dependency)

## 설치

[![](https://jitpack.io/v/agent-hanju/content-stream-adapter.svg)](https://jitpack.io/#agent-hanju/content-stream-adapter)

이 라이브러리는 [JitPack](https://jitpack.io/#agent-hanju/content-stream-adapter)을 통해 배포됩니다.

### Gradle

**Step 1.** `settings.gradle`에 JitPack 저장소 추가:

```gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** 의존성 추가:

```gradle
dependencies {
    implementation 'com.github.agent-hanju:content-stream-adapter:0.2.0-SNAPSHOT'
}
```

### Maven

**Step 1.** `pom.xml`에 JitPack 저장소 추가:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**Step 2.** 의존성 추가:

```xml
<dependency>
    <groupId>com.github.agent-hanju</groupId>
    <artifactId>content-stream-adapter</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

## 사용법

### 기본 사용

스키마 정의와 XML 태그 바인딩은 별도 단계입니다: `TransitionSchema`는 추상적인 경로 구조만 정의하고,
`XmlTagBinding`이 그 구조 위에 실제 태그 이름(과 속성)을 매핑합니다.

```java
import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.xml.XmlTagBinding;
import dev.hanju.adapter.xml.XmlStreamOutput;

import java.util.List;

// 1. 경로 구조 정의
TransitionSchema schema = TransitionSchema.root()
    .path("section", section -> section
        .path("subsection", subsection -> subsection
            .path("content"))
        .path("metadata"))
    .path("result");

// 2. 스키마 경로에 태그 이름 바인딩
XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/section").tag("section").and()
    .bind("/section/subsection").tag("subsection").and()
    .bind("/section/subsection/content").tag("content").and()
    .bind("/section/metadata").tag("metadata").and()
    .bind("/result").tag("result").and()
    .build();

// 3. 어댑터 생성
ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

// 4. 토큰 스트리밍 처리
String input = "Hello <section><subsection><content>world</content></subsection></section>!";
List<XmlStreamOutput> outputs = adapter.feedToken(input);

// 5. 결과 출력 (sealed 타입: Text / Enter / Exit)
for (XmlStreamOutput output : outputs) {
    switch (output) {
        case XmlStreamOutput.Text t -> System.out.println("Text: " + t.content());
        case XmlStreamOutput.Enter e -> System.out.println("Enter: " + e.path());
        case XmlStreamOutput.Exit e -> System.out.println("Exit: " + e.path());
    }
}

// 6. 스트림 종료 시 flush. 이때 어댑터도 함께 초기화되어(FSM 상태, 원문 누적기)
//    동일 인스턴스를 새 스트림에 재사용할 수 있습니다.
List<XmlStreamOutput> remaining = adapter.flush();
```

### 출력 예시

```
Text: Hello
Enter: /section
Enter: /section/subsection
Enter: /section/subsection/content
Text: world
Exit: /section/subsection/content
Exit: /section/subsection
Exit: /section
Text: !
```

### 별칭(Alias) 사용

여러 태그 이름을 같은 경로로 매핑할 수 있습니다:

```java
TransitionSchema schema = TransitionSchema.root().path("cite");

XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/cite").tag("cite").alias("rag")
    .and()
    .build();

ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

// <cite>와 <rag> 모두 /cite 경로로 처리됨
adapter.feedToken("Reference: <cite>source1</cite>");
adapter.feedToken("RAG: <rag>source2</rag>");
```

### 속성(Attribute) 지원

바인딩에서 허용한 속성만 파싱하여 필터링합니다:

```java
TransitionSchema schema = TransitionSchema.root().path("cite");

XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/cite").tag("cite").attr("id", "source")   // "id"와 "source"만 허용
    .and()
    .build();

ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

for (XmlStreamOutput output : adapter.feedToken(
        "<cite id=\"ref1\" source=\"wiki\" extra=\"ignored\">content</cite>")) {
    if (output instanceof XmlStreamOutput.Enter enter) {
        // enter.attributes()에는 허용된 속성만 포함: {id: "ref1", source: "wiki"}
        // "extra"는 필터링됨
        System.out.println("Cite 열림: " + enter.attributes());
    }
}
```

**주요 동작:**

- 속성은 여는 태그에서 파싱됨 (예: `<cite id="ref">`)
- `.attr(...)`로 허용된 속성만 출력에 포함
- 큰따옴표와 작은따옴표 모두 지원. 따옴표 없는 값(`id=1`)은 XML 비적합 문법이라
  파싱은 하되 값은 버림(값 없는 속성으로 기록)
- 불완전한 속성(따옴표가 닫히지 않음)은 flush 시 무시
- `.attr(...)` 바인딩이 없는 태그는 빈 `attributes()` 맵 반환

### Enter / Exit 이벤트

`XmlStreamOutput`은 `Text`, `Enter`, `Exit` 세 가지 variant를 가진 sealed interface입니다.
문자열 필드가 아니라 **타입**으로 태그 전이를 구분합니다:

```java
TransitionSchema schema = TransitionSchema.root()
    .path("cite")
    .path("think");

XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/cite").tag("cite").and()
    .bind("/think").tag("think").and()
    .build();

ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

for (XmlStreamOutput output : adapter.feedToken("Start <cite>source</cite> end")) {
    switch (output) {
        case XmlStreamOutput.Enter e -> System.out.println("태그 열림: " + e.path());
        case XmlStreamOutput.Exit e -> System.out.println("태그 닫힘: " + e.path());
        case XmlStreamOutput.Text t -> System.out.println("[content] " + t.content());
    }
}
```

**출력:**

```
[content] Start
태그 열림: /cite
[content] source
태그 닫힘: /cite
[content] end
```

`Text`에는 path가 없습니다 — 어떤 섹션에 속한 텍스트인지 알아야 한다면, `Text` 출력을 받은 시점에
`adapter.getCurrentPath()`를 호출하세요 (아래 스트리밍 패턴 참고).

이 기능은 섹션 경계 추적, UI 업데이트 트리거, 태그 구조에 대한 메타데이터 수집 등에 유용합니다.

### 원문 접근

`getRaw()`를 사용하여 언제든 누적된 원문 입력을 가져올 수 있습니다:

```java
ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

adapter.feedToken("안녕 ");
adapter.feedToken("<cite>");
adapter.feedToken("내용");
adapter.feedToken("</cite>");

// 모든 누적된 입력을 원본 그대로 가져오기
String raw = adapter.getRaw();  // "안녕 <cite>내용</cite>"
```

이 기능은 디버깅, 로깅, 또는 처리되지 않은 원본 입력이 필요할 때 유용합니다.

**참고:** `flush()`는 어댑터를 재사용 가능한 상태로 초기화하는 과정에서 누적기도 함께 비웁니다.
따라서 `flush()` 이후 `getRaw()`는 빈 문자열을 반환합니다. 전체 원문이 필요하면 flush 전에 호출하세요.

### 스트리밍 처리

#### 패턴 1: 직접 반복문 (단순)

```java
TransitionSchema schema = TransitionSchema.root()
    .path("think")
    .path("cite");

XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/think").tag("think").and()
    .bind("/cite").tag("cite").and()
    .build();

ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

// LLM 스트리밍 토큰 처리
for (String token : llmStreamingTokens) {
    for (XmlStreamOutput output : adapter.feedToken(token)) {
        if (output instanceof XmlStreamOutput.Text text) {
            // 현재 경로별 실시간 처리
            switch (adapter.getCurrentPath()) {
                case "/think" -> logThinkingProcess(text.content());
                case "/cite" -> collectCitation(text.content());
                default -> outputToUser(text.content());
            }
        }
    }
}

// 스트림 종료 시 남은 버퍼 flush (어댑터도 함께 초기화됨)
for (XmlStreamOutput output : adapter.flush()) {
    if (output instanceof XmlStreamOutput.Text text) {
        outputToUser(text.content());
    }
}
```

#### 패턴 2: Consumer 패턴 (콜백 기반)

```java
public class StreamingConsumer {
    private final ContentStreamAdapter adapter;
    private final Consumer<String> onUserContent;
    private final Consumer<String> onCitation;

    public StreamingConsumer(
            XmlTagBinding binding,
            Consumer<String> onUserContent,
            Consumer<String> onCitation) {
        this.adapter = new ContentStreamAdapter(binding);
        this.onUserContent = onUserContent;
        this.onCitation = onCitation;
    }

    public void accept(String token) {
        for (XmlStreamOutput output : adapter.feedToken(token)) {
            if (output instanceof XmlStreamOutput.Text text) {
                dispatch(text.content());
            }
        }
    }

    public void end() {
        for (XmlStreamOutput output : adapter.flush()) {
            if (output instanceof XmlStreamOutput.Text text) {
                dispatch(text.content());
            }
        }
    }

    private void dispatch(String content) {
        switch (adapter.getCurrentPath()) {
            case "/" -> onUserContent.accept(content);
            case "/cite" -> onCitation.accept(content);
            // "/think" 경로는 조용히 무시
        }
    }
}

// 사용 예시
StreamingConsumer consumer = new StreamingConsumer(
    binding,
    content -> sendToClient(content),      // 사용자에게 표시할 내용
    citation -> storeCitation(citation)     // 백그라운드 처리
);

llmStream.forEach(consumer::accept);
consumer.end();
```

## 아키텍처

### 핵심 컴포넌트

1. **ContentStreamAdapter**: 메인 어댑터 클래스

   - 토큰을 입력받아 `XmlStreamOutput` 리스트 반환
   - FSM 기반 상태 관리
   - `flush()`가 스트림을 마무리하고 어댑터를 재사용 가능한 상태로 초기화

2. **TransitionSchema**: 계층적 경로 구조 빌더

   - `.path(...)` 기반 Fluent API로 중첩 경로 정의
   - 순수 구조만 담당 — 태그 이름/별칭/속성은 별도로 바인딩

3. **XmlTagBinding**: 스키마 경로에 XML 태그 이름을 매핑

   - 바인딩된 경로마다 `.tag(name)`, `.alias(...)`, `.attr(...)`
   - 태그 검출에 쓰이는 Aho-Corasick 패턴을 생성

4. **XmlStreamOutput**: sealed 출력 타입

   - `Text(content)`: 일반 텍스트 내용
   - `Enter(path, attributes)`: 태그 열림, `path`로 진입
   - `Exit(path)`: 태그 닫힘, `path`에서 이탈

5. **TransitionTable**: 상태 전이 테이블

   - `TransitionNode` 트리를 사용한 O(1) 전이
   - 별칭 호환 닫는 태그 지원 (`<rag>`로 열고 `</cite>`로 닫기 가능)

6. **OpenTagParser**: 스트리밍 여는 태그 파서
   - 상태 머신 기반 속성 파싱
   - 여러 토큰에 걸친 따옴표 처리
   - 큰따옴표와 작은따옴표 지원

## 성능 특성

- **상태 전이**: O(1) - HashMap lookup
- **패턴 매칭**: O(n) - Aho-Corasick 알고리즘 (n = 입력 길이)
- **토큰 처리**: 원본 토큰 경계 보존

## 제한사항

- 자가 닫힘 태그(`<tag/>`)는 안전하게 파싱되지만 별도 시맨틱은 없습니다 — 일반 여는 태그처럼 처리됩니다
- 따옴표 없는 속성값(`id=1`)은 XML 비적합 문법이라 파싱은 하되 값은 버립니다 (값 없는 속성으로 기록)
- 중첩된 같은 태그는 지원하지 않습니다 (`<a><a></a></a>`)

## 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 기여

이슈 및 Pull Request는 환영합니다.

## 변경 이력

### 0.2.0-SNAPSHOT (Current)

- 아키텍처: `buffer`, `matching`, `transition`, `xml` 패키지로 재구성
- 아키텍처: `XmlTagBinding` 도입 — 추상 경로 구조(`TransitionSchema`)와 실제 태그 이름/별칭/속성 분리
- 아키텍처: `TaggedToken`을 sealed `XmlStreamOutput`(`Text` / `Enter` / `Exit`)으로 대체
- 기능: `flush()`가 어댑터를 초기화(FSM 상태, 원문 누적기)하여 새 스트림에 재사용 가능
- 수정: 자가 닫힘 문법(`<tag/>`)이 더 이상 속성 파싱을 오염시키지 않음
- 수정: 따옴표 없는 속성값을 캡처하지 않고 버림 (XML 적합성)

### 0.1.6

- 기능: 태그 속성 파싱 지원 (`<cite id="ref">`)
- 기능: 스키마 기반 속성 화이트리스트 (`.attr("id", "source")`)
- 기능: `TaggedToken.attributes()`로 파싱된 속성 접근
- 아키텍처: `OpenTagParser` - 상태 머신 기반 스트리밍 속성 파서
- 아키텍처: `TransitionTable.getAllowedAttributes()` - 속성 필터링

### 0.1.5

- 기능: `getRaw()` 메서드로 누적된 원문 입력 조회

### 0.1.4

- 수정: 단일 토큰 내 다중 패턴이 올바르게 처리되도록 수정
- 수정: 패턴 검출 후 prefix가 아닌 텍스트가 즉시 flush되도록 수정

### 0.1.3

- 수정: close 이벤트 시 path가 닫힌 이후의 path로 나타나는 오류 수정

### 0.1.2

- 빌드: Java 21로 업그레이드 (toolchain 기반)
- 빌드: JUnit 5.10.1 → 5.11.4 업데이트
- 빌드: AssertJ 3.24.2 → 3.27.6 업데이트

### 0.1.1

- 성능: StringBuilder 직접 사용으로 문자열 버퍼 출력 최적화
- 성능: O(1) 분할 및 제거 연산으로 TokenBuffer 최적화
- 기능: TaggedToken에 event 필드 추가 (OPEN/CLOSE 이벤트)
