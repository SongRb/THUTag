package org.thunlp.tagsuggest.contentbase;

import org.json.JSONException;
import org.thunlp.language.chinese.LangUtils;
import org.thunlp.misc.WeightString;
import org.thunlp.tagsuggest.common.SMTSuggestBase;
import org.thunlp.tagsuggest.common.WordFeatureExtractor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class SMTZhihuTopicSuggest extends SMTSuggestBase {
    protected WordFeatureExtractor extractor = null;
    Vector<String> lines = new Vector<>();
    Vector<String> words = new Vector<>();

    public SMTZhihuTopicSuggest() throws IOException, JSONException {
        loadJson();
    }

    protected double calProbability(int id, Map.Entry<Integer, Double> ee, int tagId) {
        return 1.0 / (para / ee.getValue() + (1.0 - para) / inverseTable.get(id).get(tagId));
    }

    protected List<WeightString> rankTags(HashMap<Integer, Double> proMap) {
        List<WeightString> tags = new ArrayList<>();
        List<WeightString> result = new ArrayList<>();

        Map<String, Double> proTable = new HashMap<>();

        Vector<String> qList = new Vector<>();
        Vector<String> wList = new Vector<>();

        for (Map.Entry<Integer, Double> e : proMap.entrySet()) {
            String tag = bookTagMap.get(e.getKey());
            Vector<String> tagV = findString(tag);
            if (!tagV.isEmpty()) {
                double avgP = e.getValue() / tagV.size();

                for (String s : tagV) {
                    proTable.merge(s, avgP, (a, b) -> a + b);
                    qList.add(tag);
                    wList.add(s);
                }
            }
        }

        for (Map.Entry<String, Double> pair : proTable.entrySet()) {
            tags.add(new WeightString(pair.getKey(), pair.getValue()));
        }

        tags.sort((o1, o2) -> Double.compare(o2.weight, o1.weight));

        int count = 0;
        for (WeightString w : tags) {
            result.add(w);
            count += 1;

            for (int i = 0; i < wList.size(); i++) {
                if (Objects.equals(wList.elementAt(i), w.text))
                    LOG.info(qList.elementAt(i) + "->" + w.text);
            }

            if (count > 30) {
                break;
            }
        }
        return result;
    }

    private void loadJson() throws JSONException, IOException {
        BufferedReader bufferedReader =
                new BufferedReader(new InputStreamReader(new FileInputStream("/mnt/hgfs/D/Administrator/Documents/thu-tag-workspace/topic_name.txt"), "UTF-8"));

        String line;

        while ((line = bufferedReader.readLine()) != null) {
            lines.add(line);
            String word = LangUtils.removePunctuationMarks(line);
            word = LangUtils.removeLineEnds(word);
            word = LangUtils.removeExtraSpaces(word);
            word = LangUtils.removeEmptyLines(word);
            word = word.toLowerCase();
            words.add(word);
        }
    }

    private Vector<String> findString(String q) {
        Vector<String> topics = new Vector<>();

        for (int i = 0; i < words.size(); i++) {
            if (words.elementAt(i).contains(q)) {
                topics.add(lines.elementAt(i));
            }
        }
        return topics;
    }
}
