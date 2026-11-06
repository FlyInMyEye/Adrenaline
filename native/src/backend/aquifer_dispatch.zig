const std = @import("std");
const builtin = @import("builtin");
const backend_runtime = @import("runtime.zig");
const backend_options = @import("options.zig");
const backend_abi = @import("abi.zig");
const aquifer_cell = @import("../aquifer/cell.zig");
pub inline fn prepare_cell(density: [*]const f64, candidate_values: [*]i64, locations: [*]const i16, cache_length: usize, levels: [*]const i32, types: [*]const i8, global_fluid_level: i32, global_fluid_type: i8, min_grid_x: i32, min_grid_y: i32, min_grid_z: i32, grid_size_x: i32, grid_size_z: i32, base_x: i32, base_y: i32, base_z: i32, width: usize, height: usize, deferred: [*]i32, materials: [*]i8) ?usize {
    if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
        if (backend_runtime.supports(.avx2)) {
            const count = backend_abi.adrenaline_prepare_aquifer_cell_avx2(density, candidate_values, locations, cache_length, levels, types, global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred, materials);
            return if (count == std.math.maxInt(usize)) null else count;
        }
    }
    const total = width * width * height;
    return aquifer_cell.prepare_aquifer_cell_impl(false, comptime builtin.cpu.arch == .aarch64, density[0..total], candidate_values[0 .. total * 2], locations[0..cache_length], levels[0..cache_length], types[0..cache_length], global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred[0..total], materials[0..total]);
}
