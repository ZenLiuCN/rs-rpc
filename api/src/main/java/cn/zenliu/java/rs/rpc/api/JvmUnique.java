package cn.zenliu.java.rs.rpc.api;

import lombok.Getter;
import lombok.SneakyThrows;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;

import static cn.zenliu.java.rs.rpc.api.Tick.fromNowUTC;

/**
 * Tool to Build a Unique Id For Current Jvm<br>
 * 1. every Jvm Instance is differ by {@link JvmUnique#PROCESS_ID}
 * 2. every Machine is differ by {@link JvmUnique#MAC_HASH}
 * 3. for differ with container like docker by {@link JvmUnique.internal#random}
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-17
 */
public interface JvmUnique {
    long PROCESS_ID = internal.ProcessIdReader();
    String MAC_HASH = internal.getRawOrMd5Mac();
    long TICK = fromNowUTC();
    char SPLITTER = '|';

    static String hexdump(byte[] array) {
        final StringBuilder result = new StringBuilder();
        for (byte b : array) {
            result.append(Integer.toHexString(b));
        }
        return result.toString();
    }

    static String uniqueNameWithRandom(String name) {
        return new JvmUniqueId(name).toString();
    }

    static String uniqueNameWithoutRandom(String name) {
        return new JvmUniqueId(name, 0).toString();
    }

    static boolean isMine(String id) {
        return JvmUniqueId.parse(id).map(x -> x.isMine() ? true : null).isPresent();
    }

    static boolean isMineFast(String id) {
        return JvmUniqueId.fastCompare(id);
    }

    static Optional<JvmUniqueId> dumpName(String uniqueName) {
        return JvmUniqueId.parse(uniqueName);
    }

    final class internal {
        static final SecureRandom secureRandom = new SecureRandom();

        @SneakyThrows
        static long ProcessIdReader() {
            return Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        }

        @SneakyThrows
        static String MacReader() {
            final StringBuilder result = new StringBuilder();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface net : Collections.list(nets)) {
                if (net.getHardwareAddress() == null || net.isLoopback() || !net.isUp()) continue;
                if (result.length() > 0) result.append(";");
                result.append(hexdump(net.getHardwareAddress()));
            }
            return result.toString();
        }

        @SneakyThrows
        static String Md5Macs() {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(MacReader().getBytes(StandardCharsets.UTF_8)));
        }

        static String getRawOrMd5Mac() {
            final String s = MacReader();
            return s.length() > 32 ? Md5Macs() : s;
        }

        static int random() {
            secureRandom.setSeed(System.currentTimeMillis());
            return secureRandom.nextInt();
        }
    }

    final class JvmUniqueId {
        @Getter final String name;
        @Getter final long processId;
        @Getter final long tick;
        @Getter final String macHash;
        @Getter final int random;

        JvmUniqueId(String name, long processId, String macHash, long startTimestamp, int random) {
            this.name = name;
            this.processId = processId;
            this.tick = startTimestamp;
            this.macHash = macHash;
            this.random = random;
        }

        JvmUniqueId(String name, int random) {
            this.name = name;
            this.processId = PROCESS_ID;
            this.macHash = MAC_HASH;
            this.random = random;
            this.tick = TICK;
        }

        JvmUniqueId(String name) {
            this.name = name;
            this.processId = PROCESS_ID;
            this.macHash = MAC_HASH;
            this.tick = TICK;
            this.random = internal.random();
        }

        public static boolean fastCompare(String id) {
            if (id == null) return false;
            final char[] chars = id.toCharArray();
            if (chars.length == 0) return false;
            try {
                int cnt = 0;
                StringBuilder valBuilder = new StringBuilder();
                String mac = null;
                long pid = -1;
                long ts = -1;
                for (char aChar : chars) {
                    if (aChar == SPLITTER) {
                        switch (cnt) {
                            case 0: {
                                valBuilder.setLength(0);
                                cnt = 1;
                                continue;
                            }
                            case 1: {
                                pid = Long.parseLong(valBuilder.toString(), 16);
                                if (pid != PROCESS_ID) return false;
                                valBuilder.setLength(0);
                                cnt = 2;
                                continue;
                            }
                            case 2: {
                                mac = valBuilder.toString();
                                if (!mac.equals(MAC_HASH)) return false;
                                valBuilder.setLength(0);
                                cnt = 3;
                                continue;
                            }
                            case 3: {
                                ts = Long.parseLong(valBuilder.toString(), 16);
                                return ts == TICK;
                            }
                            default:
                                return false;
                        }
                    } else {
                        valBuilder.append(aChar);
                    }
                }
            } catch (NumberFormatException e) {
                return false;
            }
            return false;
        }

        public static Optional<JvmUniqueId> parse(String id) {
            if (id == null) return Optional.empty();
            final char[] chars = id.toCharArray();
            if (chars.length == 0) return Optional.empty();
            try {
                StringBuilder valBuilder = new StringBuilder();
                int cnt = 0;
                String name = null;
                String mac = null;
                long pid = -1;
                long ts = -1;
                int rnd = 0;
                for (char aChar : chars) {
                    if (aChar == SPLITTER) {
                        switch (cnt) {
                            case 0: {
                                name = valBuilder.toString();
                                valBuilder.setLength(0);
                                cnt = 1;
                                continue;
                            }
                            case 1: {
                                pid = Long.parseLong(valBuilder.toString(), 16);
                                valBuilder.setLength(0);
                                cnt = 2;
                                continue;
                            }
                            case 2: {
                                mac = valBuilder.toString();
                                valBuilder.setLength(0);
                                cnt = 3;
                                continue;
                            }
                            case 3: {
                                ts = Long.parseLong(valBuilder.toString(), 16);
                                valBuilder.setLength(0);
                                cnt = 4;
                                continue;
                            }
                            default:
                                return Optional.empty();
                        }
                    } else {
                        valBuilder.append(aChar);
                    }
                }
                if (valBuilder.length() > 0) rnd = Integer.parseInt(valBuilder.toString(), 16);
                if (name == null || mac == null) return Optional.empty();
                return Optional.of(new JvmUniqueId(name, pid, mac, ts, rnd));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        /**
         * decider whether current id is generate in current jvm instance.
         */
        public boolean isMine() {
            return processId == PROCESS_ID && macHash.equals(MAC_HASH);
        }

        @Override
        public String toString() {
            return name + SPLITTER + Long.toHexString(processId) + SPLITTER + macHash + SPLITTER + Long.toHexString(tick) + SPLITTER + Integer.toHexString(random);
        }

        public String dump() {
            return "IDENTITY:" + name + ";PROCESS:" + processId + ";MAC:" + macHash + ";VM_TICK:" + tick + ";RND:" + random;
        }
    }
}
