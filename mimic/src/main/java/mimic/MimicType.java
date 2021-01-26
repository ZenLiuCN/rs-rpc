package mimic;

import org.jooq.lambda.Seq;

import java.util.List;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface MimicType {
    /**
     * if a instance is match to Mimic
     *
     * @param instance the instance
     * @return can mimic by this
     */
    boolean match(Object instance);

    /**
     * if a type is match to Mimic
     *
     * @param type the type
     * @return can mimic by this
     */
    boolean match(Class<?> type);

    /**
     * mimic this instance
     *
     * @param instance instance
     * @return mimic
     */
    Object mimic(Object instance);

    /**
     * restore this mimic
     *
     * @param mimic mimic
     * @return original form
     */
    Object delegate(Object mimic);

    /**
     * all types
     */
    List<MimicType> mimicTypes = Seq.<MimicType>of(
        DefaultMimicType.VALUE
        , DefaultMimicType.OPTIONAL
        , DefaultMimicType.LIST
        , DefaultMimicType.MAP
        , DefaultMimicType.ENTRY
        , DefaultMimicType.TUPLE
        , DefaultMimicType.ARRAY
    ).toList();
}
