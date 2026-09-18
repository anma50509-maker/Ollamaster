import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 针对毒网关，对比 Http.postStream 修复前后的行解析结果。
 *  用法：java -cp <classes> HttpLineRegression <port> */
public class HttpLineRegression {
    static String lastTail = "";

    static void probe(String port, String path) {
        StringBuilder got = new StringBuilder();
        int[] lines = {0};
        try {
            // 直接复用生产类 Http
            com.ollamaster.Http.Cancel cancel = new com.ollamaster.Http.Cancel();
            com.ollamaster.Http.postStream("http://127.0.0.1:" + port + path, "{}", null, cancel,
                new com.ollamaster.Http.StreamCb() {
                    @Override public boolean onLine(String line) {
                        lines[0]++;
                        got.append('[').append(line.length() > 40
                                ? line.substring(0, 40) + "..." : line).append(']');
                        return true;
                    }
                }, 5000);
        } catch (Exception e) {
            System.out.println("  EX " + e);
        }
        System.out.printf("%-14s onLine次数=%d  %s%n", path, lines[0], got);
    }

    public static void main(String[] a) throws Exception {
        String port = a.length > 0 ? a[0] : "8901";
        for (String p : new String[]{"/sse", "/sse_no_nl", "/plain_json", "/err200", "/empty"}) probe(port, p);
    }
}
