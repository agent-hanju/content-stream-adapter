package me.hanju.adapter;

import me.hanju.adapter.payload.TaggedToken;
import me.hanju.adapter.transition.TransitionSchema;
import org.junit.jupiter.api.Test;
import java.util.List;

class CitationCloseTest {
  @Test
  void testCiteCloseSplit() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite", cite -> cite.tag("id")).alias("rag");
    
    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);
    
    // chunk1: <cite><id>doc1</id>text</cit
    // chunk2: e>
    
    System.out.println("=== Chunk 1 ===");
    for (TaggedToken token : adapter.feedToken("<cite><id>doc1</id>text</cit")) {
      System.out.println("  path=" + token.path() + ", content=" + token.content() + ", event=" + token.event());
    }
    
    System.out.println("=== Chunk 2 ===");
    for (TaggedToken token : adapter.feedToken("e>")) {
      System.out.println("  path=" + token.path() + ", content=" + token.content() + ", event=" + token.event());
    }
    
    System.out.println("=== Flush ===");
    for (TaggedToken token : adapter.flush()) {
      System.out.println("  path=" + token.path() + ", content=" + token.content() + ", event=" + token.event());
    }
  }
}
