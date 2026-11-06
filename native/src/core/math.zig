const core_vectors = @import("vectors.zig");

pub fn read_f64(data: [*]const u8, offset: usize) f64 {
    const value: *align(1) const f64 = @ptrCast(data + offset);
    return value.*;
}

pub fn wrap(value: f64) f64 {
    return value - @floor(value / 33554432.0 + 0.5) * 33554432.0;
}

pub fn floor_int(value: f64) i32 {
    return @intFromFloat(@floor(value));
}

pub fn permutation_value(permutation: [*]const u8, index: i32) i32 {
    return permutation[@as(usize, @intCast(index & 255))];
}

pub fn gradient(value: i32, x: f64, y: f64, z: f64) f64 {
    return switch (value & 15) {
        0 => x + y,
        1 => -x + y,
        2 => x - y,
        3 => -x - y,
        4 => x + z,
        5 => -x + z,
        6 => x - z,
        7 => -x - z,
        8 => y + z,
        9 => -y + z,
        10 => y - z,
        11 => -y - z,
        12 => x + y,
        13 => -y + z,
        14 => -x + y,
        else => -y - z,
    };
}

pub fn gradient2(first: i32, second: i32, x: f64, y: core_vectors.Vec2, z: f64) core_vectors.Vec2 {
    return .{ gradient(first, x, y[0], z), gradient(second, x, y[1], z) };
}

pub fn smoothstep(value: f64) f64 {
    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
}

pub fn smoothstep2(value: core_vectors.Vec2) core_vectors.Vec2 {
    return value * value * value * (value * (value * @as(core_vectors.Vec2, @splat(6.0)) - @as(core_vectors.Vec2, @splat(15.0))) + @as(core_vectors.Vec2, @splat(10.0)));
}

pub fn lerp(delta: f64, start: f64, end: f64) f64 {
    return start + delta * (end - start);
}

pub fn lerp2(x: f64, y: f64, value00: f64, value10: f64, value01: f64, value11: f64) f64 {
    return lerp(y, lerp(x, value00, value10), lerp(x, value01, value11));
}

pub fn lerp3(x: f64, y: f64, z: f64, value000: f64, value100: f64, value010: f64, value110: f64, value001: f64, value101: f64, value011: f64, value111: f64) f64 {
    return lerp(z, lerp2(x, y, value000, value100, value010, value110), lerp2(x, y, value001, value101, value011, value111));
}

pub fn lerp2_2(x: core_vectors.Vec2, y: core_vectors.Vec2, value00: core_vectors.Vec2, value10: core_vectors.Vec2, value01: core_vectors.Vec2, value11: core_vectors.Vec2) core_vectors.Vec2 {
    return lerp_2(y, lerp_2(x, value00, value10), lerp_2(x, value01, value11));
}

pub fn lerp3_2(x: core_vectors.Vec2, y: core_vectors.Vec2, z: core_vectors.Vec2, value000: core_vectors.Vec2, value100: core_vectors.Vec2, value010: core_vectors.Vec2, value110: core_vectors.Vec2, value001: core_vectors.Vec2, value101: core_vectors.Vec2, value011: core_vectors.Vec2, value111: core_vectors.Vec2) core_vectors.Vec2 {
    return lerp_2(z, lerp2_2(x, y, value000, value100, value010, value110), lerp2_2(x, y, value001, value101, value011, value111));
}

pub fn lerp2_4(x: core_vectors.Vec4, y: core_vectors.Vec4, value00: core_vectors.Vec4, value10: core_vectors.Vec4, value01: core_vectors.Vec4, value11: core_vectors.Vec4) core_vectors.Vec4 {
    return lerp_4(y, lerp_4(x, value00, value10), lerp_4(x, value01, value11));
}

pub fn lerp3_4(x: core_vectors.Vec4, y: core_vectors.Vec4, z: core_vectors.Vec4, value000: core_vectors.Vec4, value100: core_vectors.Vec4, value010: core_vectors.Vec4, value110: core_vectors.Vec4, value001: core_vectors.Vec4, value101: core_vectors.Vec4, value011: core_vectors.Vec4, value111: core_vectors.Vec4) core_vectors.Vec4 {
    return lerp_4(z, lerp2_4(x, y, value000, value100, value010, value110), lerp2_4(x, y, value001, value101, value011, value111));
}

pub fn lerp_2(delta: core_vectors.Vec2, start: core_vectors.Vec2, end: core_vectors.Vec2) core_vectors.Vec2 {
    return start + delta * (end - start);
}

pub fn lerp_4(delta: core_vectors.Vec4, start: core_vectors.Vec4, end: core_vectors.Vec4) core_vectors.Vec4 {
    return start + delta * (end - start);
}

pub fn lerp_8(delta: core_vectors.Vec8d, start: core_vectors.Vec8d, end: core_vectors.Vec8d) core_vectors.Vec8d {
    return start + delta * (end - start);
}

pub fn lerp2_8(x: core_vectors.Vec8d, y: core_vectors.Vec8d, value00: core_vectors.Vec8d, value10: core_vectors.Vec8d, value01: core_vectors.Vec8d, value11: core_vectors.Vec8d) core_vectors.Vec8d {
    return lerp_8(y, lerp_8(x, value00, value10), lerp_8(x, value01, value11));
}

pub fn lerp3_8(x: core_vectors.Vec8d, y: core_vectors.Vec8d, z: core_vectors.Vec8d, value000: core_vectors.Vec8d, value100: core_vectors.Vec8d, value010: core_vectors.Vec8d, value110: core_vectors.Vec8d, value001: core_vectors.Vec8d, value101: core_vectors.Vec8d, value011: core_vectors.Vec8d, value111: core_vectors.Vec8d) core_vectors.Vec8d {
    return lerp_8(z, lerp2_8(x, y, value000, value100, value010, value110), lerp2_8(x, y, value001, value101, value011, value111));
}

pub fn lerp_16(delta: core_vectors.Vec16d, start: core_vectors.Vec16d, end: core_vectors.Vec16d) core_vectors.Vec16d {
    return start + delta * (end - start);
}

pub fn lerp2_16(x: core_vectors.Vec16d, y: core_vectors.Vec16d, value00: core_vectors.Vec16d, value10: core_vectors.Vec16d, value01: core_vectors.Vec16d, value11: core_vectors.Vec16d) core_vectors.Vec16d {
    return lerp_16(y, lerp_16(x, value00, value10), lerp_16(x, value01, value11));
}

pub fn lerp3_16(x: core_vectors.Vec16d, y: core_vectors.Vec16d, z: core_vectors.Vec16d, value000: core_vectors.Vec16d, value100: core_vectors.Vec16d, value010: core_vectors.Vec16d, value110: core_vectors.Vec16d, value001: core_vectors.Vec16d, value101: core_vectors.Vec16d, value011: core_vectors.Vec16d, value111: core_vectors.Vec16d) core_vectors.Vec16d {
    return lerp_16(z, lerp2_16(x, y, value000, value100, value010, value110), lerp2_16(x, y, value001, value101, value011, value111));
}
