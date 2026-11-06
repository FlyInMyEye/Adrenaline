const std = @import("std");
const core_math = @import("../core/math.zig");

pub inline fn permutation_pair(pairs: []const u8, index: i32) u16 {
    const i: usize = @intCast(index & 255);
    return std.mem.readInt(u16, pairs[i..][0..2], .little);
}

pub fn blended_sample(comptime lanes: usize, comptime paired: bool, permutation: [*]const u8, pairs: []const u8, xy: @Vector(lanes, i32), xy_next: @Vector(lanes, i32), next_xy: @Vector(lanes, i32), next_xy_next: @Vector(lanes, i32), z: i32, fx: f64, fy: @Vector(lanes, f64), fz: f64, sx: f64, sy: @Vector(lanes, f64), sz: f64) @Vector(lanes, f64) {
    @setFloatMode(.strict);
    const V = @Vector(lanes, f64);
    var hashes: [8]@Vector(lanes, i64) = undefined;
    inline for (0..lanes) |lane| {
        if (comptime paired) {
            const words = [_]u16{
                permutation_pair(pairs[0..], (xy[lane] + z)),
                permutation_pair(pairs[0..], (next_xy[lane] + z)),
                permutation_pair(pairs[0..], (xy_next[lane] + z)),
                permutation_pair(pairs[0..], (next_xy_next[lane] + z)),
            };
            inline for (0..4) |corner| {
                hashes[corner][lane] = @intCast(words[corner] & 255);
                hashes[corner + 4][lane] = @intCast(words[corner] >> 8);
            }
        } else {
            hashes[0][lane] = core_math.permutation_value(permutation, xy[lane] + z);
            hashes[1][lane] = core_math.permutation_value(permutation, next_xy[lane] + z);
            hashes[2][lane] = core_math.permutation_value(permutation, xy_next[lane] + z);
            hashes[3][lane] = core_math.permutation_value(permutation, next_xy_next[lane] + z);
            hashes[4][lane] = core_math.permutation_value(permutation, xy[lane] + z + 1);
            hashes[5][lane] = core_math.permutation_value(permutation, next_xy[lane] + z + 1);
            hashes[6][lane] = core_math.permutation_value(permutation, xy_next[lane] + z + 1);
            hashes[7][lane] = core_math.permutation_value(permutation, next_xy_next[lane] + z + 1);
        }
    }
    const x0: V = @splat(fx);
    const x1: V = @splat(fx - 1.0);
    const y1 = fy - @as(V, @splat(1.0));
    const z0: V = @splat(fz);
    const z1: V = @splat(fz - 1.0);
    const v000 = blended_gradient(lanes, hashes[0], x0, fy, z0);
    const v100 = blended_gradient(lanes, hashes[1], x1, fy, z0);
    const v010 = blended_gradient(lanes, hashes[2], x0, y1, z0);
    const v110 = blended_gradient(lanes, hashes[3], x1, y1, z0);
    const v001 = blended_gradient(lanes, hashes[4], x0, fy, z1);
    const v101 = blended_gradient(lanes, hashes[5], x1, fy, z1);
    const v011 = blended_gradient(lanes, hashes[6], x0, y1, z1);
    const v111 = blended_gradient(lanes, hashes[7], x1, y1, z1);
    return if (lanes == 4) core_math.lerp3_4(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111) else core_math.lerp3_2(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111);
}

pub fn blended_gradient(comptime lanes: usize, hash: anytype, x: @Vector(lanes, f64), y: @Vector(lanes, f64), z: @Vector(lanes, f64)) @Vector(lanes, f64) {
    const I = @TypeOf(hash);
    const h = hash & @as(I, @splat(15));
    const u = @select(f64, h < @as(I, @splat(8)), x, y);
    const v = @select(f64, h < @as(I, @splat(4)), y, @select(f64, (h == @as(I, @splat(12))) | (h == @as(I, @splat(14))), x, z));
    const Bits = @Vector(lanes, u64);
    const Shift = @Vector(lanes, u6);
    const sign_u = @as(Bits, @intCast(h & @as(I, @splat(1)))) << @as(Shift, @splat(63));
    const sign_v = @as(Bits, @intCast(h & @as(I, @splat(2)))) << @as(Shift, @splat(62));
    const signed_u: @Vector(lanes, f64) = @bitCast(@as(Bits, @bitCast(u)) ^ sign_u);
    const signed_v: @Vector(lanes, f64) = @bitCast(@as(Bits, @bitCast(v)) ^ sign_v);
    return signed_u + signed_v;
}

