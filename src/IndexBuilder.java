import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;

/**
 * Created by frank on 17-5-22.
 */
public class IndexBuilder
{
    public static void main(String[] args) throws Exception
    {
        String dataPath = "./data/meta_data/songs";
        String indexPath = "./index";

        System.out.println("building index...");
        removeDir(new File(indexPath));
        buildIndex(dataPath, indexPath);
        System.out.println("done");
    }

    private static void buildIndex(String dataPath, String indexPath) throws Exception
    {
        Analyzer analyzer = new IKAnalyzer(true);
        Directory directory = new SimpleFSDirectory(new File(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_40, analyzer);
        IndexWriter indexWriter = new IndexWriter(directory, config);

        File[] files = new File(dataPath).listFiles();

        for (File f : files) {
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(f)));


            String id = reader.readLine().split(":")[1].trim();
            String song = reader.readLine().split(":")[1].trim();
            reader.readLine(); // artist_id
            String singer = reader.readLine().split("\\[|\\]")[1].trim();
            reader.readLine(); // album_id
            reader.readLine(); // album_name
            String publishTime = reader.readLine().split(":")[1].trim();
            String popularity = reader.readLine().split(":")[1].trim();

            String lrc = readLrc(id, "./data/lyrics");

            addDoc(indexWriter,
                    Integer.valueOf(id),
                    song,
                    singer,
                    Double.valueOf(publishTime),
                    Double.valueOf(popularity),
                    lrc);

            reader.close();
        }

        indexWriter.close();
        directory.close();
    }

    private static String readLrc(String id, String path)
    {
        File lrc = new File(path + "/" +id + ".lrc");

        BufferedReader reader;
        try {
            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(lrc)));
        }
        catch (Exception e) {
            return "";
        }

        char[] buffer = new char[2048];
        try {
            reader.read(buffer, 0, 2048);
        }
        catch (Exception e) {
            return "";
        }

        return String.valueOf(buffer);
    }

    private static void addDoc(IndexWriter writer,
                               int id,
                               String song,
                               String singer,
                               double publishTime,
                               double popularity,
                               String lrc) throws Exception
    {
        Document doc = new Document();
        doc.add(new IntField("id", id, IntField.TYPE_STORED));
        doc.add(new Field("song", song, TextField.TYPE_STORED));
        doc.add(new Field("singer", singer, TextField.TYPE_STORED));
        doc.add(new DoubleField("publishTime", publishTime, DoubleField.TYPE_STORED));
        doc.add(new DoubleField("popularity", popularity, DoubleField.TYPE_STORED));
        doc.add(new Field("lrc", lrc, TextField.TYPE_STORED));
        writer.addDocument(doc);
    }

    private static void removeDir(File file) throws Exception
    {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                removeDir(sub);
            }
        }
        file.delete();
    }
}
