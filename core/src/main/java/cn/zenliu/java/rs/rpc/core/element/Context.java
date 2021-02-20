package cn.zenliu.java.rs.rpc.core.element;

import java.util.function.Supplier;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-20
 */
public interface Context {
    String getName();

    void onDebug(String template, Object... args);

    void onDebug(String template, Supplier<Object[]> args);

    void info(String template, Object... args);

    void debug(String template, Object... args);

    void warn(String template, Object... args);

    void error(String template, Object... args);
}
