const std = @import("std");
const core_vectors = @import("../core/vectors.zig");

pub fn density_min4_finite(left: core_vectors.Vec4, right: core_vectors.Vec4) core_vectors.Vec4 {
    const zero: core_vectors.Vec4 = @splat(0.0);
    const signed_zero: core_vectors.Vec4 = @bitCast(@as(@Vector(4, u64), @bitCast(left)) | @as(@Vector(4, u64), @bitCast(right)));
    return @select(f64, (left == zero) & (right == zero), signed_zero, @min(left, right));
}

pub fn density_clamp(value: f64, minimum: f64, maximum: f64) f64 {
    if (value < minimum) return minimum;
    if (std.math.isNan(value)) return value;
    if (std.math.isNan(maximum)) return maximum;
    if (value == 0.0 and maximum == 0.0) return @bitCast(@as(u64, @bitCast(value)) | @as(u64, @bitCast(maximum)));
    return @min(value, maximum);
}

pub fn density_lerp3(x: f64, y: f64, z: f64, noise000: f64, noise100: f64, noise010: f64, noise110: f64, noise001: f64, noise101: f64, noise011: f64, noise111: f64) f64 {
    const value_xz00 = noise000 + y * (noise010 - noise000);
    const value_xz10 = noise100 + y * (noise110 - noise100);
    const value_xz01 = noise001 + y * (noise011 - noise001);
    const value_xz11 = noise101 + y * (noise111 - noise101);
    const value_z0 = value_xz00 + x * (value_xz10 - value_xz00);
    const value_z1 = value_xz01 + x * (value_xz11 - value_xz01);
    return value_z0 + z * (value_z1 - value_z0);
}
