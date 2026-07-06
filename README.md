# Content Stream Adapter

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

A zero-dependency streaming XML-like parser with FSM-based state transitions, token boundary preservation, and Aho-Corasick pattern matching for structured text processing.

[한국어 문서](README-ko.md)

## Overview

ContentStreamAdapter parses XML-like sectioned text that arrives token-by-token (e.g., from LLM streaming responses), extracts content while preserving path context, and outputs structured events in real-time.

## Key Features

- **O(1) State Transitions**: HashMap-based fast transition table
- **Token Boundary Preservation**: Maintains original token segmentation
- **Aho-Corasick Algorithm**: O(n) multi-pattern matching
- **Multi-depth Path Support**: Hierarchical structures like `/section/subsection/content`
- **Alias Support**: Map multiple tag names to the same path
- **Attribute Support**: Parse and filter tag attributes (e.g., `<cite id="ref">`)
- **Fault-tolerant**: Unrecognized or invalid transitions output as text
- **Reusable Adapter**: `flush()` resets the adapter so it can process a new stream

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
    implementation 'com.github.agent-hanju:content-stream-adapter:0.2.0-SNAPSHOT'
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
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Usage

Schema definition and XML tag binding are separate steps: `TransitionSchema` defines the abstract path
structure, and `XmlTagBinding` maps concrete tag names (and attributes) onto that structure.

```java
import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.xml.XmlTagBinding;
import dev.hanju.adapter.xml.XmlStreamOutput;

import java.util.List;

// 1. Define the path structure
TransitionSchema schema = TransitionSchema.root()
    .path("section", section -> section
        .path("subsection", subsection -> subsection
            .path("content"))
        .path("metadata"))
    .path("result");

// 2. Bind tag names to schema paths
XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/section").tag("section").and()
    .bind("/section/subsection").tag("subsection").and()
    .bind("/section/subsection/content").tag("content").and()
    .bind("/section/metadata").tag("metadata").and()
    .bind("/result").tag("result").and()
    .build();

// 3. Create adapter
ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

// 4. Feed tokens
String input = "Hello <section><subsection><content>world</content></subsection></section>!";
List<XmlStreamOutput> outputs = adapter.feedToken(input);

// 5. Process output (sealed type: Text / Enter / Exit)
for (XmlStreamOutput output : outputs) {
    switch (output) {
        case XmlStreamOutput.Text t -> System.out.println("Text: " + t.content());
        case XmlStreamOutput.Enter e -> System.out.println("Enter: " + e.path());
        case XmlStreamOutput.Exit e -> System.out.println("Exit: " + e.path());
    }
}

// 6. Flush at stream end. This also resets the adapter (FSM state, raw
//    accumulator) so the same instance can be reused for a new stream.
List<XmlStreamOutput> remaining = adapter.flush();
```

### Output

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

### Alias Support

Map multiple tag names to the same path:

```java
TransitionSchema schema = TransitionSchema.root().path("cite");

XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/cite").tag("cite").alias("rag")
    .and()
    .build();

ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

// Both <cite> and <rag> are treated as path /cite
adapter.feedToken("Reference: <cite>source1</cite>");
adapter.feedToken("RAG: <rag>source2</rag>");
```

### Attribute Support

Parse tag attributes and filter them through the binding's allowed-attribute list:

```java
TransitionSchema schema = TransitionSchema.root().path("cite");

XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/cite").tag("cite").attr("id", "source")   // Allow only "id" and "source"
    .and()
    .build();

ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

for (XmlStreamOutput output : adapter.feedToken(
        "<cite id=\"ref1\" source=\"wiki\" extra=\"ignored\">content</cite>")) {
    if (output instanceof XmlStreamOutput.Enter enter) {
        // enter.attributes() contains only allowed attributes: {id: "ref1", source: "wiki"}
        // "extra" is filtered out
        System.out.println("Cite opened with: " + enter.attributes());
    }
}
```

**Key behaviors:**

- Attributes are parsed from open tags (e.g., `<cite id="ref">`)
- Only attributes allowed via `.attr(...)` are included in the output
- Attributes support both double and single quotes; unquoted values (`id=1`) are non-conformant
  XML and are parsed but discarded (recorded as valueless)
- Incomplete attributes (unclosed quotes) are ignored on flush
- Tags without an `.attr(...)` binding have an empty `attributes()` map

### Enter / Exit Events

`XmlStreamOutput` is a sealed interface with three variants — `Text`, `Enter`, and `Exit` — so tag
transitions are distinguished by type rather than by a string field:

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
        case XmlStreamOutput.Enter e -> System.out.println("Tag opened: " + e.path());
        case XmlStreamOutput.Exit e -> System.out.println("Tag closed: " + e.path());
        case XmlStreamOutput.Text t -> System.out.println("[content] " + t.content());
    }
}
```

**Output:**

```
[content] Start
Tag opened: /cite
[content] source
Tag closed: /cite
[content] end
```

Note that `Text` carries no path — use `adapter.getCurrentPath()` at the point a `Text` output is
received if you need to know which section it belongs to (see the streaming patterns below).

This is useful for tracking section boundaries, triggering UI updates, or collecting metadata about tag structure.

### Raw Input Access

Retrieve the accumulated raw input at any time using `getRaw()`:

```java
ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

