package dev.hanju.adapter.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.hanju.adapter.matching.AhoCorasickTrie;
import dev.hanju.adapter.matching.TokenMatchingResult;

/**
 * TokenMatchingBuffer 성능 벤치마크 테스트
 *
 * O(n²) → O(n) 개선 효과를 검증합니다.
 * JMH가 아닌 간단한 측정이므로 참고용입니다.
 *
 * 측정 시나리오:
 * 1. 많은 토큰 스트리밍 (각 토큰마다 match() 호출)
 * 2. 긴 단일 토큰 처리
 * 3. 패턴이 여러 토큰에 걸친 경우
 */
@DisplayName("TokenMatchingBuffer 성능 벤치마크")
class TokenMatchingBufferBenchmarkTest {

  private static final int WARMUP_ITERATIONS = 3;
  private static final int MEASURE_ITERATIONS = 5;

  @Test
  @DisplayName("많은 토큰 스트리밍 성능 (O(n) 검증)")
  void benchmarkManyTokensStreaming() {
    // 패턴: XML 태그들
    AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>", "</tag>"));

    int tokenCount = 1000;
    String[] tokens = generateStreamingTokens(tokenCount);

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      runStreamingBenchmark(trie, tokens);
    }

    // Measure
    long totalTime = 0;
    int totalMatches = 0;
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      long start = System.nanoTime();
      int matches = runStreamingBenchmark(trie, tokens);
      totalTime += System.nanoTime() - start;
      totalMatches += matches;
    }

    double avgTimeMs = totalTime / MEASURE_ITERATIONS / 1_000_000.0;
    double throughput = tokenCount / avgTimeMs * 1000; // tokens/sec

    System.out.printf("[Streaming] %d tokens, avg %.2f ms, %.0f tokens/sec%n",
        tokenCount, avgTimeMs, throughput);

    // 성능 검증: 1000 토큰을 100ms 이내에 처리해야 함 (매우 보수적인 기준)
    assertThat(avgTimeMs).isLessThan(100.0);
    assertThat(totalMatches / MEASURE_ITERATIONS).isGreaterThan(0);
  }

  @Test
  @DisplayName("긴 단일 토큰 성능")
  void benchmarkLongSingleToken() {
    AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>", "</tag>"));

    // 10KB 텍스트에 태그 10개 삽입
    String longToken = generateLongTokenWithTags(10_000, 10);

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      runSingleTokenBenchmark(trie, longToken);
    }

    // Measure
    long totalTime = 0;
    int totalMatches = 0;
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      long start = System.nanoTime();
      int matches = runSingleTokenBenchmark(trie, longToken);
      totalTime += System.nanoTime() - start;
      totalMatches += matches;
    }

    double avgTimeMs = totalTime / MEASURE_ITERATIONS / 1_000_000.0;
    double throughput = longToken.length() / avgTimeMs * 1000; // chars/sec

    System.out.printf("[Long Token] %d chars, avg %.2f ms, %.0f chars/sec%n",
        longToken.length(), avgTimeMs, throughput);

    // 10KB를 50ms 이내에 처리
    assertThat(avgTimeMs).isLessThan(50.0);
    assertThat(totalMatches / MEASURE_ITERATIONS).isEqualTo(20); // 10 open + 10 close
  }

  @Test
  @DisplayName("패턴이 여러 토큰에 걸친 경우 성능")
  void benchmarkPatternAcrossTokens() {
    AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<verylongtag>", "</verylongtag>"));

    // 패턴이 여러 토큰에 걸쳐 분할됨
    String[] splitPattern = { "<very", "long", "tag>" };
    int iterations = 500;

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      runSplitPatternBenchmark(trie, splitPattern, iterations);
    }

    // Measure
    long totalTime = 0;
    int totalMatches = 0;
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      long start = System.nanoTime();
      int matches = runSplitPatternBenchmark(trie, splitPattern, iterations);
      totalTime += System.nanoTime() - start;
      totalMatches += matches;
    }

    double avgTimeMs = totalTime / MEASURE_ITERATIONS / 1_000_000.0;

    System.out.printf("[Split Pattern] %d patterns, avg %.2f ms%n",
        iterations, avgTimeMs);

    // 500 패턴을 100ms 이내에 처리
    assertThat(avgTimeMs).isLessThan(100.0);
    assertThat(totalMatches / MEASURE_ITERATIONS).isEqualTo(iterations);
  }

  @Test
  @DisplayName("선형 스케일링 검증 (토큰 수 2배 → 시간 약 2배)")
  void benchmarkLinearScaling() {
    AhoCorasickTrie trie = new AhoCorasickTrie(List.of("<tag>", "</tag>"));

    int baseCount = 500;
    String[] tokensSmall = generateStreamingTokens(baseCount);
    String[] tokensLarge = generateStreamingTokens(baseCount * 2);

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      runStreamingBenchmark(trie, tokensSmall);
      runStreamingBenchmark(trie, tokensLarge);
    }

    // Measure small
    long timeSmall = 0;
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      long start = System.nanoTime();
      runStreamingBenchmark(trie, tokensSmall);
      timeSmall += System.nanoTime() - start;
    }

    // Measure large
    long timeLarge = 0;
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      long start = System.nanoTime();
      runStreamingBenchmark(trie, tokensLarge);
      timeLarge += System.nanoTime() - start;
    }

    double ratio = (double) timeLarge / timeSmall;

    System.out.printf("[Scaling] %d tokens: %.2f ms, %d tokens: %.2f ms, ratio: %.2fx%n",
        baseCount, timeSmall / MEASURE_ITERATIONS / 1_000_000.0,
        baseCount * 2, timeLarge / MEASURE_ITERATIONS / 1_000_000.0,
        ratio);

    // 2배 토큰에 대해 3배 이내 시간 (O(n) 보장, 약간의 오차 허용)
    // O(n²)였다면 4배가 되어야 함
    assertThat(ratio).isLessThan(3.0);
  }

  // === Helper Methods ===

  private String[] generateStreamingTokens(int count) {
    String[] tokens = new String[count];
    for (int i = 0; i < count; i++) {
      if (i % 20 == 0) {
        tokens[i] = "<tag>";
      } else if (i % 20 == 10) {
        tokens[i] = "</tag>";
      } else {
        tokens[i] = "token" + i + " ";
      }
    }
    return tokens;
  }

  private String generateLongTokenWithTags(int baseLength, int tagCount) {
    StringBuilder sb = new StringBuilder();
    int segmentLength = baseLength / (tagCount * 2 + 1);

    for (int i = 0; i < tagCount; i++) {
      sb.append("x".repeat(segmentLength));
      sb.append("<tag>");
      sb.append("content").append(i);
      sb.append("</tag>");
    }
    sb.append("x".repeat(segmentLength));

    return sb.toString();
  }

  private int runStreamingBenchmark(AhoCorasickTrie trie, String[] tokens) {
    TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);
    int matchCount = 0;

    for (String token : tokens) {
      List<TokenMatchingResult> results = matcher.accept(token);
      for (TokenMatchingResult result : results) {
        if (result.type() == TokenMatchingResult.Type.PATTERN) {
          matchCount++;
        }
      }
    }

    // Flush remaining
    List<TokenMatchingResult> remaining = matcher.flush();
    for (TokenMatchingResult result : remaining) {
      if (result.type() == TokenMatchingResult.Type.PATTERN) {
        matchCount++;
      }
    }

    return matchCount;
  }

  private int runSingleTokenBenchmark(AhoCorasickTrie trie, String token) {
    TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);

    int matchCount = 0;
    List<TokenMatchingResult> results = new java.util.ArrayList<>(matcher.accept(token));
    results.addAll(matcher.flush());
    for (TokenMatchingResult result : results) {
      if (result.type() == TokenMatchingResult.Type.PATTERN) {
        matchCount++;
      }
    }

    return matchCount;
  }

  private int runSplitPatternBenchmark(AhoCorasickTrie trie, String[] splitPattern, int iterations) {
    TokenMatchingBuffer matcher = new TokenMatchingBuffer(trie);
    int matchCount = 0;

    for (int i = 0; i < iterations; i++) {
      for (String part : splitPattern) {
        List<TokenMatchingResult> results = matcher.accept(part);
        for (TokenMatchingResult result : results) {
          if (result.type() == TokenMatchingResult.Type.PATTERN) {
            matchCount++;
          }
        }
      }
    }

    return matchCount;
  }
}
