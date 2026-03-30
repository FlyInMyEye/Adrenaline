package net.fly.adrenaline.mixin;

import net.fly.adrenaline.util.SectionSerializationCache;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunk.class)
public class MixinLevelChunk implements SectionSerializationCache {

    @Unique
    private Tag[][] adrenaline$sectionTags;

    @Override
    public Tag[][] adrenaline$getSectionTags() {
        return adrenaline$sectionTags;
    }

    @Override
    public void adrenaline$setSectionTags(Tag[][] tags) {
        this.adrenaline$sectionTags = tags;
    }
}
