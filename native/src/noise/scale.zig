const core_vectors = @import("../core/vectors.zig");

pub fn scale_f64_values(comptime avx2: bool, values: []f64, factor: f64) void {
    @setFloatMode(.strict);
    var index: usize = 0;
    if (comptime avx2) {
        const factor4: core_vectors.Vec4 = @splat(factor);
        while (index + 3 < values.len) : (index += 4) {
            const current: core_vectors.Vec4 = @bitCast(values[index..][0..4].*);
            values[index..][0..4].* = @bitCast(current * factor4);
        }
    }
    const factor2: core_vectors.Vec2 = @splat(factor);
    while (index + 1 < values.len) : (index += 2) {
        const current: core_vectors.Vec2 = @bitCast(values[index..][0..2].*);
        values[index..][0..2].* = @bitCast(current * factor2);
    }
    while (index < values.len) : (index += 1) {
        values[index] *= factor;
    }
}

pub fn scale_f32_values(comptime avx2: bool, values: []f32, factor: f32) void {
    var index: usize = 0;
    if (comptime avx2) {
        const factor8: core_vectors.Vec8f = @splat(factor);
        while (index + 7 < values.len) : (index += 8) {
            const current: core_vectors.Vec8f = @bitCast(values[index..][0..8].*);
            values[index..][0..8].* = @bitCast(current * factor8);
        }
    }
    const factor4: core_vectors.Vec4f = @splat(factor);
    while (index + 3 < values.len) : (index += 4) {
        const current: core_vectors.Vec4f = @bitCast(values[index..][0..4].*);
        values[index..][0..4].* = @bitCast(current * factor4);
    }
    while (index < values.len) : (index += 1) {
        values[index] *= factor;
    }
}
