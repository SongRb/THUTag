package org.thunlp.tagsuggest.common;

import org.thunlp.io.JsonUtil;
import org.thunlp.io.RecordReader;
import org.thunlp.language.chinese.ForwardMaxWordSegment;
import org.thunlp.language.chinese.WordSegment;
import org.thunlp.misc.Counter;
import org.thunlp.misc.Flags;
import org.thunlp.text.Lexicon;
import org.thunlp.tool.GenericTool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class TrainWAMBase implements GenericTool, ModelTrainer {
    protected static Logger LOG = Logger.getAnonymousLogger();
    protected Properties config = null;
    protected String fold = "";
    protected String giza_path = null;

    protected JsonUtil J = new JsonUtil();
    protected WordFeatureExtractor extractor = null;
    protected TagFilter tagFilter = null;
    protected WordSegment ws = null;

    @Override
    public void run(String[] args) throws Exception {
        // TODO Auto-generated method stub
        Flags flags = new Flags();
        flags.add("input");
        flags.add("output");
        flags.add("config");
        flags.parseAndCheck(args);

        Properties config = ConfigIO
                .configFromString(flags.getString("config"));
        train(flags.getString("input"), flags.getString("output"), config);
    }

    @Override
    public void train(String inputPath, String modelPath, Properties config)
            throws IOException {
        // TODO Auto-generated method stub
        this.config = config;
        this.fold = config.getProperty("fold", "");

        giza_path = config.getProperty("giza_path", RtuMain.getProjectPath());
        LOG.info("giza_path:" + giza_path);

        buildProTable(inputPath, new File(modelPath));
    }

    public void buildProTable(String input, File modelDir) {


        try {
            if (!modelDir.exists()) {
                modelDir.mkdir();
            }

            createExtractor(input);

            // the first time : create wordlex and taglex to store the tf and df
            // information
            Lexicon localWordLex = new Lexicon();
            Lexicon localTagLex = new Lexicon();

            createLocalLex(localWordLex, localTagLex, modelDir, input);

            double scoreLimit = Double.parseDouble(config.getProperty("scoreLimit", "0.1"));

            // the second time :
            createTrainData(input, modelDir, localWordLex, localTagLex, scoreLimit);

            // training
            gizappTrain(modelDir);


        } catch (Exception e) {
            LOG.info("Error exec!");
        }


    }

    protected void createExtractor(String input) throws IOException {
        ws = new ForwardMaxWordSegment();
        Lexicon wordLex = new Lexicon();
        Lexicon tagLex = new Lexicon();
        WordFeatureExtractor.buildLexicons(input, wordLex, tagLex, config);
        extractor = new WordFeatureExtractor(config);
        extractor.setWordLexicon(wordLex);
        extractor.setTagLexicon(tagLex);
        tagFilter = new TagFilter(config, tagLex);
    }

    protected void createTrainData(String input, File modelDir, Lexicon wordLex, Lexicon tagLex, double scoreLimit) throws IOException {
    }

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


                Set<String> filtered = new HashSet<>();
                tagFilter.filterWithNorm(p.getTags(), filtered);
                if (filtered == null) {
                    continue;
                }
                wordLex.addDocument(features);
                tagLex.addDocument(filtered
                        .toArray(new String[filtered.size()]));

                if (reader.numRead() % 1000 == 0)
                    LOG.info(modelDir.getAbsolutePath()
                            + " building lexicons: " + reader.numRead());
            }
            reader.close();
            wordLex.saveToFile(wordLexFile);
            tagLex.saveToFile(tagLexFile);
        }


    }

    protected void gizappTrain(File modelDir) throws IOException, InterruptedException {
        Runtime rn = Runtime.getRuntime();
        Process p;
        p = rn
                .exec(giza_path + File.separator + "mkcls -c80 -pbook -Vbook.vcb.classes opt",
                        null, modelDir);
        p.waitFor();
        p = rn
                .exec(giza_path + File.separator + "mkcls -c80 -pbookTag -VbookTag.vcb.classes opt",
                        null, modelDir);
        p.waitFor();
        LOG.info("mkcls ok!");
        p = rn
                .exec(giza_path + File.separator + "plain2snt.out bookTag book",
                        null, modelDir);
        p.waitFor();
        LOG.info("plain2snt ok!");

        // from word to tag
        p = rn.exec(giza_path + File.separator + "GIZA++ -S book.vcb -T bookTag.vcb -C book_bookTag.snt  -m1 5 -m2 0 -mh 0 -m3 0 -m4 0 -model1dumpfrequency 1"
                , null, modelDir);
        org.thunlp.tagsuggest.common.StreamGobbler errorGobbler = new org.thunlp.tagsuggest.common.StreamGobbler(p.getErrorStream(),
                "Error", LOG);
        org.thunlp.tagsuggest.common.StreamGobbler outputGobbler = new org.thunlp.tagsuggest.common.StreamGobbler(p.getInputStream(),
                "Output", LOG);
        errorGobbler.start();
        outputGobbler.start();
        p.waitFor();
        LOG.info("GIZA++ word to tag Ok!");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        // from tag to word
        p = rn.exec(giza_path + File.separator + "GIZA++ -S bookTag.vcb -T book.vcb -C bookTag_book.snt -m1 5 -m2 0 -mh 0 -m3 0 -m4 0  -model1dumpfrequency 1",
                null, modelDir);
        errorGobbler = new org.thunlp.tagsuggest.common.StreamGobbler(p.getErrorStream(), "Error", LOG);
        outputGobbler = new org.thunlp.tagsuggest.common.StreamGobbler(p.getInputStream(), "Output", LOG);
        errorGobbler.start();
        outputGobbler.start();
        p.waitFor();
        LOG.info("GIZA++ tag to word Ok!");
    }

    protected void writeResultLine(String[] wordList, Lexicon lex, BufferedWriter bufferedWriter) throws IOException {
        for (int j = 0; j < wordList.length; j++) {
            if (lex.getWord(wordList[j]) != null) {
                if (j == 0) {
                    bufferedWriter.write(wordList[j]);
                } else {
                    bufferedWriter.write(" " + wordList[j]);
                }
            }
        }
    }

    protected void writeResultLines(String[] wordList, Lexicon lex, BufferedWriter bufferedWriter) throws IOException {
        writeResultLine(wordList, lex, bufferedWriter);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    protected void writeRandomResultLines(BufferedWriter bufferedWriter, Random random, int wordnum, Vector<String> tagList, Vector<Double> tagProb) throws IOException {
        writeRandomResultLine(bufferedWriter, random, wordnum, tagList, tagProb);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    private void writeRandomResultLine(BufferedWriter bufferedWriter, Random random, int length, Vector<String> wordList, Vector<Double> wordProb) throws IOException {
        for (int i = 0; i < length; i++) {
            double choice = random.nextDouble();
            double sum = 0.0;
            int j;
            for (j = 0; j < wordProb.size(); j++) {
                sum += wordProb.elementAt(j);
                if (sum >= choice)
                    break;
            }
            String tag = wordList.elementAt(j);
            if (i == 0) {
                bufferedWriter.write(tag);
            } else {
                bufferedWriter.write(" " + tag);
            }
        }
    }

    protected double getTotalTfidf(Lexicon localWordlex, String[] words, Counter<String> wordCounter, Vector<Double> wordTfidf, Vector<String> wordList, boolean inLex, boolean doubleNormal) {
        double totalTfidf = 0.0;
        for (Map.Entry<String, Long> e : wordCounter) {
            String word = e.getKey();
            if (inLex && localWordlex.getWord(word) == null) {
                continue;
            }

            wordList.add(word);
            double tf = ((double) e.getValue())
                    / ((double) words.length);
            double idf = Math.log(((double) localWordlex.getNumDocs())
                    / ((double) localWordlex.getWord(word)
                    .getDocumentFrequency()));
            double tfidf = tf * idf;
            wordTfidf.add(tfidf);

            if (doubleNormal) {
                totalTfidf += tfidf * tfidf;
            } else {
                totalTfidf += tfidf;
            }
        }
        return totalTfidf;
    }

    protected double calTFIDFTimes(Lexicon lex, Counter<String> wordCounter, HashMap<String, Double> wordTfidf, String[] words) {
        double normalize;
        normalize = 0.0;
        for (Map.Entry<String, Long> e : wordCounter) {
            String word = e.getKey();
            if (lex.getWord(word) == null) {
                continue;
            }
            double tf = ((double) e.getValue()) / ((double) words.length);
            double idf = Math.log(((double) lex.getNumDocs())
                    / ((double) lex.getWord(word)
                    .getDocumentFrequency()));
            double tfidf = tf * idf;
            wordTfidf.put(word, tfidf);
            normalize += tfidf * tfidf;
        }
        return normalize;
    }


    public static void main(String[] args) throws IOException {

    }

}
