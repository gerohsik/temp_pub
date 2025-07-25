import com.opencsv.CSVReader;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Matcher {

    private static final double THRESHOLD = 0.88;   // adjust to be stricter/looser
    private static final JaroWinklerSimilarity JW = new JaroWinklerSimilarity();

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.err.println("Usage: java Matcher <fileA.csv> <fileB.csv> <output.csv>");
            System.exit(1);
        }

        String fileA = args[0];
        String fileB = args[1];
        String out   = args[2];

        List<String> namesA = readProjectNames(fileA);
        List<String> namesB = readProjectNames(fileB);

        List<Match> matches = findMatches(namesA, namesB);

        writeOutput(out, matches);

        System.out.printf("✓ Finished! %d matches written to %s%n", matches.size(), out);
    }

    /** Reads the first column of a CSV file into a list (adapt if your schema differs). */
    private static List<String> readProjectNames(String csvPath) throws IOException {
        try (CSVReader r = new CSVReader(new FileReader(csvPath))) {
            return r.readAll().stream()
                    .skip(1)                    // skip header row – comment out if none
                    .map(row -> row[0])         // first column only
                    .map(Matcher::normalise)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    /** Lowercase, trim, replace punctuation with space, collapse multiple spaces, etc. */
    private static String normalise(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT)
                      .replaceAll("[\\p{Punct}]+", " ") // punctuation → space
                      .replaceAll("\\s+", " ")          // collapse whitespace
                      .trim();
        return s;
    }

    /** Performs all-against-all comparison and keeps those above threshold. */
    private static List<Match> findMatches(List<String> namesA, List<String> namesB) {
        List<Match> results = new ArrayList<>();
        for (String a : namesA) {
            double bestScore = 0.0;
            String bestB = null;

            for (String b : namesB) {
                double score = JW.apply(a, b);
                if (score > bestScore) {
                    bestScore = score;
                    bestB = b;
                }
            }

            if (bestScore >= THRESHOLD && bestB != null) {
                results.add(new Match(a, bestB, bestScore));
            }
        }
        return results;
    }

    /** Writes matches to CSV: A-name, B-name, similarity (0-1). */
    private static void writeOutput(String outPath, List<Match> matches) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(Paths.get(outPath))) {
            w.write("FileA_Project,FileB_Project,Similarity");
            w.newLine();
            for (Match m : matches) {
                w.write(String.format("\"%s\",\"%s\",%.3f%n", m.a, m.b, m.score));
            }
        }
    }

    /** Simple value object. */
    private record Match(String a, String b, double score) {}
}
