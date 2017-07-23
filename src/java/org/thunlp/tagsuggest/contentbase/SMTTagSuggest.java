package org.thunlp.tagsuggest.contentbase;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.crypto.spec.IvParameterSpec;

import org.thunlp.io.JsonUtil;
import org.thunlp.io.RecordReader;
import org.thunlp.misc.Counter;
import org.thunlp.misc.WeightString;
import org.thunlp.tagsuggest.common.ConfigIO;
import org.thunlp.tagsuggest.common.DoubanPost;
import org.thunlp.tagsuggest.common.Post;
import org.thunlp.tagsuggest.common.KeywordPost;
import org.thunlp.tagsuggest.common.TagSuggest;
import org.thunlp.tagsuggest.common.Filter;
import org.thunlp.tagsuggest.common.WordFeatureExtractor;
import org.thunlp.text.Lexicon;

public class SMTTagSuggest implements TagSuggest {
    private static Logger LOG = Logger.getAnonymousLogger();

    private WordFeatureExtractor extractor = null;
    private Lexicon wordLex = null;

    private Properties config = new Properties();
    private static List<WeightString> EMPTY_SUGGESTION = new LinkedList<>();

    private HashMap<Integer, String> bookMap = new HashMap<>();
    private HashMap<String, Integer> idMap = new HashMap<>();
    private HashMap<Integer, String> bookTagMap = new HashMap<>();

    private HashMap<String, Integer> df = new HashMap<>();

    private HashMap<Integer, HashMap<Integer, Double>> proTable = new HashMap<>();
    private HashMap<Integer, HashMap<Integer, Double>> inverseTable = new HashMap<>();
    private double para = 0.5;

    @Override
    public void feedback(Post p) {
        // TODO Auto-generated method stub
    }

    @Override
    public void loadModel(String modelPath) throws IOException {
        // TODO Auto-generated method stub

        para = Double.parseDouble(config.getProperty("para", "0.5"));

        // Read book.vcb
        String bookFile = modelPath + File.separator + "book.vcb";
        BufferedReader book = new BufferedReader(new InputStreamReader(
                new FileInputStream(bookFile), "UTF-8"));
        String bookLine;
        while ((bookLine = book.readLine()) != null) {
            String[] datas = bookLine.split(" ");
            bookMap.put(Integer.parseInt(datas[0]), datas[1]);
            idMap.put(datas[1], Integer.parseInt(datas[0]));
            df.put(datas[1], Integer.parseInt(datas[2]));
        }
        book.close();

        // Read bookTag.vcb
        String tagFile = modelPath + File.separator + "bookTag.vcb";
        BufferedReader bookTag = new BufferedReader(new InputStreamReader(
                new FileInputStream(tagFile), "UTF-8"));
        String tagLine;
        while ((tagLine = bookTag.readLine()) != null) {
            String[] datas = tagLine.split(" ");
            bookTagMap.put(Integer.parseInt(datas[0]), datas[1]);
        }
        bookTag.close();

        // Read *.t1.5
        File dir = new File(modelPath);

        Filter filter = new Filter("t1.5");
        String files_tmp[] = dir.list(filter);

        Vector<String> files = new Vector<String>();
        files.addAll(Arrays.asList(files_tmp));

        Collections.sort(files);

        int files_len = files.size();

        if (files_len == 0) {
            System.out.println("*.t1.5 not exist");
            LOG.info("*.t1.5 not exist");
        } else {
            String word2Tag;
            String tag2Word;

            word2Tag = files.get(files_len - 2);
            tag2Word = files.get(files_len - 1);
            LOG.info(word2Tag);
            LOG.info(tag2Word);
            BufferedReader pro = new BufferedReader(new InputStreamReader(
                    new FileInputStream(modelPath + File.separator + word2Tag),
                    "UTF-8"));
            String proLine;
            while ((proLine = pro.readLine()) != null) {
                String[] data = proLine.split(" ");

                if (data.length != 3)
                    continue;

                int first = Integer.parseInt(data[0]);
                int second = Integer.parseInt(data[1]);
                double probability = Double.parseDouble(data[2]);
                if (first == 0 || second == 0) {
                    continue;
                }
                if (proTable.containsKey(first)) {
                    proTable.get(first).put(second, probability);
                } else {
                    HashMap<Integer, Double> tmp = new HashMap<Integer, Double>();
                    tmp.put(second, probability);
                    proTable.put(first, tmp);
                }
            }
            pro.close();
        }
        LOG.info(Integer.toString(proTable.size()));

        // Read ti.fianl
        Filter filter2 = new Filter("ti.final");
        String files2_tmp[] = dir.list(filter2);

        Vector<String> files2 = new Vector<String>();
        for (String e : files2_tmp) {
            if (!e.contains("actual"))
                files2.add(e);
        }

        Collections.sort(files2);


        int files2_len = files2.size();

        if (files2_len == 0) {
            System.out.println("*.ti.final not exist");
            LOG.info("*.ti.final not exist");
        } else {
            String word2Tag;
            String tag2Word;

            word2Tag = files2.get(files2_len - 2);
            tag2Word = files2.get(files2_len - 1);
            LOG.info(word2Tag);
            LOG.info(tag2Word);
            BufferedReader inverse = new BufferedReader(
                    new InputStreamReader(new FileInputStream(modelPath + File.separator + tag2Word), "UTF-8"));
            String line;
            while ((line = inverse.readLine()) != null) {
                String[] data = line.split(" ");
                if (data.length != 3) continue;

                int first = Integer.parseInt(data[0]);
                int second = Integer.parseInt(data[1]);
                double probability = Double.parseDouble(data[2]);
                if (first == 0 || second == 0 || (probability < 0.01)) {
                    continue;
                }
                if (inverseTable.containsKey(first)) {
                    inverseTable.get(first).put(second, probability);
                } else {
                    HashMap<Integer, Double> tmp = new HashMap<Integer, Double>();
                    tmp.put(second, probability);
                    inverseTable.put(first, tmp);
                }
            }
            inverse.close();
        }

        // read wordlex
        wordLex = new Lexicon();
        String input = modelPath + "/wordlex";
        File cachedWordLexFile = new File(input);
        if (cachedWordLexFile.exists()) {
            LOG.info("Use cached lexicons");
            wordLex.loadFromFile(cachedWordLexFile);
        }
    }

