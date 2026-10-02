package org.zalava.modules.catalog;

/**
 * Framework-free comparison of dotted module release versions. Segments are split on {@code . + -},
 * missing segments count as zero, numeric segments compare numerically and any remaining segment
 * compares lexically. This makes {@code 1.10.0} newer than {@code 1.9.0}.
 */
public final class ModuleReleaseVersion {

  private ModuleReleaseVersion() {}

  public static int compare(String left, String right) {
    String[] leftParts = left.split("[.+-]", -1);
    String[] rightParts = right.split("[.+-]", -1);
    int length = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < length; index++) {
      String leftPart = index < leftParts.length ? leftParts[index] : "0";
      String rightPart = index < rightParts.length ? rightParts[index] : "0";
      int comparison = comparePart(leftPart, rightPart);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  public static boolean isNewer(String candidate, String current) {
    return compare(candidate, current) > 0;
  }

  private static int comparePart(String left, String right) {
    if (left.equals(right)) {
      return 0;
    }
    if (left.matches("\\d+") && right.matches("\\d+")) {
      return Long.compare(Long.parseLong(left), Long.parseLong(right));
    }
    return left.compareTo(right);
  }
}
