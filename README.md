# Content Stream Adapter

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

A zero-dependency streaming XML-like parser with FSM-based state transitions, token boundary preservation, and XML-like tag tokenization for structured text processing.

[한국어 문서](README-ko.md)

## Overview

ContentStreamAdapter parses XML-like sectioned text that arrives token-by-token (e.g., from LLM streaming responses), extracts content while preserving path context, and outputs structured events in real-time.

## Key Features

- **O(1) State Transitions**: HashMap-based fast transition table
- **Token Boundary Preservation**: Maintains original token segmentation
- **Streaming Tag Tokenization**: O(n) XML-like tag scanning across token boundaries
- **Multi-depth Path Support**: Hierarchical structures like `/section/subsection/content`
- **Alias Support**: Map multiple tag names to the same path
- **Attribute Support**: Parse and filter tag attributes (e.g., `<cite id="ref">`)
- **Fault-tolerant**: Unrecognized or invalid transitions output as text
- **Reusable Adapter**: `flush()` resets the adapter so it can process a new stream

## Requirements

- Java 21 or higher
- Zero runtime dependencies

## Versioning

This project is `0.x` — the API (including the standalone building blocks below) may still change between
minor versions without notice while it's being validated through real usage. `1.0.0` will mark the start of
stable, semver-guaranteed compatibility.

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
structure, and `ContentStreamAdapter builder` maps concrete tag names (and attributes) onto that structure.

```java
import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.ContentStreamResult;

import java.util.List;

// 1. Define the path structure
TransitionSchema schema = TransitionSchema.root()
    .path("section", section -> section
        .path("subsection", subsection -> subsection
            .path("content"))
        .path("metadata"))
    .path("result");

// 2. Bind tag names to schema paths
ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
    .bind("/section").tag("section").and()
    .bind("/section/subsection").tag("subsection").and()
    .bind("/section/subsection/content").tag("content").and()
    .bind("/section/metadata").tag("metadata").and()
    .bind("/result").tag("result").and()
    .build();


// 4. Feed tokens
String input = "Hello <section><subsection><content>world</content></subsection></section>!";
List<ContentStreamResult> outputs = adapter.feedToken(input);

// 5. Process output (sealed type: Text / Enter / Exit)
for (ContentStreamResult output : outputs) {
    switch (output) {
        case ContentStreamResult.Text t -> System.out.println("Text: " + t.content());
        case ContentStreamResult.Enter e -> System.out.println("Enter: " + e.path());
        case ContentStreamResult.Exit e -> System.out.println("Exit: " + e.path());
    }
}

// 6. Flush at stream end. This also resets the adapter (FSM state, raw
//    accumulator) so the same instance can be reused for a new stream.
List<ContentStreamResult> remaining = adapter.flush();
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

ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
    .bind("/cite").tag("cite").alias("rag")
    .and()
    .build();


// Both <cite> and <rag> are treated as path /cite
adapter.feedToken("Reference: <cite>source1</cite>");
adapter.feedToken("RAG: <rag>source2</rag>");
```

### Attribute Support

Parse tag attributes and filter them through the binding rule's allowed-attribute list:

```java
TransitionSchema schema = TransitionSchema.root().path("cite");

ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
    .bind("/cite").tag("cite").attr("id", "source")   // Allow only "id" and "source"
    .and()
    .build();


for (ContentStreamResult output : adapter.feedToken(
        "<cite id=\"ref1\" source=\"wiki\" extra=\"ignored\">content</cite>")) {
    if (output instanceof ContentStreamResult.Enter enter) {
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
- Tags without an `.attr(...)` config have an empty `attributes()` map

### Enter / Exit Events

`ContentStreamResult` is a sealed interface with three variants — `Text`, `Enter`, and `Exit` — so tag
transitions are distinguished by type rather than by a string field:

```java
TransitionSchema schema = TransitionSchema.root()
    .path("cite")
    .path("think");

ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
    .bind("/cite").tag("cite").and()
    .bind("/think").tag("think").and()
    .build();


