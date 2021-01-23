package cn.zenliu.java.rs.rpc.rpc;

import cn.zenliu.java.rs.rpc.api.Delegator;
import cn.zenliu.java.rs.rpc.core.Proto;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.util.Arrays;
import java.util.List;

import static org.jooq.lambda.tuple.Tuple.tuple;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public class DelegatorTest {
    public interface Dictionary {
        long getId();

        long getSystemIdentity();

        String getName();

        String getDisc();

        boolean isTree();

        boolean isEditable();

        boolean isNumericKey();

        Class<?> getValueClass();

        default boolean aha() {
            return true;
        }
    }

    static final class Lists {
        final List<Dictionary> l;

        Lists(List<Dictionary> l) {
            this.l = l;
        }
    }

    public static void main(String[] args) {
        final Dictionary d = Delegator.proxy(Dictionary.class, Seq.of(
            tuple("Id", 123L),
            tuple("SystemIdentity", 123L),
            tuple("Name", "A"),
            tuple("Disc", "A"),
            tuple("Tree", false),
            tuple("Editable", false),
            tuple("NumericKey", false),
            tuple("ValueClass", String.class)
        ).toMap(Tuple2::v1, Tuple2::v2));
        final Dictionary d2 = Delegator.proxy(Dictionary.class, Seq.of(
            tuple("Id", 124L),
            tuple("SystemIdentity", 123L),
            tuple("Name", "A"),
            tuple("Disc", "A"),
            tuple("Tree", false),
            tuple("Editable", false),
            tuple("NumericKey", false),
            tuple("ValueClass", String.class)
        ).toMap(Tuple2::v1, Tuple2::v2));
        System.out.println(d);
        final byte[] bytes = Proto.to(d);
        System.out.println(ByteBufUtil.prettyHexDump(Unpooled.copiedBuffer(bytes)));
        final Dictionary from = Proto.from(bytes, Dictionary.class);
        System.out.println(from);
        System.out.println(from.getId());
        System.out.println(from.aha());
        Lists di = new Lists(Arrays.asList(d, d2));
        final byte[] bytes1 = Proto.to(di);
        System.out.println(ByteBufUtil.prettyHexDump(Unpooled.copiedBuffer(bytes1)));
        final Lists ls = Proto.from(bytes1, Lists.class);
        System.out.println(ls.l);
        System.out.println(ls.l.get(0).aha());
        System.out.println(ls.l.get(1).getId());
    }
}