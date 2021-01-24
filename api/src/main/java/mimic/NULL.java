package mimic;

//use to hold a NULL place , request argument sequence is important
public final class NULL {
    private NULL() {
    }

    //https://github.com/kshchepanovskyi/protostuff-googlecode-exported/issues/141
    public static final NULL instance = new NULL();
}