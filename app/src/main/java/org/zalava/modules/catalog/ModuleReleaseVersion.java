package org.zalava.modules.catalog;

import java.math.BigInteger;

/**
 * Numeric core comparison with SemVer prerelease precedence; missing core segments count as zero.
 */
public final class ModuleReleaseVersion {

  private ModuleReleaseVersion() {}

  public static int compare(String left, String right) {
    String[] leftVersion = left.split("\\+", 2)[0].split("-", 2);
    String[] rightVersion = right.split("\\+", 2)[0].split("-", 2);
    String[] leftParts = leftVersion[0].split("\\.", -1);
    String[] rightParts = rightVersion[0].split("\\.", -1);
    int length = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < length; index++) {
      String leftPart = index < leftParts.length ? leftParts[index] : "0";
      String rightPart = index < rightParts.length ? rightParts[index] : "0";
      int comparison = comparePart(leftPart, rightPart);
      if (comparison != 0) {
        return comparison;
      }
    }
    if (leftVersion.length == 1 || rightVersion.length == 1) {
      return Integer.compare(rightVersion.length, leftVersion.length);
    }
    String[] leftPrerelease = leftVersion[1].split("\\.", -1);
    String[] rightPrerelease = rightVersion[1].split("\\.", -1);
    for (int index = 0; index < Math.min(leftPrerelease.length, rightPrerelease.length); index++) {
      String a = leftPrerelease[index];
      String b = rightPrerelease[index];
      boolean aNumeric = a.matches("\\d+");
      boolean bNumeric = b.matches("\\d+");
      int comparison = aNumeric != bNumeric ? (aNumeric ? -1 : 1) : comparePart(a, b);
      if (comparison != 0) return comparison;
    }
    return Integer.compare(leftPrerelease.length, rightPrerelease.length);
  }

  public static boolean isNewer(String candidate, String current) {
    return compare(candidate, current) > 0;
  }

  private static int comparePart(String left, String right) {
    if (left.equals(right)) {
      return 0;
    }
    if (left.matches("\\d+") && right.matches("\\d+")) {
      return new BigInteger(left).compareTo(new BigInteger(right));
    }
    return left.compareTo(right);
  }
}
