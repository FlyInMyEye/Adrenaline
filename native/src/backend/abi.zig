const c = @import("../core/jni.zig").c;
const noise_types = @import("../noise/types.zig");

pub extern fn adrenaline_has_avx2() c_int;

pub extern fn adrenaline_blended_noise_grid_avx2(min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const noise_types.BlendedNoiseConfig, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;

pub extern fn adrenaline_normal_noise_batch_small_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, count: usize, values: [*]f64) callconv(.c) void;

pub extern fn adrenaline_normal_noise_batch_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, count: usize, values: [*]f64) callconv(.c) void;

pub extern fn adrenaline_density_cell_avx2(program: [*]const u8, program_length: usize, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: [*]f64) callconv(.c) c_int;

pub extern fn adrenaline_density_scalar(program: [*]const u8, program_length: usize, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: [*]f64) callconv(.c) c_int;

pub extern fn adrenaline_normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;

pub extern fn adrenaline_normal_noise_grid_approx_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;

pub extern fn adrenaline_normal_noise_grid_approx_float_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f32) callconv(.c) void;

pub extern fn adrenaline_all_solid_avx2(density: [*]const f64, count: usize) callconv(.c) c_int;

pub extern fn adrenaline_aquifer_air_bounds_avx2(levels: [*]const i32, length: usize, min_grid_x: i32, min_grid_y: i32, min_grid_z: i32, grid_size_x: i32, grid_size_z: i32, base_x: i32, base_y: i32, base_z: i32, width: usize, height: usize) callconv(.c) c_int;

pub extern fn adrenaline_fill_air_avx2(density: [*]const f64, materials: [*]i8, count: usize) callconv(.c) void;

pub extern fn adrenaline_prepare_aquifer_cell_avx2(density: [*]const c.jdouble, candidates: [*]c.jlong, packed_locations: [*]const c.jshort, packed_location_count: usize, fluid_levels: [*]const c.jint, fluid_types: [*]const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: [*]c.jint, materials: [*]c.jbyte) callconv(.c) usize;

pub extern fn adrenaline_fused_terrain_avx2(program: [*]const u8, height: usize, output: [*]c.jdouble) callconv(.c) void;

pub extern fn adrenaline_fused_ridge_avx2(program: [*]const u8, height: usize, output: [*]c.jdouble) callconv(.c) void;

pub extern fn adrenaline_terrain_prefix_avx2(program: [*]const u8, height: usize, terrain: [*]c.jdouble, noodle: [*]c.jdouble) callconv(.c) void;

pub extern fn adrenaline_preliminary_height_avx2(program: [*]const u8, program_length: usize, inputs: [*]const f64, input_length: usize, slots: usize, x: c.jint, z: c.jint, min_y: c.jint, max_y: c.jint, step: c.jint) callconv(.c) c.jint;

pub extern fn adrenaline_aquifer_context_size(count: usize) callconv(.c) usize;

pub extern fn adrenaline_aquifer_context_validate(memory: [*]align(8) const u8, capacity: usize) callconv(.c) c_int;

pub extern fn adrenaline_aquifer_context_init(memory: [*]align(8) u8, capacity: usize, locations: [*]const i16, count: usize, levels: [*]const i32, types: [*]const i8, min_x: i32, min_y: i32, min_z: i32, sx: i32, sz: i32) callconv(.c) c_int;

pub extern fn adrenaline_aquifer_context_prepare(memory: [*]align(8) const u8, density: [*]const f64, candidates: [*]i64, global_level: i32, global_type: i8, base_x: i32, base_y: i32, base_z: i32, width: usize, height: usize, deferred: [*]i32, materials: [*]i8) callconv(.c) usize;

pub extern fn adrenaline_density_slice_avx2(program: [*]const u8, program_length: usize, x: i32, y: i32, z: i32, step: i32, count: usize, inputs: [*]const f64, input_length: usize, output: [*]f64) callconv(.c) c_int;
