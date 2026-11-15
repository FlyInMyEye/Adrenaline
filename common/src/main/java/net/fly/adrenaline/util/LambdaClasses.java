package net.fly.adrenaline.util;

public final class LambdaClasses {
    private LambdaClasses() {
    }

    public static boolean isFrom(Class<?> type, Class<?> owner) {
        return type.isHidden() && type.isSynthetic() && type.getName().startsWith(owner.getName() + "$$Lambda");
    }
}
