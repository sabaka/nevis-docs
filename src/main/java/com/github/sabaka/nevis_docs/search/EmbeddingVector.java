package com.github.sabaka.nevis_docs.search;

import java.util.stream.IntStream;

public final class EmbeddingVector {

  public static final int DIMENSIONS = 1024;

  private EmbeddingVector() {}

  public static boolean isValid(float[] embedding) {
    return embedding.length == DIMENSIONS
        && IntStream.range(0, embedding.length).allMatch(index -> Float.isFinite(embedding[index]));
  }
}
