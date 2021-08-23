package xiao.parsec;

import java.nio.file.Files;
import java.nio.file.Paths;

public interface Utils {

    static String resource(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(Utils.class.getResource(path).toURI())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    static String unEscape(String s, char quote) {
        char[] a = s.toCharArray(), ss = new char[a.length];
        int l = a.length, cnt = 0;
        for (int i = 0; i < l; i++) {
            char c = a[i];
            if (c == quote && i + 1 < l) {
                // """"   ''''
                char n = a[i + 1];
                if (n == quote) {
                    i++;
                    ss[cnt++] = quote;
                } else {
                    ss[cnt++] = c;
                }
            } else if (c == '\\' && i + 1 < l) {
                // \' \" \\ \/ \t \r \n \b \f
                char n = a[i + 1];
                i++;
                if (n == quote) {
                    ss[cnt++] = quote;
                } else {
                    switch (n) {
                        // case quote: ss[cnt++] = quote ;break;
                        case '\\': ss[cnt++] = '\\';break;
                        case '/': ss[cnt++] = '/';break;
                        case 't': ss[cnt++] = '\t';break;
                        case 'r': ss[cnt++] = '\r';break;
                        case 'n': ss[cnt++] = '\n';break;
                        case 'b': ss[cnt++] = '\b';break;
                        case 'f': ss[cnt++] = '\f';break;
                        default:
                            i--;
                            ss[cnt++] = c;
                    }
                }
            } else {
                ss[cnt++] = c;
            }
        }
        return new String(ss, 0, cnt);
    }
}
