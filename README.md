# Content Stream Adapter

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

A zero-dependency streaming XML-like parser with FSM-based state transitions, token boundary preservation, and Aho-Corasick pattern matching for structured text processing.

[한국어 문서](README-ko.md)

## Overview

ContentStreamAdapter parses XML-like sectioned text that arrives token-by-token (e.g., from LLM streaming responses), extracts content while preserving path context, and outputs structured tokens in real-time.

## Key Features

- **O(1) State Transitions**: HashMap-based fast transition table
- **Token Boundary Preservation**: Maintains original token segmentation
- **Aho-Corasick Algorithm**: O(n) multi-pattern matching
- **Multi-depth Path Support**: Hierarchical structures like `/section/subsection/content`
- **Alias Support**: Map multiple tag names to the same path
- **Attribute Support**: Parse and filter tag attributes (e.g., `<cite id="ref">`)
- **Fault-tolerant**: Unrecognized or invalid transitions output as text

## Requirements

- Java 21 or higher
- Zero runtime dependencies

## Installation

[![](https://jitpack.io/v/agent-hanju/content-stream-adapter.svg)](https://jitpack.io/#agent-hanju/content-stream-adapter)

This library is available via [JitPack](https://jitpack.io/#agent-hanju/content-stream-adapter).

### Gradle

**Step 1.** Add JitPack repository to your `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the dependency:

```gradle
dependencies {
    implementation 'com.github.agent-hanju:content-stream-adapter:0.1.6'
}
```

### Maven

**Step 1.** Add JitPack repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**Step 2.** Add the dependency:

```xml
<dependency>
    <groupId>com.github.agent-hanju</groupId>
    <artifactId>content-stream-adapter</artifactId>
    <version>0.1.6</version>
</dependency>
```

## Usage

### Basic Usage

```java
import me.hanju.adapter.ContentStreamAdapter;
import me.hanju.adapter.transition.TransitionSchema;
import me.hanju.adapter.payload.TaggedToken;

import java.util.List;

// 1. Define schema
TransitionSchema schema = TransitionSchema.root()
    .tag("section", section -> section
        .tag("subsection", subsection -> subsection
            .tag("content"))
        .tag("metadata"))
    .tag("result");

// 2. Create adapter
ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

// 3. Feed tokens
String input = "Hello <section><subsection><content>world</content></subsection></section>!";
List<TaggedToken> tokens = adapter.feedToken(input);

// 4. Process output
for (TaggedToken token : tokens) {
    System.out.println("Path: " + token.path() + ", Content: " + token.content());
}

// 5. Flush buffer (on stream end)
List<TaggedToken> remaining = adapter.flush();
```

### Output

```
Path: /, Content: Hello
Path: /section/subsection/content, Content: world
Path: /, Content: !
```

### Alias Support

Map multiple tag names to the same path:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("cite").alias("rag")           // Both <cite> and <rag> map to /cite
    .tag("think").alias("thinking");    // Both <think> and <thinking> map to /think

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

// Both processed as /cite path
adapter.feedToken("Reference: <cite>source1</cite>");
adapter.feedToken("RAG: <rag>source2</rag>");
```

### Attribute Support

Parse tag attributes and filter them through schema-defined allowed attributes:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("cite").attr("id", "source")   // Allow only "id" and "source" attributes
    .tag("think");

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

for (TaggedToken token : adapter.feedToken("<cite id=\"ref1\" source=\"wiki\" extra=\"ignored\">content</cite>")) {
    if ("OPEN".equals(token.event())) {
        // token.attributes() contains only allowed attributes: {id: "ref1", source: "wiki"}
        // "extra" is filtered out
        System.out.println("Cite opened with: " + token.attributes());
    }
}
```

**Key behaviors:**

- Attributes are parsed from open tags (e.g., `<cite id="ref">`)
- Only schema-defined attributes are included in the output
- Attributes support both double and single quotes
- Incomplete attributes (unclosed quotes) are ignored on flush
- Tags without defined attributes have empty `attributes()` map

### Event Handling

TaggedToken includes an `event` field that notifies when tags open or close:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("cite")
    .tag("think");

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

for (TaggedToken token : adapter.feedToken("Start <cite>source</cite> end")) {
    if ("OPEN".equals(token.event())) {
        System.out.println("Tag opened: " + token.path());
    } else if ("CLOSE".equals(token.event())) {
        System.out.println("Tag closed: " + token.path());
    } else {
        // Regular content (event is null)
        System.out.println("[" + token.path() + "] " + token.content());
    }
}
```

**Output:**

```
[/] Start
Tag opened: /cite
[/cite] source
Tag closed: /cite
[/] end
```

This is useful for tracking section boundaries, triggering UI updates, or collecting metadata about tag structure.

### Raw Input Access

Retrieve the accumulated raw input at any time using `getRaw()`:

```java
ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

adapter.feedToken("Hello ");
adapter.feedToken("<cite>");
adapter.feedToken("content");
adapter.feedToken("</cite>");

// Get all accumulated input as-is
String raw = adapter.getRaw();  // "Hello <cite>content</cite>"
```

This is useful for debugging, logging, or when you need the original unprocessed input.

### Streaming Processing

#### Pattern 1: Direct Iteration (Simple)

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("think")
    .tag("cite");

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

// Process LLM streaming tokens
for (String token : llmStreamingTokens) {
    List<TaggedToken> results = adapter.feedToken(token);

    for (TaggedToken taggedToken : results) {
        // Real-time processing per path
        switch (taggedToken.path()) {
            case "/think" -> logThinkingProcess(taggedToken.content());
            case "/cite" -> collectCitation(taggedToken.content());
            default -> outputToUser(taggedToken.content());
        }
    }
}

// Flush remaining buffer on stream end
adapter.flush().forEach(token -> processToken(token));
```

#### Pattern 2: Reactive Streams (WebFlux/Reactor)

```java
import reactor.core.publisher.Flux;

TransitionSchema schema = TransitionSchema.root()
    .tag("think")
    .tag("cite");

// SSE or WebFlux streaming endpoint
public Flux<ServerSentEvent<String>> streamLlmResponse() {
    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

    return llmClient.streamTokens()
        .flatMapIterable(adapter::feedToken)  // Feed each token
        .filter(token -> !"/think".equals(token.path()))  // Filter out thinking
        .map(TaggedToken::content)
        .concatWith(Flux.defer(() ->
            Flux.fromIterable(adapter.flush())  // Flush on stream end
                .filter(token -> !"/think".equals(token.path()))
                .map(TaggedToken::content)
        ))
        .map(content -> ServerSentEvent.builder(content).build());
}
```

#### Pattern 3: Consumer Pattern (Callback-based)

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
                // Silently ignore "/think" path
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

// Usage
StreamingConsumer consumer = new StreamingConsumer(
    schema,
    content -> sendToClient(content),      // User-visible content
    citation -> storeCitation(citation)     // Background processing
);

llmStream.forEach(consumer::accept);
consumer.end();
```

## Architecture

### Core Components

1. **ContentStreamAdapter**: Main adapter class

   - Accepts tokens and returns TaggedToken lists
   - FSM-based state management

2. **TransitionSchema**: Hierarchical tag schema builder

   - Fluent API for intuitive schema definition
   - Alias support (`.alias()`)
   - Attribute whitelist (`.attr()`)

3. **TaggedToken**: Output token (record)

   - `path`: Current FSM path (e.g., "/", "/section", "/section/subsection")
   - `content`: Text content excluding tags
   - `event`: Event type ("OPEN", "CLOSE", or null for regular content)
   - `attributes`: Filtered attribute map (OPEN event only)

4. **StreamPatternMatcher**: Aho-Corasick based pattern matching

   - O(n) multi-pattern detection
   - Token boundary preservation

5. **TransitionTable**: State transition table
   - O(1) transitions using TransitionNode tree
   - Alias support
   - Attribute whitelist lookup

6. **OpenTagParser**: Streaming open tag parser
   - State machine-based attribute parsing
   - Handles quotes spanning multiple tokens
   - Supports both single and double quotes

## Performance Characteristics

- **State Transitions**: O(1) - HashMap lookup
- **Pattern Matching**: O(n) - Aho-Corasick algorithm (n = input length)
- **Token Processing**: Preserves original token boundaries

## Limitations

- No support for self-closing tags (`<tag/>`)
- No support for nested identical tags (`<a><a></a></a>`)

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Issues and Pull Requests are welcome.

## Changelog

### 0.1.6 (Current)

- Feature: Tag attribute parsing support (`<cite id="ref">`)
- Feature: Schema-based attribute whitelist (`.attr("id", "source")`)
- Feature: `TaggedToken.attributes()` for accessing parsed attributes
- Architecture: `OpenTagParser` for streaming attribute parsing with state machine
- Architecture: `TransitionTable.getAllowedAttributes()` for attribute filtering

### 0.1.5

- Feature: `getRaw()` method to retrieve accumulated raw input

### 0.1.4

- Fix: Multiple patterns in single token now processed correctly
- Fix: Non-prefix text after pattern detection now flushed immediately

### 0.1.3

- Fix: CLOSE event now shows the closed path instead of post-transition path

### 0.1.2

- Build: Upgraded to Java 21 (toolchain-based)
- Build: Updated JUnit 5.10.1 → 5.11.4
- Build: Updated AssertJ 3.24.2 → 3.27.6

### 0.1.1

- Performance: Optimized string buffer output with direct StringBuilder usage
- Performance: Optimized TokenBuffer with O(1) split and remove operations
- Feature: Added event field to TaggedToken (OPEN/CLOSE events)
