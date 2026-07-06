# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

```bash
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

## Architecture Overview

This is a **zero-dependency streaming XML-like parser** for processing LLM streaming responses with structured tags.

### Data Flow

```
Input tokens → TokenMatchingBuffer (Aho-Corasick) → ContentStreamAdapter (FSM) → XmlStreamOutput
                     ↓                                        ↓
              Pattern detection                    State transitions via TransitionTable
```

### Core Components

**ContentStreamAdapter** (`dev.hanju.adapter`) - Main entry point
- Accepts streaming tokens via `feedToken(String)`
- Manages FSM state and coordinates pattern matching
- Returns `List<XmlStreamOutput>` (`Text` / `Enter` / `Exit`) with path context
- Must call `flush()` at stream end; `flush()` also resets the adapter (FSM state, raw accumulator) so it can be reused for a new stream

**TransitionSchema** (`dev.hanju.adapter.transition`) - Fluent schema builder
- Defines hierarchical path structure via `.path()` with optional nested builders
- Paths are pure structure; tag names/aliases/attributes are bound separately via `XmlTagBinding`

**TransitionTable** - O(1) state transition lookup
- Built from TransitionSchema via `TransitionNode.createTree()`
- Stateless: `tryOpen(node, segment)` / `tryClose(node)` take and return `TransitionNode` (caller holds the cursor)

**XmlTagBinding** (`dev.hanju.adapter.xml`) - Maps XML tag names to schema paths
- Binds tag names, aliases (e.g. `<cite>`/`<rag>` → same path), and allowed attributes per path
- Generates Aho-Corasick patterns (`<tag`, `</tag>`) via `generatePatterns()`

**AhoCorasickTrie** (`dev.hanju.adapter.matching`) - Stateless multi-pattern automaton
- O(n) matching; caller holds the `int` state id (mirrors TransitionTable's caller-held-cursor design)

**TokenMatchingBuffer** (`dev.hanju.adapter.buffer`) - Aho-Corasick based pattern detection over streamed tokens
- O(n) multi-pattern matching, wraps `TokenBuffer` for token-boundary-preserving storage
- Maximal-munch matching (earliest start, longest match) with a pending-candidate mechanism

**OpenTagParser** (`dev.hanju.adapter.xml`) - Streaming attribute parser
- State machine for parsing attributes within open tags
- Handles quotes spanning multiple tokens; unquoted attribute values are non-conformant XML and are parsed but discarded (treated as valueless)

**XmlStreamOutput** (`dev.hanju.adapter.xml`) - Sealed output type: `Text(content)`, `Enter(path, attributes)`, `Exit(path)`

### Key Design Decisions

- **Fault-tolerant parsing**: Invalid transitions output tags as plain text
- **Token boundary preservation**: Original streaming token splits are maintained
- **Greedy pattern matching**: Prefers longer patterns when ambiguous
- **Alias compatibility**: Different tag names can map to same path; close tags work across aliases

## Package Structure

```
dev.hanju.adapter
├── ContentStreamAdapter.java       # Main adapter
├── buffer/
│   ├── TokenBuffer.java           # Char-based buffer, O(1) token add/extract
│   └── TokenMatchingBuffer.java   # Streaming pattern matching over TokenBuffer
├── matching/
│   ├── AhoCorasickTrie.java       # Stateless trie with failure links
│   └── TokenMatchResult.java      # TEXT/PATTERN result record (token list preserved)
├── transition/
│   ├── TransitionNode.java        # Immutable tree node for state machine
│   ├── TransitionSchema.java      # Fluent path-structure builder
│   └── TransitionTable.java       # Stateless state transition lookup
└── xml/
    ├── OpenTagParser.java         # Streaming attribute parser
    ├── TagInfo.java               # Tag type (OPEN/CLOSE), name, attributes
    ├── XmlStreamOutput.java       # Sealed output: Text/Enter/Exit
    └── XmlTagBinding.java         # Tag name ↔ schema path ↔ attribute binding
```

## Limitations

- Self-closing syntax (`<tag/>`) is parsed safely (the `/` is discarded, not treated as an attribute) but has no distinct semantics — it is handled like a regular open tag
- Unquoted attribute values (`id=1`) are non-conformant XML; parsed but the value is discarded (attribute recorded as valueless)
- No nested identical tags (`<a><a></a></a>`)
