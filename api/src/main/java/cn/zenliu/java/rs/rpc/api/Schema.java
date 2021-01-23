package cn.zenliu.java.rs.rpc.api;

import java.util.HashMap;
import java.util.Objects;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public final class Schema {
    final boolean list;
    final boolean mapKey;
    final boolean mapValue;
    final String rec;
    final boolean recList;
    final Class<?> type;
    final HashMap<String, Schema> values;

    Schema(boolean list, boolean mapKey, boolean mapValue, String rec, boolean recList, Class<?> type, HashMap<String, Schema> values) {
        this.list = list;
        this.mapKey = mapKey;
        this.mapValue = mapValue;
        this.rec = rec;
        this.recList = recList;
        this.type = type;
        this.values = values;
    }

    public static SchemaBuilder builder() {
        return new SchemaBuilder();
    }

    public static class SchemaBuilder {
        private boolean list = false;
        private boolean mapKey = false;
        private boolean mapValue = false;
        private String recursive;
        private boolean recList = false;
        private Class<?> type;
        private HashMap<String, Schema> values;

        SchemaBuilder() {
        }

        public SchemaBuilder list() {
            this.list = true;
            this.mapKey = false;
            this.mapValue = false;
            return this;
        }

        public SchemaBuilder mapKey() {
            this.list = false;
            this.mapKey = true;
            this.mapValue = false;
            return this;
        }

        public SchemaBuilder mapValue() {
            this.list = false;
            this.mapKey = false;
            this.mapValue = true;
            return this;
        }

        public SchemaBuilder type(Class<?> type) {
            this.type = type;
            return this;
        }

        public SchemaBuilder recursive(String field, boolean isList) {
            this.recursive = field;
            this.recList = isList;
            return this;
        }

        public SchemaBuilder withValue(String fieldName, Schema type) {
            values = values == null ? new HashMap<>() : values;
            values.put(fieldName, type);
            return this;
        }

        public SchemaBuilder values(HashMap<String, Schema> values) {
            this.values = values;
            return this;
        }

        public Schema build() {
            return new Schema(list, mapKey, mapValue, recursive, recList, Objects.requireNonNull(type, "type must not null!"), values);
        }

        public String toString() {
            return "Schema.SchemaBuilder(list=" + this.list + ", mapKey=" + this.mapKey + ", mapValue=" + this.mapValue + ", type=" + this.type + ", values=" + this.values + ")";
        }
    }

    public static Schema light(Class<?> type) {
        return Schema.builder().type(type).build();
    }
}
