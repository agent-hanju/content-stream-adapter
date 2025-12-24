# Content Stream Adapter

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

LLM 스트리밍 응답에서 XML-like 태그를 파싱하여 경로별로 구조화된 토큰을 출력하는 zero-dependency Java 라이브러리.

[English Document](README.md)

## 개요

토큰 단위로 스트리밍되는 XML-like 스타일의 섹셔닝 텍스트를 파싱하여, 검출어(태그)를 제외한 텍스트를 현재 경로 상태와 함께 스트림으로 출력하는 어댑터입니다.

## 주요 특징

- **O(1) 상태 전이**: HashMap 기반 빠른 전이 테이블
- **토큰 경계 보존**: 원본 토큰의 분절점 유지
- **Aho-Corasick 알고리즘**: O(n) 다중 패턴 매칭
- **Multi-depth 경로 지원**: `/section/subsection/content` 등의 계층 구조
- **별칭(Alias) 지원**: 여러 태그 이름을 같은 경로로 매핑
- **Fault-tolerant**: 인식된 태그라도 전이 불가하면 텍스트로 처리

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
    implementation 'com.github.agent-hanju:content-stream-adapter:0.1.2'
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
    <version>0.1.2</version>
</dependency>
```

## 사용법

### 기본 사용

```java
import me.hanju.adapter.ContentStreamAdapter;
import me.hanju.adapter.transition.TransitionSchema;
import me.hanju.adapter.payload.TaggedToken;

import java.util.List;

// 1. 스키마 정의
TransitionSchema schema = TransitionSchema.root()
    .tag("section", section -> section
        .tag("subsection", subsection -> subsection
            .tag("content"))
        .tag("metadata"))
    .tag("result");

// 2. 어댑터 생성
ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

// 3. 토큰 스트리밍 처리
String input = "Hello <section><subsection><content>world</content></subsection></section>!";
List<TaggedToken> tokens = adapter.feedToken(input);

// 4. 결과 출력
for (TaggedToken token : tokens) {
    System.out.println("Path: " + token.path() + ", Content: " + token.content());
}

// 5. 버퍼 flush (스트림 종료 시)
List<TaggedToken> remaining = adapter.flush();
```

### 출력 예시

```
Path: /, Content: Hello
Path: /section/subsection/content, Content: world
Path: /, Content: !
```

### 별칭(Alias) 사용

여러 태그 이름을 같은 경로로 매핑할 수 있습니다:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("cite").alias("rag")           // <cite>와 <rag> 모두 /cite로
    .tag("think").alias("thinking");    // <think>와 <thinking> 모두 /think로

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

// <cite>와 <rag>는 모두 /cite 경로로 처리됨
adapter.feedToken("Reference: <cite>source1</cite>");
adapter.feedToken("RAG: <rag>source2</rag>");
```

### 이벤트 처리

TaggedToken은 태그가 열리고 닫힐 때 알림을 제공하는 `event` 필드를 포함합니다:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("cite")
    .tag("think");

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

for (TaggedToken token : adapter.feedToken("Start <cite>source</cite> end")) {
    if ("OPEN".equals(token.event())) {
        System.out.println("태그 열림: " + token.path());
    } else if ("CLOSE".equals(token.event())) {
        System.out.println("태그 닫힘: " + token.path());
    } else {
        // 일반 콘텐츠 (event는 null)
        System.out.println("[" + token.path() + "] " + token.content());
    }
}
```

**출력:**

```
[/] Start
태그 열림: /cite
[/cite] source
태그 닫힘: /cite
[/] end
```

이 기능은 섹션 경계 추적, UI 업데이트 트리거, 태그 구조에 대한 메타데이터 수집 등에 유용합니다.

### 스트리밍 처리

#### 패턴 1: 직접 반복문 (단순)

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("think")
    .tag("cite");

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

// LLM 스트리밍 토큰 처리
for (String token : llmStreamingTokens) {
    List<TaggedToken> results = adapter.feedToken(token);

    for (TaggedToken taggedToken : results) {
        // 경로별 실시간 처리
        switch (taggedToken.path()) {
            case "/think" -> logThinkingProcess(taggedToken.content());
            case "/cite" -> collectCitation(taggedToken.content());
            default -> outputToUser(taggedToken.content());
        }
    }
}

// 스트림 종료 시 남은 버퍼 flush
adapter.flush().forEach(token -> processToken(token));
```

#### 패턴 2: Reactive Streams (WebFlux/Reactor)

