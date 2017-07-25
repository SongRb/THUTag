package org.thunlp.tagsuggest.contentbase;

import org.thunlp.tagsuggest.common.ConfigIO;
import org.thunlp.tagsuggest.common.SMTSuggestBase;

import java.io.IOException;
import java.util.Map;


public class SMTTagSuggest extends SMTSuggestBase {
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


    protected double calProbability(int id, Map.Entry<Integer, Double> ee, int tagId) {
        return 1.0 / (para / ee.getValue() + (1.0 - para) / inverseTable.get(id).get(tagId));
    }

}
