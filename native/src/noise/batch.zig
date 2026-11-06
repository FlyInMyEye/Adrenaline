const std = @import("std");
const c = @import("../core/jni.zig").c;
const backend_options = @import("../backend/options.zig");
const core_limits = @import("../core/limits.zig");
const core_math = @import("../core/math.zig");
const core_vectors = @import("../core/vectors.zig");
const noise_gradients = @import("gradients.zig");
const noise_sampling = @import("sampling.zig");
const noise_scale = @import("scale.zig");

pub fn normal_noise_batch_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, values: []f64) void {
    normal_noise_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, values);
}

pub fn normal_noise_batch(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, values: []c.jdouble) void {
    @memset(values, 0.0);
    perlin_value_batch(first, first_count, x, y, z, y_step, 1.0, values);
    perlin_value_batch(second, second_count, x, y, z, y_step, core_limits.normal_noise_input_factor, values);
    noise_scale.scale_f64_values(false, values, value_factor);
}

pub fn perlin_value_batch(data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, y_step: f64, coordinate_scale: f64, values: []c.jdouble) void {
    if (@abs(y_step) >= 2.0) {
        return perlin_value_batch_impl(true, data, octaves, x, y, z, y_step, coordinate_scale, values);
    }
    return perlin_value_batch_impl(false, data, octaves, x, y, z, y_step, coordinate_scale, values);
}

