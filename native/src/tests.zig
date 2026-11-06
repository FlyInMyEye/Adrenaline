pub const native_backend: @import("backend/options.zig").Options = .{ .instruction_set = .avx2 };
test {
    _ = @import("noise/sampling.zig");
    _ = @import("noise/gradients.zig");
    _ = @import("aquifer/selection.zig");
    _ = @import("aquifer/locate_tests.zig");
    _ = @import("density/fused.zig");
}
