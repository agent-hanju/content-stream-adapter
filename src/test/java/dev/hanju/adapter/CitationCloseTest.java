package dev.hanju.adapter;

import org.junit.jupiter.api.Test;

import dev.hanju.adapter.ContentStreamAdapter;
import dev.hanju.adapter.transition.TransitionSchema;
import dev.hanju.adapter.xml.XmlStreamOutput;
import dev.hanju.adapter.xml.XmlTagBinding;

class CitationCloseTest {
  @Test
  void testCiteCloseSplit() {
    TransitionSchema schema = TransitionSchema.root()
        .path("cite", cite -> cite.path("id"));

    XmlTagBinding binding = XmlTagBinding.from(schema)
        .bind("/cite").tag("cite").alias("rag")
        .and()
        .bind("/cite/id").tag("id")
        .and()
        .build();

    ContentStreamAdapter adapter = new ContentStreamAdapter(binding);

    System.out.println("=== Chunk 1 ===");
    for (XmlStreamOutput output : adapter.feedToken("<cite><id>doc1</id>text</cit")) {
      printOutput(output);
    }

    System.out.println("=== Chunk 2 ===");
    for (XmlStreamOutput output : adapter.feedToken("e>")) {
      printOutput(output);
    }

    System.out.println("=== Flush ===");
    for (XmlStreamOutput output : adapter.flush()) {
      printOutput(output);
    }
  }

  private void printOutput(XmlStreamOutput output) {
    String content = output instanceof XmlStreamOutput.Text t ? t.content() : null;
    String path = output instanceof XmlStreamOutput.Enter e ? e.path()
        : output instanceof XmlStreamOutput.Exit e ? e.path() : null;
    String type = output instanceof XmlStreamOutput.Enter ? "ENTER"
        : output instanceof XmlStreamOutput.Exit ? "EXIT" : "TEXT";
    System.out.println("  path=" + path + ", content=" + content + ", type=" + type);
  }
}
