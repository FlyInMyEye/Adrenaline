const std = @import("std");
const core_vectors = @import("../core/vectors.zig");
const density_math = @import("math.zig");
const density_read = @import("read.zig");

pub fn ridge_dominant(program: []const u8) u8 {
    @setFloatMode(.strict);
    const first0 = @abs(density_read.density_program_value(program, 1));
    const second0 = @abs(density_read.density_program_value(program, 67));
    if (first0 <= second0 * 1.5 and second0 <= first0 * 1.5) return 0;
    const first_a: core_vectors.Vec4 = @bitCast(program[1..][0..32].*);
    const first_b: core_vectors.Vec4 = @bitCast(program[33..][0..32].*);
    const second_a: core_vectors.Vec4 = @bitCast(program[67..][0..32].*);
    const second_b: core_vectors.Vec4 = @bitCast(program[99..][0..32].*);
    const aa = @abs(first_a);
    const ab = @abs(first_b);
    const ba = @abs(second_a);
    const bb = @abs(second_b);
    const first_max = @reduce(.Max, @max(aa, ab));
    const second_max = @reduce(.Max, @max(ba, bb));
    const first_min = @reduce(.Min, @min(aa, ab));
    const second_min = @reduce(.Min, @min(ba, bb));
    const margin = @max(first_max, second_max) * 0x1p-40 + std.math.floatMin(f64);
    const zero: core_vectors.Vec4 = @splat(0.0);
    const first_same_sign = (@reduce(.And, first_a >= zero) and @reduce(.And, first_b >= zero)) or (@reduce(.And, first_a <= zero) and @reduce(.And, first_b <= zero));
    if (first_same_sign and first_min - margin > second_max + margin) return 1;
    const second_same_sign = (@reduce(.And, second_a >= zero) and @reduce(.And, second_b >= zero)) or (@reduce(.And, second_a <= zero) and @reduce(.And, second_b <= zero));
    if (second_same_sign and second_min - margin > first_max + margin) return 2;
    return 0;
}

pub const TerrainChoice = enum { mixed, inside, outside, inside_high };

pub fn terrain_choice(program: []const u8) TerrainChoice {
    @setFloatMode(.strict);
    const first: core_vectors.Vec4 = @bitCast(program[77..][0..32].*);
    const second: core_vectors.Vec4 = @bitCast(program[109..][0..32].*);
    const minimum = density_read.density_program_value(program, 360);
    const maximum = density_read.density_program_value(program, 368);
    if (minimum >= maximum) return .outside;
    const magnitude = @reduce(.Max, @max(@abs(first), @abs(second)));
    const margin = if (magnitude == 0.0) 0.0 else magnitude * 0x1p-40 + std.math.floatMin(f64);
    const lower = @reduce(.Min, @min(first, second)) - margin;
    const upper = @reduce(.Max, @max(first, second)) + margin;
    if (lower >= minimum and upper < maximum) return .inside;
    if (upper < minimum or lower >= maximum) return .outside;
    return .mixed;
}

pub fn terrain_negative_saturated(program: []const u8) bool {
    @setFloatMode(.strict);
    const scale = density_read.density_program_value(program, 66);
    if (!(density_read.density_program_value(program, 1) * scale < -1.0)) return false;
    const a: core_vectors.Vec4 = @bitCast(program[1..][0..32].*);
    const b: core_vectors.Vec4 = @bitCast(program[33..][0..32].*);
    const magnitude = @reduce(.Max, @max(@abs(a), @abs(b)));
    const margin = magnitude * 0x1p-40 + std.math.floatMin(f64);
    const bound = if (scale > 0.0) (@reduce(.Max, @max(a, b)) + margin) * scale else (@reduce(.Min, @min(a, b)) - margin) * scale;
    return bound < -1.0;
}

pub fn terrain_thickness_above_floor(program: []const u8) bool {
    @setFloatMode(.strict);
    if (!(density_read.density_program_value(program, 349) >= 0.0)) return false;
    const a: core_vectors.Vec4 = @bitCast(program[151..][0..32].*);
    const b: core_vectors.Vec4 = @bitCast(program[183..][0..32].*);
    const magnitude = @reduce(.Max, @max(@abs(a), @abs(b)));
    const margin = magnitude * 0x1p-40 + std.math.floatMin(f64);
    return @reduce(.Min, @min(a, b)) - margin > -0.4;
}

pub fn ridge_min_abs(program: []const u8, offset: usize) f64 {
    const a: core_vectors.Vec4 = @bitCast(program[offset..][0..32].*);
    const b: core_vectors.Vec4 = @bitCast(program[offset + 32 ..][0..32].*);
    const zero: core_vectors.Vec4 = @splat(0.0);
    const same_sign = (@reduce(.And, a >= zero) and @reduce(.And, b >= zero)) or (@reduce(.And, a <= zero) and @reduce(.And, b <= zero));
    if (!same_sign) return 0.0;
    const magnitude = @reduce(.Max, @max(@abs(a), @abs(b)));
    const margin = magnitude * 0x1p-40 + std.math.floatMin(f64);
    return @max(0.0, @reduce(.Min, @min(@abs(a), @abs(b))) - margin);
}

