package cn.zenliu.java.rs.rpc.core;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.DefaultIdStrategy;
import io.protostuff.runtime.IdStrategy;
import io.protostuff.runtime.RuntimeSchema;
import org.jooq.lambda.Sneaky;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;


interface Proto {

    static byte[] to(Object o) {
        Object instance;
        try {
            Field h = o.getClass().getSuperclass().getDeclaredField("h");
            h.setAccessible(true);
            instance = h.get(o);
        } catch (Exception ex) {
            instance = o;
        }
        final Object target = instance;
        final Schema<Object> schema = internal.schemaFrom.apply(target);
        if (schema == null) throw new IllegalStateException("not found schema for type: " + o.getClass());
        try {
            return ProtostuffIOUtil.toByteArray(target, schema, internal.buffer);
        } finally {
            internal.buffer.clear();
        }
    }

    static Object from(byte[] data, String clz) {
        final Schema<Object> schema = internal.schemaOf.apply(clz);
        if (schema == null) throw new IllegalStateException("not found schema for type: " + clz);
        Object o = schema.newMessage();
        ProtostuffIOUtil.mergeFrom(data, o, schema);
        return o;
    }


    @SuppressWarnings("unchecked")
    static <T> T from(byte[] data, Class<T> clz) {
        final Schema<Object> schema = internal.getSchema(clz);
        if (schema == null) throw new IllegalStateException("not found schema for type: " + clz);
        Object o = schema.newMessage();
        ProtostuffIOUtil.mergeFrom(data, o, schema);
        return (T) o;
    }


    final class internal {
        static final DefaultIdStrategy STRATEGY = new DefaultIdStrategy(
            IdStrategy.DEFAULT_FLAGS
                | IdStrategy.ALLOW_NULL_ARRAY_ELEMENT
                | IdStrategy.MORPH_COLLECTION_INTERFACES
                | IdStrategy.MORPH_MAP_INTERFACES
                | IdStrategy.MORPH_NON_FINAL_POJOS
        );
        static final LinkedBuffer buffer = LinkedBuffer.allocate(512);
        static final Map<String, Schema<Object>> schemaPool = new ConcurrentHashMap<>();
        static final Function<String, Schema<Object>> schemaOf = Sneaky.function(internal::classFromString).andThen(internal::getSchema);
        static final Function<Object, Schema<Object>> schemaFrom = Sneaky.function(Object::getClass).andThen(internal::getSchema);

        @SuppressWarnings("unchecked")
        static Schema<Object> getSchema(Class<?> clz) {
            Schema<Object> schema = schemaPool.get(clz.getCanonicalName());
            if (schema == null) {
                try {
                    schema = (Schema<Object>) RuntimeSchema.createFrom(clz, STRATEGY);
                } catch (Exception e) {
                    return null;
                }
                schemaPool.put(clz.getCanonicalName(), schema);
            }
            return schema;
        }

        static Schema<Object> getSchema(String clz) {
            Schema<Object> schema = schemaPool.get(clz);
            if (schema == null) {
                try {
                    final Class<?> aClass = classFromString(clz);
                    return getSchema(aClass);
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
            return schema;
        }

        static Class<?> classFromString(String name) throws ClassNotFoundException {
            //@formatter:off
            if (long.class.getCanonicalName().equals(name)) {return long.class;}
            else if (int.class.getCanonicalName().equals(name)) {return int.class;}
            else if (byte.class.getCanonicalName().equals(name)) {return byte.class;}
            else if (short.class.getCanonicalName().equals(name)) {return short.class;}
            else if (boolean.class.getCanonicalName().equals(name)) {return boolean.class;}
            else if (double.class.getCanonicalName().equals(name)) {return double.class;}
            else if (float.class.getCanonicalName().equals(name)) {return float.class;}
            else if (name.endsWith("[]")) {
                //array
                final String arrayName = "[L" + name.replace(Pattern.quote("[]"), ";");
                return Class.forName(arrayName);
            }
            return name.getClass().getClassLoader().loadClass(name);
            //@formatter:on
        }
    }
}