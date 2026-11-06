const core_limits = @import("../core/limits.zig");
const core_math = @import("../core/math.zig");
const core_vectors = @import("../core/vectors.zig");
const noise_scale = @import("scale.zig");

pub fn normal_noise_grid_approx(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    normal_noise_grid_approx_impl(false, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_approx_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    normal_noise_grid_approx_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_approx_float(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    normal_noise_grid_approx_float_impl(false, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_approx_float_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    normal_noise_grid_approx_float_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_approx_impl(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    const total = x_count * y_count * z_count;
    var float_values: [core_limits.max_grid_size]f32 = undefined;
    const output = float_values[0..total];
    normal_noise_grid_approx_float_impl(avx2, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, output);
    var index: usize = 0;
    while (index < total) : (index += 1) {
        values[index] = @floatCast(output[index]);
    }
}

pub fn normal_noise_grid_approx_float_impl(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    @memset(values, 0.0);
    perlin_value_grid_approx(avx2, first, first_count, @floatCast(x), @floatCast(y), @floatCast(z), @floatCast(x_step), @floatCast(y_step), @floatCast(z_step), 1.0, x_count, y_count, z_count, values);
    perlin_value_grid_approx(avx2, second, second_count, @floatCast(x), @floatCast(y), @floatCast(z), @floatCast(x_step), @floatCast(y_step), @floatCast(z_step), @floatCast(core_limits.normal_noise_input_factor), x_count, y_count, z_count, values);
    const factor: f32 = @floatCast(value_factor);
    noise_scale.scale_f32_values(avx2, values, factor);
}

pub fn perlin_value_grid_approx(comptime avx2: bool, data: [*]const u8, octaves: usize, x: f32, y: f32, z: f32, x_step: f32, y_step: f32, z_step: f32, coordinate_scale: f32, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    if (x_count > core_limits.max_grid_axis or y_count > core_limits.max_grid_axis or z_count > core_limits.max_grid_axis) {
        return;
    }
    const lowest_value_factor: f32 = @floatCast(core_math.read_f64(data, 0));
    const lowest_input_factor: f32 = @floatCast(core_math.read_f64(data, 8));
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    var integer_x: [core_limits.max_grid_axis]i32 = undefined;
    var integer_y: [core_limits.max_grid_axis]i32 = undefined;
    var integer_z: [core_limits.max_grid_axis]i32 = undefined;
    var fraction_x: [core_limits.max_grid_axis]f32 = undefined;
    var fraction_y: [core_limits.max_grid_axis]f32 = undefined;
    var fraction_z: [core_limits.max_grid_axis]f32 = undefined;
    var smooth_x: [core_limits.max_grid_axis]f32 = undefined;
    var smooth_y: [core_limits.max_grid_axis]f32 = undefined;
    var smooth_z: [core_limits.max_grid_axis]f32 = undefined;
    var input_factor = lowest_input_factor;
    var octave_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude: f32 = @floatCast(core_math.read_f64(data, amplitude_offset + octave * 8));
            const origin = origin_offset + octave * 24;
            const seed = quick_seed(data, origin, octave);
            prepare_grid_axis_approx(x, x_step, x_count, coordinate_scale, input_factor, integer_x[0..x_count], fraction_x[0..x_count], smooth_x[0..x_count]);
            prepare_grid_axis_approx(y, y_step, y_count, coordinate_scale, input_factor, integer_y[0..y_count], fraction_y[0..y_count], smooth_y[0..y_count]);
            prepare_grid_axis_approx(z, z_step, z_count, coordinate_scale, input_factor, integer_z[0..z_count], fraction_z[0..z_count], smooth_z[0..z_count]);
            var x_start: usize = 0;
            while (x_start < x_count) {
                var x_end = x_start + 1;
                while (x_end < x_count and integer_x[x_end] == integer_x[x_start]) : (x_end += 1) {}
                var z_start: usize = 0;
                while (z_start < z_count) {
                    var z_end = z_start + 1;
                    while (z_end < z_count and integer_z[z_end] == integer_z[z_start]) : (z_end += 1) {}
                    var y_start: usize = 0;
                    while (y_start < y_count) {
                        var y_end = y_start + 1;
                        while (y_end < y_count and integer_y[y_end] == integer_y[y_start]) : (y_end += 1) {}
                        const gradients = QuickGradientCell.init(seed, integer_x[x_start], integer_y[y_start], integer_z[z_start]);
                        var x_index = x_start;
                        while (x_index < x_end) : (x_index += 1) {
                            var z_index = z_start;
                            while (z_index < z_end) : (z_index += 1) {
                                const offset = (x_index * z_count + z_index) * y_count;
                                const line = gradients.line(fraction_x[x_index], fraction_z[z_index]);
                                var y_index = y_start;
                                if (comptime avx2) {
                                    const amplitude8: core_vectors.Vec8f = @splat(amplitude);
                                    const octave_factor8: core_vectors.Vec8f = @splat(octave_factor);
                                    while (y_index + 7 < y_end) : (y_index += 8) {
                                        const sampled = line.sample8(.{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3], fraction_y[y_index + 4], fraction_y[y_index + 5], fraction_y[y_index + 6], fraction_y[y_index + 7] }, smooth_x[x_index], .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3], smooth_y[y_index + 4], smooth_y[y_index + 5], smooth_y[y_index + 6], smooth_y[y_index + 7] }, smooth_z[z_index]);
                                        const value_index = offset + y_index;
                                        const current: core_vectors.Vec8f = @bitCast(values[value_index..][0..8].*);
                                        values[value_index..][0..8].* = @bitCast(current + amplitude8 * sampled * octave_factor8);
                                    }
                                }
                                const amplitude4: core_vectors.Vec4f = @splat(amplitude);
                                const octave_factor4: core_vectors.Vec4f = @splat(octave_factor);
                                while (y_index + 3 < y_end) : (y_index += 4) {
                                    const sampled = line.sample4(.{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3] }, smooth_x[x_index], .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3] }, smooth_z[z_index]);
                                    const value_index = offset + y_index;
                                    const current: core_vectors.Vec4f = @bitCast(values[value_index..][0..4].*);
                                    values[value_index..][0..4].* = @bitCast(current + amplitude4 * sampled * octave_factor4);
                                }
                                while (y_index < y_end) : (y_index += 1) {
                                    values[offset + y_index] += amplitude * line.sample(fraction_y[y_index], smooth_x[x_index], smooth_y[y_index], smooth_z[z_index]) * octave_factor;
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
        octave_factor *= 0.5;
    }
}

pub fn prepare_grid_axis_approx(base: f32, step: f32, count: usize, coordinate_scale: f32, input_factor: f32, integers: []i32, fractions: []f32, smooth: []f32) void {
    var index: usize = 0;
    while (index < count) : (index += 1) {
        const sample = base + @as(f32, @floatFromInt(index)) * step;
        const shifted = wrap_approx(sample * coordinate_scale * input_factor);
        const integer: i32 = @intFromFloat(@floor(shifted));
        const fraction = shifted - @as(f32, @floatFromInt(integer));
        integers[index] = integer;
        fractions[index] = fraction;
        smooth[index] = smoothstep_approx(fraction);
    }
}

pub const QuickGradientCell = struct {
    values: [8]u32,

    pub fn init(seed: u32, x: i32, y: i32, z: i32) QuickGradientCell {
        return .{ .values = .{
            quick_hash(seed, x, y, z),           quick_hash(seed, x +% 1, y, z),
            quick_hash(seed, x, y +% 1, z),      quick_hash(seed, x +% 1, y +% 1, z),
            quick_hash(seed, x, y, z +% 1),      quick_hash(seed, x +% 1, y, z +% 1),
            quick_hash(seed, x, y +% 1, z +% 1), quick_hash(seed, x +% 1, y +% 1, z +% 1),
        } };
    }

    pub fn line(self: QuickGradientCell, x: f32, z: f32) QuickGradientLine {
        return .{ .values = .{
            quick_gradient_term(self.values[0], x, z, 0.0),        quick_gradient_term(self.values[1], x - 1.0, z, 0.0),
            quick_gradient_term(self.values[2], x, z, -1.0),       quick_gradient_term(self.values[3], x - 1.0, z, -1.0),
            quick_gradient_term(self.values[4], x, z - 1.0, 0.0),  quick_gradient_term(self.values[5], x - 1.0, z - 1.0, 0.0),
            quick_gradient_term(self.values[6], x, z - 1.0, -1.0), quick_gradient_term(self.values[7], x - 1.0, z - 1.0, -1.0),
        } };
    }
};

pub const QuickGradientTerm = struct {
    base: f32,
    y_offset: f32,
    mode: enum { none, add, subtract },

    pub fn sample(self: QuickGradientTerm, y: f32) f32 {
        return switch (self.mode) {
            .none => self.base,
            .add => self.base + y + self.y_offset,
            .subtract => self.base - y - self.y_offset,
        };
    }

    pub fn sample4(self: QuickGradientTerm, y: core_vectors.Vec4f) core_vectors.Vec4f {
        const base: core_vectors.Vec4f = @splat(self.base);
        const offset: core_vectors.Vec4f = @splat(self.y_offset);
        return switch (self.mode) {
            .none => base,
            .add => base + y + offset,
            .subtract => base - y - offset,
        };
    }

    pub fn sample8(self: QuickGradientTerm, y: core_vectors.Vec8f) core_vectors.Vec8f {
        const base: core_vectors.Vec8f = @splat(self.base);
        const offset: core_vectors.Vec8f = @splat(self.y_offset);
        return switch (self.mode) {
            .none => base,
            .add => base + y + offset,
            .subtract => base - y - offset,
        };
    }
};

pub const QuickGradientLine = struct {
    values: [8]QuickGradientTerm,

    pub fn sample(self: QuickGradientLine, y: f32, x_fade: f32, y_fade: f32, z_fade: f32) f32 {
        return lerp3_approx(x_fade, y_fade, z_fade, self.values[0].sample(y), self.values[1].sample(y), self.values[2].sample(y), self.values[3].sample(y), self.values[4].sample(y), self.values[5].sample(y), self.values[6].sample(y), self.values[7].sample(y));
    }

    pub fn sample4(self: QuickGradientLine, y: core_vectors.Vec4f, x_fade: f32, y_fade: core_vectors.Vec4f, z_fade: f32) core_vectors.Vec4f {
        return lerp3_approx4(@splat(x_fade), y_fade, @splat(z_fade), self.values[0].sample4(y), self.values[1].sample4(y), self.values[2].sample4(y), self.values[3].sample4(y), self.values[4].sample4(y), self.values[5].sample4(y), self.values[6].sample4(y), self.values[7].sample4(y));
    }

    pub fn sample8(self: QuickGradientLine, y: core_vectors.Vec8f, x_fade: f32, y_fade: core_vectors.Vec8f, z_fade: f32) core_vectors.Vec8f {
        return lerp3_approx8(@splat(x_fade), y_fade, @splat(z_fade), self.values[0].sample8(y), self.values[1].sample8(y), self.values[2].sample8(y), self.values[3].sample8(y), self.values[4].sample8(y), self.values[5].sample8(y), self.values[6].sample8(y), self.values[7].sample8(y));
    }
};

pub fn quick_seed(data: [*]const u8, origin: usize, octave: usize) u32 {
    const first: u64 = @bitCast(core_math.read_f64(data, origin));
    const second: u64 = @bitCast(core_math.read_f64(data, origin + 8));
    const third: u64 = @bitCast(core_math.read_f64(data, origin + 16));
    return quick_mix(@as(u32, @truncate(first)) ^ @as(u32, @truncate(first >> 32)) ^ @as(u32, @truncate(second)) ^ @as(u32, @truncate(second >> 32)) ^ @as(u32, @truncate(third)) ^ @as(u32, @truncate(third >> 32)) ^ @as(u32, @intCast(octave)) *% 0x9e3779b9);
}

pub fn quick_hash(seed: u32, x: i32, y: i32, z: i32) u32 {
    const x_bits: u32 = @bitCast(x);
    const y_bits: u32 = @bitCast(y);
    const z_bits: u32 = @bitCast(z);
    return quick_mix(seed ^ (x_bits *% 0x85ebca6b) ^ (y_bits *% 0xc2b2ae35) ^ (z_bits *% 0x27d4eb2f));
}

pub fn quick_mix(value: u32) u32 {
    var mixed = value;
    mixed ^= mixed >> 16;
    mixed *%= 0x7feb352d;
    mixed ^= mixed >> 15;
    mixed *%= 0x846ca68b;
    mixed ^= mixed >> 16;
    return mixed;
}

pub fn quick_gradient_term(value: u32, x: f32, z: f32, y_offset: f32) QuickGradientTerm {
    return switch (value & 15) {
        0 => .{ .base = x, .y_offset = y_offset, .mode = .add },
        1 => .{ .base = -x, .y_offset = y_offset, .mode = .add },
        2 => .{ .base = x, .y_offset = y_offset, .mode = .subtract },
        3 => .{ .base = -x, .y_offset = y_offset, .mode = .subtract },
        4 => .{ .base = x + z, .y_offset = y_offset, .mode = .none },
        5 => .{ .base = -x + z, .y_offset = y_offset, .mode = .none },
        6 => .{ .base = x - z, .y_offset = y_offset, .mode = .none },
        7 => .{ .base = -x - z, .y_offset = y_offset, .mode = .none },
        8 => .{ .base = z, .y_offset = y_offset, .mode = .add },
        9 => .{ .base = z, .y_offset = y_offset, .mode = .subtract },
        10 => .{ .base = -z, .y_offset = y_offset, .mode = .add },
        11 => .{ .base = -z, .y_offset = y_offset, .mode = .subtract },
        12 => .{ .base = x, .y_offset = y_offset, .mode = .add },
        13 => .{ .base = z, .y_offset = y_offset, .mode = .subtract },
        14 => .{ .base = -x, .y_offset = y_offset, .mode = .add },
        else => .{ .base = -z, .y_offset = y_offset, .mode = .subtract },
    };
}

pub fn wrap_approx(value: f32) f32 {
    return value - @floor(value / 33554432.0 + 0.5) * 33554432.0;
}

pub fn smoothstep_approx(value: f32) f32 {
    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
}

pub fn lerp_approx(delta: f32, start: f32, end: f32) f32 {
    return start + delta * (end - start);
}

pub fn lerp3_approx(x: f32, y: f32, z: f32, value000: f32, value100: f32, value010: f32, value110: f32, value001: f32, value101: f32, value011: f32, value111: f32) f32 {
    return lerp_approx(z, lerp_approx(y, lerp_approx(x, value000, value100), lerp_approx(x, value010, value110)), lerp_approx(y, lerp_approx(x, value001, value101), lerp_approx(x, value011, value111)));
}

pub fn lerp_approx4(delta: core_vectors.Vec4f, start: core_vectors.Vec4f, end: core_vectors.Vec4f) core_vectors.Vec4f {
    return start + delta * (end - start);
}

pub fn lerp3_approx4(x: core_vectors.Vec4f, y: core_vectors.Vec4f, z: core_vectors.Vec4f, value000: core_vectors.Vec4f, value100: core_vectors.Vec4f, value010: core_vectors.Vec4f, value110: core_vectors.Vec4f, value001: core_vectors.Vec4f, value101: core_vectors.Vec4f, value011: core_vectors.Vec4f, value111: core_vectors.Vec4f) core_vectors.Vec4f {
    return lerp_approx4(z, lerp_approx4(y, lerp_approx4(x, value000, value100), lerp_approx4(x, value010, value110)), lerp_approx4(y, lerp_approx4(x, value001, value101), lerp_approx4(x, value011, value111)));
}

pub fn lerp_approx8(delta: core_vectors.Vec8f, start: core_vectors.Vec8f, end: core_vectors.Vec8f) core_vectors.Vec8f {
    return start + delta * (end - start);
}

pub fn lerp3_approx8(x: core_vectors.Vec8f, y: core_vectors.Vec8f, z: core_vectors.Vec8f, value000: core_vectors.Vec8f, value100: core_vectors.Vec8f, value010: core_vectors.Vec8f, value110: core_vectors.Vec8f, value001: core_vectors.Vec8f, value101: core_vectors.Vec8f, value011: core_vectors.Vec8f, value111: core_vectors.Vec8f) core_vectors.Vec8f {
    return lerp_approx8(z, lerp_approx8(y, lerp_approx8(x, value000, value100), lerp_approx8(x, value010, value110)), lerp_approx8(y, lerp_approx8(x, value001, value101), lerp_approx8(x, value011, value111)));
}
