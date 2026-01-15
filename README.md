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
    implementation 'com.github.agent-hanju:content-stream-adapter:0.1.5'
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
    <version>0.1.5</version>
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

### Attribute Support

XML-like tags can include attributes. Define allowed attributes in the schema using `.attr()`:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("cite").attr("id", "source")    // Allow id and source attributes
    .tag("section").attr("id");          // Allow only id attribute

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

for (TaggedToken token : adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">content</cite>")) {
    if ("OPEN".equals(token.event())) {
        System.out.println("Tag opened: " + token.path());
        System.out.println("Attributes: " + token.attributes());
    } else if (token.event() == null) {
        System.out.println("[" + token.path() + "] " + token.content());
    }
}
```

**Output:**

```
Tag opened: /cite
Attributes: {id=ref1, source=wiki}
[/cite] content
```

**Attribute Filtering:**

Only attributes defined in the schema are included. Undefined attributes are silently ignored:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("cite").attr("id");  // Only "id" is allowed

// source attribute will be ignored (not defined in schema)
adapter.feedToken("<cite id=\"ref1\" source=\"wiki\">content</cite>");
// OPEN event attributes: {id=ref1}  (source is filtered out)
```

**Nested Tags with Attributes:**

Each tag in a hierarchy can have its own allowed attributes:

```java
TransitionSchema schema = TransitionSchema.root()
    .tag("section", section -> section
        .tag("cite").attr("ref"))    // /section/cite allows "ref"
    .attr("id");                      // /section allows "id"

ContentStreamAdapter adapter = new ContentStreamAdapter(schema);
adapter.feedToken("<section id=\"s1\"><cite ref=\"r1\">text</cite></section>");

// OPEN /section: {id=s1}
// OPEN /section/cite: {ref=r1}
```

**Attribute Parsing Rules:**

- Attributes use `key="value"` format with double quotes
- Multiple attributes separated by spaces
- Spaces allowed in attribute values
- Escape sequences not supported (values end at closing `"`)
- Only OPEN events include attributes (CLOSE and content tokens have empty attribute maps)
- Attributes are parsed when the `>` character is detected
- Schema-undefined attributes are silently ignored

**Token Boundary Preservation:**

- Tags (including attributes) are buffered until `>` is found
- Content tokens preserve original boundaries (no buffering)
- O(n) complexity maintained by buffering only tags, not content

**Example with Streaming:**

```java
ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

// Attributes can be split across tokens
List<TaggedToken> tokens1 = adapter.feedToken("<cite id=\"re");
List<TaggedToken> tokens2 = adapter.feedToken("f1\">content</cite>");

// tokens1: empty (tag not yet complete)
// tokens2: OPEN event with {id=ref1}, then content token
```

### Raw Input Access

Retrieve the accumulated raw input at any time using `getRaw()`:

```java
ContentStreamAdapter adapter = new ContentStreamAdapter(schema);

adapter.feedToken("Hello ");
adapter.feedToken("<cite id=\"ref1\">");
adapter.feedToken("content");
adapter.feedToken("</cite>");

// Get all accumulated input as-is
String raw = adapter.getRaw();  // "Hello <cite id=\"ref1\">content</cite>"
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
   - Alias support

3. **TaggedToken**: Output token (record)

   - `path`: Current FSM path (e.g., "/", "/section", "/section/subsection")
   - `content`: Text content excluding tags
   - `event`: Event type ("OPEN", "CLOSE", or null for regular content)
   - `attributes`: Map of tag attributes (populated for OPEN events, empty otherwise)

4. **StreamPatternMatcher**: Aho-Corasick based pattern matching

   - O(n) multi-pattern detection
   - Token boundary preservation

5. **TransitionTable**: State transition table
   - O(1) transitions using TransitionNode tree
   - Alias support

## Performance Characteristics

- **State Transitions**: O(1) - HashMap lookup
- **Pattern Matching**: O(n) - Aho-Corasick algorithm (n = input length)
- **Token Processing**: Preserves original token boundaries

## When to Use Which Adapter?

This library provides 2 adapters for different parsing scenarios:

**ContentStreamAdapter (XML-like Streaming):**
- Use when LLM outputs **natural language mixed with XML tags**
- Example: `"Hello <think>reasoning here</think> world <cite>source</cite>!"`
- Schema-based validation: only defined tags are recognized as paths
- Invalid tags are treated as plain text
- Supports tag attributes: `<cite id="ref1" source="wiki">content</cite>`
- Output: **TaggedToken** (path + content + event + attributes)
- **Use case**: Real-time LLM streaming with structured sections

**JsonDeltaStreamAdapter (JSON Streaming):**
- Package: `me.hanju.jsondelta` ([Full Documentation](src/main/java/me/hanju/jsondelta/README.md))
- Use when you need **Java objects** from streaming JSON
- Example: `{"choices":[{"delta":{"content":"Hello"}}]}`
- Deserializes complete JSON objects as they arrive
- Designed for use with **DeltaMerger** (incremental object merging)
- Output: **Typed Java objects** (deltas)
- **Use case**: OpenAI/Anthropic streaming APIs, delta accumulation

## Limitations

- No support for self-closing tags (`<tag/>`)
- No support for nested identical tags (`<a><a></a></a>`)
- Attribute values do not support escape sequences (simple `key="value"` format only)

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Issues and Pull Requests are welcome.

## Changelog

### 0.1.5 (Current)

- Feature: Schema-based attribute definition with `.attr()` method
- Feature: Attribute filtering - only schema-defined attributes are included
- Feature: `getRaw()` method to retrieve accumulated raw input
- Feature: Incomplete tags emit OPEN events on `flush()` with available attributes
- Change: Open tag pattern matching now uses `<tagname` (without `>`) to support attributes

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
