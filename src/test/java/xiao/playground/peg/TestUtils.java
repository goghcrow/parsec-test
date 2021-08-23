package xiao.playground.peg;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Predicate;

public interface TestUtils {
    /**
     * idea 动态开始 enable assert, 不用配置 -ea
     */
    static void runMainWithEnableAssert(Class<?> k, String[] args, Predicate<String> filter) {
        if (k.desiredAssertionStatus()) {
            return;
        }

        URL[] urls = ((URLClassLoader) k.getClassLoader()).getURLs();
        ClassLoader appCacheCl = ClassLoader.getSystemClassLoader();
        ClassLoader extNoCacheCl = appCacheCl.getParent();
        URLClassLoader cl = new URLClassLoader(urls, extNoCacheCl) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (filter.test(name)) {
                    setClassAssertionStatus(name, true);
                }
                return super.loadClass(name);
            }
        };

        try {
            cl.setClassAssertionStatus(k.getName(), true);
            Method main = cl.loadClass(k.getName()).getDeclaredMethod("main", String[].class);
            main.invoke(null, new Object[] { args } );
        } catch (InvocationTargetException e) {
            e.getTargetException().printStackTrace(System.err);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}
