package org.thunlp.tagsuggest.common;

import org.thunlp.html.HtmlReformatter;
import org.thunlp.io.JsonUtil;
import org.thunlp.io.RecordReader;
import org.thunlp.language.chinese.ForwardMaxWordSegment;
import org.thunlp.language.chinese.LangUtils;
import org.thunlp.language.chinese.Stopwords;
import org.thunlp.language.chinese.WordSegment;
import org.thunlp.text.Lexicon;
import org.thunlp.text.Lexicon.Word;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class WordFeatureExtractor implements FeatureExtractor {
    public static int ENGLISH = 0;
    public static int CHINESE = 1;
    private static Logger LOG = Logger.getAnonymousLogger();
    private static Pattern bracesRE = Pattern.compile("[{}]+");
    private static String Chinese_stopword_path = new String();
    //	private static String project_path = "/home/tang/Documents/THUTag/demo" ;
    private static String project_path = RtuMain.getProjectPath();
    private static RtuMain jar_path = new RtuMain();
    WordSegment ws = null;
    boolean useContent = true;
    boolean useTitle = true;
    boolean useBigram = false;
    boolean useSingleChineseChar = false;
    boolean useChineseOnly = false;
    Lexicon wordLex = null;
    Lexicon tagLex = null;
    int minWordCount = 0;
    int minTagCount = 0;
    Set<String> stopwords = null;
    int lang = -1;
    private Properties config = null;
    private HashSet<String> tagPossibleSet = new HashSet<>();

    public WordFeatureExtractor() {

        stopwords = new HashSet<>();
        stopwords.add("本书");
        stopwords.add("读者");
        stopwords.add("作者");
        stopwords.add("介绍");
        stopwords.add("我们");
        stopwords.add("以及");
        stopwords.add("一个");
        stopwords.add("文章");
        stopwords.add("研究");
        stopwords.add("他们");
        stopwords.add("成为");
        stopwords.add("内容");
        stopwords.add("全书");
        stopwords.add("这些");
        stopwords.add("他的");
        stopwords.add("简介");
        stopwords.add("装帧");
        stopwords.add("简体");
        stopwords.add("imdb");
        stopwords.add("定价");
        stopwords.add("踏上");
        stopwords.add("介质");
        stopwords.add("编号");
        stopwords.add("译者");
        stopwords.add("制片");

        try {
            String stopWordsFile = project_path + File.separator + "chinese_stop_word.txt";

            BufferedReader stop = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(
                                    stopWordsFile),
                            "UTF-8"));
            String line;
            while ((line = stop.readLine()) != null) {
                stopwords.add(line);
            }
            stop.close();
        } catch (IOException e) {
            // e.printStackTrace();
            BufferedReader stop;
            try {
                stop = new BufferedReader(new InputStreamReader(
                        new FileInputStream("chinese_stop_word.txt"), "UTF-8"));
                String line;
                while ((line = stop.readLine()) != null) {
                    stopwords.add(line);
                }
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                //	e1.printStackTrace();
            }
        }
    }

    public WordFeatureExtractor(Properties config) {

        this();
        this.config = config;

        try {
            String stopWordsFile = config.getProperty("model", project_path) +
                    File.separator +
                    "chinese_stop_word.txt";

            LOG.info(stopWordsFile);

            BufferedReader stop = new BufferedReader(
                    new InputStreamReader(new FileInputStream(stopWordsFile), "UTF-8"));
            String line;
            while ((line = stop.readLine()) != null) {
                stopwords.add(line);
            }
            stop.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (!config.getProperty("dataType", "Post").equals("Keyword")) {
                System.setProperty("wordsegment.automata.file",
                        config.getProperty("model", project_path) + File.separator + "book.model");

            }
            ws = new ForwardMaxWordSegment();
            if (!config.getProperty("dataType", "Post").equals("Keyword")) {
                System.clearProperty("wordsegment.automata.file");
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        HashSet<String> possibleTagSet = new HashSet<>();

        String[] possibleTags = config.getProperty("POS", "ns np nz ni n m i j a v x t").split(" ");
        //{"ns","np","nz","ni","n","i","a","v","x","t"};
        possibleTagSet.addAll(Arrays.asList(possibleTags));
        setTagPossibleSet(possibleTagSet);

        if (!config.getProperty("content", "true").equals("true")) {
            useContent = false;
        }
        if (!config.getProperty("title", "true").equals("true")) {
            useTitle = false;
        }
        if (config.getProperty("bigram", "false").equals("true")) {
            useBigram = true;
        }
        if (config.getProperty("singlechinese", "false").equals("true")) {
            useSingleChineseChar = true;
        }
        if (config.getProperty("chineseonly", "false").equals("true")) {
            useChineseOnly = true;
        }
        if (useBigram)
            LOG.info("use bigram features.");
        minWordCount = Integer.parseInt(config.getProperty("minwordfreq", "0"));
        if (config.getProperty("lang", "english").equals("english"))
            lang = ENGLISH;
        else if (config.getProperty("lang", "english").equals("chinese"))
            lang = CHINESE;
    }

    /*
    * @param input input file path
    * @param worlex output lexicon
    * @param taglex output lexicon
    * @param config input configuration
    */
    public static void buildLexicons(String input, Lexicon wordlex,
                                     Lexicon taglex, Properties config) throws IOException {

        // We use lexicon cache to avoid repeatly lexicon building.
        File cachedTagLexFile = new File(input + ".taglex");
        File cachedWordLexFile = new File(input + ".wordlex");
        if (cachedWordLexFile.exists() && cachedTagLexFile.exists()) {
            LOG.info("Use cached lexicons");
            wordlex.loadFromFile(cachedWordLexFile);
            taglex.loadFromFile(cachedTagLexFile);
            return;
        }

        WordFeatureExtractor extractor = new WordFeatureExtractor(config);
        JsonUtil J = new JsonUtil();
        RecordReader reader = new RecordReader(input);
        Set<String> filtered = new HashSet<>();
        TagFilter tagFilter = new TagFilter(config, null);

        boolean useBigram = config.getProperty("bigram", "false")
                .equals("true");
        Lexicon localWordLex = new Lexicon();

        String dataType = config.getProperty("dataType", "Post");

        switch (dataType) {
            case "DoubanPost":
                localWordLex = readDoubanPost(taglex, extractor, J, reader, filtered, tagFilter, useBigram, localWordLex);
                break;
            case "Post":
                if (config.getProperty("isSegmented", "false").equals("true")) {
                    if (config.getProperty("useLda", "false").equals("true")) {
                        localWordLex = readPostSegmentedLda(taglex, extractor, J, reader, filtered, tagFilter, useBigram, localWordLex);
                    } else {
                        localWordLex = readPostSegmentedNotLda(taglex, extractor, J, reader, filtered, tagFilter, useBigram, localWordLex);
                    }
                } else {
                    localWordLex = readPostNotSegmented(taglex, extractor, J, reader, filtered, tagFilter, useBigram, localWordLex);
                }
                break;
            case "KeywordPost":
                if (config.getProperty("isSegmented", "false").equals("true")) {
                    if (config.getProperty("useLda", "false").equals("true")) {
                        localWordLex = readKeywordPostSegmentedLda(taglex, extractor, J, reader, filtered, tagFilter, useBigram, localWordLex);
                    } else {
                        localWordLex = readKeywordPostSegmentedNotLda(taglex, extractor, J, reader, filtered, tagFilter, useBigram, localWordLex);
                    }
                } else {
                    localWordLex = readKeywordPostNotSegmented(taglex, extractor, J, reader, filtered, tagFilter, useBigram, localWordLex);
                }
                break;
        }
        // TODO Check why should we save and load same file here
        LOG.info("Saving lexicons to cache files");
        localWordLex.saveToFile(cachedWordLexFile);
        wordlex.loadFromFile(cachedWordLexFile);
        taglex.saveToFile(cachedTagLexFile);
        LOG.info("done. " + wordlex.getSize() + " words, " + taglex.getSize()
                + " tags.");
    }

    private static Lexicon readKeywordPostNotSegmented(Lexicon taglex, WordFeatureExtractor extractor, JsonUtil j, RecordReader reader, Set<String> filtered, TagFilter tagFilter, boolean useBigram, Lexicon localWordLex) throws IOException {
        while (reader.next()) {
            KeywordPost p = j.fromJson(reader.value(), KeywordPost.class);
            String[] features = extractor.extractKeyword(p, true, true,
                    true);
            localWordLex.addDocument(features);
            // filtered 被传进去之后首先会清除里面的内容
            tagFilter.filterWithNorm(p.getTags(), filtered);
            taglex.addDocument(filtered.toArray(new String[filtered.size()]));
            localWordLex = printStatus(reader, useBigram, localWordLex);
        }
        reader.close();
        return localWordLex;
    }

    private static Lexicon readKeywordPostSegmentedNotLda(Lexicon taglex, WordFeatureExtractor extractor, JsonUtil j, RecordReader reader, Set<String> filtered, TagFilter tagFilter, boolean useBigram, Lexicon localWordLex) throws IOException {
        while (reader.next()) {
            KeywordPost p = j.fromJson(reader.value(), KeywordPost.class);
            String[] features = extractor.extractKeywordSegmented(p, true, true,
                    true);
            localWordLex.addDocument(features);
            tagFilter.filterWithNorm(p.getTags(), filtered);
            taglex.addDocument(filtered.toArray(new String[filtered.size()]));
            localWordLex = printStatus(reader, useBigram, localWordLex);
        }
        reader.close();
        return localWordLex;
    }

    private static Lexicon readKeywordPostSegmentedLda(Lexicon taglex, WordFeatureExtractor extractor, JsonUtil j, RecordReader reader, Set<String> filtered, TagFilter tagFilter, boolean useBigram, Lexicon localWordLex) throws IOException {
        while (reader.next()) {
            KeywordPost p = j.fromJson(reader.value(), KeywordPost.class);
            String[] features = extractor.extractKeywordLda(p, true, true,
                    true);
            localWordLex.addDocument(features);
            tagFilter.filterWithNorm(p.getTags(), filtered);
            taglex.addDocument(filtered.toArray(new String[filtered.size()]));
            localWordLex = printStatus(reader, useBigram, localWordLex);
        }
        reader.close();
        return localWordLex;
    }

    private static Lexicon readPostNotSegmented(Lexicon taglex, WordFeatureExtractor extractor, JsonUtil j, RecordReader reader, Set<String> filtered, TagFilter tagFilter, boolean useBigram, Lexicon localWordLex) throws IOException {
        while (reader.next()) {
            Post p = j.fromJson(reader.value(), Post.class);
            String[] features = extractor.extract(p);
            localWordLex.addDocument(features);
            tagFilter.filterWithNorm(p.getTags(), filtered);
            taglex.addDocument(filtered.toArray(new String[filtered.size()]));
            localWordLex = printStatus(reader, useBigram, localWordLex);
        }
        reader.close();
        return localWordLex;
    }

    private static Lexicon readPostSegmentedNotLda(Lexicon taglex, WordFeatureExtractor extractor, JsonUtil j, RecordReader reader, Set<String> filtered, TagFilter tagFilter, boolean useBigram, Lexicon localWordLex) throws IOException {
        while (reader.next()) {
            Post p = j.fromJson(reader.value(), Post.class);
            String[] features = extractor.extractPostSegmented(p);
            localWordLex.addDocument(features);
            tagFilter.filterWithNorm(p.getTags(), filtered);
            taglex.addDocument(filtered.toArray(new String[filtered.size()]));
            localWordLex = printStatus(reader, useBigram, localWordLex);
        }
        reader.close();
        return localWordLex;
    }


    private static Lexicon readPostSegmentedLda(Lexicon taglex, WordFeatureExtractor extractor, JsonUtil j, RecordReader reader, Set<String> filtered, TagFilter tagFilter, boolean useBigram, Lexicon localWordLex) throws IOException {
        while (reader.next()) {
            Post p = j.fromJson(reader.value(), Post.class);
            String[] features = extractor.extractPostLda(p);
            localWordLex.addDocument(features);
            tagFilter.filterWithNorm(p.getTags(), filtered);
            taglex.addDocument(filtered.toArray(new String[filtered.size()]));
            localWordLex = printStatus(reader, useBigram, localWordLex);
        }
        reader.close();
        return localWordLex;
    }

    private static Lexicon readDoubanPost(Lexicon taglex, WordFeatureExtractor extractor, JsonUtil j, RecordReader reader, Set<String> filtered, TagFilter tagFilter, boolean useBigram, Lexicon localWordLex) throws IOException {
        while (reader.next()) {
            DoubanPost p = j.fromJson(reader.value(), DoubanPost.class);
            String[] features = extractor.extract(p);
            localWordLex.addDocument(features);
            tagFilter.filterMapWithNorm(p.getDoubanTags(), filtered);
            taglex.addDocument(filtered.toArray(new String[filtered.size()]));
            localWordLex = printStatus(reader, useBigram, localWordLex);
        }
        reader.close();
        return localWordLex;
    }

    private static Lexicon printStatus(RecordReader reader, boolean useBigram, Lexicon localWordLex) {
        if (reader.numRead() % 1000 == 0)
            LOG.info("building lexicons: " + reader.numRead());
        if (reader.numRead() % 5000 == 0 && useBigram) {
            LOG.info("trim lexicon");
            localWordLex = localWordLex.removeLowDfWords(5);
            LOG.info("building lexicons: " + reader.numRead());
        }
        return localWordLex;
    }

    public static String clean(String text) {
        String cleaned = HtmlReformatter.getPlainText(text);
        cleaned = cleaned.replaceAll("\\\\[\"']", "");
        cleaned = cleaned.replaceAll("\\{\\\\ss\\}", "s");
        cleaned = cleaned.replaceAll("Proc\\.", "Proceedings");
        cleaned = cleaned.replaceAll("Int\\.|Intl\\.", "International");
        cleaned = bracesRE.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("(?<=[a-zA-Z0-9])-(?=[a-zA-Z0-9])", "");
        cleaned = LangUtils.removePunctuationMarks(cleaned);
        cleaned = LangUtils.mapFullWidthLetterToHalfWidth(cleaned);
        cleaned = LangUtils.mapFullWidthNumberToHalfWidth(cleaned);
        cleaned = LangUtils.T2S(cleaned);
        cleaned = LangUtils.removeLineEnds(cleaned);
        cleaned = LangUtils.removeExtraSpaces(cleaned);
        return cleaned.trim();
    }

    public HashSet<String> getTagPossibleSet() {
        return tagPossibleSet;
    }

    public void setTagPossibleSet(HashSet<String> tagPossibleSet) {
        this.tagPossibleSet = tagPossibleSet;
    }

    public void setWordLexicon(Lexicon l) {
        wordLex = l;
    }

    public void setTagLexicon(Lexicon l) {
        tagLex = l;
    }

    @Override
    public String[] extract(Post p) {
        String content = "";
        if (useTitle) {
            content += " " + p.getTitle();
        }
        if (useContent) {
            content += " " + p.getContent();
        }
        content = normalizeString(content);
        String[] words = ws.segment(content);

        List<String> filtered = new LinkedList<>();
        filterWord(words, filtered);

        return filtered.toArray(new String[filtered.size()]);
    }

    private void filterWord(String[] words, List<String> filtered) {
        for (String word : words) {
            if (word.length() < 1)
                continue;
            if (useSingleChineseChar) {
                if (word.length() == 1
                        && !LangUtils.isChinese(word.codePointAt(0))) {
                    continue;
                }
            } else {
                if (word.length() <= 1)
                    continue;
            }

            if (useChineseOnly && word.matches("^[a-zA-Z0-9]+$"))
                continue;
            if (lang == ENGLISH
                    && org.thunlp.language.english.Stopwords.isStopword(word))
                continue;
            if (lang == CHINESE
                    && Stopwords.isStopword(word))
                continue;

            if (wordLex != null) {
                Word wordType = wordLex.getWord(word);
                if (wordType == null || wordType.getFrequency() < minWordCount)
                    continue;
            }
            if (stopwords.contains(word))
                continue;
            filtered.add(word);
        }

        if (useBigram) {
            for (int i = 0; i < words.length - 1; i++) {
                String bigram = words[i] + "+" + words[i + 1];
                if (wordLex != null) {
                    Word wordType = wordLex.getWord(bigram);
                    if (wordType == null
                            || wordType.getFrequency() < minWordCount)
                        continue;
                }
                filtered.add(bigram);
            }
        }
    }

    public String[] extractKeyword(KeywordPost p, boolean containTitle,
                                   boolean containSummary, boolean containContent) {
        String content = extractString(p, containTitle, containSummary, containContent);

        content = normalizeString(content);
        String[] words = ws.segment(content);

        List<String> filtered = new LinkedList<>();
        filterWord(words, filtered);

        return filtered.toArray(new String[filtered.size()]);
    }

    private String normalizeString(String content) {
        content = LangUtils.removePunctuationMarks(content);
        content = LangUtils.removeLineEnds(content);
        content = LangUtils.removeExtraSpaces(content);
        content = LangUtils.removeEmptyLines(content);
        content = content.toLowerCase();
        return content;
    }

    public String[] extractPostSegmented(Post p) {
        String content = "";
        if (useTitle)
            content += p.getTitle();
        if (useContent)
            content += " " + p.getContent();

        content = content.replaceAll("。", " ");
        String[] wordWithTagList = content.split(" ");
        Vector<String> results = new Vector<>();
        filterWordWithTag(wordWithTagList, results);
        String[] words = results.toArray(new String[0]);

        List<String> filtered = new LinkedList<>();
        filterWord(words, filtered);

        return filtered.toArray(new String[filtered.size()]);
    }

    private void filterWordWithTag(String[] wordWithTagList, Vector<String> results) {
        for (String wordWithTag : wordWithTagList) {
            if (wordWithTag.equals("")) {
                continue;
            }
            String[] datas = wordWithTag.split("_");
            if (!tagPossibleSet.contains(datas[1])) {
                continue;
            }
            results.add(datas[0]);
        }
    }

    public String[] extractKeywordSegmented(KeywordPost p, boolean containTitle,
                                            boolean containSummary, boolean containContent) {
        String content = extractString(p, containTitle, containSummary, containContent);

        content = content.replaceAll("。", " ");
        String[] wordWithTags = content.split(" ");
        Vector<String> results = new Vector<>();
        filterWordWithTag(wordWithTags, results);
        String[] words = results.toArray(new String[0]);

        List<String> filtered = new LinkedList<>();
        filterWord(words, filtered);

        return filtered.toArray(new String[filtered.size()]);
    }

    public String[] extractKeywordLda(KeywordPost p, boolean containTitle,
                                      boolean containSummary, boolean containContent) {
        String content = extractString(p, containTitle, containSummary, containContent);

        content = content.replaceAll("。", " ");
        String[] wordWithTags = content.split(" ");
        Vector<String> results = new Vector<>();
        filterWordWithTag2(wordWithTags, results);
        String[] words = results.toArray(new String[0]);

        List<String> filtered = new LinkedList<>();
        filterWord(words, filtered);

        return filtered.toArray(new String[filtered.size()]);
    }

    private String extractString(KeywordPost p, boolean containTitle, boolean containSummary, boolean containContent) {
        String content = "";
        if (containTitle)
            content += p.getTitle();
        if (containSummary)
            content += " " + p.getSummary();
        if (containContent)
            content += " " + p.getContent();
        return content;
    }

    private void filterWordWithTag2(String[] wordWithTags, Vector<String> results) {
        for (String wordWithTag : wordWithTags) {
            if (wordWithTag.equals("")) {
                continue;
            }
            String[] datas = wordWithTag.split("_");
            if (!tagPossibleSet.contains(datas[1])) {
                continue;
            }
            results.add(datas[0] + "_" + datas[2]);
        }
    }

    public String[] extractPostLda(Post p) {
        String content = "";
        if (useTitle) {
            content += " " + p.getTitle();
        }
        if (useContent) {
            content += " " + p.getContent();
        }

        content = content.replaceAll("。", " ");
        String[] wordWithTags = content.split(" ");
        Vector<String> results = new Vector<>();
        filterWordWithTag2(wordWithTags, results);
        String[] words = results.toArray(new String[0]);

        List<String> filtered = new LinkedList<>();
        filterWord(words, filtered);

        return filtered.toArray(new String[filtered.size()]);
    }

    public String[] getWords(String content) {
        content = normalizeString(content);
        String[] words = ws.segment(content);

        List<String> filtered = new LinkedList<>();
        filterWord(words, filtered);

        return filtered.toArray(new String[filtered.size()]);
    }
}