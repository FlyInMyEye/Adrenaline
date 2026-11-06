const backend_aquifer = @import("../backend/aquifer_dispatch.zig");
const backend_runtime = @import("../backend/runtime.zig");

const std = @import("std");
const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;
const aquifer_cell = @import("../aquifer/cell.zig");
const backend_abi = @import("../backend/abi.zig");
const backend_options = @import("../backend/options.zig");
const core_limits = @import("../core/limits.zig");

pub fn native_aquifer_sampler_evaluate0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, barrier_values: c.jdoubleArray, candidates: c.jlongArray, fluid_levels: c.jintArray, fluid_types: c.jbyteArray, global_fluid_level: c.jint, global_fluid_type: c.jbyte, base_y: c.jint, cell_width: c.jint, cell_height: c.jint, deferred_indices: c.jintArray, deferred_count: c.jint, output: c.jbyteArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > core_limits.max_density_values or deferred_count < 0 or deferred_count > @as(c.jint, @intCast(total))) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const fluid_count = get_array_length(env, fluid_levels);
    if (fluid_count <= 0 or fluid_count > core_limits.max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, barrier_values) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2)) or get_array_length(env, deferred_indices) < deferred_count or get_array_length(env, output) < value_count or get_array_length(env, fluid_types) < fluid_count) {
        return c.JNI_FALSE;
    }

    const density_address = get_primitive_array_critical(env, density_values, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, density_values, density_address, 0);
    const barrier_address = get_primitive_array_critical(env, barrier_values, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, barrier_values, barrier_address, 0);
    const candidates_address = get_primitive_array_critical(env, candidates, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, candidates, candidates_address, 0);
    const levels_address = get_primitive_array_critical(env, fluid_levels, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, fluid_levels, levels_address, 0);
    const types_address = get_primitive_array_critical(env, fluid_types, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, fluid_types, types_address, 0);
    const deferred_indices_address = get_primitive_array_critical(env, deferred_indices, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, deferred_indices, deferred_indices_address, 0);
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const density: [*]const c.jdouble = @ptrCast(@alignCast(density_address));
    const barrier: [*]const c.jdouble = @ptrCast(@alignCast(barrier_address));
    const candidate_values: [*]const c.jlong = @ptrCast(@alignCast(candidates_address));
    const levels: [*]const c.jint = @ptrCast(@alignCast(levels_address));
    const types: [*]const c.jbyte = @ptrCast(@alignCast(types_address));
    const deferred: [*]const c.jint = @ptrCast(@alignCast(deferred_indices_address));
    const materials: [*]c.jbyte = @ptrCast(@alignCast(output_address));
    const cache_length: usize = @intCast(fluid_count);
    const deferred_total: usize = @intCast(deferred_count);
    return if (aquifer_cell.evaluate_aquifer_cell(density[0..total], barrier[0..total], candidate_values[0 .. total * 2], levels[0..cache_length], types[0..cache_length], global_fluid_level, global_fluid_type, base_y, width, height, deferred[0..deferred_total], materials[0..total])) c.JNI_TRUE else c.JNI_FALSE;
}

pub fn native_aquifer_sampler_locate0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, global_materials: c.jbyteArray, candidates: c.jlongArray, packed_locations: c.jshortArray, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0 or grid_size_x <= 0 or grid_size_z <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > core_limits.max_density_values) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const location_count = get_array_length(env, packed_locations);
    if (location_count <= 0 or location_count > core_limits.max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, global_materials) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2))) {
        return c.JNI_FALSE;
    }

    const density_address = get_primitive_array_critical(env, density_values, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, density_values, density_address, 0);
    const global_address = get_primitive_array_critical(env, global_materials, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, global_materials, global_address, 0);
    const candidates_address = get_primitive_array_critical(env, candidates, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, candidates, candidates_address, 0);
    const locations_address = get_primitive_array_critical(env, packed_locations, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, packed_locations, locations_address, 0);

    const density: [*]const c.jdouble = @ptrCast(@alignCast(density_address));
    const global: [*]const c.jbyte = @ptrCast(@alignCast(global_address));
    const candidate_values: [*]c.jlong = @ptrCast(@alignCast(candidates_address));
    const locations: [*]const c.jshort = @ptrCast(@alignCast(locations_address));
    const cache_length: usize = @intCast(location_count);
    return if (aquifer_cell.locate_aquifer_cell(density[0..total], global[0..total], candidate_values[0 .. total * 2], locations[0..cache_length], min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height)) c.JNI_TRUE else c.JNI_FALSE;
}

pub fn native_aquifer_sampler_classify0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, global_materials: c.jbyteArray, candidates: c.jlongArray, fluid_levels: c.jintArray, fluid_types: c.jbyteArray, base_y: c.jint, cell_width: c.jint, cell_height: c.jint, output: c.jbyteArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > core_limits.max_density_values) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const fluid_count = get_array_length(env, fluid_levels);
    if (fluid_count <= 0 or fluid_count > core_limits.max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, global_materials) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2)) or get_array_length(env, fluid_types) < fluid_count or get_array_length(env, output) < value_count) {
        return c.JNI_FALSE;
    }

    const density_address = get_primitive_array_critical(env, density_values, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, density_values, density_address, 0);
    const global_address = get_primitive_array_critical(env, global_materials, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, global_materials, global_address, 0);
    const candidates_address = get_primitive_array_critical(env, candidates, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, candidates, candidates_address, 0);
    const levels_address = get_primitive_array_critical(env, fluid_levels, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, fluid_levels, levels_address, 0);
    const types_address = get_primitive_array_critical(env, fluid_types, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, fluid_types, types_address, 0);
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const density: [*]const c.jdouble = @ptrCast(@alignCast(density_address));
    const global: [*]const c.jbyte = @ptrCast(@alignCast(global_address));
    const candidate_values: [*]const c.jlong = @ptrCast(@alignCast(candidates_address));
    const levels: [*]const c.jint = @ptrCast(@alignCast(levels_address));
    const types: [*]const c.jbyte = @ptrCast(@alignCast(types_address));
    const requirements: [*]c.jbyte = @ptrCast(@alignCast(output_address));
    const cache_length: usize = @intCast(fluid_count);
    return if (aquifer_cell.classify_aquifer_cell(density[0..total], global[0..total], candidate_values[0 .. total * 2], levels[0..cache_length], types[0..cache_length], base_y, width, height, requirements[0..total])) c.JNI_TRUE else c.JNI_FALSE;
}

