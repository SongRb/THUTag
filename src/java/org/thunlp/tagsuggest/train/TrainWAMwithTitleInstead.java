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
    protected void createTrainData(String input, File modelDir, Lexicon wordLex, Lexicon tagLex, double scoreLimit) throws IOException {
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

            writeResultLines(titleWords, wordLex, out);
            writeResultLines(titleWords, wordLex, outTag);

            Vector<Double> wordTfidf = new Vector<>();
            Vector<String> wordList = new Vector<>();

            Counter<String> termFreq = new Counter<>();
            for (String word : titleWords) {
                termFreq.inc(word, 1);
            }

            double normalize;
            normalize = getTotalTfidf(wordLex, titleWords, termFreq, wordTfidf, wordList, true, true);

            Vector<Double> wordProb = new Vector<>();
            for (int i = 0; i < wordTfidf.size(); i++) {
                wordProb.add(wordTfidf.elementAt(i) / normalize);
            }


            for (int i = 1; i < sentences.length; i++) {
                Counter<String> contentTf = new Counter<>();
                HashMap<String, Double> contentTfidf = new HashMap<>();
                double score = 0.0;
                String sentence = sentences[i];
                String[] words = ws.segment(sentence);
                for (String word : words) {
                    contentTf.inc(word, 1);
                }
                normalize = calTFIDFTimes(wordLex, contentTf, contentTfidf, words);
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
                    writeResultLines(words, wordLex, out);
                    writeResultLines(titleWords, wordLex, outTag);
                }
            }

        }

        reader.close();
        out.close();
        outTag.close();

        LOG.info("source and target are prepared!");


    }


    public static void main(String[] args) throws IOException {
    }

}