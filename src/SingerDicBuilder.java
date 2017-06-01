import java.io.*;
import java.util.Collections;
import java.util.HashSet;

/**
 * Created by frank on 17-5-28.
 */
public class SingerDicBuilder
{
    public static void main(String[] args) throws Exception
    {
        String dataPath = "./data/meta_data/songs";
        System.out.println("building singer dictionary...");
        buildSingerDic(dataPath);
        System.out.println("done");
    }

    private static void buildSingerDic(String dataPath) throws Exception
    {
        File[] files = new File(dataPath).listFiles();
        HashSet<String> singers = new HashSet<>();

        for (File f : files) {
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(f)));
            reader.readLine();
            reader.readLine();
            reader.readLine();
            String singer = reader.readLine().split("[\\[|\\]]")[1].trim();
            Collections.addAll(singers, singer.split("['|,]"));
            reader.close();
        }

        BufferedWriter writer =
                new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream("./src/singer.dic")));
        for (String s : singers) {
            writer.write(s);
            writer.newLine();
        }
        writer.flush();
        writer.close();
    }
}