pub fn native_aquifer_sampler_prepare0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, candidates: c.jlongArray, packed_locations: c.jshortArray, fluid_levels: c.jintArray, fluid_types: c.jbyteArray, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, deferred_indices: c.jintArray, deferred_count: c.jintArray, output: c.jbyteArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0 or grid_size_x <= 0 or grid_size_z <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > core_limits.max_density_values) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const location_count = get_array_length(env, packed_locations);
    if (location_count <= 0 or location_count > core_limits.max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2)) or get_array_length(env, deferred_indices) < value_count or get_array_length(env, deferred_count) < 1 or get_array_length(env, output) < value_count or get_array_length(env, fluid_levels) < location_count or get_array_length(env, fluid_types) < location_count) {
        return c.JNI_FALSE;
    }

    const density_address = get_primitive_array_critical(env, density_values, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, density_values, density_address, 0);
    if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
        const density: [*]const f64 = @ptrCast(@alignCast(density_address));
        if (density[0] > 0.0 and (global_fluid_type == 2 or global_fluid_type == 3) and backend_runtime.supports(.avx2) and backend_abi.adrenaline_all_solid_avx2(density, total) != 0) {
            const count_address = get_primitive_array_critical(env, deferred_count, null) orelse return c.JNI_FALSE;
            defer release_primitive_array_critical(env, deferred_count, count_address, 0);
            const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
            defer release_primitive_array_critical(env, output, output_address, 0);
            const count_ptr: [*]c.jint = @ptrCast(@alignCast(count_address));
            const material_ptr: [*]c.jbyte = @ptrCast(@alignCast(output_address));
            @memset(material_ptr[0..total], 0);
            count_ptr[0] = 0;
            return c.JNI_TRUE;
        }
    }
    const levels_address = get_primitive_array_critical(env, fluid_levels, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, fluid_levels, levels_address, 0);
    if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
        if (@as(i64, base_y) >= @as(i64, global_fluid_level) + 5 and
            @as(i64, base_y) + @as(i64, @intCast(height - 1)) <= std.math.maxInt(i32) and
            (global_fluid_type == 2 or global_fluid_type == 3) and backend_runtime.supports(.avx2))
        {
            const early_levels: [*]const i32 = @ptrCast(@alignCast(levels_address));
            if (backend_abi.adrenaline_aquifer_air_bounds_avx2(early_levels, @intCast(location_count), min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height) != 0) {
                const count_address = get_primitive_array_critical(env, deferred_count, null) orelse return c.JNI_FALSE;
                defer release_primitive_array_critical(env, deferred_count, count_address, 0);
                const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
                defer release_primitive_array_critical(env, output, output_address, 0);
                const density_ptr: [*]const f64 = @ptrCast(@alignCast(density_address));
                const output_ptr: [*]i8 = @ptrCast(@alignCast(output_address));
                const count_ptr: [*]i32 = @ptrCast(@alignCast(count_address));
                backend_abi.adrenaline_fill_air_avx2(density_ptr, output_ptr, total);
                count_ptr[0] = 0;
                return c.JNI_TRUE;
            }
        }
    }
    const candidates_address = get_primitive_array_critical(env, candidates, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, candidates, candidates_address, 0);
    const locations_address = get_primitive_array_critical(env, packed_locations, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, packed_locations, locations_address, 0);
    const types_address = get_primitive_array_critical(env, fluid_types, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, fluid_types, types_address, 0);
    const deferred_indices_address = get_primitive_array_critical(env, deferred_indices, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, deferred_indices, deferred_indices_address, 0);
    const deferred_count_address = get_primitive_array_critical(env, deferred_count, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, deferred_count, deferred_count_address, 0);
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const density: [*]const c.jdouble = @ptrCast(@alignCast(density_address));
    const candidate_values: [*]c.jlong = @ptrCast(@alignCast(candidates_address));
    const locations: [*]const c.jshort = @ptrCast(@alignCast(locations_address));
    const levels: [*]const c.jint = @ptrCast(@alignCast(levels_address));
    const types: [*]const c.jbyte = @ptrCast(@alignCast(types_address));
    const deferred: [*]c.jint = @ptrCast(@alignCast(deferred_indices_address));
    const deferred_total: [*]c.jint = @ptrCast(@alignCast(deferred_count_address));
    const materials: [*]c.jbyte = @ptrCast(@alignCast(output_address));
    const cache_length: usize = @intCast(location_count);
    const count = backend_aquifer.prepare_cell(density, candidate_values, locations, cache_length, levels, types, global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred, materials) orelse return c.JNI_FALSE;
    deferred_total[0] = @intCast(count);
    return c.JNI_TRUE;
}

pub fn register() void {
    @export(&native_aquifer_sampler_evaluate0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_evaluate0" });
    @export(&native_aquifer_sampler_locate0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_locate0" });
    @export(&native_aquifer_sampler_classify0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_classify0" });
    @export(&native_aquifer_sampler_prepare0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_prepare0" });
}
