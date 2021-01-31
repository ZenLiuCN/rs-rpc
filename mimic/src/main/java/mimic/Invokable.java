package mimic;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
@FunctionalInterface
public
interface Invokable {
    Object invoke(Object[] args);

    default Object invokeWith(Object... args) {
        return invoke(args);
    }

}
