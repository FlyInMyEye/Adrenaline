pub const adrenaline_avx2 = true;
pub const adrenaline_small = true;

const native = @import("adrenaline_native.zig");

pub export fn adrenaline_normal_noise_batch_small_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, count: usize, values: [*]f64) callconv(.c) void {
    native.normal_noise_batch_avx2(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..count]);
}
