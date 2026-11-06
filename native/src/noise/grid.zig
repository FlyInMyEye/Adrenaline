const std = @import("std");
const core_limits = @import("../core/limits.zig");
const core_math = @import("../core/math.zig");
const core_vectors = @import("../core/vectors.zig");
const noise_batch = @import("batch.zig");
const noise_gradients = @import("gradients.zig");
const noise_sampling = @import("sampling.zig");

pub inline fn perlin_value_grid(comptime avx2: bool, data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, coordinate_scale: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    if (comptime avx2) {
        const last_factor = core_math.read_f64(data, 8) * @as(f64, @floatFromInt(@as(u64, 1) << @as(u6, @intCast(octaves -| 1))));
        if (y_count == 1 or @abs(y_step * coordinate_scale * last_factor) >= 1.0) {
            if (comptime avx2) {
                if (z_count >= 4 and y_count > 1 and @abs(z_step * coordinate_scale * core_math.read_f64(data, 8)) < 1.0) return perlin_value_grid_impl(avx2, true, true, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
            }
            return perlin_value_grid_impl(avx2, true, false, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
        }
    }
    if (comptime avx2) {
        if (z_count >= 4 and y_count > 1 and @abs(z_step * coordinate_scale * core_math.read_f64(data, 8)) < 1.0) return perlin_value_grid_impl(avx2, false, true, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
    }
    return perlin_value_grid_impl(avx2, false, false, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
}

pub fn perlin_mixed_y_octave(permutation: [*]const u8, ix: []const i32, iy: []const i32, iz: []const i32, fx: []const f64, fy: []const f64, fz: []const f64, sx: []const f64, sy: []const f64, sz: []const f64, amplitude: f64, factor: f64, values: []f64) void {
    @setFloatMode(.strict);
    var pairs: [257]u8 = undefined;
    @memcpy(pairs[0..256], permutation[0..256]);
    pairs[256] = permutation[0];
    var xy0: [core_limits.max_grid_axis]i32 = undefined;
    var xy1: [core_limits.max_grid_axis]i32 = undefined;
    var xn0: [core_limits.max_grid_axis]i32 = undefined;
    var xn1: [core_limits.max_grid_axis]i32 = undefined;
    const amp: core_vectors.Vec4 = @splat(amplitude);
    const octave_factor: core_vectors.Vec4 = @splat(factor);
    for (ix, 0..) |x, xi| {
        const xpair = noise_sampling.permutation_pair(&pairs, x);
        const hx: i32 = @intCast(xpair & 255);
        const hx1: i32 = @intCast(xpair >> 8);
        for (iy, 0..) |y, yi| {
            const a = noise_sampling.permutation_pair(&pairs, hx + y);
            const b = noise_sampling.permutation_pair(&pairs, hx1 + y);
            xy0[yi] = @intCast(a & 255);
            xy1[yi] = @intCast(a >> 8);
            xn0[yi] = @intCast(b & 255);
            xn1[yi] = @intCast(b >> 8);
        }
        for (iz, 0..) |z, zi| {
            const start = (xi * iz.len + zi) * iy.len;
            var yi: usize = 0;
            while (yi + 3 < iy.len) : (yi += 4) {
                const frac: core_vectors.Vec4 = @bitCast(fy[yi..][0..4].*);
                const smooth: core_vectors.Vec4 = @bitCast(sy[yi..][0..4].*);
                const ys: core_vectors.Vec4i = @bitCast(iy[yi..][0..4].*);
                const sampled = if (@reduce(.And, ys == @as(core_vectors.Vec4i, @splat(ys[0])))) block: {
                    const cell = noise_gradients.GradientCell.init_cached(permutation, xy0[yi], xy1[yi], xn0[yi], xn1[yi], z);
                    break :block cell.line_paired(fx[xi], fz[zi]).sample_grid(4, frac, sx[xi], smooth, sz[zi]);
                } else noise_sampling.blended_sample(4, true, permutation, &pairs, @bitCast(xy0[yi..][0..4].*), @bitCast(xy1[yi..][0..4].*), @bitCast(xn0[yi..][0..4].*), @bitCast(xn1[yi..][0..4].*), z, fx[xi], frac, fz[zi], sx[xi], smooth, sz[zi]);
                const current: core_vectors.Vec4 = @bitCast(values[start + yi ..][0..4].*);
                values[start + yi ..][0..4].* = @bitCast(current + amp * sampled * octave_factor);
            }
            while (yi < iy.len) : (yi += 1) {
                const cell = noise_gradients.GradientCell.init_cached(permutation, xy0[yi], xy1[yi], xn0[yi], xn1[yi], z);
                values[start + yi] += amplitude * cell.sample_packed(fx[xi], fy[yi], fz[zi], sx[xi], sy[yi], sz[zi]) * factor;
            }
        }
    }
}

pub fn grid_fractions_safe(values: []const f64, reject_zero: bool) bool {
    const limit: core_vectors.Vec4 = @splat(std.math.inf(f64));
    const zero: core_vectors.Vec4 = @splat(0.0);
    var i: usize = 0;
    while (i + 3 < values.len) : (i += 4) {
        const v: core_vectors.Vec4 = @bitCast(values[i..][0..4].*);
        if (!@reduce(.And, @abs(v) < limit)) return false;
        if (reject_zero and @reduce(.Or, v == zero)) return false;
    }
    while (i < values.len) : (i += 1) {
        if (!std.math.isFinite(values[i]) or (reject_zero and values[i] == 0.0)) return false;
    }
    return true;
}

pub fn all_adjacent_distinct(values: []const i32) bool {
    for (1..values.len) |i| if (values[i] == values[i - 1]) return false;
    return true;
}

pub fn perlin_value_grid_impl(comptime avx2: bool, comptime packed_corners: bool, comptime z4_enabled: bool, data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, coordinate_scale: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    @setFloatMode(.strict);
    if (x_count > core_limits.max_grid_axis or y_count > core_limits.max_grid_axis or z_count > core_limits.max_grid_axis) {
        var x_index: usize = 0;
        while (x_index < x_count) : (x_index += 1) {
            const sample_x = x + @as(f64, @floatFromInt(x_index)) * x_step;
            var z_index: usize = 0;
            while (z_index < z_count) : (z_index += 1) {
                const sample_z = z + @as(f64, @floatFromInt(z_index)) * z_step;
                const offset = (x_index * z_count + z_index) * y_count;
                noise_batch.perlin_value_batch(data, octaves, sample_x, y, sample_z, y_step, coordinate_scale, values[offset .. offset + y_count]);
            }
        }
        return;
    }
    const lowest_value_factor = core_math.read_f64(data, 0);
    const lowest_input_factor = core_math.read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var integer_x: [core_limits.max_grid_axis]i32 = undefined;
    var integer_y: [core_limits.max_grid_axis]i32 = undefined;
    var integer_z: [core_limits.max_grid_axis]i32 = undefined;
    var fraction_x: [core_limits.max_grid_axis]f64 = undefined;
    var fraction_y: [core_limits.max_grid_axis]f64 = undefined;
    var fraction_z: [core_limits.max_grid_axis]f64 = undefined;
    var smooth_x: [core_limits.max_grid_axis]f64 = undefined;
    var smooth_y: [core_limits.max_grid_axis]f64 = undefined;
    var smooth_z: [core_limits.max_grid_axis]f64 = undefined;
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = core_math.read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            prepare_grid_axis(x, x_step, x_count, coordinate_scale, input_factor, core_math.read_f64(data, origin), integer_x[0..x_count], fraction_x[0..x_count], smooth_x[0..x_count]);
            prepare_grid_axis(y, y_step, y_count, coordinate_scale, input_factor, core_math.read_f64(data, origin + 8), integer_y[0..y_count], fraction_y[0..y_count], smooth_y[0..y_count]);
            prepare_grid_axis(z, z_step, z_count, coordinate_scale, input_factor, core_math.read_f64(data, origin + 16), integer_z[0..z_count], fraction_z[0..z_count], smooth_z[0..z_count]);
            if (comptime avx2 and packed_corners) {
                if (y_count >= 9 and y_count % 4 == 1 and z_count >= 3 and x_count * y_count * z_count >= 256 and
                    @abs(y_step * coordinate_scale * input_factor) >= 0.25 and
                    grid_fractions_safe(fraction_x[0..x_count], false) and grid_fractions_safe(fraction_y[0..y_count], true) and grid_fractions_safe(fraction_z[0..z_count], false))
                {
                    perlin_mixed_y_octave(permutation, integer_x[0..x_count], integer_y[0..y_count], integer_z[0..z_count], fraction_x[0..x_count], fraction_y[0..y_count], fraction_z[0..z_count], smooth_x[0..x_count], smooth_y[0..y_count], smooth_z[0..z_count], amplitude, value_factor, values);
                    input_factor *= 2.0;
                    value_factor /= 2.0;
                    continue;
                }
            }
            var x_start: usize = 0;
            while (x_start < x_count) {
                var x_end = x_start + 1;
                while (x_end < x_count and integer_x[x_end] == integer_x[x_start]) : (x_end += 1) {}
                const x_hash = core_math.permutation_value(permutation, integer_x[x_start]);
                const next_x_hash = core_math.permutation_value(permutation, integer_x[x_start] + 1);
                var z_start: usize = 0;
                while (z_start < z_count) {
                    var z_end = z_start + 1;
                    while (z_end < z_count and integer_z[z_end] == integer_z[z_start]) : (z_end += 1) {}
                    var y_start: usize = 0;
                    while (y_start < y_count) {
                        if (comptime avx2 and packed_corners) {
                            if (y_start + 3 < y_count and integer_y[y_start] != integer_y[y_start + 1] and integer_y[y_start + 1] != integer_y[y_start + 2] and integer_y[y_start + 2] != integer_y[y_start + 3]) {
                                const ys: core_vectors.Vec4i = @bitCast(integer_y[y_start..][0..4].*);
                                const fy: core_vectors.Vec4 = @bitCast(fraction_y[y_start..][0..4].*);
                                const sy: core_vectors.Vec4 = @bitCast(smooth_y[y_start..][0..4].*);
                                for (x_start..x_end) |xi| {
                                    for (z_start..z_end) |zi| {
                                        const sampled = noise_sampling.sample_distinct_y(4, true, permutation, x_hash, next_x_hash, ys, integer_z[zi], fraction_x[xi], fy, fraction_z[zi], smooth_x[xi], sy, smooth_z[zi]);
                                        const offset = (xi * z_count + zi) * y_count + y_start;
                                        const current: core_vectors.Vec4 = @bitCast(values[offset..][0..4].*);
                                        values[offset..][0..4].* = @bitCast(current + @as(core_vectors.Vec4, @splat(amplitude)) * sampled * @as(core_vectors.Vec4, @splat(value_factor)));
                                    }
                                }
                                y_start += 4;
                                continue;
                            }
                        }
                        var y_end = y_start + 1;
                        while (y_end < y_count and integer_y[y_end] == integer_y[y_start]) : (y_end += 1) {}
                        const gradients = noise_gradients.GradientCell.init(permutation, x_hash, next_x_hash, integer_y[y_start], integer_z[z_start]);
                        var x_index = x_start;
                        while (x_index < x_end) : (x_index += 1) {
                            var z_index = z_start;
                            if (comptime avx2 and z4_enabled) {
                                while (z_index + 3 < z_end and y_end > y_start + 1) : (z_index += 4) {
                                    const fz4: core_vectors.Vec4 = @bitCast(fraction_z[z_index..][0..4].*);
                                    const sz4: core_vectors.Vec4 = @bitCast(smooth_z[z_index..][0..4].*);
                                    const line4 = gradients.line_z4(fraction_x[x_index], fz4);
                                    var yi = y_start;
                                    while (yi < y_end) : (yi += 1) {
                                        const sampled = line4.sample(fraction_y[yi], smooth_x[x_index], smooth_y[yi], sz4);
                                        const first_index = (x_index * z_count + z_index) * y_count + yi;
                                        const current: core_vectors.Vec4 = .{ values[first_index], values[first_index + y_count], values[first_index + 2 * y_count], values[first_index + 3 * y_count] };
                                        const result = current + @as(core_vectors.Vec4, @splat(amplitude)) * sampled * @as(core_vectors.Vec4, @splat(value_factor));
                                        values[first_index] = result[0];
                                        values[first_index + y_count] = result[1];
                                        values[first_index + 2 * y_count] = result[2];
                                        values[first_index + 3 * y_count] = result[3];
                                    }
                                }
                            }
                            while (z_index < z_end) : (z_index += 1) {
                                const offset = (x_index * z_count + z_index) * y_count;
                                if (packed_corners and y_end == y_start + 1) {
                                    values[offset + y_start] += amplitude * gradients.sample_packed(fraction_x[x_index], fraction_y[y_start], fraction_z[z_index], smooth_x[x_index], smooth_y[y_start], smooth_z[z_index]) * value_factor;
                                    continue;
                                }
                                const line = if (x_count * y_count * z_count >= 256)
                                    gradients.line_paired(fraction_x[x_index], fraction_z[z_index])
                                else
                                    gradients.line(fraction_x[x_index], fraction_z[z_index]);
                                var y_index = y_start;
                                if (comptime avx2) {
                                    const amplitude8: core_vectors.Vec8d = @splat(amplitude);
                                    const value_factor8: core_vectors.Vec8d = @splat(value_factor);
                                    while (y_index + 7 < y_end) : (y_index += 8) {
                                        const sampled = line.sample_grid(
                                            8,
                                            .{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3], fraction_y[y_index + 4], fraction_y[y_index + 5], fraction_y[y_index + 6], fraction_y[y_index + 7] },
                                            smooth_x[x_index],
                                            .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3], smooth_y[y_index + 4], smooth_y[y_index + 5], smooth_y[y_index + 6], smooth_y[y_index + 7] },
                                            smooth_z[z_index],
                                        );
                                        const value_index = offset + y_index;
                                        const current: core_vectors.Vec8d = @bitCast(values[value_index..][0..8].*);
                                        values[value_index..][0..8].* = @bitCast(current + amplitude8 * sampled * value_factor8);
                                    }
                                    const amplitude4: core_vectors.Vec4 = @splat(amplitude);
                                    const value_factor4: core_vectors.Vec4 = @splat(value_factor);
                                    while (y_index + 3 < y_end) : (y_index += 4) {
                                        const sampled = line.sample_grid(
                                            4,
                                            .{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3] },
                                            smooth_x[x_index],
                                            .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3] },
                                            smooth_z[z_index],
                                        );
                                        const value_index = offset + y_index;
                                        const current: core_vectors.Vec4 = @bitCast(values[value_index..][0..4].*);
                                        values[value_index..][0..4].* = @bitCast(current + amplitude4 * sampled * value_factor4);
                                    }
                                }
                                const amplitude2: core_vectors.Vec2 = @splat(amplitude);
                                const value_factor2: core_vectors.Vec2 = @splat(value_factor);
                                while (y_index + 1 < y_end) : (y_index += 2) {
                                    const sampled = line.sample_grid(
                                        2,
                                        .{ fraction_y[y_index], fraction_y[y_index + 1] },
                                        smooth_x[x_index],
                                        .{ smooth_y[y_index], smooth_y[y_index + 1] },
                                        smooth_z[z_index],
                                    );
                                    const value_index = offset + y_index;
                                    const current: core_vectors.Vec2 = @bitCast(values[value_index..][0..2].*);
                                    values[value_index..][0..2].* = @bitCast(current + amplitude2 * sampled * value_factor2);
                                }
                                while (y_index < y_end) : (y_index += 1) {
                                    values[offset + y_index] += amplitude * line.sample_smoothed_shifted(fraction_y[y_index], smooth_x[x_index], smooth_y[y_index], smooth_z[z_index]) * value_factor;
                                }
                            }
                        }
                        y_start = y_end;
                    }
                    z_start = z_end;
                }
                x_start = x_end;
            }
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
}

pub fn prepare_grid_axis(base: f64, step: f64, count: usize, coordinate_scale: f64, input_factor: f64, origin: f64, integers: []i32, fractions: []f64, smooth: []f64) void {
    if (count == 0) return;
    const last = base + @as(f64, @floatFromInt(count - 1)) * step;
    const first_scaled = base * coordinate_scale * input_factor;
    const last_scaled = last * coordinate_scale * input_factor;
    const no_wrap = std.math.isFinite(first_scaled) and std.math.isFinite(last_scaled) and
        @abs(first_scaled) < 16777216.0 and @abs(last_scaled) < 16777216.0;
    var index: usize = 0;
    while (index < count) : (index += 1) {
        const sample = base + @as(f64, @floatFromInt(index)) * step;
        const scaled = sample * coordinate_scale * input_factor;
        const shifted = (if (no_wrap) scaled - 0.0 else core_math.wrap(scaled)) + origin;
        const integer = core_math.floor_int(shifted);
        const fraction = shifted - @as(f64, @floatFromInt(integer));
        integers[index] = integer;
        fractions[index] = fraction;
        smooth[index] = core_math.smoothstep(fraction);
    }
}
