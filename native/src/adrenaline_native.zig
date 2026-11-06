pub const native_backend: @import("backend/options.zig").Options = .{};
comptime {
    if (!@import("builtin").is_test) @import("jni/registry.zig").register();
}
