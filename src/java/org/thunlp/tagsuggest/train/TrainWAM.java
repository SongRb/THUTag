package org.thunlp.tagsuggest.train;


import org.thunlp.io.RecordReader;
import org.thunlp.misc.Counter;
import org.thunlp.tagsuggest.common.ConfigIO;
import org.thunlp.tagsuggest.common.KeywordPost;
import org.thunlp.tagsuggest.common.TrainWAMBase;
import org.thunlp.text.Lexicon;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;


public class TrainWAM extends TrainWAMBase {
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

            // Operation on title
            String title = p.getTitle();
            String[] titleWords = ws.segment(title);

            // Write to file if words in title already in content lexicon
            writeResultLines(titleWords, wordLex, out);
            writeResultLines(titleWords, wordLex, outTag);

            // Calculate word frequency in title
            Counter<String> termFreq = new Counter<>();
            for (String word : titleWords) {
                termFreq.inc(word, 1);
            }

            Vector<Double> wordTfidf = new Vector<>();
            Vector<String> wordList = new Vector<>();

            double normalize = getTotalTfidf(wordLex, titleWords.length, termFreq, wordTfidf, wordList, true, true);

            // Normalization
            // word in title and whole corpus
            Vector<Double> wordProb = new Vector<>();
            for (int i = 0; i < wordTfidf.size(); i++) {
                wordProb.add(wordTfidf.elementAt(i) / normalize);
            }

            // Operation on article content
            String content = p.getSummary() + p.getContent();
            content = content.replaceAll("\n", "");
            String[] sentenceList = content.split("[。！]");

            for (String sentence : sentenceList) {
                // TODO Find out whether sentenceList or a single sentence should be used
                if (sentenceList.length <= 2) {
                    continue;
                }

                // Word freq in one sentence
                // Calculate the importance of a sentence
                // to decide whether associate it with title as candidate translation pair or not
                String[] words = ws.segment(sentence);
                Counter<String> contentTf = new Counter<>();
                for (String word : words) {
                    if (contentTf.get(word) == 0) {
                        contentTf.inc(word, 1);
                    } else {
                        contentTf.inc(word, 2);
                    }
                }

                // TF is word frequency in one sentence
                // IDF is word frequency among whole corpus
                double score = 0.0;
                HashMap<String, Double> contentTfidf = new HashMap<>();
                normalize = calTFIDFTimes(wordLex, words.length, contentTf, contentTfidf);

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
        TrainWAM Test = new TrainWAM();
        Test.config = ConfigIO.configFromString("num_tags=10;norm=all_log;isSample=true;model=/home/meepo/test/sample/book.model;size=70000;minwordfreq=10;mintagfreq=10;selfTrans=0.2;commonLimit=2");
        Test.buildProTable("/home/meepo/test/sample/post.dat", new
                File("/home/meepo/test/sample"));
    }

}