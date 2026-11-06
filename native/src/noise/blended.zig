const std = @import("std");
const c = @import("../core/jni.zig").c;
const core_limits = @import("../core/limits.zig");
const core_math = @import("../core/math.zig");
const core_vectors = @import("../core/vectors.zig");
const noise_gradients = @import("gradients.zig");
const noise_sampling = @import("sampling.zig");
const noise_types = @import("types.zig");

pub const BlendedColumnCache = struct {
    valid: bool,
    min: usize,
    max: usize,
    main: usize,
    config: noise_types.BlendedNoiseConfig,
    base_y: i32,
    base_z: i32,
    step_x: i32,
    step_y: i32,
    step_z: i32,
    y_count: usize,
    z_count: usize,
    first_x: i32,
    last_x: i32,
    min_data: [16 + 16 * 289]u8,
    max_data: [16 + 16 * 289]u8,
    main_data: [16 + 8 * 289]u8,
    first: [512]f64,
    last: [512]f64,
};

pub threadlocal var blended_column_cache: BlendedColumnCache = std.mem.zeroes(BlendedColumnCache);

pub fn blended_noise_grid_avx2(min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const noise_types.BlendedNoiseConfig, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    const column_len = y_count * z_count;
    if (x_count < 2 or column_len > 512 or (x_count - 1) * column_len < 256 or
        !std.math.isFinite(config.xz_multiplier) or !std.math.isFinite(config.y_multiplier) or
        !std.math.isFinite(config.xz_factor) or !std.math.isFinite(config.y_factor) or !std.math.isFinite(config.smear_scale_multiplier))
    {
        blended_noise_grid_impl(true, min, max, main, config, base_x, base_y, base_z, step_x, step_y, step_z, x_count, y_count, z_count, values);
        return;
    }
    const cache = &blended_column_cache;
    const last_x = base_x +% @as(i32, @intCast(x_count - 1)) *% step_x;
    var same = cache.valid and cache.min == @intFromPtr(min) and cache.max == @intFromPtr(max) and cache.main == @intFromPtr(main) and cache.base_y == base_y and cache.base_z == base_z and cache.step_x == step_x and cache.step_y == step_y and cache.step_z == step_z and cache.y_count == y_count and cache.z_count == z_count and std.mem.eql(u8, std.mem.asBytes(&cache.config), std.mem.asBytes(config));
    if (same) same = std.mem.eql(u8, &cache.min_data, min[0..cache.min_data.len]) and std.mem.eql(u8, &cache.max_data, max[0..cache.max_data.len]) and std.mem.eql(u8, &cache.main_data, main[0..cache.main_data.len]);
    const reuse_first = same and (base_x == cache.first_x or base_x == cache.last_x);
    const reuse_last = same and (last_x == cache.first_x or last_x == cache.last_x);
    if (reuse_first) @memcpy(values[0..column_len], if (base_x == cache.first_x) cache.first[0..column_len] else cache.last[0..column_len]);
    if (reuse_last) @memcpy(values[(x_count - 1) * column_len ..][0..column_len], if (last_x == cache.first_x) cache.first[0..column_len] else cache.last[0..column_len]);
    const first = @intFromBool(reuse_first);
    const count = x_count - first - @intFromBool(reuse_last);
    if (count > 0) blended_noise_grid_impl(true, min, max, main, config, base_x +% @as(i32, @intCast(first)) *% step_x, base_y, base_z, step_x, step_y, step_z, count, y_count, z_count, values[first * column_len ..][0 .. count * column_len]);
    if (!same) {
        @memcpy(&cache.min_data, min[0..cache.min_data.len]);
        @memcpy(&cache.max_data, max[0..cache.max_data.len]);
        @memcpy(&cache.main_data, main[0..cache.main_data.len]);
    }
    cache.min = @intFromPtr(min);
    cache.max = @intFromPtr(max);
    cache.main = @intFromPtr(main);
    cache.config = config.*;
    cache.base_y = base_y;
    cache.base_z = base_z;
    cache.step_x = step_x;
    cache.step_y = step_y;
    cache.step_z = step_z;
    cache.y_count = y_count;
    cache.z_count = z_count;
    cache.first_x = base_x;
    cache.last_x = last_x;
    @memcpy(cache.first[0..column_len], values[0..column_len]);
    @memcpy(cache.last[0..column_len], values[(x_count - 1) * column_len ..][0..column_len]);
    cache.valid = true;
}

