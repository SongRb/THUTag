`cerr` was used in giza-pp to print debug information, however, only messages begin with ERROR are considered as error report.

target command should be executed under `./demo`

command:

    java -Xmx3G -jar tagsuggest.jar evaluation.CrossValidator --dataset=/path/bookPost70000.dat --trainer_class=TrainWTM --suggester_class=SMTTagSuggest --num_folds=5 --config="dataType=DoubanPost;para=0.5;minwordfreq=10;mintagfreq=10;selfTrans=0.2;commonLimit=2" --working_dir=/path/ --at_n=10 --report=/path/report.txt

report file format:

|number| m(Precision) |std(Precision)|m(Recall)|std(Recall)|m(F1)|std(F1) |m(loglikelihood)|std(loglikelihood)|m(Perplexity)|std(Perplexity)|result_record|
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|

Last row:

|number|suggest_number|answer_number|
|:---|:---|:---|


`org.thunlp.tagsuggest.contentbase.SMTTagSuggest.inverseTable `

key: id of a chinese word
value: a hash table contains corresponding tag and frequency

after recompile the project, you should copy `tagsuggest.jar` from `./build` to `./demo`

`WordFeatureExtractor.java`: way to get project_path was hard-encoded into `/home/tang/Documents/THUTag/demo`