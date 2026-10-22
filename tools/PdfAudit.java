import org.apache.pdfbox.Loader; import org.apache.pdfbox.text.PDFTextStripper; import java.io.File; import java.nio.file.*;
public class PdfAudit { public static void main(String[] a) throws Exception {
  try (var s = Files.walk(Path.of(a[0]))) { for (Path p : s.filter(f -> f.toString().toLowerCase().endsWith(".pdf")).sorted().toList()) {
    try (var d = Loader.loadPDF(p.toFile())) { String t = new PDFTextStripper().getText(d).replaceAll("\s+", "");
      boolean tagged = d.getDocumentCatalog().getMarkInfo() != null && d.getDocumentCatalog().getMarkInfo().isMarked();
      String title = d.getDocumentInformation().getTitle();
      System.out.printf("%-6s pages=%3d text/page=%6d tagged=%-5s title=%s  %s%n", t.length() / Math.max(1, d.getNumberOfPages()) < 50 ? "SCAN?" : "text", d.getNumberOfPages(), t.length() / Math.max(1, d.getNumberOfPages()), tagged, title == null ? "-" : title.substring(0, Math.min(30, title.length())), Path.of(a[0]).relativize(p)); }
    catch (Exception e) { System.out.println("ERROR " + p + " " + e); } } } } }
