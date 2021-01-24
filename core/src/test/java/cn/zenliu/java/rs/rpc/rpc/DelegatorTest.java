package cn.zenliu.java.rs.rpc.rpc;


import cn.zenliu.java.rs.rpc.core.Proto;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import mimic.Mimic;
import mimic.MimicUtil;
import mimic.Proxy;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.jooq.lambda.tuple.Tuple.tuple;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public class DelegatorTest {
    public interface Item {
        long getId();

        String getName();

        String getDisc();

    }

    public interface Dictionary {
        long getId();

        long getSystemIdentity();

        String getName();

        String getDisc();

        boolean isTree();

        boolean isEditable();

        boolean isNumericKey();

        Class<?> getValueClass();

        List<Item> getItems();

        default boolean aha() {
            return true;
        }
    }


    public static void main(String[] args) {
        generate();
        // validate();
    }

    static void validate() {
        final byte[] data = Base64.getDecoder().decode(
            "C5IBMmNuLnplbmxpdS5qYXZhLnJzLnJwYy5ycGMuRGVsZWdhdG9yVGVzdCREaWN0aW9uYXJ5DBPSARFDb25jdXJyZW50SGFzaE1hcAsLSgREaXNjDBNKAkExFAwLC0oKTnVtZXJpY0tleQwTCAAUDAsLSgRUcmVlDBMIABQMCwtKCEVkaXRhYmxlDBMIABQMCwtKClZhbHVlQ2xhc3MME5IBEGphdmEubGFuZy5TdHJpbmcUDAsLSg5TeXN0ZW1JZGVudGl0eQwTMHsUDAsLSgVJdGVtcwwTygEJQXJyYXlMaXN0C/oHH2NuLnplbmxpdS5qYXZhLnJzLnJwYy5hcGkuTWltaWMLkgEsY24uemVubGl1LmphdmEucnMucnBjLnJwYy5EZWxlZ2F0b3JUZXN0JEl0ZW0ME9IBEUNvbmN1cnJlbnRIYXNoTWFwCwtKBERpc2MME0oBQRQMCwtKAklkDBMwexQMCwtKBE5hbWUME0oBQRQMFBvSAQdIYXNoTWFwHAwUDAsLSgJJZAwTMHwUDAsLSgROYW1lDBNKAUEUDBQb0gEHSGFzaE1hcAsLSgVJdGVtcwwTKAEUDBw="
        );
        System.out.println(Proto.from(data, Dictionary.class));
        final byte[] data2 = Base64.getDecoder().decode(
            "CwgAEAAYACgAM5IBMmNuLnplbmxpdS5qYXZhLnJzLnJwYy5ycGMuRGVsZWdhdG9yVGVzdCREaWN0aW9uYXJ5NAwT0gERQ29uY3VycmVudEhhc2hNYXALC0oKTnVtZXJpY0tleQwTCAAUDAsLSgpWYWx1ZUNsYXNzDBOSARBqYXZhLmxhbmcuU3RyaW5nFAwLC0oOU3lzdGVtSWRlbnRpdHkMEzB7FAwLC0oETmFtZQwTSgFBFAwLC0oERGlzYwwTSgFBFAwLC0oIRWRpdGFibGUMEwgAFAwLC0oEVHJlZQwTCAAUDAsLSgJJZAwTMHsUDBQ=");
        System.out.println(Proto.from(data2, Dictionary.class));
    }

    static void generate() {
        final Dictionary d = Proxy.of(Dictionary.class, Seq.of(
            tuple("Id", 123L),
            tuple("SystemIdentity", 123L),
            tuple("Name", "A"),
            tuple("Disc", "A"),
            tuple("Tree", false),
            tuple("Editable", false),
            tuple("NumericKey", false),
            tuple("ValueClass", String.class)
        ).toMap(Tuple2::v1, Tuple2::v2));
        final Dictionary d2 = Proxy.of(Dictionary.class, Seq.of(
            tuple("Id", 124L),
            tuple("SystemIdentity", 123L),
            tuple("Name", "A"),
            tuple("Disc", "A1"),
            tuple("Tree", false),
            tuple("Editable", false),
            tuple("NumericKey", false),
            tuple("ValueClass", String.class),
            tuple("Items", Arrays.asList(
                Proxy.of(Item.class, Seq.of(
                    tuple("Id", 123L),
                    tuple("Name", "A"),
                    tuple("Disc", "A")
                ).toMap(Tuple2::v1, Tuple2::v2))
            ))
        ).toMap(Tuple2::v1, Tuple2::v2));
        final byte[] bytes1 = Proto.to(d);
        System.out.println(Base64.getEncoder().encodeToString(bytes1));
        final Dictionary from1 = Proto.from(bytes1, Dictionary.class);
        System.out.println(from1);
        final Mimic<Dictionary> mm = MimicUtil.mimic(d2, Dictionary.class);
        System.out.println(mm);
        final byte[] bytes = Proto.to(mm.delegate());
        System.out.println(ByteBufUtil.prettyHexDump(Unpooled.copiedBuffer(bytes)));
        System.out.println(Base64.getEncoder().encodeToString(bytes));
        final Dictionary from = Proto.from(bytes, Dictionary.class);
        System.out.println(from);
    }
}