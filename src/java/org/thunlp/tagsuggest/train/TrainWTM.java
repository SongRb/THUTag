package org.thunlp.tagsuggest.train;

import org.thunlp.io.RecordReader;
import org.thunlp.language.chinese.LangUtils;
import org.thunlp.misc.Counter;
import org.thunlp.tagsuggest.common.DoubanPost;
import org.thunlp.tagsuggest.common.TagFilter;
import org.thunlp.tagsuggest.common.TrainWAMBase;
import org.thunlp.text.Lexicon;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class TrainWTM extends TrainWAMBase {
    protected void createTrainData(String input, File modelDir, Lexicon localWordLex, Lexicon localTagLex, double scoreLimit) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath() + "/book"),
                "UTF-8"));

        BufferedWriter outTag = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(modelDir.getAbsolutePath()
                        + "/bookTag"), "UTF-8"));
        RecordReader reader = new RecordReader(input);

        TagFilter localTagFilter = new TagFilter(config, localTagLex);
        Set<String> localFiltered = new HashSet<>();
        HashSet<String> tagSet = new HashSet<String>();

        Random random = new Random();
        Pattern spaceRE = Pattern.compile(" +");
        // the second time :
        while (reader.next()) {
            DoubanPost p = J.fromJson(reader.value(), DoubanPost.class);
            if (fold.length() > 0 && p.getExtras().equals(fold)) {
                continue;
            }
            localTagFilter.filterMapWithNorm(p.getDoubanTags(),
                    localFiltered);
            double total = 0.0;
            Vector<Double> tagTfidf = new Vector<>();
            Vector<String> tagList = new Vector<>();
            // calculate the tfidf of tag
            for (Entry<String, Integer> e : p.getDoubanTags().entrySet()) {
                String tag = e.getKey();
                tag = LangUtils.removePunctuationMarks(tag);
                tag = spaceRE.matcher(tag).replaceAll("");
                tag = LangUtils.T2S(tag);
                tag = tag.toLowerCase();
                if (localFiltered.contains(tag)) {
                    double idf = Math.log(((double) localTagLex
                            .getNumDocs())
                            / ((double) localTagLex.getWord(tag)
                            .getDocumentFrequency()));
                    tagTfidf.add(((double) e.getValue()) * idf);
                    total += ((double) e.getValue()) * idf;
                    tagList.add(tag);
                    tagSet.add(tag);
                }
            }
            if (total == 0.0)
                continue;
            Vector<Double> tagProb = new Vector<Double>();
            for (int i = 0; i < tagTfidf.size(); i++) {
                tagProb.add(tagTfidf.elementAt(i) / total);
            }
            String[] words = extractor.extract(p);
            if (words.length <= 0) {
                continue;
            }


            int wordnum = (words.length > 100) ? 100 : words.length;
            int wordCount = Integer.parseInt(config.getProperty("wordCount", "1"));
            int tagCount = Integer.parseInt(config.getProperty("tagCount", "1"));
            int tagLength = wordnum * tagCount / wordCount;
            if (tagLength <= 0) {
                continue;
            }
            if (tagLength > 100) {
                tagLength = 100;
            }

            // sample the words
            Vector<Double> wordTfidf = new Vector<Double>();
            Vector<String> wordList = new Vector<String>();
            Counter<String> termFreq = new Counter<String>();
            for (String word : words) {
                termFreq.inc(word, 1);
            }
            Iterator<Entry<String, Long>> iter = termFreq.iterator();
            double totalTfidf = 0.0;
            while (iter.hasNext()) {
                Entry<String, Long> e = iter.next();
                String word = e.getKey();
                wordList.add(word);
                double tf = ((double) e.getValue())
                        / ((double) words.length);
                double idf = Math.log(((double) localWordLex.getNumDocs())
                        / ((double) localWordLex.getWord(word)
                        .getDocumentFrequency()));
                wordTfidf.add(tf * idf);
                totalTfidf += tf * idf;
            }
            Vector<Double> wordProb = new Vector<Double>();
            for (int i = 0; i < wordTfidf.size(); i++) {
                wordProb.add(wordTfidf.elementAt(i) / totalTfidf);
            }

            for (int i = 0; i < words.length && i < 100; i++) {
                double select = random.nextDouble();
                double sum = 0.0;
                int j = 0;
                for (j = 0; j < wordProb.size(); j++) {
                    sum += wordProb.elementAt(j);
                    if (sum >= select)
                        break;
                }
                String word = wordList.elementAt(j);
                if (i == 0) {
                    out.write(word);
                } else {
                    out.write(" " + word);
                }
            }
            out.newLine();
            out.flush();

            // sample the tags
            for (int i = 0; i < tagLength; i++) {
                double num = random.nextDouble();
                double sum = 0.0;
                int j = 0;
                for (j = 0; j < tagProb.size(); j++) {
                    sum += tagProb.elementAt(j);
                    if (sum >= num)
                        break;
                }
                if (i == 0) {
                    outTag.write(tagList.elementAt(j));
                } else {
                    outTag.write(" " + tagList.elementAt(j));
                }
            }
            outTag.newLine();
            outTag.flush();
        }

        for (String tag : tagSet) {
            out.write(tag);
            out.newLine();
            out.flush();
            outTag.write(tag);
            outTag.newLine();
            outTag.flush();
        }

        reader.close();
        out.close();
        outTag.close();

        LOG.info("source and target are prepared!");
    }

	public static void main(String[] args) throws IOException {
	}

}