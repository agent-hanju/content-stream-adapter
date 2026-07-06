package dev.hanju.adapter;

import org.junit.jupiter.api.Test;

import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.ContentStreamResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoundaryTest {
  @Test
  void testEnhancedCompletionScenario() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite", cite -> cite.path("id"));

    ContentStreamAdapter adapter = ContentStreamAdapter.from(schema.toPaths())
        .bind("/cite").tag("cite").alias("rag")
        .and()
        .bind("/cite/id").tag("id")
        .and()
        .build();

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
      List<ContentStreamResult> outputs = adapter.feedToken(chunk);
      System.out.println("Chunk: \"" + chunk + "\"");
      for (ContentStreamResult output : outputs) {
        String outputContent = output instanceof ContentStreamResult.Text t ? t.content() : null;
        String path = output instanceof ContentStreamResult.Enter e ? e.path()
            : output instanceof ContentStreamResult.Exit e ? e.path() : null;
        String type = output instanceof ContentStreamResult.Enter ? "ENTER"
            : output instanceof ContentStreamResult.Exit ? "EXIT" : "TEXT";
        System.out.println("  -> path=" + path + ", content=" + outputContent + ", type=" + type);

        if (outputContent != null) {
          String currentPath = adapter.getCurrentPath();
          if ("/".equals(currentPath) || "/cite".equals(currentPath)) {
            content.append(outputContent);
          }
        }
      }
    }

    for (ContentStreamResult output : adapter.flush()) {
      String outputContent = output instanceof ContentStreamResult.Text t ? t.content() : null;
      String path = output instanceof ContentStreamResult.Enter e ? e.path()
          : output instanceof ContentStreamResult.Exit e ? e.path() : null;
      String type = output instanceof ContentStreamResult.Enter ? "ENTER"
          : output instanceof ContentStreamResult.Exit ? "EXIT" : "TEXT";
      System.out.println("Flush -> path=" + path + ", content=" + outputContent + ", type=" + type);

      if (outputContent != null) {
        String currentPath = adapter.getCurrentPath();
        if ("/".equals(currentPath) || "/cite".equals(currentPath)) {
          content.append(outputContent);
        }
      }
    }

    System.out.println("\nFinal content: \"" + content + "\"");
    assertEquals("시작 인용1 중간 인용2 끝", content.toString());
  }
}
