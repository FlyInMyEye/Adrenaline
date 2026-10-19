pub const adrenaline_avx2 = true;
pub const adrenaline_small = true;

const native = @import("adrenaline_native.zig");

pub export fn adrenaline_normal_noise_batch_small_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, count: usize, values: [*]f64) callconv(.c) void {
    native.normal_noise_batch_avx2(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..count]);
}

pub export fn adrenaline_preliminary_height_avx2(program: [*]const u8, program_length: usize, inputs: [*]const f64, input_length: usize, slots: usize, x: c_int, z: c_int, min_y: c_int, max_y: c_int, step: c_int) callconv(.c) c_int {
    return native.preliminary_surface_level(program[0..program_length], inputs[0..input_length], slots, x, z, min_y, max_y, step);
}
