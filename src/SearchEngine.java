/**
 * Created by frank on 17-5-22.
 */
import com.sun.javaws.exceptions.InvalidArgumentException;
import javafx.util.Pair;
import org.apache.lucene.analysis.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.*;
import java.util.*;

public class SearchEngine
{
    private final String FieldId = "id";
    private final String FieldSong = "song";
    private final String FieldSinger = "singer";
    private final String FieldLrc = "lrc";
    private final String FieldAll = "all";
    private final String FieldPublishTime = "publishTime";
    private final String FieldPopularity = "popularity";
    private final String ActionUpdate = "update";

    private final Double lrcThreshold = 3.0;

    class MyDoc
    {
        public MyDoc(ScoreDoc sd, Query q, String fieldName, String fieldValue) throws Exception
        {
            score = sd.score;
            doc = indexSearcher.doc(sd.doc);

            setField(fieldName, fieldValue, q);

            id = Integer.valueOf(doc.get(FieldId));
            song = doc.get(FieldSong);
            singer = doc.get(FieldSinger);
            lrc = doc.get(FieldLrc);
            publishTime = Double.valueOf(doc.get(FieldPublishTime));
            popularity = Double.valueOf(doc.get(FieldPopularity));
        }

        public MyDoc(MyDoc lhs, MyDoc rhs)
        {
            assert (lhs.id == rhs.id);

            score = lhs.score * rhs.score;
            doc = lhs.doc;

            setField(lhs);
            setField(rhs);

            id = lhs.id;
            song = lhs.song;
            singer = lhs.singer;
            lrc = lhs.lrc;
            publishTime = lhs.publishTime;
            popularity = lhs.popularity;
        }

        public MyDoc(MyDoc d, double newSore)
        {
            score = newSore;
            doc = d.doc;

            setField(d);

            id = d.id;
            song = d.song;
            singer = d.singer;
            lrc = d.lrc;
            publishTime = d.publishTime;
            popularity = d.popularity;
        }

        public void setField(String fieldName, String fieldValue, Query query)
        {
            switch (fieldName) {
                case FieldSong:
                    songQueried = true;
                    songQueryValue = fieldValue;
                    songQuery = query;
                    break;

                case FieldSinger:
                    singerQueried = true;
                    singerQueryValue = fieldValue;
                    singerQuery = query;
                    break;

                case FieldLrc:
                    lrcQueried = true;
                    lrcQueryValue = fieldValue;
                    lrcQuery = query;
            }
        }

        public void setField(MyDoc doc)
        {
            if (doc.songQueried) {
                songQueried = true;
                songQueryValue = doc.songQueryValue;
                songQuery = doc.songQuery;
            }
            if (doc.singerQueried) {
                singerQueried = true;
                singerQueryValue = doc.singerQueryValue;
                singerQuery = doc.singerQuery;
            }
            if (doc.lrcQueried) {
                lrcQueried = true;
                lrcQueryValue = doc.lrcQueryValue;
                lrcQuery = doc.lrcQuery;
            }
        }

        @Override
        public String toString()
        {
            String hSong = null;
            String hSinger = null;
            String hLrc = null;

            if (songQueried) {
                hSong = highlightFiledValue(songQuery, FieldSong, song);
            }
            if (singerQueried) {
                hSinger = highlightFiledValue(singerQuery, FieldSinger, singer);
            }
            if (lrcQueried) {
                hLrc = highlightFiledValue(lrcQuery, FieldLrc, lrc);
            }

            String ret = String.format("id: %d\r\n", id);
            ret += String.format("song: %s\r\n", hSong != null ? hSong : song);
            ret += String.format("singer: %s\r\n", hSinger != null ? hSinger : singer);
            if (hLrc != null) {
                ret += String.format("lrc: %s\r\n", hLrc);
            }

            return ret;
        }

        public double score;
        public Document doc;

        public boolean songQueried = false;
        public String songQueryValue;
        public Query songQuery;

        public boolean singerQueried = false;
        public String singerQueryValue;
        public Query singerQuery;

        public boolean lrcQueried = false;
        public String lrcQueryValue;
        public Query lrcQuery;

