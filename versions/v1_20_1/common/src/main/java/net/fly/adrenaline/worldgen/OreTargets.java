package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.fly.adrenaline.mixin.MixinHolderReferenceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.TargetBlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

public final class OreTargets implements Iterator<TargetBlockState> {
    private static final TargetBlockState[] EMPTY = new TargetBlockState[0];
    private static final boolean BLOCK_RULE = unmodified(BlockMatchTest.class, boolean.class, BlockState.class, RandomSource.class)
        && unmodified(BlockBehaviour.BlockStateBase.class, boolean.class, Block.class);
    private static final boolean STATE_RULE = unmodified(BlockStateMatchTest.class, boolean.class, BlockState.class, RandomSource.class);
    private static final boolean TAG_RULE = unmodified(TagMatchTest.class, boolean.class, BlockState.class, RandomSource.class)
        && unmodified(BlockBehaviour.BlockStateBase.class, boolean.class, TagKey.class)
        && unmodified(Block.class, Holder.Reference.class)
        && unmodified(Holder.Reference.class, boolean.class, TagKey.class)
        && unmodified(Holder.Reference.class, void.class, Collection.class);
    private static final boolean ALWAYS_RULE = unmodified(AlwaysTrueTest.class, boolean.class, BlockState.class, RandomSource.class);
    private static final boolean ORIGINAL_ORE_TEST = originalOreTest();

    private final Lookup lookup;
    private BlockState lastState;
    private Match lastMatch;
    private TargetBlockState[] current = EMPTY;
    private BlockState currentState;
    private int cursor;

    private OreTargets(Lookup lookup) {
        this.lookup = lookup;
    }

    public static OreTargets create(OreConfiguration config) {
        if (!ORIGINAL_ORE_TEST) return null;
        Lookup lookup = ((OreTargetLookup) config).adrenaline$oreTargets();
        return lookup.supported ? new OreTargets(lookup) : null;
    }

    public boolean matching(List<TargetBlockState> list) {
        return this.lookup.source == list;
    }

    public Iterator<TargetBlockState> iterator(BlockState state) {
        Object tags = this.lookup.tags ? ((MixinHolderReferenceAccessor<?>) state.getBlock().builtInRegistryHolder()).adrenaline$getTags() : null;
        Match result = state == this.lastState ? this.lastMatch : this.lookup.cache.get(state);
        if (result == null || result.tags != tags) result = this.lookup.match(state, tags);
        this.lastState = state;
        this.lastMatch = result;
        this.current = result.targets;
        this.currentState = state;
        this.cursor = 0;
        return this;
    }

    public boolean selected(BlockState state, TargetBlockState target) {
        return this.currentState == state && this.cursor > 0 && this.current[this.cursor - 1] == target;
    }

    @Override
    public boolean hasNext() {
        return this.cursor < this.current.length;
    }

    @Override
    public TargetBlockState next() {
        if (!this.hasNext()) throw new NoSuchElementException();
        return this.current[this.cursor++];
    }

    private record Match(Object tags, TargetBlockState[] targets) {
    }

    public static final class Lookup {
        private final List<TargetBlockState> source;
        private final TargetBlockState[] targets;
        private final ConcurrentHashMap<BlockState, Match> cache = new ConcurrentHashMap<>();
        private final boolean supported;
        private final boolean tags;

        public Lookup(List<TargetBlockState> source) {
            this.source = source;
            this.targets = source.toArray(EMPTY);
            boolean supported = true;
            boolean tags = false;
            for (TargetBlockState target : this.targets) {
                Class<?> type = target.target.getClass();
                supported &= type == BlockMatchTest.class && BLOCK_RULE || type == BlockStateMatchTest.class && STATE_RULE
                    || type == TagMatchTest.class && TAG_RULE || type == AlwaysTrueTest.class && ALWAYS_RULE;
                tags |= type == TagMatchTest.class;
            }
            this.supported = supported;
            this.tags = tags;
        }

        public boolean current(List<TargetBlockState> source) {
            if (this.source != source || this.targets.length != source.size()) return false;
            for (int i = 0; i < this.targets.length; i++) {
                if (this.targets[i] != source.get(i)) return false;
            }
            return true;
        }

        private Match match(BlockState state, Object tags) {
            TargetBlockState[] matched = null;
            int count = 0;
            for (TargetBlockState target : this.targets) {
                if (target.target.test(state, null)) {
                    if (matched == null) matched = new TargetBlockState[this.targets.length];
                    matched[count++] = target;
                }
            }
            TargetBlockState[] targets = count == 0 ? EMPTY : count == this.targets.length ? this.targets : Arrays.copyOf(matched, count);
            Match result = new Match(tags, targets);
            this.cache.put(state, result);
            return result;
        }
    }

    private static boolean originalOreTest() {
        Set<String> preserved = new HashSet<>();
        for (var method : OreFeature.class.getDeclaredMethods()) {
            if (method.getReturnType() == boolean.class && Arrays.equals(method.getParameterTypes(), new Class<?>[]{RandomSource.class, float.class})) {
                preserved.add(method.getName() + MethodType.methodType(boolean.class, RandomSource.class, float.class).descriptorString());
            }
        }
        for (var method : OreFeature.class.getDeclaredMethods()) {
            if (method.getReturnType() == boolean.class && Arrays.equals(method.getParameterTypes(),
                new Class<?>[]{BlockState.class, Function.class, RandomSource.class, OreConfiguration.class, TargetBlockState.class, BlockPos.MutableBlockPos.class})) {
                return !SurfaceRuleHooks.hookedOutside(OreFeature.class,
                    method.getName() + MethodType.methodType(boolean.class, method.getParameterTypes()).descriptorString(), preserved);
            }
        }
        return false;
    }

    private static boolean unmodified(Class<?> type, Class<?> result, Class<?>... parameters) {
        boolean found = false;
        for (var method : type.getDeclaredMethods()) {
            if (method.getReturnType() == result && Arrays.equals(method.getParameterTypes(), parameters)) {
                found = true;
                if (SurfaceRuleHooks.hooked(type, method.getName() + MethodType.methodType(result, parameters).descriptorString())) return false;
            }
        }
        return found;
    }
}
