const std = @import("std");
const backend_options = @import("../backend/options.zig");
const core_math = @import("../core/math.zig");
const core_vectors = @import("../core/vectors.zig");
const noise_sampling = @import("sampling.zig");

pub const GradientCell = struct {
    values: [8]i32,

    pub fn init(permutation: [*]const u8, x_hash: i32, next_x_hash: i32, y: i32, z: i32) GradientCell {
        const xy_hash = core_math.permutation_value(permutation, x_hash + y);
        const xy_next_hash = core_math.permutation_value(permutation, x_hash + y + 1);
        const next_xy_hash = core_math.permutation_value(permutation, next_x_hash + y);
        const next_xy_next_hash = core_math.permutation_value(permutation, next_x_hash + y + 1);
        return .{ .values = .{
            core_math.permutation_value(permutation, xy_hash + z),
            core_math.permutation_value(permutation, next_xy_hash + z),
            core_math.permutation_value(permutation, xy_next_hash + z),
            core_math.permutation_value(permutation, next_xy_next_hash + z),
            core_math.permutation_value(permutation, xy_hash + z + 1),
            core_math.permutation_value(permutation, next_xy_hash + z + 1),
            core_math.permutation_value(permutation, xy_next_hash + z + 1),
            core_math.permutation_value(permutation, next_xy_next_hash + z + 1),
        } };
    }

    pub fn init_cached(permutation: [*]const u8, xy: i32, xy_next: i32, next_xy: i32, next_xy_next: i32, z: i32) GradientCell {
        return .{ .values = .{
            core_math.permutation_value(permutation, xy + z),          core_math.permutation_value(permutation, next_xy + z),
            core_math.permutation_value(permutation, xy_next + z),     core_math.permutation_value(permutation, next_xy_next + z),
            core_math.permutation_value(permutation, xy + z + 1),      core_math.permutation_value(permutation, next_xy + z + 1),
            core_math.permutation_value(permutation, xy_next + z + 1), core_math.permutation_value(permutation, next_xy_next + z + 1),
        } };
    }

    pub fn sample_packed(self: GradientCell, x: f64, y: f64, z: f64, sx: f64, sy: f64, sz: f64) f64 {
        @setFloatMode(.strict);
        const hashes: core_vectors.Vec8i = @bitCast(self.values);
        const xx: core_vectors.Vec8d = .{ x, x - 1.0, x, x - 1.0, x, x - 1.0, x, x - 1.0 };
        const yy: core_vectors.Vec8d = .{ y, y, y - 1.0, y - 1.0, y, y, y - 1.0, y - 1.0 };
        const zz: core_vectors.Vec8d = .{ z, z, z, z, z - 1.0, z - 1.0, z - 1.0, z - 1.0 };
        const dots = noise_sampling.gradient_vector(8, hashes, xx, yy, zz);
        const x0 = @shuffle(f64, dots, undefined, @as(@Vector(4, i32), .{ 0, 2, 4, 6 }));
        const x1 = @shuffle(f64, dots, undefined, @as(@Vector(4, i32), .{ 1, 3, 5, 7 }));
        const vx = x0 + @as(core_vectors.Vec4, @splat(sx)) * (x1 - x0);
        const y0 = @shuffle(f64, vx, undefined, @as(@Vector(2, i32), .{ 0, 2 }));
        const y1 = @shuffle(f64, vx, undefined, @as(@Vector(2, i32), .{ 1, 3 }));
        const vy = y0 + @as(core_vectors.Vec2, @splat(sy)) * (y1 - y0);
        return vy[0] + sz * (vy[1] - vy[0]);
    }

    pub fn sample(self: GradientCell, fraction_x: f64, fraction_y: f64, fraction_z: f64) f64 {
        const value000 = core_math.gradient(self.values[0], fraction_x, fraction_y, fraction_z);
        const value100 = core_math.gradient(self.values[1], fraction_x - 1.0, fraction_y, fraction_z);
        const value010 = core_math.gradient(self.values[2], fraction_x, fraction_y - 1.0, fraction_z);
        const value110 = core_math.gradient(self.values[3], fraction_x - 1.0, fraction_y - 1.0, fraction_z);
        const value001 = core_math.gradient(self.values[4], fraction_x, fraction_y, fraction_z - 1.0);
        const value101 = core_math.gradient(self.values[5], fraction_x - 1.0, fraction_y, fraction_z - 1.0);
        const value011 = core_math.gradient(self.values[6], fraction_x, fraction_y - 1.0, fraction_z - 1.0);
        const value111 = core_math.gradient(self.values[7], fraction_x - 1.0, fraction_y - 1.0, fraction_z - 1.0);
        return core_math.lerp3(core_math.smoothstep(fraction_x), core_math.smoothstep(fraction_y), core_math.smoothstep(fraction_z), value000, value100, value010, value110, value001, value101, value011, value111);
    }

    pub fn sample_smoothed(self: GradientCell, fraction_x: f64, fraction_y: f64, fraction_z: f64, smooth_x: f64, smooth_y: f64, smooth_z: f64) f64 {
        const value000 = core_math.gradient(self.values[0], fraction_x, fraction_y, fraction_z);
        const value100 = core_math.gradient(self.values[1], fraction_x - 1.0, fraction_y, fraction_z);
        const value010 = core_math.gradient(self.values[2], fraction_x, fraction_y - 1.0, fraction_z);
        const value110 = core_math.gradient(self.values[3], fraction_x - 1.0, fraction_y - 1.0, fraction_z);
        const value001 = core_math.gradient(self.values[4], fraction_x, fraction_y, fraction_z - 1.0);
        const value101 = core_math.gradient(self.values[5], fraction_x - 1.0, fraction_y, fraction_z - 1.0);
        const value011 = core_math.gradient(self.values[6], fraction_x, fraction_y - 1.0, fraction_z - 1.0);
        const value111 = core_math.gradient(self.values[7], fraction_x - 1.0, fraction_y - 1.0, fraction_z - 1.0);
        return core_math.lerp3(smooth_x, smooth_y, smooth_z, value000, value100, value010, value110, value001, value101, value011, value111);
    }

    pub inline fn line_paired(self: GradientCell, fraction_x: f64, fraction_z: f64) GradientLine {
        const pair00 = gradient_terms_pair(self.values[0], self.values[2], fraction_x, fraction_z);
        const pair10 = gradient_terms_pair(self.values[1], self.values[3], fraction_x - 1.0, fraction_z);
        const pair01 = gradient_terms_pair(self.values[4], self.values[6], fraction_x, fraction_z - 1.0);
        const pair11 = gradient_terms_pair(self.values[5], self.values[7], fraction_x - 1.0, fraction_z - 1.0);
        return .{ .values = .{
            pair00[0], pair10[0], pair00[1], pair10[1],
            pair01[0], pair11[0], pair01[1], pair11[1],
        } };
    }

    pub fn line_z4(self: GradientCell, fraction_x: f64, fraction_z: core_vectors.Vec4) GradientLineZ4 {
        const fx0: core_vectors.Vec4 = @splat(fraction_x);
        const fx1: core_vectors.Vec4 = @splat(fraction_x - 1.0);
        const fz1 = fraction_z - @as(core_vectors.Vec4, @splat(1.0));
        const terms = [_]GradientTermZ4{
            gradient_term_z4(self.values[0], fx0, fraction_z),
            gradient_term_z4(self.values[1], fx1, fraction_z),
            gradient_term_z4(self.values[2], fx0, fraction_z),
            gradient_term_z4(self.values[3], fx1, fraction_z),
            gradient_term_z4(self.values[4], fx0, fz1),
            gradient_term_z4(self.values[5], fx1, fz1),
            gradient_term_z4(self.values[6], fx0, fz1),
            gradient_term_z4(self.values[7], fx1, fz1),
        };
        var result: GradientLineZ4 = .{ .bases = undefined, .signs = undefined, .none = undefined };
        inline for (terms, 0..) |term, index| {
            result.bases[index] = term.base;
            result.signs[index] = @splat(@as(u64, @intFromBool(term.mode == .subtract)) << 63);
            result.none[index] = @splat(term.mode == .none);
        }
        return result;
    }

    pub inline fn line(self: GradientCell, fraction_x: f64, fraction_z: f64) GradientLine {
        return .{ .values = .{
            gradient_term(self.values[0], fraction_x, fraction_z, 0.0),
            gradient_term(self.values[1], fraction_x - 1.0, fraction_z, 0.0),
            gradient_term(self.values[2], fraction_x, fraction_z, -1.0),
            gradient_term(self.values[3], fraction_x - 1.0, fraction_z, -1.0),
            gradient_term(self.values[4], fraction_x, fraction_z - 1.0, 0.0),
            gradient_term(self.values[5], fraction_x - 1.0, fraction_z - 1.0, 0.0),
            gradient_term(self.values[6], fraction_x, fraction_z - 1.0, -1.0),
            gradient_term(self.values[7], fraction_x - 1.0, fraction_z - 1.0, -1.0),
        } };
    }
};

