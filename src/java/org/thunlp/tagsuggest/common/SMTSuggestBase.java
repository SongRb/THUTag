package org.thunlp.tagsuggest.common;

import org.thunlp.misc.Counter;
import org.thunlp.misc.WeightString;
import org.thunlp.text.Lexicon;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class SMTSuggestBase implements TagSuggest {
    protected static Logger LOG = Logger.getAnonymousLogger();
    protected static List<WeightString> EMPTY_SUGGESTION = new LinkedList<>();
    protected WordFeatureExtractor extractor = null;
    protected Lexicon wordLex = null;
    protected Properties config = new Properties();
    protected HashMap<Integer, String> bookMap = new HashMap<>();
    protected HashMap<String, Integer> idMap = new HashMap<>();
    protected HashMap<Integer, String> bookTagMap = new HashMap<>();

    protected HashMap<String, Integer> df = new HashMap<>();

    protected HashMap<Integer, HashMap<Integer, Double>> proTable = new HashMap<>();
    protected HashMap<Integer, HashMap<Integer, Double>> inverseTable = new HashMap<>();
    protected double para = 0.5;


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

        Vector<String> files2 = new Vector<>();
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
                    HashMap<Integer, Double> tmp = new HashMap<>();
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

        String[] words = extractor.extract(p);
        Counter<String> termFreq = new Counter<>();
        // calculate the word tfidf
        for (String word : words) {
            if (idMap.containsKey(word))
                termFreq.inc(word, 1);
        }

        HashMap<Integer, Double> proMap = new HashMap<>();
        for (Map.Entry<String, Long> e : termFreq) {

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
                for (Map.Entry<Integer, Double> ee : proTable.get(id).entrySet()) {
                    int tagId = ee.getKey();
                    if (inverseTable.containsKey(id) && inverseTable.get(id).containsKey(tagId)) {
                        double pro = getPro(id, ee, tagId);

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
        for (Map.Entry<Integer, Double> e : proMap.entrySet()) {
            tags.add(new WeightString(bookTagMap.get(e.getKey()), e.getValue()));
        }
        tags.sort((o1, o2) -> Double.compare(o2.weight, o1.weight));

        return tags;
    }

    protected double getPro(int id, Map.Entry<Integer, Double> ee, int tagId) {
        return 0.0;
    }

    @Override
    public void feedback(Post p) {

    }
}
