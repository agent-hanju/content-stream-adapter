package dev.hanju.adapter;

import org.junit.jupiter.api.Test;

import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.ContentStreamResult;

class CitationCloseTest {
  @Test
  void testCiteCloseSplit() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite", cite -> cite.path("id"));

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").alias("rag")
        .and()
        .bind("/cite/id").tag("id")
        .and()
        .build();

    System.out.println("=== Chunk 1 ===");
    for (ContentStreamResult output : adapter.feedToken("<cite><id>doc1</id>text</cit")) {
      printOutput(output);
    }

    System.out.println("=== Chunk 2 ===");
    for (ContentStreamResult output : adapter.feedToken("e>")) {
      printOutput(output);
    }

    System.out.println("=== Flush ===");
    for (ContentStreamResult output : adapter.flush()) {
      printOutput(output);
    }
  }

  private void printOutput(ContentStreamResult output) {
    String content = output instanceof ContentStreamResult.Text t ? t.content() : null;
    String path = output instanceof ContentStreamResult.Enter e ? e.path()
        : output instanceof ContentStreamResult.Exit e ? e.path() : null;
    String type = output instanceof ContentStreamResult.Enter ? "ENTER"
        : output instanceof ContentStreamResult.Exit ? "EXIT" : "TEXT";
    System.out.println("  path=" + path + ", content=" + content + ", type=" + type);
  }
}
