package com.seventhray.contracts.service;

import com.seventhray.contracts.dto.AnswerResponse;
import com.seventhray.contracts.dto.AnswerResponse.Passage;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QaService {

    private static final int MAX_PASSAGES = 3;
    private static final int MAX_ANSWER_CHARS = 700;
    private static final double K1 = 1.5, B = 0.75;

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "by", "with", "at", "from",
            "is", "are", "was", "were", "be", "been", "being", "am", "do", "does", "did", "has", "have", "had",
            "what", "which", "who", "whom", "whose", "when", "where", "why", "how", "can", "could", "should",
            "would", "will", "shall", "may", "might", "must", "this", "that", "these", "those", "it", "its",
            "there", "their", "they", "them", "we", "our", "us", "you", "your", "i", "me", "my", "any", "all",
            "about", "as", "if", "than", "then", "so", "not", "no", "tell", "please", "give", "say", "says",
            "mentioned", "contract", "agreement", "document");

    private static final String[] SUFFIXES = {
            "ments", "ment", "ions", "ion", "ings", "ing", "ies", "ed", "es", "s"};

    private static final Map<String, List<String>> SYNONYMS = buildSynonyms(Map.ofEntries(
            Map.entry("long", List.of("term", "duration", "begin", "continue", "effective", "month", "year")),
            Map.entry("last", List.of("term", "duration", "begin", "continue", "effective", "month", "year")),
            Map.entry("duration", List.of("term", "begin", "continue", "month", "year")),
            Map.entry("period", List.of("term", "begin", "continue", "month", "year")),
            Map.entry("expire", List.of("term", "end", "expiry")),
            Map.entry("cancel", List.of("terminate", "termination", "notice")),
            Map.entry("end", List.of("terminate", "termination")),
            Map.entry("fee", List.of("payment", "invoice", "amount")),
            Map.entry("cost", List.of("payment", "fee", "amount")),
            Map.entry("paid", List.of("payment", "invoice", "pay")),
            Map.entry("law", List.of("governing", "jurisdiction", "court"))));

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?;])\\s+(?=[A-Z0-9(\"'])");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    public AnswerResponse answer(String question, String contractText) {
        List<String> queryTerms = expand(tokenize(question));
        if (contractText == null || contractText.isBlank()) {
            return new AnswerResponse(question, false,
                    "This contract has no readable text to search.", List.of());
        }
        if (queryTerms.isEmpty()) {
            return new AnswerResponse(question, false,
                    "Please ask a more specific question, for example \"What is the payment term?\"",
                    List.of());
        }

        List<String> passages = splitIntoPassages(contractText);
        List<List<String>> tokens = passages.stream().map(this::tokenize).toList();

        int n = passages.size();
        double avgLen = tokens.stream().mapToInt(List::size).average().orElse(1);
        Map<String, Integer> df = new HashMap<>();
        for (List<String> t : tokens) {
            for (String term : new HashSet<>(t)) df.merge(term, 1, Integer::sum);
        }

        double maxPossible = 0;
        for (String q : queryTerms) maxPossible += idf(n, df.getOrDefault(q, 0)) * (K1 + 1);

        List<Passage> scored = new ArrayList<>();
        List<double[]> raw = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Long> tf = tokens.get(i).stream()
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            double score = 0;
            for (String q : queryTerms) {
                long f = tf.getOrDefault(q, 0L);
                if (f == 0) continue;
                double len = tokens.get(i).size();
                score += idf(n, df.get(q)) * (f * (K1 + 1)) / (f + K1 * (1 - B + B * len / avgLen));
            }
            if (score > 0) raw.add(new double[]{score, i});
        }
        raw.sort((a, b) -> Double.compare(b[0], a[0]));

        int rank = 1;
        for (double[] r : raw) {
            if (rank > MAX_PASSAGES) break;
            double rel = maxPossible == 0 ? 0 : Math.min(1.0, r[0] / maxPossible);
            scored.add(new Passage(rank++, Math.round(rel * 100.0) / 100.0, passages.get((int) r[1])));
        }

        if (scored.isEmpty()) {
            return new AnswerResponse(question, false,
                    "I couldn't find anything about that in this contract.", List.of());
        }
        String answer = focus(scored.get(0).text(), queryTerms);
        return new AnswerResponse(question, true, answer, scored);
    }

    List<String> splitIntoPassages(String text) {
        List<String> out = new ArrayList<>();
        String pendingHeading = "";
        for (String para : text.split("\\n\\s*\\n")) {
            String p = para.replace('\n', ' ').trim();
            if (p.isEmpty()) continue;
            if (p.length() < 80 && !p.endsWith(".") && !p.endsWith(";")) {
                pendingHeading = pendingHeading.isEmpty() ? p : pendingHeading + " " + p;
                continue;
            }
            String full = pendingHeading.isEmpty() ? p : pendingHeading + ": " + p;
            pendingHeading = "";
            if (full.length() <= 900) {
                out.add(full);
            } else {
                String[] sentences = SENTENCE_SPLIT.split(full);
                StringBuilder cur = new StringBuilder();
                int count = 0;
                for (String s : sentences) {
                    cur.append(s).append(' ');
                    if (++count == 3 || cur.length() > 600) {
                        out.add(cur.toString().trim());
                        cur.setLength(0);
                        count = 0;
                    }
                }
                if (cur.length() > 0) out.add(cur.toString().trim());
            }
        }
        if (!pendingHeading.isEmpty()) out.add(pendingHeading);
        return out;
    }

    List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        for (String w : NON_WORD.split(s.toLowerCase(Locale.ROOT))) {
            if (w.isEmpty() || STOP.contains(w)) continue;
            out.add(stem(w));
        }
        return out;
    }

    private List<String> expand(List<String> terms) {
        Set<String> out = new LinkedHashSet<>(terms);
        for (String t : terms) out.addAll(SYNONYMS.getOrDefault(t, List.of()));
        return List.copyOf(out);
    }

    private static Map<String, List<String>> buildSynonyms(Map<String, List<String>> raw) {
        Map<String, List<String>> m = new HashMap<>();
        raw.forEach((k, v) -> m.put(stem(k), v.stream().map(QaService::stem).toList()));
        return m;
    }

    static String stem(String w) {
        for (String suf : SUFFIXES) {
            if (w.endsWith(suf) && w.length() - suf.length() >= 4) {
                w = w.substring(0, w.length() - suf.length());
                if (suf.equals("ies")) w = w + "y";
                break;
            }
        }
        if (w.length() > 4 && w.endsWith("e")) w = w.substring(0, w.length() - 1);
        return w;
    }

    private static double idf(int n, int df) {
        return Math.log(1 + (n - df + 0.5) / (df + 0.5));
    }

    private String focus(String passage, List<String> queryTerms) {
        String[] sentences = SENTENCE_SPLIT.split(passage);
        String result;
        if (sentences.length <= 2 || passage.length() <= 500) {
            result = passage;
        } else {
            List<String> hits = new ArrayList<>();
            for (String s : sentences) {
                Set<String> t = new HashSet<>(tokenize(s));
                if (queryTerms.stream().anyMatch(t::contains)) hits.add(s.trim());
            }
            result = hits.isEmpty() ? passage : String.join(" ", hits);
        }
        if (result.length() > MAX_ANSWER_CHARS) result = result.substring(0, MAX_ANSWER_CHARS).trim() + "...";
        return result;
    }
}
