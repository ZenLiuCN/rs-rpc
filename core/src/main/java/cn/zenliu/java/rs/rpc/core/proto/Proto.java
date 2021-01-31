package cn.zenliu.java.rs.rpc.core.proto;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.DefaultIdStrategy;
import io.protostuff.runtime.Delegate;
import io.protostuff.runtime.IdStrategy;
import io.protostuff.runtime.RuntimeSchema;
import mimic.Mimic;
import mimic.MimicDeep;
import mimic.MimicLight;
import org.jooq.lambda.Sneaky;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import static cn.zenliu.java.rs.rpc.core.proto.Proto.internal.STRATEGY;
import static cn.zenliu.java.rs.rpc.core.proto.Proto.internal.staticMapping;


public interface Proto {
    /**
     * use to mapping top level class as other class
     *
     * @param from source type
     * @param to   target type
     * @return null or last setting type
     */
    static Class<?> registerProtoStaticMapping(Class<?> from, Class<?> to) {
        return staticMapping.put(from, to);
    }

    /**
     * clean all static mapping
     */
    static void clearProtoStaticMapping() {
        staticMapping.clear();
    }

    /**
     * configure Protostuff delegate
     *
     * @param delegate the delegate
     */
    static void registerDelegate(Delegate<?> delegate) {
        STRATEGY.registerDelegate(delegate);
    }

    /**
     * configure Protostuff delegate
     *
     * @param delegate  the delegate
     * @param className the delegated class name
     */
    static void registerDelegate(String className, Delegate<?> delegate) {
        STRATEGY.registerDelegate(className, delegate);
    }

    Logger log = LoggerFactory.getLogger(Proto.class);

    static byte[] to(Object o) {
        //  synchronized (internal.buffer) {
        Object instance;
        try {
            Field h = o.getClass().getSuperclass().getDeclaredField("h");
            h.setAccessible(true);
            instance = h.get(o);
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.error("error to de proxy of {} ", o, ex);
            }
            instance = o;
        }
        final Schema<Object> schema = internal.schemaFrom.apply(instance);
        if (schema == null) throw new IllegalStateException("not found schema for type: " + o.getClass());
        try {
            return ProtostuffIOUtil.toByteArray(instance, schema, internal.buffer.get());
        } finally {
            internal.buffer.get().clear();
        }
        // }
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T> T from(byte[] data, Class<T> clz) {
        Schema<Object> schema;
        if (clz.isInterface()) {
            schema = internal.getSchema(MimicDeep.class);
        } else {
            schema = internal.getSchema(clz);
        }
        if (schema == null) throw new IllegalStateException("not found schema for type: " + clz);
        Object o = null;
        try {
            o = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(data, o, schema);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("error to decode message of {} \n{}", schema.typeClass(), ByteBufUtil.prettyHexDump(Unpooled.copiedBuffer(data)), e);
            }
            schema = internal.getSchema(MimicLight.class);
            if (schema == null) throw new IllegalStateException("not found schema for type: " + clz);
            o = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(data, o, schema);
        }
        if (o instanceof Mimic) {
            return (T) ((Mimic) o).disguise();
        }
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
        //        static final LinkedBuffer buffer = LinkedBuffer.allocate(512);
        static final ThreadLocal<LinkedBuffer> buffer = ThreadLocal.withInitial(() -> LinkedBuffer.allocate(512));//this may better
        static final Map<String, Schema<Object>> schemaPool = new ConcurrentHashMap<>();
        static final Function<String, Schema<Object>> schemaOf = Sneaky.function(internal::classFromString).andThen(internal::getSchema);
        static final Function<Object, Schema<Object>> schemaFrom = Sneaky.function(Object::getClass).andThen(internal::getSchema);
        static final Map<Class<?>, Class<?>> staticMapping = new ConcurrentHashMap<>();

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