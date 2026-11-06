const std = @import("std");
const c = @import("../core/jni.zig").c;
const core_limits = @import("../core/limits.zig");
const core_math = @import("../core/math.zig");
const noise_grid = @import("grid.zig");
const noise_scalar = @import("scalar.zig");
const noise_scale = @import("scale.zig");

pub fn normal_noise_grid(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []c.jdouble) void {
    normal_noise_grid_impl(false, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub const GridColumnKey = extern struct { value_factor: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64 };

pub fn GridColumnEntry(comptime max_cached_octaves: usize) type {
    return struct {
        valid: bool,
        first_ptr: usize,
        second_ptr: usize,
        first_count: usize,
        second_count: usize,
        key: GridColumnKey,
        y_count: usize,
        z_count: usize,
        first_x: u64,
        last_x: u64,
        first_data: [16 + max_cached_octaves * 289]u8,
        second_data: [16 + max_cached_octaves * 289]u8,
        first_column: [256]f64,
        last_column: [256]f64,
    };
}

pub fn GridColumnCache(comptime capacity: usize, comptime max_cached_octaves: usize) type {
    return struct { next: usize, entries: [capacity]GridColumnEntry(max_cached_octaves) };
}

pub threadlocal var small_grid_cache: GridColumnCache(24, 3) = std.mem.zeroes(GridColumnCache(24, 3));

pub threadlocal var large_grid_cache: GridColumnCache(4, 9) = std.mem.zeroes(GridColumnCache(4, 9));

pub fn normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    const column_len = y_count * z_count;
    if (x_count < 4 or column_len > 256 or (x_count - 1) * column_len < 256 or first_count == 0 or first_count > 9 or second_count == 0 or second_count > 9 or
        y_step == 0.0 or !std.math.isFinite(x) or !std.math.isFinite(y) or !std.math.isFinite(z) or !std.math.isFinite(value_factor) or
        !std.math.isFinite(x_step) or !std.math.isFinite(y_step) or !std.math.isFinite(z_step))
    {
        normal_noise_grid_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
        return;
    }

    const edge_x = x + @as(f64, @floatFromInt(x_count - 1)) * x_step;
    const max_x = @max(@abs(x), @abs(edge_x));
    const first_scale = @abs(core_math.read_f64(first, 8)) * @as(f64, @floatFromInt(@as(u32, 1) << @as(u5, @intCast(first_count))));
    const second_scale = @abs(core_math.read_f64(second, 8)) * core_limits.normal_noise_input_factor * @as(f64, @floatFromInt(@as(u32, 1) << @as(u5, @intCast(second_count))));
    if (!(max_x * @max(first_scale, second_scale) < 0x1p22)) {
        normal_noise_grid_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
        return;
    }
    if (first_count <= 3 and second_count <= 3) {
        normal_noise_grid_cached(24, 3, &small_grid_cache, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
    } else {
        normal_noise_grid_cached(4, 9, &large_grid_cache, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
    }
}

pub fn normal_noise_grid_cached(comptime capacity: usize, comptime max_cached_octaves: usize, cache: *GridColumnCache(capacity, max_cached_octaves), first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    const column_len = y_count * z_count;
    const key: GridColumnKey = .{ .value_factor = value_factor, .y = y, .z = z, .x_step = x_step, .y_step = y_step, .z_step = z_step };
    var found: ?usize = null;
    for (&cache.entries, 0..) |*entry, i| {
        if (entry.valid and entry.first_ptr == @intFromPtr(first) and entry.second_ptr == @intFromPtr(second)) {
            found = i;
            break;
        }
    }
    const index = found orelse cache.next;
    const entry = &cache.entries[index];
    const first_len = 16 + first_count * 289;
    const second_len = 16 + second_count * 289;
    var same = found != null and entry.first_count == first_count and entry.second_count == second_count and entry.y_count == y_count and entry.z_count == z_count and std.mem.eql(u8, std.mem.asBytes(&entry.key), std.mem.asBytes(&key));
    if (same) same = std.mem.eql(u8, entry.first_data[0..first_len], first[0..first_len]) and std.mem.eql(u8, entry.second_data[0..second_len], second[0..second_len]);
    const first_x: u64 = @bitCast(x + @as(f64, 0.0) * x_step);
    const last_x: u64 = @bitCast(x + @as(f64, @floatFromInt(x_count - 1)) * x_step);

    const integer_x = x != 0.0 and x_step > 0.0 and @abs(x) < 0x1p45 and @abs(x_step) < 0x1p24 and @floor(x) == x and @floor(x_step) == x_step;
    const reuse_first = same and integer_x and (first_x == entry.first_x or first_x == entry.last_x);
    const reuse_last = same and (last_x == entry.first_x or last_x == entry.last_x);
    if (reuse_first) @memcpy(values[0..column_len], if (first_x == entry.first_x) entry.first_column[0..column_len] else entry.last_column[0..column_len]);
    if (reuse_last) @memcpy(values[(x_count - 1) * column_len ..][0..column_len], if (last_x == entry.first_x) entry.first_column[0..column_len] else entry.last_column[0..column_len]);
    const skip_first: usize = @intFromBool(reuse_first);
    const count = x_count - skip_first - @as(usize, @intFromBool(reuse_last));
    if (count > 0) normal_noise_grid_impl(true, first, first_count, second, second_count, value_factor, if (reuse_first) x + x_step else x, y, z, x_step, y_step, z_step, count, y_count, z_count, values[skip_first * column_len ..][0 .. count * column_len]);
    if (!same) {
        @memcpy(entry.first_data[0..first_len], first[0..first_len]);
        @memcpy(entry.second_data[0..second_len], second[0..second_len]);
    }
    entry.first_ptr = @intFromPtr(first);
    entry.second_ptr = @intFromPtr(second);
    entry.first_count = first_count;
    entry.second_count = second_count;
    entry.key = key;
    entry.y_count = y_count;
    entry.z_count = z_count;
    entry.first_x = first_x;
    entry.last_x = last_x;
    @memcpy(entry.first_column[0..column_len], values[0..column_len]);
    @memcpy(entry.last_column[0..column_len], values[(x_count - 1) * column_len ..][0..column_len]);
    entry.valid = true;
    if (found == null) cache.next = (cache.next + 1) % capacity;
}

pub fn normal_noise_grid_impl(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    if (avx2 and x_count == 1 and y_count == 1 and z_count == 1) {
        var value: f64 = 0.0;
        noise_scalar.perlin_grid_point(first, first_count, x, y, z, x_step, y_step, z_step, 1.0, &value);
        noise_scalar.perlin_grid_point(second, second_count, x, y, z, x_step, y_step, z_step, core_limits.normal_noise_input_factor, &value);
        values[0] = value * value_factor;
        return;
    }
    if (y_step == 0.0 and y_count > 1) {
        const columns = x_count * z_count;
        normal_noise_grid_impl(avx2, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, 1, z_count, values[0..columns]);
        var column = columns;
        while (column > 0) {
            column -= 1;
            const value = values[column];
            @memset(values[column * y_count ..][0..y_count], value);
        }
        return;
    }
    @memset(values, 0.0);
    noise_grid.perlin_value_grid(avx2, first, first_count, x, y, z, x_step, y_step, z_step, 1.0, x_count, y_count, z_count, values);
    noise_grid.perlin_value_grid(avx2, second, second_count, x, y, z, x_step, y_step, z_step, core_limits.normal_noise_input_factor, x_count, y_count, z_count, values);
    noise_scale.scale_f64_values(avx2, values, value_factor);
}
