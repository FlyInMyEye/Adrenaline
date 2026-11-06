pub const native_backend: @import("backend/options.zig").Options = .{ .instruction_set = .avx2 };
const native = @import("aquifer/context.zig");
const std = @import("std");
pub export fn adrenaline_aquifer_context_size(count: usize) callconv(.c) usize {
    return native.aquifer_context_size(count);
}
pub export fn adrenaline_aquifer_context_init(memory: [*]align(8) u8, capacity: usize, locations: [*]const i16, count: usize, levels: [*]const i32, types: [*]const i8, min_x: i32, min_y: i32, min_z: i32, sx: i32, sz: i32) callconv(.c) c_int {
    return @intFromBool(native.aquifer_context_init(memory, capacity, locations[0..count], levels[0..count], types[0..count], min_x, min_y, min_z, sx, sz));
}
pub export fn adrenaline_aquifer_context_prepare(ctx: *const native.AquiferContext, density: [*]const f64, candidates: [*]i64, global_level: i32, global_type: i8, base_x: i32, base_y: i32, base_z: i32, width: usize, height: usize, deferred: [*]i32, materials: [*]i8) callconv(.c) usize {
    if (width == 0 or width > 32 or height == 0 or height > 1024 or width * width * height > 1024 or @abs(@as(i64, base_x)) >= 0x3fff0000 or @abs(@as(i64, base_y)) >= 0x3fff0000 or @abs(@as(i64, base_z)) >= 0x3fff0000) return std.math.maxInt(usize);
    const total = width * width * height;
    return native.prepare_aquifer_context(ctx, density[0..total], candidates[0 .. total * 2], global_level, global_type, base_x, base_y, base_z, width, height, deferred[0..total], materials[0..total]) orelse std.math.maxInt(usize);
}

pub export fn adrenaline_aquifer_context_validate(memory: [*]align(8) const u8, capacity: usize) callconv(.c) c_int {
    if (capacity < @sizeOf(native.AquiferContext)) return 0;
    const ctx: *const native.AquiferContext = @ptrCast(memory);
    if (ctx.magic != 0x4144514354583031 or ctx.count == 0 or ctx.count > 4096 or ctx.flags & ~@as(u32, 1) != 0) return 0;
    const n = ctx.count;
    if (native.aquifer_context_size(n) > capacity) return 0;
    const entries = memory + @sizeOf(native.AquiferContext);
    const levels = entries + n * @sizeOf(native.AquiferContextEntry);
    const types = levels + n * @sizeOf(i32);
    if (@intFromPtr(ctx.entries) != @intFromPtr(entries) or @intFromPtr(ctx.levels) != @intFromPtr(levels) or @intFromPtr(ctx.types) != @intFromPtr(types)) return 0;
    return 1;
}