pub noinline fn blended_noise_grid_impl(comptime avx2: bool, min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const noise_types.BlendedNoiseConfig, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    @setFloatMode(.strict);
    if (x_count > core_limits.max_grid_axis or y_count > core_limits.max_grid_axis or z_count > core_limits.max_grid_axis or values.len > core_limits.max_grid_size) {
        for (0..x_count) |xi| {
            const x = base_x +% @as(i32, @intCast(xi)) *% step_x;
            for (0..z_count) |zi| {
                const z = base_z +% @as(i32, @intCast(zi)) *% step_z;
                var yi: usize = 0;
                while (yi < y_count) {
                    const count = @min(y_count - yi, core_limits.max_grid_axis);
                    const y = base_y +% @as(i32, @intCast(yi)) *% step_y;
                    const at = (xi * z_count + zi) * y_count + yi;
                    blended_noise_grid_impl(avx2, min, max, main, config, x, y, z, step_x, step_y, step_z, 1, count, 1, values[at..][0..count]);
                    yi += count;
                }
            }
        }
        return;
    }
    var limit_y: [core_limits.max_grid_axis]f64 = undefined;
    var main_y: [core_limits.max_grid_axis]f64 = undefined;
    for (0..y_count) |i| {
        const y = base_y +% @as(c.jint, @intCast(i)) *% step_y;
        limit_y[i] = @as(f64, @floatFromInt(y)) * config.y_multiplier;
        main_y[i] = limit_y[i] / config.y_factor;
    }
    var limit_x: [core_limits.max_grid_axis]f64 = undefined;
    var main_x: [core_limits.max_grid_axis]f64 = undefined;
    var limit_z: [core_limits.max_grid_axis]f64 = undefined;
    var main_z: [core_limits.max_grid_axis]f64 = undefined;
    for (0..x_count) |i| {
        limit_x[i] = @as(f64, @floatFromInt(base_x +% @as(c.jint, @intCast(i)) *% step_x)) * config.xz_multiplier;
        main_x[i] = limit_x[i] / config.xz_factor;
    }
    for (0..z_count) |i| {
        limit_z[i] = @as(f64, @floatFromInt(base_z +% @as(c.jint, @intCast(i)) *% step_z)) * config.xz_multiplier;
        main_z[i] = limit_z[i] / config.xz_factor;
    }
    const smear = config.y_multiplier * config.smear_scale_multiplier;
    var selector: [core_limits.max_grid_size]f64 = undefined;
    var low: [core_limits.max_grid_size]f64 = undefined;
    var high: [core_limits.max_grid_size]f64 = undefined;
    const total = values.len;
    @memset(selector[0..total], 0.0);
    blended_octaves(avx2, .all, main, 8, main_x[0..x_count], main_z[0..z_count], main_y[0..y_count], smear / config.y_factor, selector[0..total], selector[0..total]);
    for (selector[0..total]) |*entry| entry.* = (entry.* / 10.0 + 1.0) / 2.0;
    @memset(low[0..total], 0.0);
    @memset(high[0..total], 0.0);
    blended_octaves(avx2, .lower, min, 16, limit_x[0..x_count], limit_z[0..z_count], limit_y[0..y_count], smear, selector[0..total], low[0..total]);
    blended_octaves(avx2, .upper, max, 16, limit_x[0..x_count], limit_z[0..z_count], limit_y[0..y_count], smear, selector[0..total], high[0..total]);
    for (values, low[0..total], high[0..total], selector[0..total]) |*entry, l, h, q| {
        const start = l / 512.0;
        const end = h / 512.0;
        entry.* = (if (q < 0.0) start else if (q > 1.0) end else core_math.lerp(q, start, end)) / 128.0;
    }
}

pub const BlendedStack = enum { all, lower, upper };

pub fn blended_active(comptime kind: BlendedStack, selector: []const f64, index: usize) bool {
    return switch (kind) {
        .all => true,
        .lower => !(selector[index] >= 1.0),
        .upper => !(selector[index] <= 0.0),
    };
}

