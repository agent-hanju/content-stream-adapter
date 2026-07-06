# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

시스템 기본 JDK가 Gradle 8.11.1과 호환되지 않는 최신 버전(예: class file major version 69 = Java 25)이면,
`d:/opt/jdk` 아래 설치된 JDK 중 프로젝트 toolchain 버전(Java 21)을 `JAVA_HOME`으로 지정해서 실행한다.

```bash
# JAVA_HOME 지정이 필요한 경우 (Git Bash 예시)
JAVA_HOME="d:/opt/jdk/jdk-21.0.5+11" ./gradlew build

# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests ContentStreamAdapterTest

# Run a specific test method
./gradlew test --tests "ContentStreamAdapterTest.InvalidInputHandling.testNullToken"

# Generate Javadoc
./gradlew javadoc

# Clean build
./gradlew clean build
```

## Design Principles

- `ContentStreamAdapter`의 핵심 토큰 처리는 O(n) 복잡도를 벗어나선 안 된다. 누적된 스트림 길이에 비례해 느려지는 연산을 추가하지 않는다.
- 감지하려는 영역 외의 토큰 경계는 유지해야 한다. 원본 스트리밍 토큰의 분절점을 임의로 합치거나 쪼개지 않는다.
- 항상 최적화와 가능한 간단한 코드 구성을 고려한다. 성능과 단순성 중 하나를 고를 필요가 없다면 둘 다 만족하는 설계를 우선한다.
- 객체지향성보단 알고리즘적인 코드로 접근한다. 불필요한 추상화 계층·상속·인터페이스를 만들지 않는다.
- 재사용성 없는 private 메서드를 생성하지 않는다. 1회만 호출되는 로직은 인라인하고, 뚜렷한 목적을 가진 긴 코드는 인라인 주석으로 목적과 범위를 표시한다.
- 허용하지 않는 입력은 에러로 반환한다(예: null). 다만 스트림 특성상 실제로 발생 가능한 값(예: 빈 문자열 델타)은 무시하고 통과시킨다.
- 다양한 형식의 반환형은 sealed interface로 구현한다. 타입마다 유효한 필드만 갖도록 하여, 타입에 안 맞는 필드 접근 자체가 컴파일 에러가 되게 한다(단일 record + enum 태그 + null 필드 조합은 지양).
- Stateless 연산기(`AhoCorasickTrie`, `TransitionTable`)는 호출자가 커서(상태)를 들고 다니게 하고, 연산기 자체는 상태를 갖지 않는다.
- 이 프로젝트는 `0.x`다. 실사용 검증이 끝나기 전까지 API는 마이너 버전 사이에도 자유롭게 바뀔 수 있으므로 레거시 코드를 고려한 설계를 하지 않는다. `1.0.0`부터 안정적인 호환성 범위를 보장한다.

## Java 코드 작성 컨벤션

- null 허용 여부는 `jakarta.annotation.Nullable`만 명시한다. 어노테이션이 없으면 non-null이 기본값이며, 이는 빌드에 통합된 NullAway가 강제한다. `@Nonnull`은 작성하지 않는다(불필요한 반복이며 정책상 default).
- `jakarta.annotation-api`는 `compileOnly` 의존성이다. `@Nullable`이 `@Retention(RUNTIME)`이라 클래스 파일엔 남지만, 일반적인 메서드 호출/필드 접근에는 JVM이 이 타입을 필요로 하지 않으므로 소비자에게 런타임 의존성을 전파하지 않는다(무의존 정책 유지). 리플렉션으로 애노테이션을 직접 조회하는 예외적 소비자만 영향을 받을 수 있다.
- 코드 상 non-null이 보장되지만 타입 시스템이 증명할 수 없는 지점(예: 상태 전이 invariant, peek 이후 commit 패턴)은 `Objects.requireNonNull(...)`로 명시하고, 왜 안전한지 주석으로 남긴다. 필드/반환 타입을 `@Nullable`로 완화해 문제를 숨기지 않는다.
- 메서드 내부의 지역 변수 중 재할당되지 않는 것은 `final`을 명시적으로 붙인다.
- public/private 여부와 관계없이 모든 메서드와 필드에 Javadoc을 작성한다(private 메서드도 예외 없음). Javadoc은 한국어로 작성한다.

## 정적 분석 (NullAway / Error Prone)

- `build.gradle`에 `net.ltgt.errorprone` 플러그인과 `com.uber.nullaway:nullaway`, `com.google.errorprone:error_prone_core`가 통합되어 있다. `./gradlew build`/`compileJava` 시 `dev.hanju.adapter` 패키지 전체에 대해 NullAway가 null 안정성을 검사하며, 위반 시 컴파일 에러(`CheckSeverity.ERROR`)로 취급한다.
- `NullAway:CustomNullableAnnotations`에 `jakarta.annotation.Nullable`을 등록해뒀으므로, 이 애노테이션만 붙이면 NullAway가 nullable로 인식한다.
- 테스트 컴파일(`compileTestJava`)에서는 NullAway를 비활성화한다.
- error_prone_core 버전은 NullAway 버전과 호환성이 있다(예: 최신 error_prone_core 2.50.0은 NullAway 0.12.7의 내부 API(`DescendantOf`)와 충돌해 `NoClassDefFoundError`가 남). 두 라이브러리 버전을 올릴 때는 반드시 같이 조정하고 빌드로 검증한다.
