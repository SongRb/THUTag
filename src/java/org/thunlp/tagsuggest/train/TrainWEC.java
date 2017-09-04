package org.thunlp.tagsuggest.train;


import org.thunlp.io.RecordReader;
import org.thunlp.tagsuggest.common.KeywordPost;
import org.thunlp.tagsuggest.common.TrainWAMBase;
import org.thunlp.text.Lexicon;

import java.io.*;


public class TrainWEC extends TrainWAMBase {

    protected void createLocalLex(Lexicon wordLex, Lexicon tagLex, File modelDir, String input) throws IOException {
        File wordLexFile = new File(modelDir.getAbsolutePath() + "/wordlex");
        File tagLexFile = new File(modelDir.getAbsolutePath() + "/taglex");

        RecordReader reader = new RecordReader(input);

        if (wordLexFile.exists() && tagLexFile.exists()) {
            LOG.info("Use cached lexicons");
            wordLex.loadFromFile(wordLexFile);
            tagLex.loadFromFile(tagLexFile);
        } else {
            while (reader.next()) {
                KeywordPost p = J.fromJson(reader.value(), KeywordPost.class);
                if (fold.length() > 0 && p.getExtras().equals(fold)) {
                    continue;
                }
                String[] features = extractor.extractKeyword(p, true, true, true);
                if (features.length <= 0) {
                    continue;
                }

                wordLex.addDocument(features);
                tagLex.addDocument(features);

                if (reader.numRead() % 1000 == 0)
                    LOG.info(modelDir.getAbsolutePath()
                            + " building lexicons: " + reader.numRead());
            }
            reader.close();
            wordLex.saveToFile(wordLexFile);
            tagLex.saveToFile(tagLexFile);
        }
    }


    protected void createTrainData(String input, File modelDir, Lexicon wordLex, Lexicon tagLex, double scoreLimit) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath() + "/book"), "UTF-8"));

        BufferedWriter outTag = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath() + "/bookTag"), "UTF-8"));
        RecordReader reader = new RecordReader(input);
        while (reader.next()) {
            KeywordPost p = J.fromJson(reader.value(), KeywordPost.class);

            if (fold.length() > 0 && p.getExtras().equals(fold)) {
                continue;
            }

            String title = p.getTitle();
            String[] titleWords = ws.segment(title);
            writeLines(out, titleWords);


            String content = p.getContent();
            String[] contentWords = ws.segment(content);
            writeLines(outTag, contentWords);
        }

        reader.close();
        out.close();
        outTag.close();
        LOG.info("source and target are prepared!");
    }

    private void writeLines(BufferedWriter bufferedWriter, String[] wordList) throws IOException {
        for (int j = 0; j < wordList.length; j++) {
            if (j == 0) {
                bufferedWriter.write(wordList[j]);
            } else {
                bufferedWriter.write(" " + wordList[j]);
            }
        }
        bufferedWriter.newLine();
        bufferedWriter.flush();


    }


}