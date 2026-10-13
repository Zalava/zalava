package org.zalava.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minimal one-page digital PDF test data; no production parsing or PDF library is introduced. */
public final class DigitalPdfFixture {
  private DigitalPdfFixture() {}

  public static byte[] withText(String text) {
    if (!text.matches("[A-Za-z0-9 .-]+"))
      throw new IllegalArgumentException("Fixture text must be plain ASCII");
    String stream = "BT /F1 16 Tf 72 720 Td (" + text + ") Tj ET\n";
    List<String> objects =
        List.of(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "endstream");
    StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
    var offsets = new ArrayList<Integer>();
    for (int i = 0; i < objects.size(); i++) {
      offsets.add(pdf.length());
      pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
    }
    int xref = pdf.length();
    pdf.append("xref\n0 6\n0000000000 65535 f \n");
    offsets.forEach(offset -> pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset)));
    pdf.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
    return pdf.toString().getBytes(StandardCharsets.US_ASCII);
  }
}