adapter.feedToken("Hello ");
adapter.feedToken("<cite>");
adapter.feedToken("content");
adapter.feedToken("</cite>");

// Get all accumulated input as-is
String raw = adapter.getRaw();  // "Hello <cite>content</cite>"
```

This is useful for debugging, logging, or when you need the original unprocessed input.

**Note:** `flush()` clears the accumulator as part of resetting the adapter for reuse, so `getRaw()`
returns an empty string after `flush()`. Call `getRaw()` before flushing if you need the full raw input.

### Streaming Processing

#### Pattern 1: Direct Iteration (Simple)

```java
TransitionSchema schema = TransitionSchema.root()
    .path("think")
    .path("cite");

XmlTagBinding binding = XmlTagBinding.from(schema)
    .bind("/think").tag("think").and()
    .bind("/cite").tag("cite").and()
    .build();

ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

// Process LLM streaming tokens
for (String token : llmStreamingTokens) {
    for (XmlStreamOutput output : adapter.feedToken(token)) {
        if (output instanceof XmlStreamOutput.Text text) {
            // Real-time processing per current path
            switch (adapter.getCurrentPath()) {
                case "/think" -> logThinkingProcess(text.content());
                case "/cite" -> collectCitation(text.content());
                default -> outputToUser(text.content());
            }
        }
    }
}

// Flush remaining buffer on stream end (also resets the adapter)
for (XmlStreamOutput output : adapter.flush()) {
    if (output instanceof XmlStreamOutput.Text text) {
        outputToUser(text.content());
    }
}
```

#### Pattern 2: Consumer Pattern (Callback-based)

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
            // Silently ignore "/think" path
        }
    }
}

// Usage
StreamingConsumer consumer = new StreamingConsumer(
    binding,
    content -> sendToClient(content),      // User-visible content
    citation -> storeCitation(citation)     // Background processing
);

llmStream.forEach(consumer::accept);
consumer.end();
```

## Architecture

### Core Components

1. **ContentStreamAdapter**: Main adapter class

   - Accepts tokens and returns `XmlStreamOutput` lists
   - FSM-based state management
   - `flush()` finalizes the stream and resets the adapter for reuse

2. **TransitionSchema**: Hierarchical path structure builder

   - Fluent API via `.path(...)` for defining nested paths
   - Pure structure — tag names, aliases, and attributes are bound separately

3. **XmlTagBinding**: Maps XML tag names onto schema paths

   - `.tag(name)`, `.alias(...)`, `.attr(...)` per bound path
   - Generates the Aho-Corasick patterns used for tag detection

4. **XmlStreamOutput**: Sealed output type

   - `Text(content)`: plain text content
   - `Enter(path, attributes)`: tag opened, entering `path`
   - `Exit(path)`: tag closed, leaving `path`

5. **TransitionTable**: State transition table

   - O(1) transitions using a `TransitionNode` tree
   - Alias-compatible close tags (open with `<rag>`, close with `</cite>`)

6. **OpenTagParser**: Streaming open tag parser
   - State machine-based attribute parsing
   - Handles quotes spanning multiple tokens
   - Supports both single and double quotes

## Performance Characteristics

- **State Transitions**: O(1) - HashMap lookup
- **Pattern Matching**: O(n) - Aho-Corasick algorithm (n = input length)
- **Token Processing**: Preserves original token boundaries

## Limitations

- Self-closing syntax (`<tag/>`) is parsed safely but has no distinct semantics — treated like a regular open tag
- Unquoted attribute values (`id=1`) are non-conformant XML; parsed but discarded (attribute recorded as valueless)
- No support for nested identical tags (`<a><a></a></a>`)

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Issues and Pull Requests are welcome.

## Changelog

### 0.2.0-SNAPSHOT (Current)

- Architecture: Restructured into `buffer`, `matching`, `transition`, `xml` packages
- Architecture: Introduced `XmlTagBinding` to separate abstract path structure (`TransitionSchema`) from concrete tag names/aliases/attributes
- Architecture: Replaced `TaggedToken` with sealed `XmlStreamOutput` (`Text` / `Enter` / `Exit`)
- Feature: `flush()` now resets the adapter (FSM state, raw accumulator) so it can be reused for a new stream
- Fix: Self-closing syntax (`<tag/>`) no longer corrupts attribute parsing
- Fix: Unquoted attribute values are discarded instead of being captured (XML conformance)

### 0.1.6

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