for (ContentStreamResult output : adapter.feedToken("Start <cite>source</cite> end")) {
    switch (output) {
        case ContentStreamResult.Enter e -> System.out.println("Tag opened: " + e.path());
        case ContentStreamResult.Exit e -> System.out.println("Tag closed: " + e.path());
        case ContentStreamResult.Text t -> System.out.println("[content] " + t.content());
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

ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
    .bind("/think").tag("think").and()
    .bind("/cite").tag("cite").and()
    .build();


// Process LLM streaming tokens
for (String token : llmStreamingTokens) {
    for (ContentStreamResult output : adapter.feedToken(token)) {
        if (output instanceof ContentStreamResult.Text text) {
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
for (ContentStreamResult output : adapter.flush()) {
    if (output instanceof ContentStreamResult.Text text) {
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
            ContentStreamAdapter adapter,
            Consumer<String> onUserContent,
            Consumer<String> onCitation) {
        this.adapter = adapter;
        this.onUserContent = onUserContent;
        this.onCitation = onCitation;
    }

    public void accept(String token) {
        for (ContentStreamResult output : adapter.feedToken(token)) {
            if (output instanceof ContentStreamResult.Text text) {
                dispatch(text.content());
            }
        }
    }

    public void end() {
        for (ContentStreamResult output : adapter.flush()) {
            if (output instanceof ContentStreamResult.Text text) {
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
    adapter,
    content -> sendToClient(content),      // User-visible content
    citation -> storeCitation(citation)     // Background processing
);

llmStream.forEach(consumer::accept);
consumer.end();
```

### Building Blocks

`ContentStreamAdapter` is a self-contained, ready-to-use stream adapter — most users only need the API
above. But its internals (`TokenBuffer`, `TokenMatchingBuffer`, `TransitionTable`, `XmlTagBuffer`) are
public and have no dependency on each other beyond what's shown below, so they can be used standalone if
you need just one piece (e.g., only the tag tokenizer, or only the path FSM) without the rest of the
adapter.

**Note:** Since these are public types, they're part of the library's compatibility surface — but as
lower-level building blocks, their internals may still evolve faster than `ContentStreamAdapter` itself.

#### TokenBuffer — token-boundary-preserving char buffer

```java
import dev.hanju.adapter.matching.TokenBuffer;

import java.util.List;

TokenBuffer buffer = new TokenBuffer();
buffer.addToken("Hello");
buffer.addToken(" ");
buffer.addToken("world");

List<String> extracted = buffer.extract(5);   // ["Hello"]
String remaining = buffer.getContent();       // " world"
```

#### TokenMatchingBuffer — streaming multi-pattern matcher

```java
import dev.hanju.adapter.matching.AhoCorasickTrie;
import dev.hanju.adapter.matching.TokenMatchingBuffer;
import dev.hanju.adapter.matching.TokenMatchingResult;

import java.util.Set;

AhoCorasickTrie trie = new AhoCorasickTrie(Set.of("<tag>", "</tag>"));
TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

for (TokenMatchingResult result : matcher.accept("Hello <tag>world</tag>!")) {
    switch (result.type()) {
        case TEXT -> System.out.println("Text: " + String.join("", result.tokens()));
        case PATTERN -> System.out.println("Pattern: " + String.join("", result.tokens()));
    }
}
for (TokenMatchingResult result : matcher.flush()) {
    // process any remaining buffered result at stream end
}
```

**Output:**

```
Text: Hello
Pattern: <tag>
Text: world
Pattern: </tag>
Text: !
```

#### TransitionTable — path-based FSM

```java
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.transition.TransitionTable;

TransitionSchema schema = TransitionSchema.root()
    .path("section", section -> section.path("content"));

TransitionTable table = new TransitionTable(schema.toPaths());

String root = TransitionTable.getRoot();               // "/"
String section = table.tryOpen(root, "section");       // "/section"
String content = table.tryOpen(section, "content");    // "/section/content"
String backToSection = table.tryClose(content);         // "/section"
```

#### XmlTagBuffer — streaming XML-like tag tokenizer

```java
import dev.hanju.adapter.xml.XmlFragment;
import dev.hanju.adapter.xml.XmlTagBuffer;

import java.util.List;

XmlTagBuffer tagBuffer = new XmlTagBuffer(); // no filter: tokenizes every tag
List<XmlFragment> fragments = tagBuffer.feed("Hello <b>world</b>!");

for (XmlFragment fragment : fragments) {
    switch (fragment) {
        case XmlFragment.Text t -> System.out.println("Text: " + t.raw());
        case XmlFragment.Open o -> System.out.println("Open: " + o.name());
        case XmlFragment.Close c -> System.out.println("Close: " + c.name());
        case XmlFragment.SelfClosing s -> System.out.println("SelfClosing: " + s.name());
    }
}
```

**Output:**

```
Text: Hello
Open: b
Text: world
Close: b
Text: !
```

## Architecture

### Core Components

1. **ContentStreamAdapter**: Main adapter class

   - Accepts tokens and returns `ContentStreamResult` lists
   - FSM-based state management
   - `flush()` finalizes the stream and resets the adapter for reuse

2. **TransitionSchema**: Hierarchical path structure builder

   - Fluent API via `.path(...)` for defining nested paths
   - Pure structure — tag names, aliases, and attributes are bound separately

3. **ContentStreamAdapter builder**: Maps XML tag names onto schema paths

   - `.tag(name)`, `.alias(...)`, `.attr(...)` per bound path
   - Keeps tag/path/attribute binding separate from the abstract path schema
   - Generates target tag start patterns used by `TokenMatchingBuffer`

4. **ContentStreamResult**: Sealed output type

   - `Text(content)`: plain text content
   - `Enter(path, attributes)`: tag opened, entering `path`
   - `Exit(path)`: tag closed, leaving `path`

5. **TransitionTable**: State transition table

   - O(1) transitions, keyed by path `String` (implemented as two flat maps, `childOf`/`parentOf` — no tree/node objects)
   - Alias-compatible close tags (open with `<rag>`, close with `</cite>`)

6. **TokenMatchingBuffer**: Streaming target-pattern prefilter
   - Detects only bound tag starts while preserving non-target token boundaries
   - Prevents unrelated XML-like text from being parsed as adapter structure

7. **Transition peek**: Before parsing a detected tag, `ContentStreamAdapter` checks transition validity
   by tag name alone. Tags that can't transition from the current path are emitted as plain text without
   ever reaching the tag parser — no attribute buffering wasted on tags that will be rejected anyway.

8. **XmlTagBuffer**: Streaming XML-like tag parser
   - Parses only tag chunks that passed the transition peek
   - Emits a sealed `XmlFragment` (`Text` / `Open` / `SelfClosing` / `Close`), each carrying only the fields valid for that type
   - Handles quoted attribute values spanning multiple tokens

## Performance Characteristics

- **State Transitions**: O(1) - HashMap lookup
- **Target Pattern Matching**: O(n) - Aho-Corasick over bound tag starts
- **Tag Parsing**: O(n) - single pass over selected tag candidates
- **Token Processing**: Preserves original token boundaries

## Limitations

- Self-closing syntax (`<tag/>`) has distinct tag semantics; `ContentStreamAdapter` emits Enter then Exit when the transition is valid
- Unquoted attribute values (`id=1`) are non-conformant XML; parsed but discarded (attribute recorded as valueless)
- No XML well-formedness validation: tag pairing and nesting correctness are handled only by FSM transitions
- Incomplete tag candidates are emitted as text on `flush()`

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Issues and Pull Requests are welcome.

## Changelog

### 0.2.0-SNAPSHOT (Current)

- Architecture: Restructured into `matching`, `transition`, `xml` packages (`buffer` was later merged into `matching`)
- Architecture: Introduced `ContentStreamAdapter.from(...).bind(...)` to separate abstract path structure (`TransitionSchema`) from concrete tag names/aliases/attributes
- Architecture: Replaced `TaggedToken` with sealed `ContentStreamResult` (`Text` / `Enter` / `Exit`)
- Architecture: Replaced open-tag-only parsing with `XmlTagBuffer` text/tag tokenization
- Architecture: `TransitionTable` reimplemented as two flat maps instead of a node tree
- Architecture: `XmlFragment` converted from a single record with a type tag to a sealed interface (`Text`/`Open`/`SelfClosing`/`Close`), so each fragment type only carries its valid fields
- Architecture: `TokenBuffer`/`TokenMatchingBuffer` moved into `matching`; `TokenMatchResult` renamed to `TokenMatchingResult`
- Performance: Added a transition peek before tag parsing — non-transitionable tags are rejected by name alone, skipping attribute parsing entirely
- Feature: `flush()` now resets the adapter (FSM state, raw accumulator) so it can be reused for a new stream
- Feature: Self-closing syntax (`<tag/>`) now has distinct tag semantics
- Fix: Unquoted attribute values are discarded instead of being captured (XML conformance)

### 0.1.6

- Feature: Tag attribute parsing support (`<cite id="ref">`)
- Feature: Schema-based attribute whitelist (`.attr("id", "source")`)
- Feature: `TaggedToken.attributes()` for accessing parsed attributes
- Architecture: Stateful tag buffering for attribute parsing
- Architecture: `ContentStreamAdapter` internal attribute filtering during enter events

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
