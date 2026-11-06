const std = @import("std");
const c = @import("../core/jni.zig").c;
const backend_options = @import("../backend/options.zig");
const core_limits = @import("../core/limits.zig");
const core_vectors = @import("../core/vectors.zig");

pub const FluidLevelInfo = struct { maximum: i32, uniform_type: i8 };

pub fn aquifer_maximum_fluid_level(fluid_levels: []const c.jint, fluid_types: []const c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, grid_x: i32, grid_y: i32, grid_z: i32) ?FluidLevelInfo {
    const local_grid_x = grid_x - min_grid_x;
    const local_grid_y = grid_y - min_grid_y;
    const local_grid_z = grid_z - min_grid_z;
    if (local_grid_x < 0 or local_grid_y < 1 or local_grid_z < 0 or local_grid_x + 1 >= grid_size_x or local_grid_z + 1 >= grid_size_z) {
        return null;
    }
    const size_x: usize = @intCast(grid_size_x);
    const size_z: usize = @intCast(grid_size_z);
    if (comptime backend_options.is_simd_backend) {
        if (grid_size_x > 0 and grid_size_x <= core_limits.max_grid_size and grid_size_z > 0 and grid_size_z <= core_limits.max_grid_size and local_grid_y <= core_limits.max_grid_size and fluid_types.len >= fluid_levels.len) {
            const lx: usize = @intCast(local_grid_x);
            const ly: usize = @intCast(local_grid_y);
            const lz: usize = @intCast(local_grid_z);
            const last = ((ly + 1) * size_z + lz + 1) * size_x + lx + 1;
            if (last >= fluid_levels.len) return null;
            const first_row = ((ly - 1) * size_z + lz) * size_x + lx;
            const first_level = fluid_levels[first_row];
            const first_type = fluid_types[first_row];
            const target: core_vectors.Vec4i = @splat(first_level);
            const type_target: core_vectors.Vec4i = @splat(first_type);
            var maximums: core_vectors.Vec4i = @splat(std.math.minInt(i32));
            var same: @Vector(4, bool) = @splat(true);
            inline for (0..3) |dy| {
                const row = ((ly + dy - 1) * size_z + lz) * size_x + lx;
                const values: core_vectors.Vec4i = .{ fluid_levels[row], fluid_levels[row + 1], fluid_levels[row + size_x], fluid_levels[row + size_x + 1] };
                const types: core_vectors.Vec4i = .{ fluid_types[row], fluid_types[row + 1], fluid_types[row + size_x], fluid_types[row + size_x + 1] };
                maximums = @max(maximums, values);
                same = same & (values == target) & (types == type_target);
            }
            return .{ .maximum = @reduce(.Max, maximums), .uniform_type = if (@reduce(.And, same)) first_type else 0 };
        }
    }
    var maximum: i32 = std.math.minInt(i32);
    var first_level: i32 = 0;
    var first_type: i8 = 0;
    var first = true;
    var uniform = true;
    var offset_x: i32 = 0;
    while (offset_x <= 1) : (offset_x += 1) {
        var offset_y: i32 = -1;
        while (offset_y <= 1) : (offset_y += 1) {
            const local_y = local_grid_y + offset_y;
            if (local_y < 0) return null;
            const row: usize = (@as(usize, @intCast(local_y)) * size_z + @as(usize, @intCast(local_grid_z))) * size_x + @as(usize, @intCast(local_grid_x + offset_x));
            var offset_z: i32 = 0;
            while (offset_z <= 1) : (offset_z += 1) {
                const cache_index = row + @as(usize, @intCast(offset_z)) * size_x;
                if (cache_index >= fluid_levels.len) return null;
                const level = fluid_levels[cache_index];
                const fluid_type = fluid_types[cache_index];
                if (first) {
                    first_level = level;
                    first_type = fluid_type;
                    first = false;
                } else if (level != first_level or fluid_type != first_type) uniform = false;
                maximum = @max(maximum, level);
            }
        }
    }
    return .{ .maximum = maximum, .uniform_type = if (uniform) first_type else 0 };
}

pub fn aquifer_type_at(level: c.jint, fluid_type: c.jbyte, y: i32) ?u8 {
    const type_code: u8 = @bitCast(fluid_type);
    if (type_code != 2 and type_code != 3) {
        return null;
    }
    return if (y >= level) 1 else type_code;
}

pub fn global_aquifer_material(y: i32, fluid_level: c.jint, fluid_type: c.jbyte) ?u8 {
    const type_code: u8 = @bitCast(fluid_type);
    if (type_code != 2 and type_code != 3) {
        return null;
    }
    const lava_level = @min(@as(i32, -54), fluid_level);
    if (y < lava_level) {
        return 3;
    }
    return if (y < fluid_level) type_code else 1;
}

pub fn aquifer_similarity(first_distance: i32, second_distance: i32) f64 {
    const difference: i64 = @as(i64, second_distance) - @as(i64, first_distance);
    const absolute_difference = if (difference < 0) -difference else difference;
    return 1.0 - @as(f64, @floatFromInt(absolute_difference)) / 25.0;
}

pub fn aquifer_pressure_uses_barrier(y: i32, first_level: c.jint, first_type: u8, second_level: c.jint, second_type: u8) bool {
    if ((first_type == 3 and second_type == 2) or (first_type == 2 and second_type == 3)) {
        return false;
    }
    const level_difference: i64 = @as(i64, first_level) - @as(i64, second_level);
    const absolute_level_difference: i64 = if (level_difference < 0) -level_difference else level_difference;
    if (absolute_level_difference == 0) {
        return false;
    }
    const average_level = 0.5 * @as(f64, @floatFromInt(@as(i64, first_level) + @as(i64, second_level)));
    const delta_y = @as(f64, @floatFromInt(y)) + 0.5 - average_level;
    const boundary = @as(f64, @floatFromInt(absolute_level_difference)) / 2.0 - @abs(delta_y);
    const pressure = if (delta_y > 0.0)
        if (boundary > 0.0) boundary / 1.5 else boundary / 2.5
    else if (3.0 + boundary > 0.0) (3.0 + boundary) / 3.0 else (3.0 + boundary) / 10.0;
    return pressure >= -2.0 and pressure <= 2.0;
}

pub fn aquifer_pressure(y: i32, first_level: c.jint, first_type: u8, second_level: c.jint, second_type: u8, barrier: c.jdouble) f64 {
    if ((first_type == 3 and second_type == 2) or (first_type == 2 and second_type == 3)) {
        return 2.0;
    }
    const level_difference: i64 = @as(i64, first_level) - @as(i64, second_level);
    const absolute_level_difference: i64 = if (level_difference < 0) -level_difference else level_difference;
    if (absolute_level_difference == 0) {
        return 0.0;
    }
    const average_level = 0.5 * @as(f64, @floatFromInt(@as(i64, first_level) + @as(i64, second_level)));
    const delta_y = @as(f64, @floatFromInt(y)) + 0.5 - average_level;
    const boundary = @as(f64, @floatFromInt(absolute_level_difference)) / 2.0 - @abs(delta_y);
    const pressure = if (delta_y > 0.0)
        if (boundary > 0.0) boundary / 1.5 else boundary / 2.5
    else if (3.0 + boundary > 0.0) (3.0 + boundary) / 3.0 else (3.0 + boundary) / 10.0;
    const barrier_value = if (pressure < -2.0 or pressure > 2.0) 0.0 else barrier;
    return 2.0 * (barrier_value + pressure);
}