    @Override
    public void setConfig(Properties config) {
        // TODO Auto-generated method stub
        this.config = config;
        extractor = new WordFeatureExtractor(config);
    }

    @Override
    public List<WeightString> suggest(Post p, StringBuilder explain) {
        // TODO Auto-generated method stub
        HashMap<Integer, Double> wordTfidf = new HashMap<>();
        //	HashMap<Integer, HashMap<Integer, Double>> LDA = new HashMap<Integer, HashMap<Integer,Double>>();

        String[] words = extractor.extract(p);
        Counter<String> termFreq = new Counter<>();
        // calculate the word tfidf
        for (String word : words) {
            if (idMap.containsKey(word))
                termFreq.inc(word, 1);
        }

        Iterator<Entry<String, Long>> iter = termFreq.iterator();
        HashMap<Integer, Double> proMap = new HashMap<>();
        while (iter.hasNext()) {
            Entry<String, Long> e = iter.next();
            String word = e.getKey();

            double tf = (double) e.getValue() / (double) words.length;
            double idf = 0.0;

            if (wordLex.getWord(word) != null) {
                idf = Math.log((double) wordLex.getNumDocs()
                        / (double) wordLex.getWord(word).getDocumentFrequency());
            } else {
                continue;
            }
            double tfidf = tf * idf;
            int id = idMap.get(word);
            if (proTable.containsKey(id)) {
                wordTfidf.put(id, tfidf);

                HashMap<Integer, Double> tmpMap = new HashMap<>();
                tmpMap.putAll(proTable.get(id));

                // to suggest the tags
                for (Entry<Integer, Double> ee : proTable.get(id).entrySet()) {
                    int tagId = ee.getKey();
                    if (inverseTable.containsKey(id) && inverseTable.get(id).containsKey(tagId)) {
//						double pro = ee.getValue() * inverseTable.get(id).get(tagId);
                        double pro = 1.0 / (para / ee.getValue() + (1.0 - para) / inverseTable.get(id).get(tagId));

                        if (proMap.containsKey(tagId)) {
                            double tmp = proMap.get(tagId);
                            tmp += tfidf * pro;
                            proMap.remove(tagId);
                            proMap.put(tagId, tmp);
                        } else {
                            proMap.put(tagId, tfidf * pro);
                        }
                    }
                }

            }
        }

        // ranking
        List<WeightString> tags = new ArrayList<>();
        for (Entry<Integer, Double> e : proMap.entrySet()) {
            tags.add(new WeightString(bookTagMap.get(e.getKey()), e.getValue()));
        }
        tags.sort((o1, o2) -> Double.compare(o2.weight, o1.weight));

        return tags;
    }

    public static void main(String[] args) throws IOException {
        SMTTagSuggest smt = new SMTTagSuggest();
        smt.setConfig(ConfigIO.configFromString("stop_wordnum_tags=10;norm=all_log;size=70000;dataType=KeywordPost;minwordfreq=10;mintagfreq=10"));
        smt.loadModel("/mnt/hgfs/D/Administrator/Documents/thu-tag-workspace/sample2");
//        RecordReader reader = new RecordReader("/home/meepo/test/check.post");
//        BufferedWriter outTag = new BufferedWriter(new OutputStreamWriter(
//                new FileOutputStream("/home/meepo/test/ansmy.txt"), "UTF-8"));
//        JsonUtil J = new JsonUtil();
//        List<WeightString> tags;
//        while (reader.next()) {
//            KeywordPost p = J.fromJson(reader.value(), KeywordPost.class);
//            tags = smt.suggest(p, null);
//            int counter = 0;
//            for (WeightString s : tags) {
//                outTag.write(s.toString() + " ");
//                counter++;
//                if (counter == 10)
//                    break;
//            }
//            outTag.newLine();
//            outTag.flush();
//            break;
//        }
//        reader.close();
//        outTag.close();
    }

}
