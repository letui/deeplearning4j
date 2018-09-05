package org.nd4j.imports.TFGraphs;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.OpValidationSuite;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.function.BiFunction;
import org.nd4j.linalg.function.Function;
import org.nd4j.nativeblas.NativeOpsHolder;
import org.nd4j.resources.Downloader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
@Slf4j
public class TFGraphTestZooModels {

    public static final File BASE_MODEL_DL_DIR = new File(System.getProperty("user.home"), ".nd4jtests");

    private static final String BASE_DIR = "tf_graphs/zoo_models";
    private static final String MODEL_FILENAME = "tf_model.txt";

    private Map<String, INDArray> inputs;
    private Map<String, INDArray> predictions;
    private String modelName;

    public static final BiFunction<File,String,SameDiff> LOADER = new RemoteCachingLoader();

    public static class RemoteCachingLoader implements BiFunction<File,String,SameDiff> {
        @Override
        public SameDiff apply(File file, String name) {
            try {
                String s = FileUtils.readFileToString(file, StandardCharsets.UTF_8).replaceAll("\r\n","\n");
                String[] split = s.split("\n");
                if(split.length != 2){
                    throw new IllegalStateException("Invalid file: expected 2 lines with URL and MD5 hash. Got " + split.length + " lines");
                }
                String url = split[0];
                String md5 = split[1];

                File localDir = new File(BASE_MODEL_DL_DIR, name);
                if(!localDir.exists())
                    localDir.mkdirs();

                String filename = FilenameUtils.getName(url);
                if(filename.endsWith(".pb")) {
                    File localFile = new File(localDir, filename);
                    if (!localFile.exists())
                        log.info("Starting resource download from: {} to {}", url, localFile.getAbsolutePath());
                    Downloader.download(name, new URL(url), localFile, md5, 3);
                } else if(filename.endsWith(".tar.gz")){

                } else {
                    throw new IllegalStateException("Unknown format: " + filename);
                }

                return TFGraphTestAllHelper.LOADER.apply(localFile, name);
            } catch (IOException e){
                throw new RuntimeException(e);
            }
        }
    }

    @Parameterized.Parameters(name="{2}")
    public static Collection<Object[]> data() throws IOException {
        List<Object[]> params = TFGraphTestAllHelper.fetchTestParams(BASE_DIR, MODEL_FILENAME, TFGraphTestAllHelper.ExecuteWith.SAMEDIFF);
        return params;
    }

    public TFGraphTestZooModels(Map<String, INDArray> inputs, Map<String, INDArray> predictions, String modelName) throws IOException {
        this.inputs = inputs;
        this.predictions = predictions;
        this.modelName = modelName;
    }

    @Test(timeout = 360000L)
    public void testOutputOnly() throws Exception {
        Nd4j.create(1);
//        if (SKIP_SET.contains(modelName)) {
//            log.info("\n\tSKIPPED MODEL: " + modelName);
//            return;
//        }

//        for(String s : IGNORE_REGEXES){
//            if(modelName.matches(s)){
//                log.info("\n\tIGNORE MODEL ON REGEX: {} - regex {}", modelName, s);
//                OpValidationSuite.ignoreFailing();
//            }
//        }
        Double precisionOverride = null;    //TFGraphTestAllHelper.testPrecisionOverride(modelName);

        TFGraphTestAllHelper.checkOnlyOutput(inputs, predictions, modelName, BASE_DIR, MODEL_FILENAME, TFGraphTestAllHelper.ExecuteWith.SAMEDIFF,
                LOADER, precisionOverride);
        //TFGraphTestAllHelper.checkIntermediate(inputs, modelName, EXECUTE_WITH);
    }
}
