const core_limits = @import("../core/limits.zig");
const core_math = @import("../core/math.zig");
const core_vectors = @import("../core/vectors.zig");
const noise_gradients = @import("gradients.zig");

pub fn perlin_grid_point(data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, coordinate_scale: f64, result: *f64) void {
    @setFloatMode(.strict);
    const origin_offset = 16 + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    const bases: core_vectors.Vec4 = .{ x, y, z, 0.0 };
    const steps: core_vectors.Vec4 = .{ x_step, y_step, z_step, 0.0 };
    const samples = bases + @as(core_vectors.Vec4, @splat(0.0)) * steps;
    const scale: core_vectors.Vec4 = @splat(coordinate_scale);
    const wrap_size: core_vectors.Vec4 = @splat(33554432.0);
    const limit: core_vectors.Vec4 = @splat(16777216.0);
    var input_factor = core_math.read_f64(data, 8);
    var value_factor = core_math.read_f64(data, 0);
    var value = result.*;
    for (0..octaves) |octave| {
        if (data[active_offset + octave] != 0) {
            const factor: core_vectors.Vec4 = @splat(input_factor);
            const first_scaled = bases * scale * factor;
            const scaled = samples * scale * factor;
            const no_wrap = (@abs(first_scaled) < limit) & (@abs(scaled) < limit);
            const wrapped = scaled - @floor(scaled / wrap_size + @as(core_vectors.Vec4, @splat(0.5))) * wrap_size;
            const origin = origin_offset + octave * 24;
            const origins: core_vectors.Vec4 = .{ core_math.read_f64(data, origin), core_math.read_f64(data, origin + 8), core_math.read_f64(data, origin + 16), 0.0 };
            const shifted = @select(f64, no_wrap, scaled - @as(core_vectors.Vec4, @splat(0.0)), wrapped) + origins;
            const integers: core_vectors.Vec4i = @intFromFloat(@floor(shifted));
            const fractions = shifted - @as(core_vectors.Vec4, @floatFromInt(integers));
            const smooth = fractions * fractions * fractions * (fractions * (fractions * @as(core_vectors.Vec4, @splat(6.0)) - @as(core_vectors.Vec4, @splat(15.0))) + @as(core_vectors.Vec4, @splat(10.0)));
            const permutation = data + permutation_offset + octave * 256;
            const gradients = noise_gradients.GradientCell.init(permutation, core_math.permutation_value(permutation, integers[0]), core_math.permutation_value(permutation, integers[0] + 1), integers[1], integers[2]);
            const sampled = gradients.sample_packed(fractions[0], fractions[1], fractions[2], smooth[0], smooth[1], smooth[2]);
            value += core_math.read_f64(data, 16 + octave * 8) * sampled * value_factor;
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
    result.* = value;
}

pub fn normal_noise_value(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64) f64 {
    @setFloatMode(.strict);
    return (perlin_value(first, first_count, x, y, z) + perlin_value(second, second_count, x * core_limits.normal_noise_input_factor, y * core_limits.normal_noise_input_factor, z * core_limits.normal_noise_input_factor)) * value_factor;
}

pub fn normal_noise_value2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: core_vectors.Vec2, z: f64) core_vectors.Vec2 {
    @setFloatMode(.strict);
    const factor: core_vectors.Vec2 = @splat(value_factor);
    const input_factor: core_vectors.Vec2 = @splat(core_limits.normal_noise_input_factor);
    return (perlin_value2(first, first_count, x, y, z) + perlin_value2(second, second_count, x * core_limits.normal_noise_input_factor, y * input_factor, z * core_limits.normal_noise_input_factor)) * factor;
}

pub fn perlin_value(data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64) f64 {
    @setFloatMode(.strict);
    const lowest_value_factor = core_math.read_f64(data, 0);
    const lowest_input_factor = core_math.read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var value: f64 = 0.0;
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = core_math.read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            value += amplitude * improved_noise(permutation, core_math.read_f64(data, origin), core_math.read_f64(data, origin + 8), core_math.read_f64(data, origin + 16), core_math.wrap(x * input_factor), core_math.wrap(y * input_factor), core_math.wrap(z * input_factor)) * value_factor;
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
    return value;
}

pub fn perlin_value2(data: [*]const u8, octaves: usize, x: f64, y: core_vectors.Vec2, z: f64) core_vectors.Vec2 {
    @setFloatMode(.strict);
    const lowest_value_factor = core_math.read_f64(data, 0);
    const lowest_input_factor = core_math.read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var value: core_vectors.Vec2 = @splat(0.0);
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = core_math.read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            const sample_y: core_vectors.Vec2 = .{ core_math.wrap(y[0] * input_factor), core_math.wrap(y[1] * input_factor) };
            value += (improved_noise2(permutation, core_math.read_f64(data, origin), core_math.read_f64(data, origin + 8), core_math.read_f64(data, origin + 16), core_math.wrap(x * input_factor), sample_y, core_math.wrap(z * input_factor)) * @as(core_vectors.Vec2, @splat(amplitude))) * @as(core_vectors.Vec2, @splat(value_factor));
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
    return value;
}

pub fn improved_noise(permutation: [*]const u8, xo: f64, yo: f64, zo: f64, x: f64, y: f64, z: f64) f64 {
    @setFloatMode(.strict);
    const shifted_x = x + xo;
    const shifted_y = y + yo;
    const shifted_z = z + zo;
    const integer_x = core_math.floor_int(shifted_x);
    const integer_y = core_math.floor_int(shifted_y);
    const integer_z = core_math.floor_int(shifted_z);
    const fraction_x = shifted_x - @as(f64, @floatFromInt(integer_x));
    const fraction_y = shifted_y - @as(f64, @floatFromInt(integer_y));
    const fraction_z = shifted_z - @as(f64, @floatFromInt(integer_z));
    return sample_and_lerp(permutation, integer_x, integer_y, integer_z, fraction_x, fraction_y, fraction_z);
}

pub fn improved_noise2(permutation: [*]const u8, xo: f64, yo: f64, zo: f64, x: f64, y: core_vectors.Vec2, z: f64) core_vectors.Vec2 {
    @setFloatMode(.strict);
    const shifted_x = x + xo;
    const shifted_y = y + @as(core_vectors.Vec2, @splat(yo));
    const shifted_z = z + zo;
    const integer_x = core_math.floor_int(shifted_x);
    const integer_y = [2]i32{ core_math.floor_int(shifted_y[0]), core_math.floor_int(shifted_y[1]) };
    const integer_z = core_math.floor_int(shifted_z);
    const fraction_x = shifted_x - @as(f64, @floatFromInt(integer_x));
    const fraction_y = shifted_y - @as(core_vectors.Vec2, .{ @as(f64, @floatFromInt(integer_y[0])), @as(f64, @floatFromInt(integer_y[1])) });
    const fraction_z = shifted_z - @as(f64, @floatFromInt(integer_z));
    return sample_and_lerp2(permutation, integer_x, integer_y, integer_z, fraction_x, fraction_y, fraction_z);
}

pub fn sample_and_lerp(permutation: [*]const u8, x: i32, y: i32, z: i32, fraction_x: f64, fraction_y: f64, fraction_z: f64) f64 {
    @setFloatMode(.strict);
    const x_hash = core_math.permutation_value(permutation, x);
    const next_x_hash = core_math.permutation_value(permutation, x + 1);
    return sample_and_lerp_prepared(permutation, x_hash, next_x_hash, y, z, fraction_x, fraction_y, fraction_z);
}

pub fn sample_and_lerp_prepared(permutation: [*]const u8, x_hash: i32, next_x_hash: i32, y: i32, z: i32, fraction_x: f64, fraction_y: f64, fraction_z: f64) f64 {
    @setFloatMode(.strict);
    const xy_hash = core_math.permutation_value(permutation, x_hash + y);
    const xy_next_hash = core_math.permutation_value(permutation, x_hash + y + 1);
    const next_xy_hash = core_math.permutation_value(permutation, next_x_hash + y);
    const next_xy_next_hash = core_math.permutation_value(permutation, next_x_hash + y + 1);
    const value000 = core_math.gradient(core_math.permutation_value(permutation, xy_hash + z), fraction_x, fraction_y, fraction_z);
    const value100 = core_math.gradient(core_math.permutation_value(permutation, next_xy_hash + z), fraction_x - 1.0, fraction_y, fraction_z);
    const value010 = core_math.gradient(core_math.permutation_value(permutation, xy_next_hash + z), fraction_x, fraction_y - 1.0, fraction_z);
    const value110 = core_math.gradient(core_math.permutation_value(permutation, next_xy_next_hash + z), fraction_x - 1.0, fraction_y - 1.0, fraction_z);
    const value001 = core_math.gradient(core_math.permutation_value(permutation, xy_hash + z + 1), fraction_x, fraction_y, fraction_z - 1.0);
    const value101 = core_math.gradient(core_math.permutation_value(permutation, next_xy_hash + z + 1), fraction_x - 1.0, fraction_y, fraction_z - 1.0);
    const value011 = core_math.gradient(core_math.permutation_value(permutation, xy_next_hash + z + 1), fraction_x, fraction_y - 1.0, fraction_z - 1.0);
    const value111 = core_math.gradient(core_math.permutation_value(permutation, next_xy_next_hash + z + 1), fraction_x - 1.0, fraction_y - 1.0, fraction_z - 1.0);
    return core_math.lerp3(core_math.smoothstep(fraction_x), core_math.smoothstep(fraction_y), core_math.smoothstep(fraction_z), value000, value100, value010, value110, value001, value101, value011, value111);
}

pub fn sample_and_lerp2(permutation: [*]const u8, x: i32, y: [2]i32, z: i32, fraction_x: f64, fraction_y: core_vectors.Vec2, fraction_z: f64) core_vectors.Vec2 {
    @setFloatMode(.strict);
    const x_hash = core_math.permutation_value(permutation, x);
    const next_x_hash = core_math.permutation_value(permutation, x + 1);
    const xy_hash0 = core_math.permutation_value(permutation, x_hash + y[0]);
    const xy_next_hash0 = core_math.permutation_value(permutation, x_hash + y[0] + 1);
    const next_xy_hash0 = core_math.permutation_value(permutation, next_x_hash + y[0]);
    const next_xy_next_hash0 = core_math.permutation_value(permutation, next_x_hash + y[0] + 1);
    const xy_hash1 = core_math.permutation_value(permutation, x_hash + y[1]);
    const xy_next_hash1 = core_math.permutation_value(permutation, x_hash + y[1] + 1);
    const next_xy_hash1 = core_math.permutation_value(permutation, next_x_hash + y[1]);
    const next_xy_next_hash1 = core_math.permutation_value(permutation, next_x_hash + y[1] + 1);
    const value000 = core_math.gradient2(core_math.permutation_value(permutation, xy_hash0 + z), core_math.permutation_value(permutation, xy_hash1 + z), fraction_x, fraction_y, fraction_z);
    const value100 = core_math.gradient2(core_math.permutation_value(permutation, next_xy_hash0 + z), core_math.permutation_value(permutation, next_xy_hash1 + z), fraction_x - 1.0, fraction_y, fraction_z);
    const value010 = core_math.gradient2(core_math.permutation_value(permutation, xy_next_hash0 + z), core_math.permutation_value(permutation, xy_next_hash1 + z), fraction_x, fraction_y - @as(core_vectors.Vec2, @splat(1.0)), fraction_z);
    const value110 = core_math.gradient2(core_math.permutation_value(permutation, next_xy_next_hash0 + z), core_math.permutation_value(permutation, next_xy_next_hash1 + z), fraction_x - 1.0, fraction_y - @as(core_vectors.Vec2, @splat(1.0)), fraction_z);
    const value001 = core_math.gradient2(core_math.permutation_value(permutation, xy_hash0 + z + 1), core_math.permutation_value(permutation, xy_hash1 + z + 1), fraction_x, fraction_y, fraction_z - 1.0);
    const value101 = core_math.gradient2(core_math.permutation_value(permutation, next_xy_hash0 + z + 1), core_math.permutation_value(permutation, next_xy_hash1 + z + 1), fraction_x - 1.0, fraction_y, fraction_z - 1.0);
    const value011 = core_math.gradient2(core_math.permutation_value(permutation, xy_next_hash0 + z + 1), core_math.permutation_value(permutation, xy_next_hash1 + z + 1), fraction_x, fraction_y - @as(core_vectors.Vec2, @splat(1.0)), fraction_z - 1.0);
    const value111 = core_math.gradient2(core_math.permutation_value(permutation, next_xy_next_hash0 + z + 1), core_math.permutation_value(permutation, next_xy_next_hash1 + z + 1), fraction_x - 1.0, fraction_y - @as(core_vectors.Vec2, @splat(1.0)), fraction_z - 1.0);
    const smooth_x: core_vectors.Vec2 = @splat(core_math.smoothstep(fraction_x));
    const smooth_z: core_vectors.Vec2 = @splat(core_math.smoothstep(fraction_z));
    return core_math.lerp3_2(smooth_x, core_math.smoothstep2(fraction_y), smooth_z, value000, value100, value010, value110, value001, value101, value011, value111);
}
