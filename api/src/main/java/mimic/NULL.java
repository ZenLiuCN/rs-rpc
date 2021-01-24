package mimic;

//use to hold a NULL place , request argument sequence is important
public enum NULL {
    instance;

    //https://github.com/kshchepanovskyi/protostuff-googlecode-exported/issues/141
    public static Object restore(Object val) {
        return val instanceof NULL ? null : val;
    }

    public static Object wrap(Object val) {
        return val == null ? instance : val;
    }
}