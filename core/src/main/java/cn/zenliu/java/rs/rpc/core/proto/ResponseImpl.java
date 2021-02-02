package cn.zenliu.java.rs.rpc.core.proto;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.core.element.Meta;
import cn.zenliu.java.rs.rpc.core.element.Response;
import cn.zenliu.java.rs.rpc.core.element.Returns;
import cn.zenliu.java.rs.rpc.core.element.Transmit;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import mimic.MimicUtil;
import org.jetbrains.annotations.Nullable;

/**
 * RPC Response
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-12
 */

public final class ResponseImpl extends Transmit.BaseTransmit<Returns> implements Response {
    final static Class<Returns> TYPE_DATA = Returns.class;
    final static Class<MetaImpl> TYPE_META = MetaImpl.class;

    ResponseImpl(Payload payload) {
        super(payload);
    }

    @Override
    public Response addTrace(String name) {
        super.innerAddTrace(name);
        return this;
    }

    @Override
    public Response updateMeta(Meta meta) {
        super.innerUpdateMeta(meta);
        return this;
    }

    @Override
    public Response setMeta(Meta meta) {
        super.innerSetMeta(meta);
        return this;
    }

    @Override
    public long getTick() {
        if (data == null) parseData();
        return data.getTick();
    }

    @Override
    public boolean hasElement() {
        if (data == null) parseData();
        return data.getElement() != null;
    }

    public Result<Object> getResponse() {
        return hasElement() ? null : Rpc.autoDelegate.get() ? data.getResponse().map(MimicUtil::autoDisguise) : data.getResponse();
    }

    @Override
    public Response setResponse(Result<Object> res) {
        data = Returns.builder().response(Rpc.autoDelegate.get() ? res.map(MimicUtil::autoMimic) : res).build();
        return this;
    }

    @Override
    public @Nullable Object getElement() {
        if (data == null) parseData();
        return hasElement() ? Rpc.autoDelegate.get() ? MimicUtil.autoDisguise(data.getElement()) : data.getElement() : null;
    }

    @Override
    public Response setElement(Object element) {
        data = Returns.builder().element(Rpc.autoDelegate.get() ? MimicUtil.autoMimic(element) : element).build();
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder s = new StringBuilder("RESPONSE@[");
        if (meta != null) s.append(meta.toString());
        else if (payload != null) s.append(ByteBufUtil.prettyHexDump(payload.sliceMetadata()));
        s.append("]@");
        if (data != null) {
            s.append(data.getTick()).append("{").append(data.getResponse() == null ? data.getElement() : data.getResponse()).append("}");
        } else if (payload != null) s.append("{").append(ByteBufUtil.prettyHexDump(payload.sliceData())).append("}");
        return s.toString();
    }

    @Override
    protected Class<? extends Meta> metaType() {
        return TYPE_META;
    }

    @Override
    protected Class<? extends Returns> dataType() {
        return TYPE_DATA;
    }

    public static Response of(Payload payload) {
        return new ResponseImpl(payload);
    }

    public static Payload build(Meta meta, String name, Result<Object> result) {
        return of(null).setResponse(result).setMeta(name != null ? meta.addTrace(name) : meta).build();
    }

    public static Payload buildFirstElement(Meta meta, String name, Object element) {
        return of(null).setElement(element).setMeta(name != null ? meta.addTrace(name) : meta).build();
    }

    public static Payload buildElement(Object element) {
        return of(null).setElement(element).build();
    }

    public static Object parseElement(Payload payload) {
        return of(payload).getElement();
    }
}