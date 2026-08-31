package com.github.sabaka.nevis_docs.search;

public final class VectorLiteral {

  private VectorLiteral() {}

  public static String of(float[] vector) {
    StringBuilder builder = new StringBuilder(vector.length * 8 + 2).append('[');
    for (int index = 0; index < vector.length; index++) {
      if (index > 0) {
        builder.append(',');
      }
      builder.append(vector[index]);
    }
    return builder.append(']').toString();
  }
}
