package org.thunlp.tagsuggest.contentbase;

import org.thunlp.io.JsonUtil;
import org.thunlp.io.RecordReader;
import org.thunlp.misc.WeightString;
import org.thunlp.tagsuggest.common.ConfigIO;
import org.thunlp.tagsuggest.common.KeywordPost;
import org.thunlp.tagsuggest.common.SMTSuggestBase;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;


public class SMTKeywordTagSuggest extends SMTSuggestBase {
    public static void main(String[] args) throws IOException {
        SMTKeywordTagSuggest smt = new SMTKeywordTagSuggest();
        smt.setConfig(ConfigIO.configFromString("stop_wordnum_tags=10;norm=all_log;model=/home/meepo/test/sample;size=70000;dataType=KeywordPost;minwordfreq=10;mintagfreq=10"));
        smt.loadModel("/home/meepo/test/sample/model.3.gz");
        RecordReader reader = new RecordReader("/home/meepo/test/check.post");
        BufferedWriter outTag = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream("/home/meepo/test/ansmy.txt"), "UTF-8"));
        JsonUtil J = new JsonUtil();
        List<WeightString> tags;
        while (reader.next()) {
            KeywordPost p = J.fromJson(reader.value(), KeywordPost.class);
            tags = smt.suggest(p, null);
            int counter = 0;
            for (WeightString s : tags) {
                outTag.write(s.toString() + " ");
                counter++;
                if (counter == 10)
                    break;
            }
            outTag.newLine();
            outTag.flush();
            break;
        }
        reader.close();
        outTag.close();
    }


    protected double getPro(int id, Map.Entry<Integer, Double> ee, int tagId) {
        return ee.getValue() * inverseTable.get(id).get(tagId);
    }

}
