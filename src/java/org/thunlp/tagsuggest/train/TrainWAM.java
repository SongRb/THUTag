package org.thunlp.tagsuggest.train;


import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.thunlp.io.RecordReader;
import org.thunlp.misc.Counter;
import org.thunlp.tagsuggest.common.*;
import org.thunlp.text.Lexicon;


public class TrainWAM extends TrainWAMBase {
    protected void createTrainData(String input, File modelDir, Lexicon localWordLex, Lexicon localTagLex, double scoreLimit) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath() + "/book"),
                "UTF-8"));

        BufferedWriter outTag = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath()
                        + "/bookTag"), "UTF-8"));
        RecordReader reader = new RecordReader(input);
        while (reader.next()) {
            KeywordPost p = J.fromJson(reader.value(), KeywordPost.class);
            if (fold.length() > 0 && p.getExtras().equals(fold)) {
                continue;
            }
            String title = p.getTitle();
            String[] titleWords = ws.segment(title);

            writeResultLines(titleWords, localWordLex, out);
            writeResultLines(titleWords, localWordLex, outTag);

            Vector<Double> wordTfidf = new Vector<>();
            Vector<String> wordList = new Vector<>();
            double normalize = 0.0;
            Counter<String> termFreq = new Counter<>();
            for (String word : titleWords) {
                termFreq.inc(word, 1);
            }
            for (Map.Entry<String, Long> e : termFreq) {
                String word = e.getKey();
                if (localWordLex.getWord(word) == null) {
                    continue;
                }
                wordList.add(word);

                double tf = ((double) e.getValue())
                        / ((double) titleWords.length);
                double idf = Math.log(((double) localWordLex.getNumDocs())
                        / ((double) localWordLex.getWord(word)
                        .getDocumentFrequency()));
                double tfidf = tf * idf;
                wordTfidf.add(tfidf);
                normalize += tfidf * tfidf;
            }
            Vector<Double> wordProb = new Vector<>();
            for (int i = 0; i < wordTfidf.size(); i++) {
                wordProb.add(wordTfidf.elementAt(i) / normalize);
            }

            String content = p.getSummary() + p.getContent();
            content = content.replaceAll("\n", "");
            String[] splittedSentences = content.split("[。！]");

            for (String sentence : splittedSentences) {


                if (splittedSentences.length <= 2) {
                    continue;
                }
                double score = 0.0;
                String[] words = ws.segment(sentence);
                HashMap<String, Integer> contentTf = new HashMap<>();
                for (String word : words) {
                    if (contentTf.containsKey(word)) {
                        int tmp = contentTf.get(word) + 1;
                        contentTf.put(word, tmp + 1);
                    } else {
                        contentTf.put(word, 1);
                    }

                }

                HashMap<String, Double> contentTfidf = new HashMap<>();
                normalize = 0.0;
                for (Map.Entry<String, Integer> e : contentTf.entrySet()) {
                    String word = e.getKey();
                    if (localWordLex.getWord(word) == null) {
                        continue;
                    }
                    double tf = ((double) e.getValue()) / ((double) words.length);
                    double idf = Math.log(((double) localWordLex.getNumDocs())
                            / ((double) localWordLex.getWord(word)
                            .getDocumentFrequency()));
                    double tfidf = tf * idf;
                    contentTfidf.put(word, tfidf);
                    normalize += tfidf * tfidf;
                }
                for (Map.Entry<String, Double> e : contentTfidf.entrySet()) {
                    e.setValue(e.getValue() / normalize);
                }
                for (int j = 0; j < wordList.size(); j++) {
                    String word = wordList.get(j);
                    if (contentTfidf.containsKey(word)) {
                        score += contentTfidf.get(word) * wordProb.get(j);
                    }
                }
                if (score >= scoreLimit) {
                    writeResultLines(words, localWordLex, out);
                    writeResultLines(titleWords, localWordLex, outTag);

                }
            }

        }

        reader.close();
        out.close();
        outTag.close();
        LOG.info("source and target are prepared!");
    }


    public static void main(String[] args) throws IOException {
        TrainWAM Test = new TrainWAM();
        Test.config = ConfigIO.configFromString("num_tags=10;norm=all_log;isSample=true;model=/home/meepo/test/sample/book.model;size=70000;minwordfreq=10;mintagfreq=10;selfTrans=0.2;commonLimit=2");
        Test.buildProTable("/home/meepo/test/sample/post.dat", new
                File("/home/meepo/test/sample"));
    }

}