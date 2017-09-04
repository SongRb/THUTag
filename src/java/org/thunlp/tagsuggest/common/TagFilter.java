package org.thunlp.tagsuggest.common;

import org.thunlp.language.chinese.LangUtils;
import org.thunlp.text.Lexicon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public class TagFilter {
    private Lexicon lex = null;
    private Set<String> stopTags = null;

    public TagFilter(Properties config, Lexicon tagLex) {
        int minTagFreq = Integer.parseInt(config.getProperty("mintagfreq", "1"));
        if (tagLex != null)
            lex = tagLex.removeLowFreqWords(minTagFreq);
        stopTags = new HashSet<>();
    }

    public void filter(Set<String> tags, Set<String> filtered) {
        filtered.clear();
        for (String tag : tags) {
            if (check(tag)) {
                continue;
            }
            filtered.add(tag);
        }
    }

    /*
    * @param tags tags of one article
    * @param filtered value to write
    */
    public void filterWithNorm(Set<String> tags, Set<String> filtered) {
        filtered.clear();
        for (String tag : tags) {
            if (check(tag)) {
                continue;
            }
            String normed = normalize(tag);
            if (normed.length() > 0) {
                filtered.add(normed);
            }
        }
    }

    private boolean check(String tag) {
        return lex != null && lex.getWord(tag) == null || stopTags.contains(tag);
    }

    public void filterMapWithNorm(HashMap<String, Integer> tags,
                                  Set<String> filtered) {
        filtered.clear();
        for (Entry<String, Integer> e : tags.entrySet()) {
            String tag = e.getKey();
            if (check(tag)) {
                continue;
            }

            if (tag.length() == 1 && !LangUtils.isChinese(tag.codePointAt(0))) {
                continue;
            }

            String normed = normalize(tag);
            if (normed.length() > 0) {
                filtered.add(normed);
            }
        }
    }


    public String normalize(String tag) {
        Pattern spaceRE = Pattern.compile(" +");
        tag = LangUtils.removePunctuationMarks(tag);
        tag = spaceRE.matcher(tag).replaceAll("");
        tag = LangUtils.T2S(tag);
        tag = tag.toLowerCase();
        return tag;
    }
}
