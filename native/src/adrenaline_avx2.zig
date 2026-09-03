pub const adrenaline_avx2 = true;

const c = @cImport({
    @cInclude("jni.h");
});
const std = @import("std");
const native = @import("adrenaline_native.zig");

pub export fn adrenaline_normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void {
    native.normal_noise_grid_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values[0 .. x_count * y_count * z_count]);
}

pub export fn adrenaline_normal_noise_grid_approx_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void {
    native.normal_noise_grid_approx_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values[0 .. x_count * y_count * z_count]);
}

pub export fn adrenaline_normal_noise_grid_approx_float_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f32) callconv(.c) void {
    native.normal_noise_grid_approx_float_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values[0 .. x_count * y_count * z_count]);
}

pub export fn adrenaline_prepare_aquifer_cell_avx2(density: [*]const c.jdouble, candidates: [*]c.jlong, packed_locations: [*]const c.jshort, packed_location_count: usize, fluid_levels: [*]const c.jint, fluid_types: [*]const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: [*]c.jint, materials: [*]c.jbyte) callconv(.c) usize {
    const total = width * width * height;
    return native.prepare_aquifer_cell_avx2(density[0..total], candidates[0 .. total * 2], packed_locations[0..packed_location_count], fluid_levels[0..packed_location_count], fluid_types[0..packed_location_count], global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred_indices[0..total], materials[0..total]) orelse std.math.maxInt(usize);
}
