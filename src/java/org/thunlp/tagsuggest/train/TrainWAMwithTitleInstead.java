package org.thunlp.tagsuggest.train;


import org.thunlp.io.RecordReader;
import org.thunlp.misc.Counter;
import org.thunlp.tagsuggest.common.KeywordPost;
import org.thunlp.tagsuggest.common.TrainWAMBase;
import org.thunlp.text.Lexicon;

import java.io.*;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Vector;


public class TrainWAMwithTitleInstead extends TrainWAMBase {
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

            String content = p.getSummary() + p.getContent();
            content = content.replaceAll("\n", "");
            String[] sentences = content.split("[。！]");

            if (sentences.length < 1) {
                continue;
            }

            // use the first sentence to replace the title
            String[] titleWords = ws.segment(sentences[0]);

            for (int i = 0; i < titleWords.length; i++) {
                if (localWordLex.getWord(titleWords[i]) != null) {
                    if (i == 0) {
                        out.write(titleWords[i]);
                        outTag.write(titleWords[i]);
                    } else {
                        out.write(" " + titleWords[i]);
                        outTag.write(" " + titleWords[i]);
                    }
                }
            }
            out.newLine();
            out.flush();
            outTag.newLine();
            outTag.flush();

            Vector<Double> wordTfidf = new Vector<Double>();
            Vector<String> wordList = new Vector<String>();
            double normalize = 0.0;
            Counter<String> termFreq = new Counter<String>();
            for (String word : titleWords) {
                termFreq.inc(word, 1);
            }
            for (Entry<String, Long> e : termFreq) {
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
            Vector<Double> wordProb = new Vector<Double>();
            for (int i = 0; i < wordTfidf.size(); i++) {
                wordProb.add(wordTfidf.elementAt(i) / normalize);
            }



            for (int i = 1; i < sentences.length; i++) {
//                HashMap<String, Integer> contentTf = new HashMap<String, Integer>();
                Counter<String> contentTf = new Counter<>();
                HashMap<String, Double> contentTfidf = new HashMap<String, Double>();
                double score = 0.0;
                String sentence = sentences[i];
                String[] words = ws.segment(sentence);
                for (String word : words) {
                    contentTf.inc(word, 1);
//                    if (contentTf.containsKey(word)) {
//                        int tmp = contentTf.get(word);
//                        contentTf.put(word, tmp + 1);
//                    } else {
//                        contentTf.put(word, 1);
//                    }
                }
                normalize = getNormalize(localWordLex, contentTf, contentTfidf, words);
                for (Entry<String, Double> e : contentTfidf.entrySet()) {
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

    private double getNormalize(Lexicon localWordLex, Counter<String> contentTf, HashMap<String, Double> contentTfidf, String[] words) {
        double normalize;
        normalize = 0.0;
        for (Entry<String, Long> e : contentTf) {
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
        return normalize;
    }

    public static void main(String[] args) throws IOException {
    }

}