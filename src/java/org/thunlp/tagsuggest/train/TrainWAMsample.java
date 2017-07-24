package org.thunlp.tagsuggest.train;

import org.thunlp.io.RecordReader;
import org.thunlp.misc.Counter;
import org.thunlp.tagsuggest.common.KeywordPost;
import org.thunlp.tagsuggest.common.TrainWAMBase;
import org.thunlp.text.Lexicon;

import java.io.*;
import java.util.Random;
import java.util.Vector;

public class TrainWAMsample extends TrainWAMBase {
    protected void createTrainData(String input, File modelDir, Lexicon wordLex, Lexicon tagLex, double scoreLimit) throws IOException {

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath() + "/book"),
                "UTF-8"));

        BufferedWriter outTag = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath()
                        + "/bookTag"), "UTF-8"));
        RecordReader reader = new RecordReader(input);

        Random random = new Random();
        while (reader.next()) {
            KeywordPost p = J.fromJson(reader.value(), KeywordPost.class);
            if (fold.length() > 0 && p.getExtras().equals(fold)) {
                continue;
            }

            String[] words = extractor.extractKeyword(p, true, true, true);
            if (words.length <= 0) {
                continue;
            }

            String[] tags = extractor.extractKeyword(p, true, false, false);
            if (tags.length <= 0) {
                continue;
            }

            int wordnum = (words.length > 100) ? 100 : words.length;

            // sample the words
            Vector<Double> wordTfidf = new Vector<>();
            Vector<String> wordList = new Vector<>();
            Counter<String> termFreq = new Counter<>();
            for (String word : words) {
                termFreq.inc(word, 1);
            }

            double totalTfidf = getTotalTfidf(wordLex, words, termFreq, wordTfidf, wordList, false, false);
            Vector<Double> wordProb = new Vector<>();
            for (int i = 0; i < wordTfidf.size(); i++) {
                wordProb.add(wordTfidf.elementAt(i) / totalTfidf);
            }

            writeRandomResultLines(out, random, wordnum, wordList, wordProb);

            // sample the tags
            Vector<Double> tagTfidf = new Vector<>();
            Vector<String> tagList = new Vector<>();
            Counter<String> tagTermFreq = new Counter<>();
            for (String tag : tags) {
                tagTermFreq.inc(tag, 1);
            }

            totalTfidf = getTotalTfidf(wordLex, tags, tagTermFreq, tagTfidf, tagList, false, false);
            Vector<Double> tagProb = new Vector<>();
            for (int i = 0; i < tagTfidf.size(); i++) {
                tagProb.add(tagTfidf.elementAt(i) / totalTfidf);
            }

            writeRandomResultLines(outTag, random, wordnum, tagList, tagProb);
        }

        reader.close();
        out.close();
        outTag.close();
    }

    public static void main(String[] args) throws IOException {
        // new TrainSMT().buildProTable("/home/cxx/smt/sample/train.dat", new
        // File("/home/cxx/smt/sample"));

    }

}