pub const GradientTerm = struct {
    base: f64,
    mode: enum { none, add, subtract },

    pub fn sample_shifted(self: GradientTerm, shifted: f64) f64 {
        const base: f64 = self.base;
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    pub fn sample_shifted2(self: GradientTerm, shifted: core_vectors.Vec2) core_vectors.Vec2 {
        const base: core_vectors.Vec2 = @as(core_vectors.Vec2, @splat(self.base));
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    pub fn sample_shifted16(self: GradientTerm, shifted: core_vectors.Vec16d) core_vectors.Vec16d {
        const base: core_vectors.Vec16d = @splat(self.base);
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    pub fn sample_shifted8(self: GradientTerm, shifted: core_vectors.Vec8d) core_vectors.Vec8d {
        const base: core_vectors.Vec8d = @splat(self.base);
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    pub fn sample_shifted4(self: GradientTerm, shifted: core_vectors.Vec4) core_vectors.Vec4 {
        const base: core_vectors.Vec4 = @as(core_vectors.Vec4, @splat(self.base));
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }

    pub inline fn sample_grid(self: GradientTerm, comptime lanes: usize, shifted: @Vector(lanes, f64)) @Vector(lanes, f64) {
        return self.sample_grid_impl(backend_options.is_simd_backend, lanes, shifted);
    }
    pub inline fn sample_grid_impl(self: GradientTerm, comptime use_avx2: bool, comptime lanes: usize, shifted: @Vector(lanes, f64)) @Vector(lanes, f64) {
        @setFloatMode(.strict);
        const V = @Vector(lanes, f64);
        const base: V = @splat(self.base);
        if (comptime !use_avx2) {
            return switch (self.mode) {
                .none => base,
                .add => base + shifted,
                .subtract => base - shifted,
            };
        }
        const Bits = @Vector(lanes, u64);
        const sign: Bits = @splat(@as(u64, @intFromBool(self.mode == .subtract)) << 63);
        const adjusted: V = @bitCast(@as(Bits, @bitCast(shifted)) ^ sign);

        return @select(f64, @as(@Vector(lanes, bool), @splat(self.mode == .none)), base, base + adjusted);
    }
};

test "grid gradient vectors match scalar operations" {
    @setFloatMode(.strict);
    const inputs = [_]f64{ 0.0, -0.0, 0.5, -0.5, 1.0, -1.0, @bitCast(@as(u64, 1)), @bitCast(@as(u64, 0x8000000000000001)), 1234.5, -1234.5 };
    for (inputs) |base| {
        for (0..3) |mode| {
            const term: GradientTerm = .{ .base = base, .mode = @enumFromInt(mode) };
            for (inputs, 0..) |_, offset| {
                inline for (.{ 2, 4, 8 }) |lanes| {
                    var values: @Vector(lanes, f64) = undefined;
                    inline for (0..lanes) |lane| values[lane] = inputs[(offset + lane) % inputs.len];
                    const actual = term.sample_grid_impl(true, lanes, values);
                    inline for (0..lanes) |lane| {
                        const expected = term.sample_shifted(values[lane]);
                        try std.testing.expectEqual(@as(u64, @bitCast(expected)), @as(u64, @bitCast(actual[lane])));
                    }
                }
            }
        }
    }
}

pub fn gradient_terms_pair(first: i32, second: i32, x: f64, z: f64) [2]GradientTerm {
    const descriptors = [16]u8{ 8, 9, 16, 17, 2, 3, 4, 5, 14, 22, 15, 23, 8, 22, 9, 23 };
    const bases = [8]f64{ x, -x, x + z, -x + z, x - z, -x - z, z, -z };
    const first_desc = descriptors[@as(usize, @intCast(first & 15))];
    const second_desc = descriptors[@as(usize, @intCast(second & 15))];
    return .{
        .{ .base = bases[first_desc & 7], .mode = @enumFromInt(first_desc >> 3) },
        .{ .base = bases[second_desc & 7], .mode = @enumFromInt(second_desc >> 3) },
    };
}

pub const GradientLine = struct {
    values: [8]GradientTerm,

    pub fn sample_smoothed(self: GradientLine, fraction_y: f64, smooth_x: f64, smooth_y: f64, smooth_z: f64) f64 {
        const y0 = fraction_y + 0.0;
        const y1 = fraction_y + -1.0;
        return core_math.lerp3(smooth_x, smooth_y, smooth_z, self.values[0].sample_shifted(y0), self.values[1].sample_shifted(y0), self.values[2].sample_shifted(y1), self.values[3].sample_shifted(y1), self.values[4].sample_shifted(y0), self.values[5].sample_shifted(y0), self.values[6].sample_shifted(y1), self.values[7].sample_shifted(y1));
    }

    pub fn sample_smoothed2(self: GradientLine, fraction_y: core_vectors.Vec2, smooth_x: f64, smooth_y: core_vectors.Vec2, smooth_z: f64) core_vectors.Vec2 {
        const y0 = fraction_y + @as(core_vectors.Vec2, @splat(0.0));
        const y1 = fraction_y + @as(core_vectors.Vec2, @splat(-1.0));
        return core_math.lerp3_2(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted2(y0), self.values[1].sample_shifted2(y0), self.values[2].sample_shifted2(y1), self.values[3].sample_shifted2(y1), self.values[4].sample_shifted2(y0), self.values[5].sample_shifted2(y0), self.values[6].sample_shifted2(y1), self.values[7].sample_shifted2(y1));
    }

    pub fn sample_smoothed4(self: GradientLine, fraction_y: core_vectors.Vec4, smooth_x: f64, smooth_y: core_vectors.Vec4, smooth_z: f64) core_vectors.Vec4 {
        const y0 = fraction_y + @as(core_vectors.Vec4, @splat(0.0));
        const y1 = fraction_y + @as(core_vectors.Vec4, @splat(-1.0));
        return core_math.lerp3_4(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted4(y0), self.values[1].sample_shifted4(y0), self.values[2].sample_shifted4(y1), self.values[3].sample_shifted4(y1), self.values[4].sample_shifted4(y0), self.values[5].sample_shifted4(y0), self.values[6].sample_shifted4(y1), self.values[7].sample_shifted4(y1));
    }
    pub fn sample_smoothed16(self: GradientLine, fraction_y: core_vectors.Vec16d, smooth_x: f64, smooth_y: core_vectors.Vec16d, smooth_z: f64) core_vectors.Vec16d {
        const y0 = fraction_y + @as(core_vectors.Vec16d, @splat(0.0));
        const y1 = fraction_y + @as(core_vectors.Vec16d, @splat(-1.0));
        return core_math.lerp3_16(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted16(y0), self.values[1].sample_shifted16(y0), self.values[2].sample_shifted16(y1), self.values[3].sample_shifted16(y1), self.values[4].sample_shifted16(y0), self.values[5].sample_shifted16(y0), self.values[6].sample_shifted16(y1), self.values[7].sample_shifted16(y1));
    }
    pub fn sample_smoothed8(self: GradientLine, fraction_y: core_vectors.Vec8d, smooth_x: f64, smooth_y: core_vectors.Vec8d, smooth_z: f64) core_vectors.Vec8d {
        const y0 = fraction_y + @as(core_vectors.Vec8d, @splat(0.0));
        const y1 = fraction_y + @as(core_vectors.Vec8d, @splat(-1.0));
        return core_math.lerp3_8(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted8(y0), self.values[1].sample_shifted8(y0), self.values[2].sample_shifted8(y1), self.values[3].sample_shifted8(y1), self.values[4].sample_shifted8(y0), self.values[5].sample_shifted8(y0), self.values[6].sample_shifted8(y1), self.values[7].sample_shifted8(y1));
    }
    pub fn sample_smoothed_shifted(self: GradientLine, fraction_y: f64, smooth_x: f64, smooth_y: f64, smooth_z: f64) f64 {
        const y0 = fraction_y + 0.0;
        const y1 = fraction_y + -1.0;
        return core_math.lerp3(smooth_x, smooth_y, smooth_z, self.values[0].sample_shifted(y0), self.values[1].sample_shifted(y0), self.values[2].sample_shifted(y1), self.values[3].sample_shifted(y1), self.values[4].sample_shifted(y0), self.values[5].sample_shifted(y0), self.values[6].sample_shifted(y1), self.values[7].sample_shifted(y1));
    }

    pub fn sample_smoothed_shifted2(self: GradientLine, fraction_y: core_vectors.Vec2, smooth_x: f64, smooth_y: core_vectors.Vec2, smooth_z: f64) core_vectors.Vec2 {
        const y0 = fraction_y + @as(core_vectors.Vec2, @splat(0.0));
        const y1 = fraction_y + @as(core_vectors.Vec2, @splat(-1.0));
        return core_math.lerp3_2(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted2(y0), self.values[1].sample_shifted2(y0), self.values[2].sample_shifted2(y1), self.values[3].sample_shifted2(y1), self.values[4].sample_shifted2(y0), self.values[5].sample_shifted2(y0), self.values[6].sample_shifted2(y1), self.values[7].sample_shifted2(y1));
    }

    pub fn sample_smoothed_shifted4(self: GradientLine, fraction_y: core_vectors.Vec4, smooth_x: f64, smooth_y: core_vectors.Vec4, smooth_z: f64) core_vectors.Vec4 {
        const y0 = fraction_y + @as(core_vectors.Vec4, @splat(0.0));
        const y1 = fraction_y + @as(core_vectors.Vec4, @splat(-1.0));
        return core_math.lerp3_4(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted4(y0), self.values[1].sample_shifted4(y0), self.values[2].sample_shifted4(y1), self.values[3].sample_shifted4(y1), self.values[4].sample_shifted4(y0), self.values[5].sample_shifted4(y0), self.values[6].sample_shifted4(y1), self.values[7].sample_shifted4(y1));
    }
    pub inline fn sample_grid(self: GradientLine, comptime lanes: usize, fraction_y: @Vector(lanes, f64), smooth_x: f64, smooth_y: @Vector(lanes, f64), smooth_z: f64) @Vector(lanes, f64) {
        @setFloatMode(.strict);
        const V = @Vector(lanes, f64);
        const y0 = fraction_y + @as(V, @splat(0.0));
        const y1 = fraction_y + @as(V, @splat(-1.0));
        const values = .{
            self.values[0].sample_grid(lanes, y0), self.values[1].sample_grid(lanes, y0),
            self.values[2].sample_grid(lanes, y1), self.values[3].sample_grid(lanes, y1),
            self.values[4].sample_grid(lanes, y0), self.values[5].sample_grid(lanes, y0),
            self.values[6].sample_grid(lanes, y1), self.values[7].sample_grid(lanes, y1),
        };
        return if (lanes == 8)
            core_math.lerp3_8(@splat(smooth_x), smooth_y, @splat(smooth_z), values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7])
        else if (lanes == 4)
            core_math.lerp3_4(@splat(smooth_x), smooth_y, @splat(smooth_z), values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7])
        else
            core_math.lerp3_2(@splat(smooth_x), smooth_y, @splat(smooth_z), values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]);
    }
};

pub const GradientTermZ4 = struct {
    base: core_vectors.Vec4,
    mode: enum { none, add, subtract },

    pub fn sample(self: GradientTermZ4, shifted: core_vectors.Vec4) core_vectors.Vec4 {
        return switch (self.mode) {
            .none => self.base,
            .add => self.base + shifted,
            .subtract => self.base - shifted,
        };
    }
};

pub const GradientLineZ4 = struct {
    bases: [8]core_vectors.Vec4,
    signs: [8]@Vector(4, u64),
    none: [8]@Vector(4, bool),

    pub inline fn term(self: GradientLineZ4, comptime index: usize, shifted: core_vectors.Vec4) core_vectors.Vec4 {
        const adjusted: core_vectors.Vec4 = @bitCast(@as(@Vector(4, u64), @bitCast(shifted)) ^ self.signs[index]);
        return @select(f64, self.none[index], self.bases[index], self.bases[index] + adjusted);
    }

    pub fn sample(self: GradientLineZ4, y: f64, sx: f64, sy: f64, sz: core_vectors.Vec4) core_vectors.Vec4 {
        const y0: core_vectors.Vec4 = @splat(y + 0.0);
        const y1: core_vectors.Vec4 = @splat(y + -1.0);
        return core_math.lerp3_4(@splat(sx), @splat(sy), sz, self.term(0, y0), self.term(1, y0), self.term(2, y1), self.term(3, y1), self.term(4, y0), self.term(5, y0), self.term(6, y1), self.term(7, y1));
    }
};

pub fn gradient_term_z4(value: i32, x: core_vectors.Vec4, z: core_vectors.Vec4) GradientTermZ4 {
    @setFloatMode(.strict);
    const descriptors = [16]u8{ 8, 9, 16, 17, 2, 3, 4, 5, 14, 22, 15, 23, 8, 22, 9, 23 };
    const descriptor = descriptors[@as(usize, @intCast(value & 15))];
    const index: u3 = @truncate(descriptor);
    const Bits = @Vector(4, u64);
    const sign_x: Bits = @splat(@as(u64, index & 1) << 63);
    const negative_z: u8 = 0xb0;
    const sign_z: Bits = @splat(@as(u64, (negative_z >> index) & 1) << 63);
    const xb = @as(Bits, @bitCast(x)) ^ sign_x;
    const zb = @as(Bits, @bitCast(z)) ^ sign_z;
    const sum: Bits = @bitCast(@as(core_vectors.Vec4, @bitCast(xb)) + @as(core_vectors.Vec4, @bitCast(zb)));
    const only_x: Bits = @splat(@as(u64, 0) -% @as(u64, @intFromBool(index < 2)));
    const only_z: Bits = @splat(@as(u64, 0) -% @as(u64, @intFromBool(index >= 6)));
    return .{ .base = @bitCast((xb & only_x) | (zb & only_z) | (sum & ~(only_x | only_z))), .mode = @enumFromInt(descriptor >> 3) };
}

pub fn gradient_term(value: i32, x: f64, z: f64, y_offset: f64) GradientTerm {
    _ = y_offset;
    return switch (value & 15) {
        0 => .{ .base = x, .mode = .add },
        1 => .{ .base = -x, .mode = .add },
        2 => .{ .base = x, .mode = .subtract },
        3 => .{ .base = -x, .mode = .subtract },
        4 => .{ .base = x + z, .mode = .none },
        5 => .{ .base = -x + z, .mode = .none },
        6 => .{ .base = x - z, .mode = .none },
        7 => .{ .base = -x - z, .mode = .none },
        8 => .{ .base = z, .mode = .add },
        9 => .{ .base = z, .mode = .subtract },
        10 => .{ .base = -z, .mode = .add },
        11 => .{ .base = -z, .mode = .subtract },
        12 => .{ .base = x, .mode = .add },
        13 => .{ .base = z, .mode = .subtract },
        14 => .{ .base = -x, .mode = .add },
        else => .{ .base = -z, .mode = .subtract },
    };
}