test "blended gradients agree across hash widths and signed zeros" {
    @setFloatMode(.strict);
    const samples = [_]f64{ 0.0, -0.0, 0.25, -0.25, 1.0, -1.0, @bitCast(@as(u64, 1)), @bitCast(@as(u64, 0x8000000000000001)), 1234.5, -1234.5 };
    inline for (.{ 2, 4 }) |lanes| {
        for (0..16) |h| {
            for (samples, 0..) |_, i| {
                var xs: @Vector(lanes, f64) = undefined;
                var ys: @Vector(lanes, f64) = undefined;
                var zs: @Vector(lanes, f64) = undefined;
                var h32: @Vector(lanes, i32) = undefined;
                var h64: @Vector(lanes, i64) = undefined;
                inline for (0..lanes) |lane| {
                    xs[lane] = samples[(i + lane) % samples.len];
                    ys[lane] = samples[(i * 3 + lane + 1) % samples.len];
                    zs[lane] = samples[(i * 7 + lane + 2) % samples.len];
                    h32[lane] = @intCast((h + lane) & 15);
                    h64[lane] = h32[lane];
                }
                const old = blended_gradient(lanes, h32, xs, ys, zs);
                const new = blended_gradient(lanes, h64, xs, ys, zs);
                inline for (0..lanes) |lane| {
                    try std.testing.expectEqual(@as(u64, @bitCast(old[lane])), @as(u64, @bitCast(new[lane])));
                    try std.testing.expectEqual(@as(u64, @bitCast(core_math.gradient(h32[lane], xs[lane], ys[lane], zs[lane]))), @as(u64, @bitCast(new[lane])));
                }
            }
        }
    }
}

pub fn sample_distinct_y(comptime lanes: usize, comptime fast: bool, permutation: [*]const u8, x_hash: i32, next_x_hash: i32, y: @Vector(lanes, i32), z: i32, fx: f64, fy: @Vector(lanes, f64), fz: f64, sx: f64, sy: @Vector(lanes, f64), sz: f64) @Vector(lanes, f64) {
    @setFloatMode(.strict);
    const V = @Vector(lanes, f64);
    var hashes: [8]@Vector(lanes, i32) = undefined;
    inline for (0..lanes) |lane| {
        const xy = core_math.permutation_value(permutation, x_hash + y[lane]);
        const xy_next = core_math.permutation_value(permutation, x_hash + y[lane] + 1);
        const next_xy = core_math.permutation_value(permutation, next_x_hash + y[lane]);
        const next_xy_next = core_math.permutation_value(permutation, next_x_hash + y[lane] + 1);
        hashes[0][lane] = core_math.permutation_value(permutation, xy + z);
        hashes[1][lane] = core_math.permutation_value(permutation, next_xy + z);
        hashes[2][lane] = core_math.permutation_value(permutation, xy_next + z);
        hashes[3][lane] = core_math.permutation_value(permutation, next_xy_next + z);
        hashes[4][lane] = core_math.permutation_value(permutation, xy + z + 1);
        hashes[5][lane] = core_math.permutation_value(permutation, next_xy + z + 1);
        hashes[6][lane] = core_math.permutation_value(permutation, xy_next + z + 1);
        hashes[7][lane] = core_math.permutation_value(permutation, next_xy_next + z + 1);
    }
    const x0: V = @splat(fx);
    const x1: V = @splat(fx - 1.0);
    const y1 = fy - @as(V, @splat(1.0));
    const z0: V = @splat(fz);
    const z1: V = @splat(fz - 1.0);
    const v000 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[0], x0, fy, z0);
    const v100 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[1], x1, fy, z0);
    const v010 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[2], x0, y1, z0);
    const v110 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[3], x1, y1, z0);
    const v001 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[4], x0, fy, z1);
    const v101 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[5], x1, fy, z1);
    const v011 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[6], x0, y1, z1);
    const v111 = (if (fast) blended_gradient else gradient_vector)(lanes, hashes[7], x1, y1, z1);
    return if (lanes == 8) core_math.lerp3_8(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111) else if (lanes == 4) core_math.lerp3_4(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111) else core_math.lerp3_2(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111);
}

pub fn gradient_vector(comptime lanes: usize, hash: @Vector(lanes, i32), x: @Vector(lanes, f64), y: @Vector(lanes, f64), z: @Vector(lanes, f64)) @Vector(lanes, f64) {
    const I = @Vector(lanes, i32);
    const h = hash & @as(I, @splat(15));
    const u = @select(f64, h < @as(I, @splat(8)), x, y);
    const v = @select(f64, h < @as(I, @splat(4)), y, @select(f64, (h == @as(I, @splat(12))) | (h == @as(I, @splat(14))), x, z));
    return @select(f64, (h & @as(I, @splat(1))) == @as(I, @splat(0)), u, -u) + @select(f64, (h & @as(I, @splat(2))) == @as(I, @splat(0)), v, -v);
}