```java
import reactor.core.publisher.Flux;

TransitionSchema schema = TransitionSchema.root()
    .tag("think")
    .tag("cite");

// SSE 또는 WebFlux 스트리밍 엔드포인트
public Flux<ServerSentEvent<String>> streamLlmResponse() {
    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    return llmClient.streamTokens()
        .flatMapIterable(adapter::feedToken)  // 각 토큰 처리
        .filter(token -> !"/think".equals(token.path()))  // thinking 경로 필터링
        .map(TaggedToken::content)
        .concatWith(Flux.defer(() ->
            Flux.fromIterable(adapter.flush())  // 스트림 종료 시 flush
                .filter(token -> !"/think".equals(token.path()))
                .map(TaggedToken::content)
        ))
        .map(content -> ServerSentEvent.builder(content).build());
}
```

#### 패턴 3: Consumer 패턴 (콜백 기반)

```java
public class StreamingConsumer {
    private final ContentStreamAdapter adapter;
    private final Consumer<String> onUserContent;
    private final Consumer<String> onCitation;

    public StreamingConsumer(
            TransitionSchema schema,
            Consumer<String> onUserContent,
            Consumer<String> onCitation) {
        this.adapter = new ContentStreamAdapter(schema);
        this.onUserContent = onUserContent;
        this.onCitation = onCitation;
    }

    public void accept(String token) {
        adapter.feedToken(token).forEach(taggedToken -> {
            switch (taggedToken.path()) {
                case "/" -> onUserContent.accept(taggedToken.content());
                case "/cite" -> onCitation.accept(taggedToken.content());
                // "/think" 경로는 조용히 무시
            }
        });
    }

    public void end() {
        adapter.flush().forEach(taggedToken -> {
            switch (taggedToken.path()) {
                case "/" -> onUserContent.accept(taggedToken.content());
                case "/cite" -> onCitation.accept(taggedToken.content());
            }
        });
    }
}

// 사용 예시
StreamingConsumer consumer = new StreamingConsumer(
    schema,
    content -> sendToClient(content),      // 사용자에게 표시할 내용
    citation -> storeCitation(citation)     // 백그라운드 처리
);

llmStream.forEach(consumer::accept);
consumer.end();
```

## 아키텍처

### 핵심 컴포넌트

1. **ContentStreamAdapter**: 메인 어댑터 클래스

   - 토큰을 입력받아 TaggedToken 리스트 반환
   - FSM 기반 상태 관리

2. **TransitionSchema**: 계층적 태그 스키마 빌더

   - Fluent API로 직관적인 스키마 정의
   - 별칭 지원

3. **TaggedToken**: 출력 토큰 (record)

   - `path`: 현재 FSM 경로 (예: "/", "/section", "/section/subsection")
   - `content`: 태그를 제외한 텍스트 내용
   - `event`: 이벤트 타입 ("OPEN", "CLOSE", 또는 일반 콘텐츠일 때 null)

4. **StreamPatternMatcher**: Aho-Corasick 기반 패턴 매칭

   - O(n) 다중 패턴 검출
   - 토큰 경계 보존

5. **TransitionTable**: 상태 전이 테이블
   - TransitionNode 트리를 사용한 O(1) 전이
   - 별칭 지원

## 성능 특성

- **상태 전이**: O(1) - HashMap lookup
- **패턴 매칭**: O(n) - Aho-Corasick 알고리즘 (n = 입력 길이)
- **토큰 처리**: 원본 토큰 경계 보존

## 제한사항

- 태그 속성은 지원하지 않습니다 (`<tag attr="value">` → `<tag>`로 처리)
- 자가 닫힘 태그는 지원하지 않습니다 (`<tag/>`)
- 중첩된 같은 태그는 지원하지 않습니다 (`<a><a></a></a>`)

## 라이선스

MIT License - 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 기여

이슈 및 Pull Request는 환영합니다.

## 변경 이력

### 0.1.2 (Current)

- 빌드: Java 21로 업그레이드 (toolchain 기반)
- 빌드: JUnit 5.10.1 → 5.11.4 업데이트
- 빌드: AssertJ 3.24.2 → 3.27.6 업데이트

### 0.1.1

- 성능: StringBuilder 직접 사용으로 문자열 버퍼 출력 최적화
- 성능: O(1) 분할 및 제거 연산으로 TokenBuffer 최적화
- 기능: TaggedToken에 event 필드 추가 (OPEN/CLOSE 이벤트)
