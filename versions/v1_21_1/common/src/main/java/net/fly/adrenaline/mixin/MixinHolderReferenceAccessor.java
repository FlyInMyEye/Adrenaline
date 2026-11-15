package net.fly.adrenaline.mixin;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Holder.Reference.class)
public interface MixinHolderReferenceAccessor<T> {
    @Accessor("tags")
    Set<TagKey<T>> adrenaline$getTags();
}
