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
    protected Lexicon tagLex = null;
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

        Vector<String> fileList = new Vector<>();
        String[] files = dir.list(filter);

        if (files == null) {
            System.exit(0);
        } else {
            fileList.addAll(Arrays.asList(files));
        }
        Collections.sort(fileList);

        int files_len = fileList.size();

        if (files_len == 0) {
            System.out.println("*.t1.5 not exist");
            LOG.info("*.t1.5 not exist");
        } else {
            String word2Tag;
            String tag2Word;
            int fileListLength = fileList.size();

            word2Tag = fileList.get(fileListLength - 2);
            tag2Word = fileList.get(fileListLength - 1);
            LOG.info(word2Tag);
            LOG.info(tag2Word);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(modelPath + File.separator + word2Tag), "UTF-8"));
            loadProbabilityTable(proTable, 0, bufferedReader);
        }
        LOG.info("proTable Size: " + Integer.toString(proTable.size()));

        // Read ti.final
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
            int fileListLength = files2.size();

            word2Tag = files2.get(fileListLength - 2);
            tag2Word = files2.get(fileListLength - 1);
            LOG.info(word2Tag);
            LOG.info(tag2Word);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(modelPath + File.separator + tag2Word), "UTF-8"));
            loadProbabilityTable(inverseTable, 0.01, bufferedReader);
            LOG.info("inverseTable size: " + Integer.toString(inverseTable.size()));
        }

        // read wordlex
        wordLex = new Lexicon();
        loadLexicon(modelPath, "wordlex", wordLex);

        // Read tag lexicon
        tagLex = new Lexicon();
        loadLexicon(modelPath, "taglex", tagLex);
    }

    private void loadProbabilityTable(HashMap<Integer, HashMap<Integer, Double>> table, double threshold, BufferedReader bufferedReader) throws IOException {
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            String[] data = line.split(" ");
            if (data.length != 3) continue;

            int first = Integer.parseInt(data[0]);
            int second = Integer.parseInt(data[1]);
            double probability = Double.parseDouble(data[2]);
            if (first == 0 || second == 0 || (probability < threshold)) {
                continue;
            }
            if (table.containsKey(first)) {
                table.get(first).put(second, probability);
            } else {
                HashMap<Integer, Double> tmp = new HashMap<>();
                tmp.put(second, probability);
                table.put(first, tmp);
            }
        }
        bufferedReader.close();
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
            double idf;

            if (wordLex.getWord(word) != null) {
                idf = Math.log((double) wordLex.getNumDocs()
                        / (double) wordLex.getWord(word).getDocumentFrequency());
            } else {
                continue;
            }
            double tfidf = tf * idf;
            int id = idMap.get(word);
            if (proTable.containsKey(id)) {
                // to suggest the tags
                for (Map.Entry<Integer, Double> ee : proTable.get(id).entrySet()) {
                    int tagId = ee.getKey();
                    if (inverseTable.containsKey(id) && inverseTable.get(id).containsKey(tagId)) {
                        double pro = calProbability(id, ee, tagId);

                        if (proMap.containsKey(tagId)) {
                            proMap.put(tagId, proMap.get(tagId) + tfidf * pro);
                        } else {
                            proMap.put(tagId, tfidf * pro);
                        }
                    }
                }
            }
        }

        // ranking

        return rankTags(proMap);
    }

    protected List<WeightString> rankTags(HashMap<Integer, Double> proMap) {
        List<WeightString> tags = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : proMap.entrySet()) {
            WeightString candidate = new WeightString(bookTagMap.get(e.getKey()), e.getValue());
            tags.add(candidate);
        }
        tags.sort((o1, o2) -> Double.compare(o2.weight, o1.weight));
        return tags;
    }

    protected double calProbability(int id, Map.Entry<Integer, Double> ee, int tagId) {
        return 0.0;
    }

    private void loadLexicon(String modelPath, String lexName, Lexicon lex) {
        String input = modelPath + File.separator + lexName;
        File cachedWordLexFile = new File(input);
        if (cachedWordLexFile.exists()) {
            LOG.info("Use cached lexicons");
            lex.loadFromFile(cachedWordLexFile);
            LOG.info(lexName + " Size: " + Integer.toString(lex.getSize()));
        }
    }

    @Override
    public void feedback(Post p) {

    }
}
