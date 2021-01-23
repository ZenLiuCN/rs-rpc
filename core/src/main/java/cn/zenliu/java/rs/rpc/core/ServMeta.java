package cn.zenliu.java.rs.rpc.core;

import lombok.Builder;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-18
 */
@Builder
@ToString
public class ServMeta implements Serializable {
    private static final long serialVersionUID = -3906227121661168621L;
    /**
     * Remote Name
     */
    final String name;
    /**
     * Supported Service Domain (Sign without methods)
     */
    @Builder.Default final @Nullable List<String> service = new ArrayList<>();
    @Builder.Default final @Nullable Set<String> known = new HashSet<>();

    public boolean isKnown(Set<String> services) {
        return (services.isEmpty() && (known == null || known.isEmpty()))
            || (known != null && known.size() == services.size() && services.containsAll(known));
    }
}
