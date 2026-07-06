package dev.hanju.adapter;

import org.junit.jupiter.api.Test;

import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.xml.XmlStreamOutput;
import dev.hanju.adapter.xml.XmlTagBinding;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoundaryTest {
  @Test
  void testEnhancedCompletionScenario() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite", cite -> cite.path("id"));

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").alias("rag")
        .and()
        .bind("/cite/id").tag("id")
        .and()
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

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
      List<XmlStreamOutput> outputs = adapter.feedToken(chunk);
      System.out.println("Chunk: \"" + chunk + "\"");
      for (XmlStreamOutput output : outputs) {
        String outputContent = output instanceof XmlStreamOutput.Text t ? t.content() : null;
        String path = output instanceof XmlStreamOutput.Enter e ? e.path()
            : output instanceof XmlStreamOutput.Exit e ? e.path() : null;
        String type = output instanceof XmlStreamOutput.Enter ? "ENTER"
            : output instanceof XmlStreamOutput.Exit ? "EXIT" : "TEXT";
        System.out.println("  -> path=" + path + ", content=" + outputContent + ", type=" + type);

        if (outputContent != null) {
          String currentPath = adapter.getCurrentPath();
          if ("/".equals(currentPath) || "/cite".equals(currentPath)) {
            content.append(outputContent);
          }
        }
      }
    }

    for (XmlStreamOutput output : adapter.flush()) {
      String outputContent = output instanceof XmlStreamOutput.Text t ? t.content() : null;
      String path = output instanceof XmlStreamOutput.Enter e ? e.path()
          : output instanceof XmlStreamOutput.Exit e ? e.path() : null;
      String type = output instanceof XmlStreamOutput.Enter ? "ENTER"
          : output instanceof XmlStreamOutput.Exit ? "EXIT" : "TEXT";
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