pub fn blended_octaves(comptime avx2: bool, comptime kind: BlendedStack, data: [*]const u8, octaves: usize, raw_x: []const f64, raw_z: []const f64, raw_y: []const f64, smear: f64, selectors: []const f64, outputs: []f64) void {
    const use_pairs = avx2 and raw_x.len * raw_z.len * raw_y.len >= 256;
    if (avx2 and kind != .all and raw_y.len >= 4 and raw_y.len <= 64) {
        if (use_pairs) return blended_octaves_impl(avx2, kind, true, true, data, octaves, raw_x, raw_z, raw_y, smear, selectors, outputs);
        return blended_octaves_impl(avx2, kind, true, false, data, octaves, raw_x, raw_z, raw_y, smear, selectors, outputs);
    }
    if (use_pairs) return blended_octaves_impl(avx2, kind, false, true, data, octaves, raw_x, raw_z, raw_y, smear, selectors, outputs);
    return blended_octaves_impl(avx2, kind, false, false, data, octaves, raw_x, raw_z, raw_y, smear, selectors, outputs);
}

pub fn blended_column_active(comptime cached: bool, comptime kind: BlendedStack, mask: u64, selector: []const f64, index: usize) bool {
    if (cached) return (mask >> @as(u6, @intCast(index))) & 1 != 0;
    return blended_active(kind, selector, index);
}

pub fn blended_octaves_impl(comptime avx2: bool, comptime kind: BlendedStack, comptime cached: bool, comptime use_pairs: bool, data: [*]const u8, octaves: usize, raw_x: []const f64, raw_z: []const f64, raw_y: []const f64, smear: f64, selectors: []const f64, outputs: []f64) void {
    var in_range = avx2;
    if (in_range) for (raw_x) |v| if (!(@abs(v) < 16777215.0)) {
        in_range = false;
        break;
    };
    if (in_range) for (raw_y) |v| if (!(@abs(v) < 16777215.0)) {
        in_range = false;
        break;
    };
    if (in_range) for (raw_z) |v| if (!(@abs(v) < 16777215.0)) {
        in_range = false;
        break;
    };
    if (in_range) return blended_octaves_kernel(avx2, kind, cached, use_pairs, true, data, octaves, raw_x, raw_z, raw_y, smear, selectors, outputs);
    return blended_octaves_kernel(avx2, kind, cached, use_pairs, false, data, octaves, raw_x, raw_z, raw_y, smear, selectors, outputs);
}

