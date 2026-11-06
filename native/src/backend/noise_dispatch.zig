const builtin = @import("builtin");
const backend_runtime = @import("runtime.zig");
const backend_abi = @import("abi.zig");
const noise_batch = @import("../noise/batch.zig");
const noise_normal = @import("../noise/normal.zig");
const noise_blended = @import("../noise/blended.zig");
const noise_approximate = @import("../noise/approximate.zig");
const noise_types = @import("../noise/types.zig");

pub inline fn normal_batch(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, length: usize, values: [*]f64) void {
    @setFloatMode(.strict);
    if (comptime builtin.cpu.arch == .x86_64) {
        if (backend_runtime.supports(.avx2)) {
            if (@abs(y_step) <= 0.125) {
                backend_abi.adrenaline_normal_noise_batch_small_avx2(first, first_count, second, second_count, value_factor, x, y, z, y_step, length, values);
            } else {
                backend_abi.adrenaline_normal_noise_batch_avx2(first, first_count, second, second_count, value_factor, x, y, z, y_step, length, values);
            }
        } else {
            noise_batch.normal_noise_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..length]);
        }
    } else {
        noise_batch.normal_noise_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..length]);
    }
}

pub inline fn blended_grid(min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const noise_types.BlendedNoiseConfig, base_x: i32, base_y: i32, base_z: i32, step_x: i32, step_y: i32, step_z: i32, nx: usize, ny: usize, nz: usize, values: [*]f64) void {
    @setFloatMode(.strict);
    const total = nx * ny * nz;
    if (comptime builtin.cpu.arch == .x86_64) {
        if (backend_runtime.supports(.avx2)) {
            backend_abi.adrenaline_blended_noise_grid_avx2(min, max, main, config, base_x, base_y, base_z, step_x, step_y, step_z, nx, ny, nz, values);
            return;
        }
    }
    noise_blended.blended_noise_grid_impl(false, min, max, main, config, base_x, base_y, base_z, step_x, step_y, step_z, nx, ny, nz, values[0..total]);
}

pub inline fn normal_grid(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_length: usize, y_length: usize, z_length: usize, values: [*]f64) void {
    @setFloatMode(.strict);
    const total = x_length * y_length * z_length;
    if (comptime builtin.cpu.arch == .x86_64) {
        if (backend_runtime.supports(.avx2)) {
            backend_abi.adrenaline_normal_noise_grid_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, @ptrCast(values));
        } else {
            noise_normal.normal_noise_grid(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
        }
    } else {
        noise_normal.normal_noise_grid(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
    }
}

pub inline fn approximate_grid(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_length: usize, y_length: usize, z_length: usize, values: [*]f64) void {
    @setFloatMode(.strict);
    const total = x_length * y_length * z_length;
    if (comptime builtin.cpu.arch == .x86_64) {
        if (backend_runtime.supports(.avx2)) {
            backend_abi.adrenaline_normal_noise_grid_approx_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, @ptrCast(values));
        } else {
            noise_approximate.normal_noise_grid_approx(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
        }
    } else {
        noise_approximate.normal_noise_grid_approx(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
    }
}

pub inline fn approximate_grid_f32(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_length: usize, y_length: usize, z_length: usize, values: [*]f32) void {
    @setFloatMode(.strict);
    const total = x_length * y_length * z_length;
    if (comptime builtin.cpu.arch == .x86_64) {
        if (backend_runtime.supports(.avx2)) {
            backend_abi.adrenaline_normal_noise_grid_approx_float_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, @ptrCast(values));
        } else {
            noise_approximate.normal_noise_grid_approx_float(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
        }
    } else {
        noise_approximate.normal_noise_grid_approx_float(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
    }
}