pub fn outside_above(program: []const u8, floor: f64) bool {
    @setFloatMode(.strict);
    const scale = density_read.density_program_value(program, 349);
    if (!(scale >= 0.0)) return false;
    const estimate = density_read.density_program_value(program, 151) + @max(@abs(density_read.density_program_value(program, 216)), @abs(density_read.density_program_value(program, 282))) * scale;
    if (!(estimate > floor)) return false;
    const a: core_vectors.Vec4 = @bitCast(program[151..][0..32].*);
    const b: core_vectors.Vec4 = @bitCast(program[183..][0..32].*);
    const magnitude = @reduce(.Max, @max(@abs(a), @abs(b)));
    const margin = magnitude * 0x1p-40 + std.math.floatMin(f64);
    const thickness = @reduce(.Min, @min(a, b)) - margin;
    const ridge = @max(ridge_min_abs(program, 216), ridge_min_abs(program, 282)) * scale;
    const sum = thickness + ridge;
    const rounding = @max(@max(@abs(thickness), @abs(ridge)), @abs(floor)) * 0x1p-40 + std.math.floatMin(f64);
    return sum - rounding > floor;
}

pub fn terrain_positive_saturated(program: []const u8) bool {
    @setFloatMode(.strict);
    const scale = density_read.density_program_value(program, 66);
    if (!(density_read.density_program_value(program, 1) * scale > 1.0)) return false;
    const a: core_vectors.Vec4 = @bitCast(program[1..][0..32].*);
    const b: core_vectors.Vec4 = @bitCast(program[33..][0..32].*);
    const magnitude = @reduce(.Max, @max(@abs(a), @abs(b)));
    const margin = magnitude * 0x1p-40 + std.math.floatMin(f64);
    const lower = if (scale >= 0.0) (@reduce(.Min, @min(a, b)) - margin) * scale else (@reduce(.Max, @max(a, b)) + margin) * scale;
    return lower > 1.0;
}

pub fn terrain_squeezed_upper_bound(program: []const u8) f64 {
    @setFloatMode(.strict);
    const a: core_vectors.Vec4 = @bitCast(program[1..][0..32].*);
    const b: core_vectors.Vec4 = @bitCast(program[33..][0..32].*);
    const magnitude = @reduce(.Max, @max(@abs(a), @abs(b)));
    const margin = magnitude * 0x1p-40 + std.math.floatMin(f64);
    const scale = density_read.density_program_value(program, 66);
    const bound = if (scale >= 0.0) (@reduce(.Max, @max(a, b)) + margin) * scale else (@reduce(.Min, @min(a, b)) - margin) * scale;
    const upper = bound + @abs(bound) * 0x1p-40 + std.math.floatMin(f64);
    const clamped = density_math.density_clamp(upper, -1.0, 1.0);
    const squeezed = clamped / 2.0 - clamped * clamped * clamped / 24.0;
    return squeezed + @abs(squeezed) * 0x1p-40 + std.math.floatMin(f64);
}

pub fn terrain_ridge_dominant(program: []const u8) u8 {
    @setFloatMode(.strict);
    const first0 = @abs(density_read.density_program_value(program, 216));
    const second0 = @abs(density_read.density_program_value(program, 282));
    if (first0 <= second0 * 1.5 and second0 <= first0 * 1.5) return 0;
    const first_a: core_vectors.Vec4 = @bitCast(program[216..][0..32].*);
    const first_b: core_vectors.Vec4 = @bitCast(program[248..][0..32].*);
    const second_a: core_vectors.Vec4 = @bitCast(program[282..][0..32].*);
    const second_b: core_vectors.Vec4 = @bitCast(program[314..][0..32].*);
    const aa = @abs(first_a);
    const ab = @abs(first_b);
    const ba = @abs(second_a);
    const bb = @abs(second_b);
    const first_max = @reduce(.Max, @max(aa, ab));
    const second_max = @reduce(.Max, @max(ba, bb));
    const first_min = @reduce(.Min, @min(aa, ab));
    const second_min = @reduce(.Min, @min(ba, bb));
    const margin = @max(first_max, second_max) * 0x1p-40 + std.math.floatMin(f64);
    const zero: core_vectors.Vec4 = @splat(0.0);
    const first_same_sign = (@reduce(.And, first_a >= zero) and @reduce(.And, first_b >= zero)) or (@reduce(.And, first_a <= zero) and @reduce(.And, first_b <= zero));
    if (first_same_sign and first_min - margin > second_max + margin) return 1;
    const second_same_sign = (@reduce(.And, second_a >= zero) and @reduce(.And, second_b >= zero)) or (@reduce(.And, second_a <= zero) and @reduce(.And, second_b <= zero));
    if (second_same_sign and second_min - margin > first_max + margin) return 2;
    return 0;
}
