package me.hanju.adapter;

import me.hanju.adapter.payload.TaggedToken;
import me.hanju.adapter.transition.TransitionSchema;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoundaryTest {
  @Test
  void testEnhancedCompletionScenario() {
    TransitionSchema schema = TransitionSchema.root()
        .tag("cite", cite -> cite.tag("id")).alias("rag");
    
    ContentStreamAdapter adapter = new ContentStreamAdapter(schema);
    
    // 실제 테스트 시나리오 재현
    String[] chunks = {
      "시작 ",
      "<ci",
      "te><i",
      "d>ref1</i",
      "d>인용1</ci",
      "te> 중간 ",
      "<cite><i",
      "d>ref2</i",
      "d>인용2</ci",
      "te> 끝"
    };
    
    StringBuilder content = new StringBuilder();
    for (String chunk : chunks) {
      List<TaggedToken> tokens = adapter.feedToken(chunk);
      System.out.println("Chunk: \"" + chunk + "\"");
      for (TaggedToken token : tokens) {
        System.out.println("  -> path=" + token.path() + ", content=" + token.content() + ", event=" + token.event());
        if (("/".equals(token.path()) || "/cite".equals(token.path())) && token.content() != null) {
          content.append(token.content());
        }
      }
    }
    
    // flush
    for (TaggedToken token : adapter.flush()) {
      System.out.println("Flush -> path=" + token.path() + ", content=" + token.content() + ", event=" + token.event());
      if (("/".equals(token.path()) || "/cite".equals(token.path())) && token.content() != null) {
        content.append(token.content());
      }
    }
    
    System.out.println("\nFinal content: \"" + content + "\"");
    assertEquals("시작 인용1 중간 인용2 끝", content.toString());
  }
}
