pub const native_backend: @import("backend/options.zig").Options = .{ .instruction_set = .avx2 };
const aquifer_cell = @import("aquifer/cell.zig");
const density_fused = @import("density/fused.zig");
const density_program = @import("density/program.zig");
const noise_approximate = @import("noise/approximate.zig");
const noise_batch = @import("noise/batch.zig");
const noise_blended = @import("noise/blended.zig");
const noise_normal = @import("noise/normal.zig");
const noise_types = @import("noise/types.zig");

const c = @cImport({
    @cInclude("jni.h");
});
const std = @import("std");

pub export fn adrenaline_all_solid_avx2(density: [*]const f64, count: usize) callconv(.c) c_int {
    const Vec = @Vector(4, f64);
    const zero: Vec = @splat(0.0);
    var i: usize = 0;
    while (i + 15 < count) : (i += 16) {
        const a: Vec = @bitCast(density[i..][0..4].*);
        const b: Vec = @bitCast(density[i + 4 ..][0..4].*);
        const c1: Vec = @bitCast(density[i + 8 ..][0..4].*);
        const d: Vec = @bitCast(density[i + 12 ..][0..4].*);
        if (!@reduce(.And, (a > zero) & (b > zero) & (c1 > zero) & (d > zero))) return 0;
    }
    while (i < count) : (i += 1) if (!(density[i] > 0.0)) {
        return 0;
    };
    return 1;
}

pub export fn adrenaline_density_cell_avx2(program: [*]const u8, program_length: usize, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: [*]f64) callconv(.c) c_int {
    return if (density_program.evaluate_density_cell(program[0..program_length], base_x, base_y, base_z, width, height, output[0 .. width * width * height], &.{})) 1 else 0;
}

pub export fn adrenaline_terrain_prefix_avx2(program: [*]const u8, height: usize, terrain: [*]f64, noodle: [*]f64) callconv(.c) void {
    density_fused.evaluate_terrain_prefix(program[0..376], height, terrain[0 .. 16 * height], noodle[0 .. 16 * height]);
}

pub export fn adrenaline_fused_ridge_avx2(program: [*]const u8, height: usize, output: [*]f64) callconv(.c) void {
    density_fused.evaluate_fused_ridge(program[0..144], height, output[0 .. 16 * height]);
}

pub export fn adrenaline_fused_terrain_avx2(program: [*]const u8, height: usize, output: [*]f64) callconv(.c) void {
    density_fused.evaluate_fused_terrain(program[0..388], height, output[0 .. 16 * height]);
}

pub export fn adrenaline_blended_noise_grid_avx2(min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const noise_types.BlendedNoiseConfig, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void {
    noise_blended.blended_noise_grid_avx2(min, max, main, config, base_x, base_y, base_z, step_x, step_y, step_z, x_count, y_count, z_count, values[0 .. x_count * y_count * z_count]);
}

pub export fn adrenaline_normal_noise_batch_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, count: usize, values: [*]f64) callconv(.c) void {
    noise_batch.normal_noise_batch_avx2(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..count]);
}

pub export fn adrenaline_normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void {
    noise_normal.normal_noise_grid_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values[0 .. x_count * y_count * z_count]);
}

pub export fn adrenaline_normal_noise_grid_approx_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void {
    noise_approximate.normal_noise_grid_approx_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values[0 .. x_count * y_count * z_count]);
}

pub export fn adrenaline_normal_noise_grid_approx_float_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f32) callconv(.c) void {
    noise_approximate.normal_noise_grid_approx_float_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values[0 .. x_count * y_count * z_count]);
}

pub export fn adrenaline_prepare_aquifer_cell_avx2(density: [*]const c.jdouble, candidates: [*]c.jlong, packed_locations: [*]const c.jshort, packed_location_count: usize, fluid_levels: [*]const c.jint, fluid_types: [*]const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: [*]c.jint, materials: [*]c.jbyte) callconv(.c) usize {
    const total = width * width * height;
    return aquifer_cell.prepare_aquifer_cell_avx2(density[0..total], candidates[0 .. total * 2], packed_locations[0..packed_location_count], fluid_levels[0..packed_location_count], fluid_types[0..packed_location_count], global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred_indices[0..total], materials[0..total]) orelse std.math.maxInt(usize);
}

pub export fn adrenaline_aquifer_air_bounds_avx2(levels: [*]const i32, length: usize, min_grid_x: i32, min_grid_y: i32, min_grid_z: i32, grid_size_x: i32, grid_size_z: i32, base_x: i32, base_y: i32, base_z: i32, width: usize, height: usize) callconv(.c) c_int {
    return @intFromBool(aquifer_cell.aquifer_air_bounds_avx2(levels[0..length], min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height));
}
pub export fn adrenaline_fill_air_avx2(density: [*]const f64, materials: [*]i8, count: usize) callconv(.c) void {
    aquifer_cell.fill_air_avx2(density[0..count], materials[0..count]);
}

pub export fn adrenaline_density_slice_avx2(program: [*]const u8, program_length: usize, x: i32, y: i32, z: i32, step: i32, count: usize, inputs: [*]const f64, input_length: usize, output: [*]f64) callconv(.c) c_int {
    return @intFromBool(density_program.evaluate_density_slice(program[0..program_length], x, y, z, step, output[0..count], inputs[0..input_length]));
}