        public int id;
        public String song;
        public String singer;
        public String lrc;
        public double publishTime;
        public double popularity;
    }

    private static SearchEngine singleton;

    public static SearchEngine instance()
    {
        try {
            if (singleton == null) {
                singleton = new SearchEngine();
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        return singleton;
    }

    private SearchEngine() throws Exception
    {
        analyzer = new IKAnalyzer(true);
        directory = new SimpleFSDirectory(new File("./index"));
        directoryReader = DirectoryReader.open(directory);
        indexSearcher = new IndexSearcher(directoryReader);
        clickTimes = new HashMap<>();
        singers = new HashSet<>();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream("src/singer.dic")));

        try {
            while (true) {
                String s = reader.readLine();
                if (!s.isEmpty()) {
                    singers.add(s);
                }
            }
        }
        catch (Exception e) {
            System.out.println(singers.size() + " singers");
        }

        System.out.println(directoryReader.numDocs() + " docs in search engine");
    }

    public void update(int songId, int times)
    {
        System.out.printf("update %d %d times\n", songId, times);
        clickTimes.put(songId, times);
    }

    public String search(String input)
    {
        Vector<Pair<String, String>> query = parseQuery(input);
        MyDoc[] docs = null;

        try {
            for (Pair<String, String> p : query) {
                String key = p.getKey();
                String value = p.getValue();
                MyDoc[] newDocs = null;
                switch (key) {

                    case ActionUpdate:
                        String[] s = value.split("[ |\t]");
                        if (s.length != 2) {
                            throw new InvalidArgumentException(null);
                        }
                        update(Integer.valueOf(s[0]), Integer.valueOf(s[1]));
                        break;

                    case FieldId:
                    case FieldSong:
                    case FieldSinger:
                    case FieldLrc:
                        newDocs = searchField(key, value);
                        break;

                    case FieldAll:
                        if (singers.contains(value)) {
                            // 优先从词典中匹配歌手名
                            newDocs = searchField(FieldSinger, value);
                            System.out.printf("hits %d\n", newDocs.length);
                        }
                        else {
                            // 歌名
                            newDocs = searchField(FieldSong, value);
                            sortDocs(newDocs);
                            System.out.printf("hits %d\n", newDocs.length);
                            if (newDocs.length > 0) {
                                System.out.printf("song score %f \n", newDocs[0].score);
                            }
                            if (newDocs.length == 0 || newDocs[0].score < lrcThreshold) {
                                // 歌词优先级最低
                                newDocs = searchField(FieldLrc, value);
                                sortDocs(newDocs);
                                System.out.printf("hits %d\n", newDocs.length);
                                if (newDocs.length > 0) {
                                    System.out.printf("lrc score %f \n", newDocs[0].score);
                                }
                            }
                        }
                        break;

                    default:
                        throw new InvalidArgumentException(null);
                }

                if (docs == null) {
                    docs = newDocs;
                } else {
                    docs = intersectDocs(docs, newDocs);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (docs == null) {
            docs = new MyDoc[0];
        }

        sortDocs(docs);
        StringBuilder buf = new StringBuilder(128);
        buf.append(String.format("hits: %d\r\n\r\n", docs.length));
        for (MyDoc d : docs) {
            buf.append(d.toString());
            buf.append("\r\n");
        }

        return buf.toString();
    }

    private Vector<Pair<String, String>> parseQuery(String input)
    {
        BufferedReader reader =
                new BufferedReader(new StringReader(input));
        Vector<Pair<String, String>> ret = new Vector<>(1);

        try {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                String[] pair = line.split(":");
                if (pair.length != 2) {
                    throw new IOException("invalid query");
                }

                pair[0] = pair[0].trim();
                pair[1] = pair[1].trim();

                if (pair[0].equalsIgnoreCase(FieldSong)) {
                    pair[0] = FieldSong;
                }
                else if (pair[0].equalsIgnoreCase(FieldSinger)) {
                    pair[0] = FieldSinger;
                }
                else if (pair[0].equalsIgnoreCase(FieldLrc)) {
                    pair[0] = FieldLrc;
                }
                else if (pair[0].equalsIgnoreCase(FieldAll)) {
                    pair[0] = FieldAll;
                }
                else if (pair[0].equalsIgnoreCase(ActionUpdate)) {
                    pair[0] = ActionUpdate;
                }

                else {
                    throw new IOException("invalid query");
                }
                ret.add(new Pair<>(pair[0], pair[1]));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    private MyDoc[] searchField(String name, String value) throws Exception
    {
        QueryParser parser = new QueryParser(Version.LUCENE_40, name, analyzer);
        Query query = parser.parse(value);
        ScoreDoc[] sdocs = indexSearcher.search(query, 100).scoreDocs;
        MyDoc[] mdocs = new MyDoc[sdocs.length];


        for (int i = 0; i < mdocs.length; ++i) {
            mdocs[i] = new MyDoc(sdocs[i], query, name, value);

            int id = mdocs[i].id;
            int times = clickTimes.getOrDefault(id, 0);

            mdocs[i].score = sdocs[i].score * (1 + Math.log(1 + times));
        }

        System.out.println(query);

        return mdocs;
    }

    private String highlightFiledValue(Query q, String fieldName, String text)
    {
        SimpleHTMLFormatter simpleHTMLFormatter =
                new SimpleHTMLFormatter(
                        "*", "*");

        Highlighter highlighter =
                new Highlighter(simpleHTMLFormatter, new QueryScorer(q));

        highlighter.setTextFragmenter(new SimpleFragmenter(20));

        try {
            return highlighter.getBestFragment(analyzer, fieldName, text);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private void sortDocs(MyDoc[] docs)
    {
        /* 根据score, popularity, publishTime排序 */
        class MyComparator implements Comparator<MyDoc>
        {
            public int compare(MyDoc lhs, MyDoc rhs)
            {
                if (lhs.score != rhs.score) {
                    return lhs.score < rhs.score ? 1 : -1;
                }

                if (lhs.popularity != rhs.popularity) {
                    return lhs.popularity < rhs.popularity ? -1 : 1;
                }

                return lhs.publishTime > rhs.publishTime ? -1 : 1;
            }
        }
        Arrays.sort(docs, new MyComparator());
    }

    private int nToken(String input) throws Exception
    {
        int tokens = 0;
        TokenStream ts = analyzer.tokenStream("myfield", new StringReader(input));

        //重置TokenStream（重置StringReader）
        ts.reset();

        //迭代获取分词结果
        while (ts.incrementToken()) {
            ++tokens;
        }

        //关闭TokenStream（关闭StringReader）
        ts.end();

        return tokens;
    }

    private MyDoc[] intersectDocs(MyDoc[] a, MyDoc[] b)
    {
        Vector<MyDoc> vec = new Vector<>();
        for (MyDoc d1 : a) {
            for (MyDoc d2 : b) {
                if (d1.id == d2.id) {
                    vec.add(new MyDoc(d1, d2));
                }
            }
        }

        MyDoc[] arr = new MyDoc[vec.size()];
        return vec.toArray(arr);
    }

    private MyDoc[] unionDocs(MyDoc[] a, MyDoc[] b)
    {
        Vector<MyDoc> vec = new Vector<>();
        Collections.addAll(vec, b);
        for (MyDoc d1 : a) {
            boolean found = false;
            for (MyDoc d2 : b) {
                if (d1.id == d2.id) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                vec.add(d1);
            }
        }
        MyDoc[] arr = new MyDoc[vec.size()];
        return vec.toArray(arr);
    }

    private HashSet<String> singers;
    private Analyzer analyzer;
    private Directory directory;
    private DirectoryReader directoryReader;
    private IndexSearcher indexSearcher;
    private HashMap<Integer, Integer> clickTimes;

    public static void main(String[] args) throws Exception
    {
        SearchEngine se = new SearchEngine();
        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(System.in));

        String input = "singer: 周杰伦\r\nsong: 搁浅\r\n";
        se.search(input);

        while (true) {

            System.out.print("search: ");
            input = reader.readLine();

            se.search(input);

            //System.out.print("click: ");
            //int click = Integer.valueOf(reader.readLine());
            //se.update(click);
        }
    }
}