pub fn perlin_value_batch_impl(comptime paired: bool, data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, y_step: f64, coordinate_scale: f64, values: []c.jdouble) void {
    @setFloatMode(.strict);
    if (values.len == 0) return;
    const lowest_value_factor = core_math.read_f64(data, 0);
    const lowest_input_factor = core_math.read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = core_math.read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            const shifted_x = core_math.wrap(x * coordinate_scale * input_factor) + core_math.read_f64(data, origin);
            const shifted_z = core_math.wrap(z * coordinate_scale * input_factor) + core_math.read_f64(data, origin + 16);
            const integer_x = core_math.floor_int(shifted_x);
            const integer_z = core_math.floor_int(shifted_z);
            const fraction_x = shifted_x - @as(f64, @floatFromInt(integer_x));
            const fraction_z = shifted_z - @as(f64, @floatFromInt(integer_z));
            const smooth_x = core_math.smoothstep(fraction_x);
            const smooth_z = core_math.smoothstep(fraction_z);
            const x_hash = core_math.permutation_value(permutation, integer_x);
            const next_x_hash = core_math.permutation_value(permutation, integer_x + 1);
            const yo = core_math.read_f64(data, origin + 8);
            const first_y_scaled = y * coordinate_scale * input_factor;
            const last_y_scaled = (y + @as(f64, @floatFromInt(values.len - 1)) * y_step) * coordinate_scale * input_factor;
            const no_wrap_y = std.math.isFinite(first_y_scaled) and std.math.isFinite(last_y_scaled) and
                @abs(first_y_scaled) < 16777216.0 and @abs(last_y_scaled) < 16777216.0;

            var previous_integer_y: i32 = std.math.minInt(i32);
            var line: noise_gradients.GradientLine = undefined;
            var index: usize = 0;
            while (index < values.len) {
                var shifted_y: f64 = undefined;
                var integer_y: i32 = undefined;
                if (comptime backend_options.is_simd_backend and !backend_options.small_batch) {
                    if (index + 7 < values.len and @abs(y_step * coordinate_scale * input_factor) >= 1.0) {
                        const indices: core_vectors.Vec8d = @floatFromInt(@as(core_vectors.Vec8i, .{ @intCast(index), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3), @intCast(index + 4), @intCast(index + 5), @intCast(index + 6), @intCast(index + 7) }));
                        const sample_y4 = @as(core_vectors.Vec8d, @splat(y)) + indices * @as(core_vectors.Vec8d, @splat(y_step));
                        const scaled4 = sample_y4 * @as(core_vectors.Vec8d, @splat(coordinate_scale)) * @as(core_vectors.Vec8d, @splat(input_factor));
                        const wrapped4 = if (no_wrap_y) scaled4 - @as(core_vectors.Vec8d, @splat(0.0)) else scaled4 - @floor(scaled4 / @as(core_vectors.Vec8d, @splat(33554432.0)) + @as(core_vectors.Vec8d, @splat(0.5))) * @as(core_vectors.Vec8d, @splat(33554432.0));
                        const shifted4 = wrapped4 + @as(core_vectors.Vec8d, @splat(yo));
                        const cells: core_vectors.Vec8i = @intFromFloat(@floor(shifted4));
                        const fy = shifted4 - @as(core_vectors.Vec8d, @floatFromInt(cells));
                        const smooth: core_vectors.Vec8d = .{ core_math.smoothstep(fy[0]), core_math.smoothstep(fy[1]), core_math.smoothstep(fy[2]), core_math.smoothstep(fy[3]), core_math.smoothstep(fy[4]), core_math.smoothstep(fy[5]), core_math.smoothstep(fy[6]), core_math.smoothstep(fy[7]) };
                        const sampled = noise_sampling.sample_distinct_y(8, false, permutation, x_hash, next_x_hash, cells, integer_z, fraction_x, fy, fraction_z, smooth_x, smooth, smooth_z);
                        const current: core_vectors.Vec8d = @bitCast(values[index..][0..8].*);
                        values[index..][0..8].* = @bitCast(current + @as(core_vectors.Vec8d, @splat(amplitude)) * sampled * @as(core_vectors.Vec8d, @splat(value_factor)));
                        index += 8;
                        continue;
                    }
                    if (index + 3 < values.len and @abs(y_step * coordinate_scale * input_factor) >= 1.0) {
                        const indices: core_vectors.Vec4 = @floatFromInt(@as(core_vectors.Vec4i, .{ @intCast(index), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3) }));
                        const sample_y4 = @as(core_vectors.Vec4, @splat(y)) + indices * @as(core_vectors.Vec4, @splat(y_step));
                        const scaled4 = sample_y4 * @as(core_vectors.Vec4, @splat(coordinate_scale)) * @as(core_vectors.Vec4, @splat(input_factor));
                        const wrapped4 = if (no_wrap_y) scaled4 - @as(core_vectors.Vec4, @splat(0.0)) else scaled4 - @floor(scaled4 / @as(core_vectors.Vec4, @splat(33554432.0)) + @as(core_vectors.Vec4, @splat(0.5))) * @as(core_vectors.Vec4, @splat(33554432.0));
                        const shifted4 = wrapped4 + @as(core_vectors.Vec4, @splat(yo));
                        const cells: core_vectors.Vec4i = @intFromFloat(@floor(shifted4));
                        const fy = shifted4 - @as(core_vectors.Vec4, @floatFromInt(cells));
                        const smooth: core_vectors.Vec4 = .{ core_math.smoothstep(fy[0]), core_math.smoothstep(fy[1]), core_math.smoothstep(fy[2]), core_math.smoothstep(fy[3]) };
                        const sampled = noise_sampling.sample_distinct_y(4, false, permutation, x_hash, next_x_hash, cells, integer_z, fraction_x, fy, fraction_z, smooth_x, smooth, smooth_z);
                        const current: core_vectors.Vec4 = @bitCast(values[index..][0..4].*);
                        values[index..][0..4].* = @bitCast(current + @as(core_vectors.Vec4, @splat(amplitude)) * sampled * @as(core_vectors.Vec4, @splat(value_factor)));
                        index += 4;
                        continue;
                    }
                }
                if (comptime backend_options.small_batch) {
                    if (index + 15 < values.len and @abs(y_step * coordinate_scale * input_factor) < 0.25) {
                        const indices: core_vectors.Vec16d = @floatFromInt(@as(core_vectors.Vec16i, .{ @intCast(index + 0), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3), @intCast(index + 4), @intCast(index + 5), @intCast(index + 6), @intCast(index + 7), @intCast(index + 8), @intCast(index + 9), @intCast(index + 10), @intCast(index + 11), @intCast(index + 12), @intCast(index + 13), @intCast(index + 14), @intCast(index + 15) }));
                        const sample_y16 = @as(core_vectors.Vec16d, @splat(y)) + indices * @as(core_vectors.Vec16d, @splat(y_step));
                        const scaled16 = sample_y16 * @as(core_vectors.Vec16d, @splat(coordinate_scale)) * @as(core_vectors.Vec16d, @splat(input_factor));
                        const wrapped16 = if (no_wrap_y) scaled16 - @as(core_vectors.Vec16d, @splat(0.0)) else scaled16 - @floor(scaled16 / @as(core_vectors.Vec16d, @splat(33554432.0)) + @as(core_vectors.Vec16d, @splat(0.5))) * @as(core_vectors.Vec16d, @splat(33554432.0));
                        const shifted16 = wrapped16 + @as(core_vectors.Vec16d, @splat(yo));
                        const integer16: core_vectors.Vec16i = @intFromFloat(@floor(shifted16));
                        const cell_y = integer16[0];
                        if (@reduce(.And, integer16 == @as(core_vectors.Vec16i, @splat(cell_y)))) {
                            if (cell_y != previous_integer_y) {
                                const gradients = noise_gradients.GradientCell.init(permutation, x_hash, next_x_hash, cell_y, integer_z);
                                line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                                previous_integer_y = cell_y;
                            }
                            const fractions = shifted16 - @as(core_vectors.Vec16d, @splat(@as(f64, @floatFromInt(cell_y))));
                            const smooth: core_vectors.Vec16d = .{ core_math.smoothstep(fractions[0]), core_math.smoothstep(fractions[1]), core_math.smoothstep(fractions[2]), core_math.smoothstep(fractions[3]), core_math.smoothstep(fractions[4]), core_math.smoothstep(fractions[5]), core_math.smoothstep(fractions[6]), core_math.smoothstep(fractions[7]), core_math.smoothstep(fractions[8]), core_math.smoothstep(fractions[9]), core_math.smoothstep(fractions[10]), core_math.smoothstep(fractions[11]), core_math.smoothstep(fractions[12]), core_math.smoothstep(fractions[13]), core_math.smoothstep(fractions[14]), core_math.smoothstep(fractions[15]) };
                            const sampled = line.sample_smoothed16(fractions, smooth_x, smooth, smooth_z);
                            inline for (0..16) |lane| {
                                values[index + lane] += amplitude * sampled[lane] * value_factor;
                            }
                            index += 16;
                            continue;
                        }
                    }
                    if (index + 7 < values.len and @abs(y_step * coordinate_scale * input_factor) < 0.5) {
                        const indices: core_vectors.Vec8d = @floatFromInt(@as(core_vectors.Vec8i, .{
                            @intCast(index),     @intCast(index + 1), @intCast(index + 2), @intCast(index + 3),
                            @intCast(index + 4), @intCast(index + 5), @intCast(index + 6), @intCast(index + 7),
                        }));
                        const sample_y8 = @as(core_vectors.Vec8d, @splat(y)) + indices * @as(core_vectors.Vec8d, @splat(y_step));
                        const scaled8 = sample_y8 * @as(core_vectors.Vec8d, @splat(coordinate_scale)) * @as(core_vectors.Vec8d, @splat(input_factor));
                        const wrapped8 = if (no_wrap_y) scaled8 - @as(core_vectors.Vec8d, @splat(0.0)) else scaled8 - @floor(scaled8 / @as(core_vectors.Vec8d, @splat(33554432.0)) + @as(core_vectors.Vec8d, @splat(0.5))) * @as(core_vectors.Vec8d, @splat(33554432.0));
                        const shifted8 = wrapped8 + @as(core_vectors.Vec8d, @splat(yo));
                        const integer8: core_vectors.Vec8i = @intFromFloat(@floor(shifted8));
                        const cell_y = integer8[0];
                        if (@reduce(.And, integer8 == @as(core_vectors.Vec8i, @splat(cell_y)))) {
                            if (cell_y != previous_integer_y) {
                                const gradients = noise_gradients.GradientCell.init(permutation, x_hash, next_x_hash, cell_y, integer_z);
                                line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                                previous_integer_y = cell_y;
                            }
                            const fractions = shifted8 - @as(core_vectors.Vec8d, @splat(@as(f64, @floatFromInt(cell_y))));
                            const smooth: core_vectors.Vec8d = .{
                                core_math.smoothstep(fractions[0]), core_math.smoothstep(fractions[1]), core_math.smoothstep(fractions[2]), core_math.smoothstep(fractions[3]),
                                core_math.smoothstep(fractions[4]), core_math.smoothstep(fractions[5]), core_math.smoothstep(fractions[6]), core_math.smoothstep(fractions[7]),
                            };
                            const sampled = line.sample_smoothed8(fractions, smooth_x, smooth, smooth_z);
                            inline for (0..8) |lane| {
                                values[index + lane] += amplitude * sampled[lane] * value_factor;
                            }
                            index += 8;
                            continue;
                        }
                    }
                }
                if (index + 3 < values.len and @abs(y_step * coordinate_scale * input_factor) < 1.0) {
                    const indices: core_vectors.Vec4 = @floatFromInt(@as(core_vectors.Vec4i, .{
                        @intCast(index), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3),
                    }));
                    const sample_y4 = @as(core_vectors.Vec4, @splat(y)) + indices * @as(core_vectors.Vec4, @splat(y_step));
                    const scaled = sample_y4 * @as(core_vectors.Vec4, @splat(coordinate_scale)) * @as(core_vectors.Vec4, @splat(input_factor));
                    const wrapped4 = if (no_wrap_y) scaled - @as(core_vectors.Vec4, @splat(0.0)) else scaled - @floor(scaled / @as(core_vectors.Vec4, @splat(33554432.0)) + @as(core_vectors.Vec4, @splat(0.5))) * @as(core_vectors.Vec4, @splat(33554432.0));
                    const shifted4 = wrapped4 + @as(core_vectors.Vec4, @splat(yo));
                    const integer4: core_vectors.Vec4i = @intFromFloat(@floor(shifted4));
                    integer_y = integer4[0];
                    shifted_y = shifted4[0];
                    if (@reduce(.And, integer4 == @as(core_vectors.Vec4i, @splat(integer_y)))) {
                        if (integer_y != previous_integer_y) {
                            const gradients = noise_gradients.GradientCell.init(permutation, x_hash, next_x_hash, integer_y, integer_z);
                            line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                            previous_integer_y = integer_y;
                        }
                        const fractions = shifted4 - @as(core_vectors.Vec4, @splat(@as(f64, @floatFromInt(integer_y))));
                        const smooth: core_vectors.Vec4 = .{
                            core_math.smoothstep(fractions[0]), core_math.smoothstep(fractions[1]),
                            core_math.smoothstep(fractions[2]), core_math.smoothstep(fractions[3]),
                        };
                        const sampled = line.sample_smoothed4(fractions, smooth_x, smooth, smooth_z);
                        inline for (0..4) |lane| {
                            values[index + lane] += amplitude * sampled[lane] * value_factor;
                        }
                        index += 4;
                        continue;
                    }
                } else {
                    const sample_y = y + @as(f64, @floatFromInt(index)) * y_step;
                    const scaled_y = sample_y * coordinate_scale * input_factor;
                    shifted_y = (if (no_wrap_y) scaled_y - 0.0 else core_math.wrap(scaled_y)) + yo;
                    integer_y = core_math.floor_int(shifted_y);
                }
                if (integer_y != previous_integer_y) {
                    const gradients = noise_gradients.GradientCell.init(permutation, x_hash, next_x_hash, integer_y, integer_z);
                    line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                    previous_integer_y = integer_y;
                }
                const fraction_y = shifted_y - @as(f64, @floatFromInt(integer_y));
                const sampled = line.sample_smoothed(fraction_y, smooth_x, core_math.smoothstep(fraction_y), smooth_z);
                values[index] += amplitude * sampled * value_factor;
                index += 1;
            }
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
}
