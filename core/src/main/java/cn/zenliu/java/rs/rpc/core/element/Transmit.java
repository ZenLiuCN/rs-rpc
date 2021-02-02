package cn.zenliu.java.rs.rpc.core.element;


import cn.zenliu.java.rs.rpc.core.proto.Proto;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;

import java.nio.ByteBuffer;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-02
 */
public interface Transmit<T, R> {
    Meta getMeta();

    /**
     * true if this object is created from a payload
     */
    boolean isInput();

    /**
     * the creation time tick
     */
    long getTick();

    /**
     * update meta
     */
    Transmit<T, R> updateMeta(Meta meta);

    Transmit<T, R> addTrace(String name);

    Transmit<T, R> setMeta(Meta meta);

    /**
     * convert to a Payload
     */
    T build();

    abstract class BaseTransmit<R> implements Transmit<Payload, R> {
        protected final Payload payload;
        protected volatile Meta meta;
        protected boolean metaChanged = false;
        protected volatile R data;

        protected BaseTransmit(Payload payload) {
            this.payload = payload;
        }

        protected void parseData() {
            synchronized (this) {
                if (data == null)
                    try {
                        data = Proto.from(ByteBufUtil.getBytes(payload.sliceData()), dataType());
                    } finally {
                        payload.release();
                    }
            }
        }

        protected void parseMeta() {
            synchronized (this) {
                if (meta == null)
                    meta = Proto.from(ByteBufUtil.getBytes(payload.sliceMetadata()), metaType());
            }
        }

        protected abstract Class<? extends Meta> metaType();

        protected abstract Class<? extends R> dataType();

        @Override
        public Meta getMeta() {
            if (meta == null) parseMeta();
            return meta;
        }


        protected void innerAddTrace(String name) {
            if (name != null) updateMeta(getMeta().addTrace(name));
        }

        protected void innerUpdateMeta(Meta meta) {
            this.metaChanged = true;
            this.meta = meta;
        }

        protected void innerSetMeta(Meta meta) {
            this.metaChanged = true;
            this.meta = meta;
        }

        @Override
        public boolean isInput() {
            return payload != null;
        }

        @Override
        public Payload build() {
            //input with meta changed
            if (metaChanged && payload != null && data == null) {
                try {
                    return DefaultPayload.create(
                        payload.sliceData().nioBuffer(),
                        ByteBuffer.wrap(Proto.to(meta)));
                } finally {
                    payload.release();
                }
            } else //no meta update , no data update, is input
                if (!metaChanged && payload != null && data == null) {
                    return payload;
                } else //no meta update ,  data update, is input
                    if (!metaChanged && payload != null && data != null) {
                        try {
                            return DefaultPayload.create(
                                ByteBuffer.wrap(Proto.to(data)),
                                meta == null ? payload.hasMetadata() ? payload.sliceMetadata().nioBuffer() : null : ByteBuffer.wrap(Proto.to(meta))); //todo
                        } finally {
                            payload.release();
                        }
                    } else
                        return DefaultPayload.create(
                            data == null ? DefaultPayload.EMPTY_BUFFER : ByteBuffer.wrap(Proto.to(data)),
                            meta == null ? DefaultPayload.EMPTY_BUFFER : ByteBuffer.wrap(Proto.to(meta)));
        }
    }
}
