package net.fly.adrenaline.util;

import net.minecraft.nbt.Tag;

public interface SectionSerializationCache {

    Tag[][] adrenaline$getSectionTags();

    void adrenaline$setSectionTags(Tag[][] tags);
}