pub fn blended_octaves_kernel(comptime avx2: bool, comptime kind: BlendedStack, comptime cached: bool, comptime use_pairs: bool, comptime no_wrap: bool, data: [*]const u8, octaves: usize, raw_x: []const f64, raw_z: []const f64, raw_y: []const f64, smear: f64, selectors: []const f64, outputs: []f64) void {
    @setFloatMode(.strict);
    var active_masks: [if (cached) core_limits.max_grid_size else 1]u64 = undefined;
    if (cached) {
        var any_active = false;
        for (0..raw_x.len * raw_z.len) |column| {
            const values = selectors[column * raw_y.len ..][0..raw_y.len];
            var mask: u64 = 0;
            var index: usize = 0;
            while (index + 3 < values.len) : (index += 4) {
                const q: core_vectors.Vec4 = @bitCast(values[index..][0..4].*);
                const inactive = if (kind == .lower) q >= @as(core_vectors.Vec4, @splat(1.0)) else q <= @as(core_vectors.Vec4, @splat(0.0));
                mask |= @as(u64, ~@as(u4, @bitCast(inactive))) << @as(u6, @intCast(index));
            }
            while (index < values.len) : (index += 1) {
                if (blended_active(kind, values, index)) mask |= @as(u64, 1) << @as(u6, @intCast(index));
            }
            active_masks[column] = mask;
            any_active = any_active or mask != 0;
        }
        if (!any_active) return;
    }
    const origin_offset = 16 + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var input_factor: f64 = 1.0;
    var integer_y: [core_limits.max_grid_axis]i32 = undefined;
    var adjusted_y: [core_limits.max_grid_axis]f64 = undefined;
    var fade_y: [core_limits.max_grid_axis]f64 = undefined;
    for (0..octaves) |level| {
        const octave = octaves - 1 - level;
        const output_factor: f64 = @floatFromInt(@as(u32, 1) << @as(u5, @intCast(level)));
        defer input_factor /= 2.0;
        if (data[active_offset + octave] == 0) continue;
        const origin = origin_offset + octave * 24;
        const permutation = data + permutation_offset + octave * 256;
        var pairs: [if (use_pairs) 257 else 1]u8 = undefined;
        if (comptime use_pairs) {
            @memcpy(pairs[0..256], permutation[0..256]);
            pairs[256] = permutation[0];
        }
        var integer_z: [core_limits.max_grid_axis]i32 = undefined;
        var fraction_z: [core_limits.max_grid_axis]f64 = undefined;
        var fade_z: [core_limits.max_grid_axis]f64 = undefined;
        for (raw_z, 0..) |z, i| {
            const shifted = (if (comptime no_wrap) z * input_factor - 0.0 else core_math.wrap(z * input_factor)) + core_math.read_f64(data, origin + 16);
            integer_z[i] = core_math.floor_int(shifted);
            fraction_z[i] = shifted - @as(f64, @floatFromInt(integer_z[i]));
            fade_z[i] = core_math.smoothstep(fraction_z[i]);
        }
        const yo = core_math.read_f64(data, origin + 8);
        const y_scale = smear * input_factor;
        for (raw_y, 0..) |y, i| {
            const y_max = y * input_factor;
            const shifted = (if (comptime no_wrap) y_max - 0.0 else core_math.wrap(y_max)) + yo;
            const iy = core_math.floor_int(shifted);
            const fy = shifted - @as(f64, @floatFromInt(iy));
            const limit = if (y_max >= 0.0 and y_max < fy) y_max else fy;
            const offset = if (y_scale != 0.0) @as(f64, @floatFromInt(core_math.floor_int(limit / y_scale + @as(f64, @as(f32, 1.0e-7))))) * y_scale else 0.0;
            integer_y[i] = iy;
            adjusted_y[i] = fy - offset;
            fade_y[i] = core_math.smoothstep(fy);
        }
        for (raw_x, 0..) |x, ix| {
            const shifted_x = (if (comptime no_wrap) x * input_factor - 0.0 else core_math.wrap(x * input_factor)) + core_math.read_f64(data, origin);
            const integer_x = core_math.floor_int(shifted_x);
            const fx = shifted_x - @as(f64, @floatFromInt(integer_x));
            const sx = core_math.smoothstep(fx);
            const xp: u16 = if (comptime use_pairs) noise_sampling.permutation_pair(pairs[0..], integer_x) else 0;
            const x_hash: i32 = if (comptime use_pairs) @intCast(xp & 255) else core_math.permutation_value(permutation, integer_x);
            const next_x_hash: i32 = if (comptime use_pairs) @intCast(xp >> 8) else core_math.permutation_value(permutation, integer_x + 1);
            var xy0: [core_limits.max_grid_axis]i32 = undefined;
            var xy1: [core_limits.max_grid_axis]i32 = undefined;
            var xn0: [core_limits.max_grid_axis]i32 = undefined;
            var xn1: [core_limits.max_grid_axis]i32 = undefined;
            for (integer_y[0..raw_y.len], 0..) |iy, j| {
                if (comptime use_pairs) {
                    const a = noise_sampling.permutation_pair(pairs[0..], (x_hash + iy));
                    const b = noise_sampling.permutation_pair(pairs[0..], (next_x_hash + iy));
                    xy0[j] = @intCast(a & 255);
                    xy1[j] = @intCast(a >> 8);
                    xn0[j] = @intCast(b & 255);
                    xn1[j] = @intCast(b >> 8);
                } else {
                    xy0[j] = core_math.permutation_value(permutation, x_hash + iy);
                    xy1[j] = core_math.permutation_value(permutation, x_hash + iy + 1);
                    xn0[j] = core_math.permutation_value(permutation, next_x_hash + iy);
                    xn1[j] = core_math.permutation_value(permutation, next_x_hash + iy + 1);
                }
            }
            for (raw_z, 0..) |_, iz_index| {
                const iz = integer_z[iz_index];
                const fz = fraction_z[iz_index];
                const sz = fade_z[iz_index];
                const offset = (ix * raw_z.len + iz_index) * raw_y.len;
                const selector = selectors[offset..][0..raw_y.len];
                const output = outputs[offset..][0..raw_y.len];
                const column_mask: u64 = if (cached) active_masks[ix * raw_z.len + iz_index] else 0;
                var previous_y: i32 = std.math.minInt(i32);
                var line: noise_gradients.GradientLine = undefined;
                var i: usize = 0;
                while (i < output.len) {
                    if (cached) {
                        const remaining = column_mask >> @as(u6, @intCast(i));
                        if (remaining == 0) break;
                        i += @as(usize, @ctz(remaining));
                    } else if (!blended_active(kind, selector, i)) {
                        i += 1;
                        continue;
                    }
                    if (comptime avx2) {
                        if (i + 3 < output.len and (if (cached) true else blended_column_active(cached, kind, column_mask, selector, i + 1) and blended_active(kind, selector, i + 2) and blended_active(kind, selector, i + 3))) {
                            const ys: core_vectors.Vec4i = @bitCast(integer_y[i..][0..4].*);
                            const y4: core_vectors.Vec4 = @bitCast(adjusted_y[i..][0..4].*);
                            const fade4: core_vectors.Vec4 = @bitCast(fade_y[i..][0..4].*);
                            const sampled = if (@reduce(.And, ys == @as(core_vectors.Vec4i, @splat(ys[0])))) block: {
                                if (ys[0] != previous_y) {
                                    line = noise_gradients.GradientCell.init_cached(permutation, xy0[i], xy1[i], xn0[i], xn1[i], iz).line_paired(fx, fz);
                                    previous_y = ys[0];
                                }
                                break :block line.sample_smoothed_shifted4(y4, sx, fade4, sz);
                            } else noise_sampling.blended_sample(4, use_pairs, permutation, &pairs, @bitCast(xy0[i..][0..4].*), @bitCast(xy1[i..][0..4].*), @bitCast(xn0[i..][0..4].*), @bitCast(xn1[i..][0..4].*), iz, fx, y4, fz, sx, fade4, sz);
                            const current: core_vectors.Vec4 = @bitCast(output[i..][0..4].*);
                            const updated = current + (if (avx2) sampled * @as(core_vectors.Vec4, @splat(output_factor)) else sampled / @as(core_vectors.Vec4, @splat(input_factor)));
                            const result = if (cached) @select(f64, @as(@Vector(4, bool), @bitCast(@as(u4, @truncate(column_mask >> @as(u6, @intCast(i)))))), updated, current) else updated;
                            output[i..][0..4].* = @bitCast(result);
                            i += 4;
                            continue;
                        }
                    }
                    if (i + 1 < output.len and blended_column_active(cached, kind, column_mask, selector, i + 1) and integer_y[i] != integer_y[i + 1]) {
                        const y2: core_vectors.Vec2 = @bitCast(adjusted_y[i..][0..2].*);
                        const fade2: core_vectors.Vec2 = @bitCast(fade_y[i..][0..2].*);
                        const sampled = noise_sampling.blended_sample(2, use_pairs, permutation, &pairs, @bitCast(xy0[i..][0..2].*), @bitCast(xy1[i..][0..2].*), @bitCast(xn0[i..][0..2].*), @bitCast(xn1[i..][0..2].*), iz, fx, y2, fz, sx, fade2, sz);
                        const current: core_vectors.Vec2 = @bitCast(output[i..][0..2].*);
                        output[i..][0..2].* = @bitCast(current + (if (avx2) sampled * @as(core_vectors.Vec2, @splat(output_factor)) else sampled / @as(core_vectors.Vec2, @splat(input_factor))));
                        i += 2;
                        continue;
                    }
                    const iy = integer_y[i];
                    if (iy != previous_y) {
                        line = noise_gradients.GradientCell.init_cached(permutation, xy0[i], xy1[i], xn0[i], xn1[i], iz).line_paired(fx, fz);
                        previous_y = iy;
                    }
                    var end = i + 1;
                    while (end < output.len and blended_column_active(cached, kind, column_mask, selector, end) and integer_y[end] == iy) : (end += 1) {}
                    while (i + 1 < end) : (i += 2) {
                        const y2: core_vectors.Vec2 = @bitCast(adjusted_y[i..][0..2].*);
                        const fade2: core_vectors.Vec2 = @bitCast(fade_y[i..][0..2].*);
                        const sampled = line.sample_smoothed_shifted2(y2, sx, fade2, sz);
                        const current: core_vectors.Vec2 = @bitCast(output[i..][0..2].*);
                        output[i..][0..2].* = @bitCast(current + (if (avx2) sampled * @as(core_vectors.Vec2, @splat(output_factor)) else sampled / @as(core_vectors.Vec2, @splat(input_factor))));
                    }
                    while (i < end) : (i += 1) {
                        const sampled = line.sample_smoothed_shifted(adjusted_y[i], sx, fade_y[i], sz);
                        output[i] += if (avx2) sampled * output_factor else sampled / input_factor;
                    }
                }
            }
        }
    }
}
