const c = @cImport({
    @cInclude("jni.h");
});
const builtin = @import("builtin");
const std = @import("std");

const max_octaves = 32;
const max_batch_size = 256;
const max_grid_size = 4096;
const max_grid_axis = 256;
const max_density_program_size = 16 * 1024;
const max_density_values = 1024;
const max_density_stack = 64;
const normal_noise_input_factor = 1.0181268882175227;
const Vec2 = @Vector(2, f64);
const Vec4 = @Vector(4, f64);
const Vec4f = @Vector(4, f32);
const Vec8f = @Vector(8, f32);
const Vec4i = @Vector(4, i32);
const Vec8i = @Vector(8, i32);
const avx_only = @hasDecl(@import("root"), "adrenaline_avx2");

extern fn adrenaline_has_avx2() c_int;
extern fn adrenaline_normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;
extern fn adrenaline_normal_noise_grid_approx_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;
extern fn adrenaline_normal_noise_grid_approx_float_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f32) callconv(.c) void;
extern fn adrenaline_prepare_aquifer_cell_avx2(density: [*]const c.jdouble, candidates: [*]c.jlong, packed_locations: [*]const c.jshort, packed_location_count: usize, fluid_levels: [*]const c.jint, fluid_types: [*]const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: [*]c.jint, materials: [*]c.jbyte) callconv(.c) usize;

comptime {
    if (!avx_only) {
        @export(&JNI_OnLoad, .{ .name = "JNI_OnLoad" });
        @export(&Java_net_fly_adrenaline_natives_AdrenalineNatives_capabilities0, .{ .name = "Java_net_fly_adrenaline_natives_AdrenalineNatives_capabilities0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_address0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_address0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_sample0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sample0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleGrid0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleGrid0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGrid0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGrid0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGridFloat0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGridFloat0" });
        @export(&Java_net_fly_adrenaline_natives_NativeDensitySampler_evaluate0, .{ .name = "Java_net_fly_adrenaline_natives_NativeDensitySampler_evaluate0" });
        @export(&Java_net_fly_adrenaline_natives_NativeAquiferSampler_evaluate0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_evaluate0" });
        @export(&Java_net_fly_adrenaline_natives_NativeAquiferSampler_prepare0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_prepare0" });
        @export(&Java_net_fly_adrenaline_natives_NativeAquiferSampler_locate0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_locate0" });
        @export(&Java_net_fly_adrenaline_natives_NativeAquiferSampler_classify0, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_classify0" });
    }
}

fn JNI_OnLoad(_: ?*anyopaque, _: ?*anyopaque) callconv(.c) c.jint {
    return c.JNI_VERSION_1_8;
}

fn Java_net_fly_adrenaline_natives_AdrenalineNatives_capabilities0(_: ?*c.JNIEnv, _: c.jclass) callconv(.c) c.jint {
    if (comptime builtin.cpu.arch == .x86_64) {
        return if (adrenaline_has_avx2() != 0) 1 else 0;
    }
    if (comptime builtin.cpu.arch == .aarch64) {
        return 2;
    }
    return 0;
}

fn Java_net_fly_adrenaline_natives_PerlinNativeSampler_address0(env: ?*c.JNIEnv, _: c.jclass, buffer: c.jobject) callconv(.c) c.jlong {
    const environment = env orelse return 0;
    const get_direct_buffer_address = environment.*.*.GetDirectBufferAddress orelse return 0;
    const address = get_direct_buffer_address(env, buffer) orelse return 0;
    return @intCast(@intFromPtr(address));
}

fn Java_net_fly_adrenaline_natives_PerlinNativeSampler_sample0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, y_step: c.jdouble, count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (first_address == 0 or second_address == 0 or count <= 0 or count > max_batch_size or first_octaves < 0 or first_octaves > max_octaves or second_octaves < 0 or second_octaves > max_octaves) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < count) {
        return c.JNI_FALSE;
    }
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const length: usize = @intCast(count);
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values: [*]c.jdouble = @ptrCast(@alignCast(output_address));
    normal_noise_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..length]);
    return c.JNI_TRUE;
}

fn Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleGrid0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, x_step: c.jdouble, y_step: c.jdouble, z_step: c.jdouble, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (first_address == 0 or second_address == 0 or x_count <= 0 or y_count <= 0 or z_count <= 0 or first_octaves < 0 or first_octaves > max_octaves or second_octaves < 0 or second_octaves > max_octaves) {
        return c.JNI_FALSE;
    }

    const x_length: usize = @intCast(x_count);
    const y_length: usize = @intCast(y_count);
    const z_length: usize = @intCast(z_count);
    const total = x_length * y_length * z_length;
    if (total > max_grid_size) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < @as(c.jsize, @intCast(total))) {
        return c.JNI_FALSE;
    }
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values: [*]c.jdouble = @ptrCast(@alignCast(output_address));
    if (comptime builtin.cpu.arch == .x86_64) {
        if (adrenaline_has_avx2() != 0) {
            adrenaline_normal_noise_grid_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, @ptrCast(values));
        } else {
            normal_noise_grid(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
        }
    } else {
        normal_noise_grid(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
    }
    return c.JNI_TRUE;
}

fn Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGrid0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, x_step: c.jdouble, y_step: c.jdouble, z_step: c.jdouble, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    if (first_address == 0 or second_address == 0 or x_count <= 0 or y_count <= 0 or z_count <= 0 or first_octaves < 0 or first_octaves > max_octaves or second_octaves < 0 or second_octaves > max_octaves) {
        return c.JNI_FALSE;
    }

    const x_length: usize = @intCast(x_count);
    const y_length: usize = @intCast(y_count);
    const z_length: usize = @intCast(z_count);
    const total = x_length * y_length * z_length;
    if (total > max_grid_size) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < @as(c.jsize, @intCast(total))) {
        return c.JNI_FALSE;
    }
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values: [*]c.jdouble = @ptrCast(@alignCast(output_address));
    if (comptime builtin.cpu.arch == .x86_64) {
        if (adrenaline_has_avx2() != 0) {
            adrenaline_normal_noise_grid_approx_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, @ptrCast(values));
        } else {
            normal_noise_grid_approx(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
        }
    } else {
        normal_noise_grid_approx(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
    }
    return c.JNI_TRUE;
}

fn Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGridFloat0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, x_step: c.jdouble, y_step: c.jdouble, z_step: c.jdouble, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jfloatArray) callconv(.c) c.jboolean {
    if (first_address == 0 or second_address == 0 or x_count <= 0 or y_count <= 0 or z_count <= 0 or first_octaves < 0 or first_octaves > max_octaves or second_octaves < 0 or second_octaves > max_octaves) {
        return c.JNI_FALSE;
    }

    const x_length: usize = @intCast(x_count);
    const y_length: usize = @intCast(y_count);
    const z_length: usize = @intCast(z_count);
    const total = x_length * y_length * z_length;
    if (total > max_grid_size) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < @as(c.jsize, @intCast(total))) {
        return c.JNI_FALSE;
    }
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values: [*]c.jfloat = @ptrCast(@alignCast(output_address));
    if (comptime builtin.cpu.arch == .x86_64) {
        if (adrenaline_has_avx2() != 0) {
            adrenaline_normal_noise_grid_approx_float_avx2(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, @ptrCast(values));
        } else {
            normal_noise_grid_approx_float(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
        }
    } else {
        normal_noise_grid_approx_float(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values[0..total]);
    }
    return c.JNI_TRUE;
}

fn Java_net_fly_adrenaline_natives_NativeDensitySampler_evaluate0(env: ?*c.JNIEnv, _: c.jclass, program_buffer: c.jobject, program_length: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (program_length <= 0 or program_length > max_density_program_size or cell_width <= 0 or cell_height <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > max_density_values) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_direct_buffer_address = functions.GetDirectBufferAddress orelse return c.JNI_FALSE;
    const get_direct_buffer_capacity = functions.GetDirectBufferCapacity orelse return c.JNI_FALSE;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const length: usize = @intCast(program_length);
    if (get_direct_buffer_capacity(env, program_buffer) < @as(c.jlong, @intCast(length)) or get_array_length(env, output) < @as(c.jsize, @intCast(total))) {
        return c.JNI_FALSE;
    }
    const program_address = get_direct_buffer_address(env, program_buffer) orelse return c.JNI_FALSE;
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const program: [*]const u8 = @ptrCast(@alignCast(program_address));
    const values: [*]c.jdouble = @ptrCast(@alignCast(output_address));
    return if (evaluate_density_program(program[0..length], base_x, base_y, base_z, width, height, values[0..total])) c.JNI_TRUE else c.JNI_FALSE;
}

fn Java_net_fly_adrenaline_natives_NativeAquiferSampler_evaluate0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, barrier_values: c.jdoubleArray, candidates: c.jlongArray, fluid_levels: c.jintArray, fluid_types: c.jbyteArray, global_fluid_level: c.jint, global_fluid_type: c.jbyte, base_y: c.jint, cell_width: c.jint, cell_height: c.jint, deferred_indices: c.jintArray, deferred_count: c.jint, output: c.jbyteArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > max_density_values or deferred_count < 0 or deferred_count > @as(c.jint, @intCast(total))) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const fluid_count = get_array_length(env, fluid_levels);
    if (fluid_count <= 0 or fluid_count > max_grid_size
        or get_array_length(env, density_values) < value_count
        or get_array_length(env, barrier_values) < value_count
        or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2))
        or get_array_length(env, deferred_indices) < deferred_count
        or get_array_length(env, output) < value_count
        or get_array_length(env, fluid_types) < fluid_count) {
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
    return if (evaluate_aquifer_cell(density[0..total], barrier[0..total], candidate_values[0 .. total * 2], levels[0..cache_length], types[0..cache_length], global_fluid_level, global_fluid_type, base_y, width, height, deferred[0..deferred_total], materials[0..total])) c.JNI_TRUE else c.JNI_FALSE;
}

fn Java_net_fly_adrenaline_natives_NativeAquiferSampler_locate0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, global_materials: c.jbyteArray, candidates: c.jlongArray, packed_locations: c.jshortArray, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0 or grid_size_x <= 0 or grid_size_z <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > max_density_values) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const location_count = get_array_length(env, packed_locations);
    if (location_count <= 0 or location_count > max_grid_size
        or get_array_length(env, density_values) < value_count
        or get_array_length(env, global_materials) < value_count
        or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2))
        ) {
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
    return if (locate_aquifer_cell(density[0..total], global[0..total], candidate_values[0 .. total * 2], locations[0..cache_length], min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height)) c.JNI_TRUE else c.JNI_FALSE;
}

fn Java_net_fly_adrenaline_natives_NativeAquiferSampler_classify0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, global_materials: c.jbyteArray, candidates: c.jlongArray, fluid_levels: c.jintArray, fluid_types: c.jbyteArray, base_y: c.jint, cell_width: c.jint, cell_height: c.jint, output: c.jbyteArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > max_density_values) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const fluid_count = get_array_length(env, fluid_levels);
    if (fluid_count <= 0 or fluid_count > max_grid_size
        or get_array_length(env, density_values) < value_count
        or get_array_length(env, global_materials) < value_count
        or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2))
        or get_array_length(env, fluid_types) < fluid_count
        or get_array_length(env, output) < value_count) {
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
    return if (classify_aquifer_cell(density[0..total], global[0..total], candidate_values[0 .. total * 2], levels[0..cache_length], types[0..cache_length], base_y, width, height, requirements[0..total])) c.JNI_TRUE else c.JNI_FALSE;
}

fn Java_net_fly_adrenaline_natives_NativeAquiferSampler_prepare0(env: ?*c.JNIEnv, _: c.jclass, density_values: c.jdoubleArray, candidates: c.jlongArray, packed_locations: c.jshortArray, fluid_levels: c.jintArray, fluid_types: c.jbyteArray, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, deferred_indices: c.jintArray, deferred_count: c.jintArray, output: c.jbyteArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (cell_width <= 0 or cell_height <= 0 or grid_size_x <= 0 or grid_size_z <= 0) {
        return c.JNI_FALSE;
    }
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total == 0 or total > max_density_values) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    const get_primitive_array_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_primitive_array_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const value_count: c.jsize = @intCast(total);
    const location_count = get_array_length(env, packed_locations);
    if (location_count <= 0 or location_count > max_grid_size
        or get_array_length(env, density_values) < value_count
        or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2))
        or get_array_length(env, deferred_indices) < value_count
        or get_array_length(env, deferred_count) < 1
        or get_array_length(env, output) < value_count
        or get_array_length(env, fluid_levels) < location_count
        or get_array_length(env, fluid_types) < location_count) {
        return c.JNI_FALSE;
    }

    const density_address = get_primitive_array_critical(env, density_values, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, density_values, density_address, 0);
    const candidates_address = get_primitive_array_critical(env, candidates, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, candidates, candidates_address, 0);
    const locations_address = get_primitive_array_critical(env, packed_locations, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, packed_locations, locations_address, 0);
    const levels_address = get_primitive_array_critical(env, fluid_levels, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, fluid_levels, levels_address, 0);
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
    const count = if (comptime builtin.cpu.arch == .x86_64 and !avx_only) block: {
        if (adrenaline_has_avx2() != 0) {
            const avx_count = adrenaline_prepare_aquifer_cell_avx2(density, candidate_values, locations, cache_length, levels, types, global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred, materials);
            if (avx_count == std.math.maxInt(usize)) {
                return c.JNI_FALSE;
            }
            break :block avx_count;
        }
        break :block prepare_aquifer_cell_impl(false, comptime builtin.cpu.arch == .aarch64, density[0..total], candidate_values[0 .. total * 2], locations[0..cache_length], levels[0..cache_length], types[0..cache_length], global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred[0..total], materials[0..total]) orelse return c.JNI_FALSE;
    } else prepare_aquifer_cell_impl(false, comptime builtin.cpu.arch == .aarch64, density[0..total], candidate_values[0 .. total * 2], locations[0..cache_length], levels[0..cache_length], types[0..cache_length], global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred[0..total], materials[0..total]) orelse return c.JNI_FALSE;
    deferred_total[0] = @intCast(count);
    return c.JNI_TRUE;
}

fn evaluate_aquifer_cell(density: []const c.jdouble, barrier: []const c.jdouble, candidate_values: []const c.jlong, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, base_y: c.jint, width: usize, height: usize, deferred_indices: []const c.jint, output: []c.jbyte) bool {
    const plane = width * width;
    for (deferred_indices) |raw_index| {
        if (raw_index < 0) {
            return false;
        }
        const index: usize = @intCast(raw_index);
        if (index >= density.len) {
            return false;
        }
        const y_index = index / plane;
        if (y_index >= height) {
            return false;
        }
        const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
        const global_material = global_aquifer_material(y, global_fluid_level, global_fluid_type) orelse return false;
        if (global_material == 3) {
            output[index] = 3;
            continue;
        }
        if (global_material < 1 or global_material > 3) {
            return false;
        }

        const candidates = unpack_aquifer_candidates(candidate_values[index * 2], candidate_values[index * 2 + 1]) orelse return false;
        const nearest_type = aquifer_type_at(fluid_levels[candidates.nearest_index], fluid_types[candidates.nearest_index], y) orelse return false;
        const second_type = aquifer_type_at(fluid_levels[candidates.second_index], fluid_types[candidates.second_index], y) orelse return false;
        const third_type = aquifer_type_at(fluid_levels[candidates.third_index], fluid_types[candidates.third_index], y) orelse return false;
        const nearest_similarity = aquifer_similarity(candidates.nearest_distance, candidates.second_distance);
        if (nearest_similarity <= 0.0) {
            output[index] = @intCast(nearest_type | if (nearest_similarity >= -0.76) @as(u8, 4) else @as(u8, 0));
            continue;
        }
        const below_material = global_aquifer_material(y - 1, global_fluid_level, global_fluid_type) orelse return false;
        if (nearest_type == 2 and below_material == 3) {
            output[index] = 2 | 4;
            continue;
        }
        if (density[index] + nearest_similarity * aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type, barrier[index]) > 0.0) {
            output[index] = 0;
            continue;
        }
        const nearest_third_similarity = aquifer_similarity(candidates.nearest_distance, candidates.third_distance);
        if (nearest_third_similarity > 0.0 and density[index] + nearest_similarity * nearest_third_similarity * aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type, barrier[index]) > 0.0) {
            output[index] = 0;
            continue;
        }
        const second_third_similarity = aquifer_similarity(candidates.second_distance, candidates.third_distance);
        if (second_third_similarity > 0.0 and density[index] + nearest_similarity * second_third_similarity * aquifer_pressure(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type, barrier[index]) > 0.0) {
            output[index] = 0;
            continue;
        }
        output[index] = @intCast(nearest_type | 4);
    }
    return true;
}

fn locate_aquifer_cell(density: []const c.jdouble, global_materials: []const c.jbyte, candidate_values: []c.jlong, packed_locations: []const c.jshort, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize) bool {
    var y_index: usize = 0;
    while (y_index < height) : (y_index += 1) {
        const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
        var x_index: usize = 0;
        while (x_index < width) : (x_index += 1) {
            const x: i32 = base_x + @as(i32, @intCast(x_index));
            var z_index: usize = 0;
            var search: ?AquiferSearch = null;
            while (z_index < width) : (z_index += 1) {
                const index = (y_index * width + x_index) * width + z_index;
                if (density[index] > 0.0) {
                    continue;
                }
                const global_material: u8 = @bitCast(global_materials[index]);
                if (global_material == 3) {
                    continue;
                }
                if (global_material < 1 or global_material > 3) {
                    return false;
                }

                const z: i32 = base_z + @as(i32, @intCast(z_index));
                const grid_z = @divFloor(z - 5, 16);
                const candidates = if (search) |*cached| block: {
                    if (cached.grid_z == grid_z) {
                        cached.advanceZ();
                        break :block cached.candidates();
                    }
                    search = aquifer_search(packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, x, y, z) orelse return false;
                    break :block search.?.candidates();
                } else block: {
                    search = aquifer_search(packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, x, y, z) orelse return false;
                    break :block search.?.candidates();
                };
                pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return false;
            }
        }
    }
    return true;
}

fn prepare_aquifer_cell_impl(comptime avx2: bool, comptime neon: bool, density: []const c.jdouble, candidate_values: []c.jlong, packed_locations: []const c.jshort, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: []c.jint, materials: []c.jbyte) ?usize {
    var deferred_count: usize = 0;
    var x_index: usize = 0;
    while (x_index < width) : (x_index += 1) {
        const x: i32 = base_x + @as(i32, @intCast(x_index));
        var z_index: usize = 0;
        while (z_index < width) : (z_index += 1) {
            const z: i32 = base_z + @as(i32, @intCast(z_index));
            var search: ?AquiferSearch = null;
            var y_index: usize = 0;
            while (y_index < height) : (y_index += 1) {
                const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
                const index = (y_index * width + x_index) * width + z_index;
                materials[index] = 0;
                if (density[index] > 0.0) {
                    continue;
                }
                const global_material = global_aquifer_material(y, global_fluid_level, global_fluid_type) orelse return null;
                if (global_material == 3) {
                    materials[index] = 3;
                    continue;
                }
                if (global_material < 1 or global_material > 3) {
                    return null;
                }

                const candidates = if (search) |*cached| block: {
                    const grid_y = @divFloor(y + 1, 12);
                    if (cached.grid_y == grid_y and advance_y_down(avx2, neon, cached, y)) {
                        break :block cached.candidates();
                    }
                    search = aquifer_search(packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, x, y, z) orelse return null;
                    break :block search.?.candidates();
                } else block: {
                    search = aquifer_search(packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, x, y, z) orelse return null;
                    break :block search.?.candidates();
                };
                const nearest_type = aquifer_type_at(fluid_levels[candidates.nearest_index], fluid_types[candidates.nearest_index], y) orelse return null;
                const second_type = aquifer_type_at(fluid_levels[candidates.second_index], fluid_types[candidates.second_index], y) orelse return null;
                const third_type = aquifer_type_at(fluid_levels[candidates.third_index], fluid_types[candidates.third_index], y) orelse return null;
                const nearest_similarity = aquifer_similarity(candidates.nearest_distance, candidates.second_distance);
                if (nearest_similarity <= 0.0) {
                    materials[index] = @intCast(nearest_type | if (nearest_similarity >= -0.76) @as(u8, 4) else @as(u8, 0));
                    continue;
                }
                const below_material = global_aquifer_material(y - 1, global_fluid_level, global_fluid_type) orelse return null;
                if (nearest_type == 2 and below_material == 3) {
                    materials[index] = 2 | 4;
                    continue;
                }
                if (aquifer_pressure_uses_barrier(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type)) {
                    if (deferred_count >= deferred_indices.len) return null;
                    deferred_indices[deferred_count] = @intCast(index);
                    deferred_count += 1;
                    pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                    continue;
                }
                if (density[index] + nearest_similarity * aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type, 0.0) > 0.0) {
                    continue;
                }

                const nearest_third_similarity = aquifer_similarity(candidates.nearest_distance, candidates.third_distance);
                if (nearest_third_similarity > 0.0) {
                    if (aquifer_pressure_uses_barrier(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type)) {
                        if (deferred_count >= deferred_indices.len) return null;
                        deferred_indices[deferred_count] = @intCast(index);
                        deferred_count += 1;
                        pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                        continue;
                    }
                    if (density[index] + nearest_similarity * nearest_third_similarity * aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type, 0.0) > 0.0) {
                        continue;
                    }
                }

                const second_third_similarity = aquifer_similarity(candidates.second_distance, candidates.third_distance);
                if (second_third_similarity > 0.0) {
                    if (aquifer_pressure_uses_barrier(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type)) {
                        if (deferred_count >= deferred_indices.len) return null;
                        deferred_indices[deferred_count] = @intCast(index);
                        deferred_count += 1;
                        pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                        continue;
                    }
                    if (density[index] + nearest_similarity * second_third_similarity * aquifer_pressure(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type, 0.0) > 0.0) {
                        continue;
                    }
                }
                materials[index] = @intCast(nearest_type | 4);
            }
        }
    }
    return deferred_count;
}

pub fn prepare_aquifer_cell_avx2(density: []const c.jdouble, candidate_values: []c.jlong, packed_locations: []const c.jshort, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: []c.jint, materials: []c.jbyte) ?usize {
    return prepare_aquifer_cell_impl(true, false, density, candidate_values, packed_locations, fluid_levels, fluid_types, global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred_indices, materials);
}

fn classify_aquifer_cell(density: []const c.jdouble, global_materials: []const c.jbyte, candidate_values: []const c.jlong, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, base_y: c.jint, width: usize, height: usize, output: []c.jbyte) bool {
    var y_index: usize = 0;
    while (y_index < height) : (y_index += 1) {
        const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
        var x_index: usize = 0;
        while (x_index < width) : (x_index += 1) {
            var z_index: usize = 0;
            while (z_index < width) : (z_index += 1) {
                const index = (y_index * width + x_index) * width + z_index;
                output[index] = 0;
                if (density[index] > 0.0) {
                    continue;
                }
                const global_material: u8 = @bitCast(global_materials[index]);
                if (global_material == 3) {
                    continue;
                }
                if (global_material < 1 or global_material > 3) {
                    return false;
                }

                const candidates = unpack_aquifer_candidates(candidate_values[index * 2], candidate_values[index * 2 + 1]) orelse return false;
                const nearest_type = aquifer_type_at(fluid_levels[candidates.nearest_index], fluid_types[candidates.nearest_index], y) orelse return false;
                const second_type = aquifer_type_at(fluid_levels[candidates.second_index], fluid_types[candidates.second_index], y) orelse return false;
                const third_type = aquifer_type_at(fluid_levels[candidates.third_index], fluid_types[candidates.third_index], y) orelse return false;
                const nearest_similarity = aquifer_similarity(candidates.nearest_distance, candidates.second_distance);
                if (nearest_similarity <= 0.0) {
                    continue;
                }

                var requirements: u8 = 0;
                if (nearest_type == 2) {
                    requirements |= 2;
                }
                if (aquifer_pressure_uses_barrier(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type)) {
                    output[index] = @intCast(requirements | 1);
                    continue;
                }
                if (density[index] + nearest_similarity * aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type, 0.0) > 0.0) {
                    output[index] = @intCast(requirements);
                    continue;
                }

                const nearest_third_similarity = aquifer_similarity(candidates.nearest_distance, candidates.third_distance);
                if (nearest_third_similarity > 0.0) {
                    if (aquifer_pressure_uses_barrier(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type)) {
                        output[index] = @intCast(requirements | 1);
                        continue;
                    }
                    if (density[index] + nearest_similarity * nearest_third_similarity * aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type, 0.0) > 0.0) {
                        output[index] = @intCast(requirements);
                        continue;
                    }
                }

                const second_third_similarity = aquifer_similarity(candidates.second_distance, candidates.third_distance);
                if (second_third_similarity > 0.0 and aquifer_pressure_uses_barrier(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type)) {
                    requirements |= 1;
                }
                output[index] = @intCast(requirements);
            }
        }
    }
    return true;
}

fn pack_aquifer_candidates(output: []c.jlong, candidates: AquiferCandidates) ?void {
    if (output.len != 2 or candidates.nearest_index > 0xFFF or candidates.second_index > 0xFFF or candidates.third_index > 0xFFF
        or candidates.nearest_distance < 0 or candidates.second_distance < 0 or candidates.third_distance < 0
        or candidates.nearest_distance > std.math.maxInt(u16) or candidates.second_distance > std.math.maxInt(u16) or candidates.third_distance > std.math.maxInt(u16)) {
        return null;
    }
    const indices: u64 = @as(u64, @intCast(candidates.nearest_index))
        | @as(u64, @intCast(candidates.second_index)) << 12
        | @as(u64, @intCast(candidates.third_index)) << 24;
    const distances: u64 = @as(u64, @intCast(candidates.nearest_distance))
        | @as(u64, @intCast(candidates.second_distance)) << 16
        | @as(u64, @intCast(candidates.third_distance)) << 32;
    output[0] = @bitCast(indices);
    output[1] = @bitCast(distances);
}

fn unpack_aquifer_candidates(indices_value: c.jlong, distances_value: c.jlong) ?AquiferCandidates {
    const indices: u64 = @bitCast(indices_value);
    const distances: u64 = @bitCast(distances_value);
    return .{
        .nearest_index = @intCast(indices & 0xFFF),
        .second_index = @intCast((indices >> 12) & 0xFFF),
        .third_index = @intCast((indices >> 24) & 0xFFF),
        .nearest_distance = @intCast(distances & 0xFFFF),
        .second_distance = @intCast((distances >> 16) & 0xFFFF),
        .third_distance = @intCast((distances >> 32) & 0xFFFF),
    };
}

const AquiferCandidates = struct {
    nearest_distance: i32,
    second_distance: i32,
    third_distance: i32,
    nearest_index: usize,
    second_index: usize,
    third_index: usize,
};

const AquiferSearch = struct {
    grid_z: i32,
    grid_y: i32,
    y: i32,
    indices: [12]usize,
    distances: [12]i32,
    delta_z: [12]i32,
    delta_y: [12]i32,

    fn advanceZ(self: *AquiferSearch) void {
        for (&self.distances, &self.delta_z) |*distance, *delta_z| {
            const previous_delta_z = delta_z.*;
            delta_z.* = previous_delta_z - 1;
            distance.* += -2 * previous_delta_z + 1;
        }
    }

    fn advanceYDown(self: *AquiferSearch, y: i32) bool {
        const steps = self.y - y;
        if (steps <= 0) {
            return false;
        }
        for (&self.distances, &self.delta_y) |*distance, *delta_y| {
            const previous_delta_y = delta_y.*;
            delta_y.* = previous_delta_y + steps;
            distance.* += 2 * previous_delta_y * steps + steps * steps;
        }
        self.y = y;
        return true;
    }

    fn candidates(self: *const AquiferSearch) AquiferCandidates {
        var nearest_distance: i32 = std.math.maxInt(i32);
        var second_distance: i32 = std.math.maxInt(i32);
        var third_distance: i32 = std.math.maxInt(i32);
        var nearest_order: i32 = -1;
        var second_order: i32 = -1;
        var third_order: i32 = -1;
        var nearest_index: usize = 0;
        var second_index: usize = 0;
        var third_index: usize = 0;
        for (self.distances, self.indices, 0..) |distance, index, order_value| {
            const order: i32 = @intCast(order_value);
            if (distance < nearest_distance or (distance == nearest_distance and order > nearest_order)) {
                second_distance = nearest_distance;
                second_order = nearest_order;
                second_index = nearest_index;
                nearest_distance = distance;
                nearest_order = order;
                nearest_index = index;
            } else if (distance < second_distance or (distance == second_distance and order > second_order)) {
                third_distance = second_distance;
                third_order = second_order;
                third_index = second_index;
                second_distance = distance;
                second_order = order;
                second_index = index;
            } else if (distance < third_distance or (distance == third_distance and order > third_order)) {
                third_distance = distance;
                third_order = order;
                third_index = index;
            }
        }
        return .{
            .nearest_distance = nearest_distance,
            .second_distance = second_distance,
            .third_distance = third_distance,
            .nearest_index = nearest_index,
            .second_index = second_index,
            .third_index = third_index,
        };
    }
};

fn advance_y_down(comptime avx2: bool, comptime neon: bool, search: *AquiferSearch, y: i32) bool {
    const steps = search.y - y;
    if (steps <= 0) {
        return false;
    }
    if (comptime avx2) {
        const step8: Vec8i = @splat(steps);
        const step4: Vec4i = @splat(steps);
        const two8: Vec8i = @splat(2);
        const two4: Vec4i = @splat(2);
        const squared8: Vec8i = @splat(steps * steps);
        const squared4: Vec4i = @splat(steps * steps);
        const previous8: Vec8i = @bitCast(search.delta_y[0..8].*);
        const previous4: Vec4i = @bitCast(search.delta_y[8..12].*);
        const distance8: Vec8i = @bitCast(search.distances[0..8].*);
        const distance4: Vec4i = @bitCast(search.distances[8..12].*);
        search.delta_y[0..8].* = @bitCast(previous8 + step8);
        search.delta_y[8..12].* = @bitCast(previous4 + step4);
        search.distances[0..8].* = @bitCast(distance8 + two8 * previous8 * step8 + squared8);
        search.distances[8..12].* = @bitCast(distance4 + two4 * previous4 * step4 + squared4);
        search.y = y;
        return true;
    }
    if (comptime neon) {
        const step: Vec4i = @splat(steps);
        const two: Vec4i = @splat(2);
        const square: Vec4i = @splat(steps * steps);
        inline for (.{ 0, 4, 8 }) |index| {
            const previous: Vec4i = @bitCast(search.delta_y[index .. index + 4].*);
            const distance: Vec4i = @bitCast(search.distances[index .. index + 4].*);
            search.distances[index .. index + 4].* = @bitCast(distance + two * previous * step + square);
            search.delta_y[index .. index + 4].* = @bitCast(previous + step);
        }
        search.y = y;
        return true;
    }
    return search.advanceYDown(y);
}

fn aquifer_search(packed_locations: []const c.jshort, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, x: i32, y: i32, z: i32) ?AquiferSearch {
    const grid_x = @divFloor(x - 5, 16);
    const grid_y = @divFloor(y + 1, 12);
    const grid_z = @divFloor(z - 5, 16);
    const local_grid_x = grid_x - min_grid_x;
    const local_grid_y = grid_y - min_grid_y;
    const local_grid_z = grid_z - min_grid_z;
    if (local_grid_x < 0 or local_grid_y < 1 or local_grid_z < 0
        or local_grid_x + 1 >= grid_size_x or local_grid_z + 1 >= grid_size_z) {
        return null;
    }
    const size_x: usize = @intCast(grid_size_x);
    const size_z: usize = @intCast(grid_size_z);
    var result: AquiferSearch = undefined;
    result.grid_z = grid_z;
    result.grid_y = grid_y;
    result.y = y;
    var order: usize = 0;
    var offset_x: i32 = 0;
    while (offset_x <= 1) : (offset_x += 1) {
        const center_grid_x = grid_x + offset_x;
        var offset_y: i32 = -1;
        while (offset_y <= 1) : (offset_y += 1) {
            const center_grid_y = grid_y + offset_y;
            const local_y = local_grid_y + offset_y;
            if (local_y < 0) {
                return null;
            }
            const row: usize = (@as(usize, @intCast(local_y)) * size_z + @as(usize, @intCast(local_grid_z))) * size_x + @as(usize, @intCast(local_grid_x + offset_x));
            var offset_z: i32 = 0;
            while (offset_z <= 1) : (offset_z += 1) {
                const cache_index = row + @as(usize, @intCast(offset_z)) * size_x;
                if (cache_index >= packed_locations.len) {
                    return null;
                }
                const packed_value: u16 = @bitCast(packed_locations[cache_index]);
                const center_x = center_grid_x * 16 + @as(i32, @intCast(packed_value >> 8));
                const center_y = center_grid_y * 12 + @as(i32, @intCast((packed_value >> 4) & 15));
                const center_z = (grid_z + offset_z) * 16 + @as(i32, @intCast(packed_value & 15));
                const delta_x = center_x - x;
                const delta_y = center_y - y;
                const delta_z = center_z - z;
                const distance = delta_x * delta_x + delta_y * delta_y + delta_z * delta_z;
                result.indices[order] = cache_index;
                result.distances[order] = distance;
                result.delta_z[order] = delta_z;
                result.delta_y[order] = delta_y;
                order += 1;
            }
        }
    }
    return result;
}

fn aquifer_type_at(level: c.jint, fluid_type: c.jbyte, y: i32) ?u8 {
    const type_code: u8 = @bitCast(fluid_type);
    if (type_code != 2 and type_code != 3) {
        return null;
    }
    return if (y >= level) 1 else type_code;
}

fn global_aquifer_material(y: i32, fluid_level: c.jint, fluid_type: c.jbyte) ?u8 {
    const type_code: u8 = @bitCast(fluid_type);
    if (type_code != 2 and type_code != 3) {
        return null;
    }
    const lava_level = @min(@as(i32, -54), fluid_level);
    if (y < lava_level) {
        return 3;
    }
    return if (y < fluid_level) type_code else 1;
}

fn aquifer_similarity(first_distance: i32, second_distance: i32) f64 {
    const difference: i64 = @as(i64, second_distance) - @as(i64, first_distance);
    const absolute_difference = if (difference < 0) -difference else difference;
    return 1.0 - @as(f64, @floatFromInt(absolute_difference)) / 25.0;
}

fn aquifer_pressure_uses_barrier(y: i32, first_level: c.jint, first_type: u8, second_level: c.jint, second_type: u8) bool {
    if ((first_type == 3 and second_type == 2) or (first_type == 2 and second_type == 3)) {
        return false;
    }
    const level_difference: i64 = @as(i64, first_level) - @as(i64, second_level);
    const absolute_level_difference: i64 = if (level_difference < 0) -level_difference else level_difference;
    if (absolute_level_difference == 0) {
        return false;
    }
    const average_level = 0.5 * @as(f64, @floatFromInt(@as(i64, first_level) + @as(i64, second_level)));
    const delta_y = @as(f64, @floatFromInt(y)) + 0.5 - average_level;
    const boundary = @as(f64, @floatFromInt(absolute_level_difference)) / 2.0 - @abs(delta_y);
    const pressure = if (delta_y > 0.0)
        if (boundary > 0.0) boundary / 1.5 else boundary / 2.5
    else
        if (3.0 + boundary > 0.0) (3.0 + boundary) / 3.0 else (3.0 + boundary) / 10.0;
    return pressure >= -2.0 and pressure <= 2.0;
}

fn aquifer_pressure(y: i32, first_level: c.jint, first_type: u8, second_level: c.jint, second_type: u8, barrier: c.jdouble) f64 {
    if ((first_type == 3 and second_type == 2) or (first_type == 2 and second_type == 3)) {
        return 2.0;
    }
    const level_difference: i64 = @as(i64, first_level) - @as(i64, second_level);
    const absolute_level_difference: i64 = if (level_difference < 0) -level_difference else level_difference;
    if (absolute_level_difference == 0) {
        return 0.0;
    }
    const average_level = 0.5 * @as(f64, @floatFromInt(@as(i64, first_level) + @as(i64, second_level)));
    const delta_y = @as(f64, @floatFromInt(y)) + 0.5 - average_level;
    const boundary = @as(f64, @floatFromInt(absolute_level_difference)) / 2.0 - @abs(delta_y);
    const pressure = if (delta_y > 0.0)
        if (boundary > 0.0) boundary / 1.5 else boundary / 2.5
    else
        if (3.0 + boundary > 0.0) (3.0 + boundary) / 3.0 else (3.0 + boundary) / 10.0;
    const barrier_value = if (pressure < -2.0 or pressure > 2.0) 0.0 else barrier;
    return 2.0 * (barrier_value + pressure);
}

fn evaluate_density_program(program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: []c.jdouble) bool {
    var stack: [max_density_stack][max_density_values]f64 = undefined;
    var stack_size: usize = 0;
    var program_counter: usize = 0;
    const total = output.len;
    while (program_counter < program.len) {
        const opcode = read_program_u8(program, &program_counter) orelse return false;
        switch (opcode) {
            0 => {
                if (stack_size != 1 or program_counter != program.len) {
                    return false;
                }
                var y_index: usize = 0;
                while (y_index < height) : (y_index += 1) {
                    const output_y = height - 1 - y_index;
                    var x_index: usize = 0;
                    while (x_index < width) : (x_index += 1) {
                        var z_index: usize = 0;
                        while (z_index < width) : (z_index += 1) {
                            output[(output_y * width + x_index) * width + z_index] = stack[0][(x_index * width + z_index) * height + y_index];
                        }
                    }
                }
                return true;
            },
            1 => {
                if (stack_size >= max_density_stack) {
                    return false;
                }
                const first_address = read_program_u64(program, &program_counter) orelse return false;
                const first_octaves = read_program_u32(program, &program_counter) orelse return false;
                const second_address = read_program_u64(program, &program_counter) orelse return false;
                const second_octaves = read_program_u32(program, &program_counter) orelse return false;
                const value_factor = read_program_f64(program, &program_counter) orelse return false;
                const xz_scale = read_program_f64(program, &program_counter) orelse return false;
                const y_scale = read_program_f64(program, &program_counter) orelse return false;
                if (first_address == 0 or second_address == 0 or first_octaves > max_octaves or second_octaves > max_octaves) {
                    return false;
                }
                const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
                const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
                density_normal_noise_grid(first, first_octaves, second, second_octaves, value_factor, @as(f64, @floatFromInt(base_x)) * xz_scale, @as(f64, @floatFromInt(base_y)) * y_scale, @as(f64, @floatFromInt(base_z)) * xz_scale, xz_scale, y_scale, width, height, stack[stack_size][0..total]);
                stack_size += 1;
            },
            2 => {
                if (stack_size >= max_density_stack) {
                    return false;
                }
                const value = read_program_f64(program, &program_counter) orelse return false;
                for (stack[stack_size][0..total]) |*entry| {
                    entry.* = value;
                }
                stack_size += 1;
            },
            3 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* += right;
                }
                stack_size -= 1;
            },
            4 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* *= right;
                }
                stack_size -= 1;
            },
            5 => {
                if (stack_size == 0) {
                    return false;
                }
                const minimum = read_program_f64(program, &program_counter) orelse return false;
                const maximum = read_program_f64(program, &program_counter) orelse return false;
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* = density_clamp(entry.*, minimum, maximum);
                }
            },
            6 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* = @abs(entry.*);
                }
            },
            7 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* *= entry.*;
                }
            },
            8 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* = entry.* * entry.* * entry.*;
                }
            },
            9 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    if (entry.* <= 0.0) {
                        entry.* *= 0.5;
                    }
                }
            },
            10 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    if (entry.* <= 0.0) {
                        entry.* *= 0.25;
                    }
                }
            },
            11 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    const value = density_clamp(entry.*, -1.0, 1.0);
                    entry.* = value / 2.0 - value * value * value / 24.0;
                }
            },
            12 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* = @min(left.*, right);
                }
                stack_size -= 1;
            },
            13 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* = @max(left.*, right);
                }
                stack_size -= 1;
            },
            14 => {
                if (stack_size < 3) {
                    return false;
                }
                const minimum = read_program_f64(program, &program_counter) orelse return false;
                const maximum = read_program_f64(program, &program_counter) orelse return false;
                for (stack[stack_size - 3][0..total], stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*input, inside, outside| {
                    input.* = if (input.* >= minimum and input.* < maximum) inside else outside;
                }
                stack_size -= 2;
            },
            15 => {
                if (stack_size >= max_density_stack) {
                    return false;
                }
                const noise000 = read_program_f64(program, &program_counter) orelse return false;
                const noise001 = read_program_f64(program, &program_counter) orelse return false;
                const noise100 = read_program_f64(program, &program_counter) orelse return false;
                const noise101 = read_program_f64(program, &program_counter) orelse return false;
                const noise010 = read_program_f64(program, &program_counter) orelse return false;
                const noise011 = read_program_f64(program, &program_counter) orelse return false;
                const noise110 = read_program_f64(program, &program_counter) orelse return false;
                const noise111 = read_program_f64(program, &program_counter) orelse return false;
                var y_index: usize = 0;
                while (y_index < height) : (y_index += 1) {
                    const y_lerp = @as(f64, @floatFromInt(y_index)) / @as(f64, @floatFromInt(height));
                    var x_index: usize = 0;
                    while (x_index < width) : (x_index += 1) {
                        const x_lerp = @as(f64, @floatFromInt(x_index)) / @as(f64, @floatFromInt(width));
                        var z_index: usize = 0;
                        while (z_index < width) : (z_index += 1) {
                            const z_lerp = @as(f64, @floatFromInt(z_index)) / @as(f64, @floatFromInt(width));
                            stack[stack_size][(x_index * width + z_index) * height + y_index] = density_lerp3(x_lerp, y_lerp, z_lerp, noise000, noise100, noise010, noise110, noise001, noise101, noise011, noise111);
                        }
                    }
                }
                stack_size += 1;
            },
            else => return false,
        }
    }
    return false;
}

fn density_normal_noise_grid(first: [*]const u8, first_octaves: u32, second: [*]const u8, second_octaves: u32, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, width: usize, height: usize, values: []f64) void {
    if (comptime builtin.cpu.arch == .x86_64) {
        if (adrenaline_has_avx2() != 0) {
            adrenaline_normal_noise_grid_avx2(first, first_octaves, second, second_octaves, value_factor, x, y, z, x_step, y_step, x_step, width, height, width, values.ptr);
            return;
        }
    }
    normal_noise_grid(first, first_octaves, second, second_octaves, value_factor, x, y, z, x_step, y_step, x_step, width, height, width, values);
}

fn density_clamp(value: f64, minimum: f64, maximum: f64) f64 {
    return @max(minimum, @min(maximum, value));
}

fn density_lerp3(x: f64, y: f64, z: f64, noise000: f64, noise100: f64, noise010: f64, noise110: f64, noise001: f64, noise101: f64, noise011: f64, noise111: f64) f64 {
    const value_xz00 = noise000 + y * (noise010 - noise000);
    const value_xz10 = noise100 + y * (noise110 - noise100);
    const value_xz01 = noise001 + y * (noise011 - noise001);
    const value_xz11 = noise101 + y * (noise111 - noise101);
    const value_z0 = value_xz00 + x * (value_xz10 - value_xz00);
    const value_z1 = value_xz01 + x * (value_xz11 - value_xz01);
    return value_z0 + z * (value_z1 - value_z0);
}

fn read_program_u8(program: []const u8, program_counter: *usize) ?u8 {
    if (program_counter.* >= program.len) {
        return null;
    }
    const value = program[program_counter.*];
    program_counter.* += 1;
    return value;
}

fn read_program_u32(program: []const u8, program_counter: *usize) ?u32 {
    if (program.len -| program_counter.* < 4) {
        return null;
    }
    const start = program_counter.*;
    program_counter.* += 4;
    return @as(u32, program[start]) | @as(u32, program[start + 1]) << 8 | @as(u32, program[start + 2]) << 16 | @as(u32, program[start + 3]) << 24;
}

fn read_program_u64(program: []const u8, program_counter: *usize) ?u64 {
    if (program.len -| program_counter.* < 8) {
        return null;
    }
    const start = program_counter.*;
    program_counter.* += 8;
    var value: u64 = 0;
    inline for (0..8) |index| {
        value |= @as(u64, program[start + index]) << @as(u6, @intCast(index * 8));
    }
    return value;
}

fn read_program_f64(program: []const u8, program_counter: *usize) ?f64 {
    const bits = read_program_u64(program, program_counter) orelse return null;
    return @bitCast(bits);
}

fn normal_noise_grid(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []c.jdouble) void {
    normal_noise_grid_impl(false, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    normal_noise_grid_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

fn normal_noise_grid_impl(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    var index: usize = 0;
    while (index < values.len) : (index += 1) {
        values[index] = 0.0;
    }
    perlin_value_grid(avx2, first, first_count, x, y, z, x_step, y_step, z_step, 1.0, x_count, y_count, z_count, values);
    perlin_value_grid(avx2, second, second_count, x, y, z, x_step, y_step, z_step, normal_noise_input_factor, x_count, y_count, z_count, values);
    index = 0;
    while (index < values.len) : (index += 1) {
        values[index] *= value_factor;
    }
}

fn normal_noise_grid_approx(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    normal_noise_grid_approx_impl(false, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_approx_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    normal_noise_grid_approx_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

fn normal_noise_grid_approx_float(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    normal_noise_grid_approx_float_impl(false, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_approx_float_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    normal_noise_grid_approx_float_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

fn normal_noise_grid_approx_impl(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    const total = x_count * y_count * z_count;
    var float_values: [max_grid_size]f32 = undefined;
    const output = float_values[0..total];
    normal_noise_grid_approx_float_impl(avx2, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, output);
    var index: usize = 0;
    while (index < total) : (index += 1) {
        values[index] = @floatCast(output[index]);
    }
}

fn normal_noise_grid_approx_float_impl(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    var index: usize = 0;
    while (index < values.len) : (index += 1) {
        values[index] = 0.0;
    }
    perlin_value_grid_approx(avx2, first, first_count, @floatCast(x), @floatCast(y), @floatCast(z), @floatCast(x_step), @floatCast(y_step), @floatCast(z_step), 1.0, x_count, y_count, z_count, values);
    perlin_value_grid_approx(avx2, second, second_count, @floatCast(x), @floatCast(y), @floatCast(z), @floatCast(x_step), @floatCast(y_step), @floatCast(z_step), @floatCast(normal_noise_input_factor), x_count, y_count, z_count, values);
    const factor: f32 = @floatCast(value_factor);
    index = 0;
    while (index < values.len) : (index += 1) {
        values[index] *= factor;
    }
}

fn perlin_value_grid_approx(comptime avx2: bool, data: [*]const u8, octaves: usize, x: f32, y: f32, z: f32, x_step: f32, y_step: f32, z_step: f32, coordinate_scale: f32, x_count: usize, y_count: usize, z_count: usize, values: []f32) void {
    if (x_count > max_grid_axis or y_count > max_grid_axis or z_count > max_grid_axis) {
        return;
    }
    const lowest_value_factor: f32 = @floatCast(read_f64(data, 0));
    const lowest_input_factor: f32 = @floatCast(read_f64(data, 8));
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    var integer_x: [max_grid_axis]i32 = undefined;
    var integer_y: [max_grid_axis]i32 = undefined;
    var integer_z: [max_grid_axis]i32 = undefined;
    var fraction_x: [max_grid_axis]f32 = undefined;
    var fraction_y: [max_grid_axis]f32 = undefined;
    var fraction_z: [max_grid_axis]f32 = undefined;
    var smooth_x: [max_grid_axis]f32 = undefined;
    var smooth_y: [max_grid_axis]f32 = undefined;
    var smooth_z: [max_grid_axis]f32 = undefined;
    var input_factor = lowest_input_factor;
    var octave_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude: f32 = @floatCast(read_f64(data, amplitude_offset + octave * 8));
            const origin = origin_offset + octave * 24;
            const seed = quick_seed(data, origin, octave);
            prepare_grid_axis_approx(x, x_step, x_count, coordinate_scale, input_factor, integer_x[0..x_count], fraction_x[0..x_count], smooth_x[0..x_count]);
            prepare_grid_axis_approx(y, y_step, y_count, coordinate_scale, input_factor, integer_y[0..y_count], fraction_y[0..y_count], smooth_y[0..y_count]);
            prepare_grid_axis_approx(z, z_step, z_count, coordinate_scale, input_factor, integer_z[0..z_count], fraction_z[0..z_count], smooth_z[0..z_count]);
            var x_start: usize = 0;
            while (x_start < x_count) {
                var x_end = x_start + 1;
                while (x_end < x_count and integer_x[x_end] == integer_x[x_start]) : (x_end += 1) {}
                var z_start: usize = 0;
                while (z_start < z_count) {
                    var z_end = z_start + 1;
                    while (z_end < z_count and integer_z[z_end] == integer_z[z_start]) : (z_end += 1) {}
                    var y_start: usize = 0;
                    while (y_start < y_count) {
                        var y_end = y_start + 1;
                        while (y_end < y_count and integer_y[y_end] == integer_y[y_start]) : (y_end += 1) {}
                        const gradients = QuickGradientCell.init(seed, integer_x[x_start], integer_y[y_start], integer_z[z_start]);
                        var x_index = x_start;
                        while (x_index < x_end) : (x_index += 1) {
                            var z_index = z_start;
                            while (z_index < z_end) : (z_index += 1) {
                                const offset = (x_index * z_count + z_index) * y_count;
                                const line = gradients.line(fraction_x[x_index], fraction_z[z_index]);
                                var y_index = y_start;
                                if (comptime avx2) {
                                    while (y_index + 7 < y_end) : (y_index += 8) {
                                        const sampled = line.sample8(.{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3], fraction_y[y_index + 4], fraction_y[y_index + 5], fraction_y[y_index + 6], fraction_y[y_index + 7] }, smooth_x[x_index], .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3], smooth_y[y_index + 4], smooth_y[y_index + 5], smooth_y[y_index + 6], smooth_y[y_index + 7] }, smooth_z[z_index]);
                                        inline for (0..8) |lane| {
                                            values[offset + y_index + lane] += amplitude * sampled[lane] * octave_factor;
                                        }
                                    }
                                }
                                while (y_index + 3 < y_end) : (y_index += 4) {
                                    const sampled = line.sample4(.{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3] }, smooth_x[x_index], .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3] }, smooth_z[z_index]);
                                    inline for (0..4) |lane| {
                                        values[offset + y_index + lane] += amplitude * sampled[lane] * octave_factor;
                                    }
                                }
                                while (y_index < y_end) : (y_index += 1) {
                                    values[offset + y_index] += amplitude * line.sample(fraction_y[y_index], smooth_x[x_index], smooth_y[y_index], smooth_z[z_index]) * octave_factor;
                                }
                            }
                        }
                        y_start = y_end;
                    }
                    z_start = z_end;
                }
                x_start = x_end;
            }
        }
        input_factor *= 2.0;
        octave_factor *= 0.5;
    }
}

fn prepare_grid_axis_approx(base: f32, step: f32, count: usize, coordinate_scale: f32, input_factor: f32, integers: []i32, fractions: []f32, smooth: []f32) void {
    var index: usize = 0;
    while (index < count) : (index += 1) {
        const sample = base + @as(f32, @floatFromInt(index)) * step;
        const shifted = wrap_approx(sample * coordinate_scale * input_factor);
        const integer: i32 = @intFromFloat(@floor(shifted));
        const fraction = shifted - @as(f32, @floatFromInt(integer));
        integers[index] = integer;
        fractions[index] = fraction;
        smooth[index] = smoothstep_approx(fraction);
    }
}

const QuickGradientCell = struct {
    values: [8]u32,

    fn init(seed: u32, x: i32, y: i32, z: i32) QuickGradientCell {
        return .{ .values = .{
            quick_hash(seed, x, y, z), quick_hash(seed, x +% 1, y, z),
            quick_hash(seed, x, y +% 1, z), quick_hash(seed, x +% 1, y +% 1, z),
            quick_hash(seed, x, y, z +% 1), quick_hash(seed, x +% 1, y, z +% 1),
            quick_hash(seed, x, y +% 1, z +% 1), quick_hash(seed, x +% 1, y +% 1, z +% 1),
        } };
    }

    fn line(self: QuickGradientCell, x: f32, z: f32) QuickGradientLine {
        return .{ .values = .{
            quick_gradient_term(self.values[0], x, z, 0.0), quick_gradient_term(self.values[1], x - 1.0, z, 0.0),
            quick_gradient_term(self.values[2], x, z, -1.0), quick_gradient_term(self.values[3], x - 1.0, z, -1.0),
            quick_gradient_term(self.values[4], x, z - 1.0, 0.0), quick_gradient_term(self.values[5], x - 1.0, z - 1.0, 0.0),
            quick_gradient_term(self.values[6], x, z - 1.0, -1.0), quick_gradient_term(self.values[7], x - 1.0, z - 1.0, -1.0),
        } };
    }
};

const QuickGradientTerm = struct {
    base: f32,
    y_offset: f32,
    mode: enum { none, add, subtract },

    fn sample(self: QuickGradientTerm, y: f32) f32 {
        return switch (self.mode) {
            .none => self.base,
            .add => self.base + y + self.y_offset,
            .subtract => self.base - y - self.y_offset,
        };
    }

    fn sample4(self: QuickGradientTerm, y: Vec4f) Vec4f {
        const base: Vec4f = @splat(self.base);
        const offset: Vec4f = @splat(self.y_offset);
        return switch (self.mode) {
            .none => base,
            .add => base + y + offset,
            .subtract => base - y - offset,
        };
    }

    fn sample8(self: QuickGradientTerm, y: Vec8f) Vec8f {
        const base: Vec8f = @splat(self.base);
        const offset: Vec8f = @splat(self.y_offset);
        return switch (self.mode) {
            .none => base,
            .add => base + y + offset,
            .subtract => base - y - offset,
        };
    }
};

const QuickGradientLine = struct {
    values: [8]QuickGradientTerm,

    fn sample(self: QuickGradientLine, y: f32, x_fade: f32, y_fade: f32, z_fade: f32) f32 {
        return lerp3_approx(x_fade, y_fade, z_fade, self.values[0].sample(y), self.values[1].sample(y), self.values[2].sample(y), self.values[3].sample(y), self.values[4].sample(y), self.values[5].sample(y), self.values[6].sample(y), self.values[7].sample(y));
    }

    fn sample4(self: QuickGradientLine, y: Vec4f, x_fade: f32, y_fade: Vec4f, z_fade: f32) Vec4f {
        return lerp3_approx4(@splat(x_fade), y_fade, @splat(z_fade), self.values[0].sample4(y), self.values[1].sample4(y), self.values[2].sample4(y), self.values[3].sample4(y), self.values[4].sample4(y), self.values[5].sample4(y), self.values[6].sample4(y), self.values[7].sample4(y));
    }

    fn sample8(self: QuickGradientLine, y: Vec8f, x_fade: f32, y_fade: Vec8f, z_fade: f32) Vec8f {
        return lerp3_approx8(@splat(x_fade), y_fade, @splat(z_fade), self.values[0].sample8(y), self.values[1].sample8(y), self.values[2].sample8(y), self.values[3].sample8(y), self.values[4].sample8(y), self.values[5].sample8(y), self.values[6].sample8(y), self.values[7].sample8(y));
    }
};

fn quick_seed(data: [*]const u8, origin: usize, octave: usize) u32 {
    const first: u64 = @bitCast(read_f64(data, origin));
    const second: u64 = @bitCast(read_f64(data, origin + 8));
    const third: u64 = @bitCast(read_f64(data, origin + 16));
    return quick_mix(@as(u32, @truncate(first)) ^ @as(u32, @truncate(first >> 32)) ^ @as(u32, @truncate(second)) ^ @as(u32, @truncate(second >> 32)) ^ @as(u32, @truncate(third)) ^ @as(u32, @truncate(third >> 32)) ^ @as(u32, @intCast(octave)) *% 0x9e3779b9);
}

fn quick_hash(seed: u32, x: i32, y: i32, z: i32) u32 {
    const x_bits: u32 = @bitCast(x);
    const y_bits: u32 = @bitCast(y);
    const z_bits: u32 = @bitCast(z);
    return quick_mix(seed ^ (x_bits *% 0x85ebca6b) ^ (y_bits *% 0xc2b2ae35) ^ (z_bits *% 0x27d4eb2f));
}

fn quick_mix(value: u32) u32 {
    var mixed = value;
    mixed ^= mixed >> 16;
    mixed *%= 0x7feb352d;
    mixed ^= mixed >> 15;
    mixed *%= 0x846ca68b;
    mixed ^= mixed >> 16;
    return mixed;
}

fn quick_gradient_term(value: u32, x: f32, z: f32, y_offset: f32) QuickGradientTerm {
    return switch (value & 15) {
        0 => .{ .base = x, .y_offset = y_offset, .mode = .add },
        1 => .{ .base = -x, .y_offset = y_offset, .mode = .add },
        2 => .{ .base = x, .y_offset = y_offset, .mode = .subtract },
        3 => .{ .base = -x, .y_offset = y_offset, .mode = .subtract },
        4 => .{ .base = x + z, .y_offset = y_offset, .mode = .none },
        5 => .{ .base = -x + z, .y_offset = y_offset, .mode = .none },
        6 => .{ .base = x - z, .y_offset = y_offset, .mode = .none },
        7 => .{ .base = -x - z, .y_offset = y_offset, .mode = .none },
        8 => .{ .base = z, .y_offset = y_offset, .mode = .add },
        9 => .{ .base = z, .y_offset = y_offset, .mode = .subtract },
        10 => .{ .base = -z, .y_offset = y_offset, .mode = .add },
        11 => .{ .base = -z, .y_offset = y_offset, .mode = .subtract },
        12 => .{ .base = x, .y_offset = y_offset, .mode = .add },
        13 => .{ .base = z, .y_offset = y_offset, .mode = .subtract },
        14 => .{ .base = -x, .y_offset = y_offset, .mode = .add },
        else => .{ .base = -z, .y_offset = y_offset, .mode = .subtract },
    };
}

fn wrap_approx(value: f32) f32 {
    return value - @floor(value / 33554432.0 + 0.5) * 33554432.0;
}

fn smoothstep_approx(value: f32) f32 {
    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
}

fn lerp_approx(delta: f32, start: f32, end: f32) f32 {
    return start + delta * (end - start);
}

fn lerp3_approx(x: f32, y: f32, z: f32, value000: f32, value100: f32, value010: f32, value110: f32, value001: f32, value101: f32, value011: f32, value111: f32) f32 {
    return lerp_approx(z, lerp_approx(y, lerp_approx(x, value000, value100), lerp_approx(x, value010, value110)), lerp_approx(y, lerp_approx(x, value001, value101), lerp_approx(x, value011, value111)));
}

fn lerp_approx4(delta: Vec4f, start: Vec4f, end: Vec4f) Vec4f {
    return start + delta * (end - start);
}

fn lerp3_approx4(x: Vec4f, y: Vec4f, z: Vec4f, value000: Vec4f, value100: Vec4f, value010: Vec4f, value110: Vec4f, value001: Vec4f, value101: Vec4f, value011: Vec4f, value111: Vec4f) Vec4f {
    return lerp_approx4(z, lerp_approx4(y, lerp_approx4(x, value000, value100), lerp_approx4(x, value010, value110)), lerp_approx4(y, lerp_approx4(x, value001, value101), lerp_approx4(x, value011, value111)));
}

fn lerp_approx8(delta: Vec8f, start: Vec8f, end: Vec8f) Vec8f {
    return start + delta * (end - start);
}

fn lerp3_approx8(x: Vec8f, y: Vec8f, z: Vec8f, value000: Vec8f, value100: Vec8f, value010: Vec8f, value110: Vec8f, value001: Vec8f, value101: Vec8f, value011: Vec8f, value111: Vec8f) Vec8f {
    return lerp_approx8(z, lerp_approx8(y, lerp_approx8(x, value000, value100), lerp_approx8(x, value010, value110)), lerp_approx8(y, lerp_approx8(x, value001, value101), lerp_approx8(x, value011, value111)));
}

fn normal_noise_batch(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, values: []c.jdouble) void {
    var index: usize = 0;
    while (index < values.len) : (index += 1) {
        values[index] = 0.0;
    }
    perlin_value_batch(first, first_count, x, y, z, y_step, 1.0, values);
    perlin_value_batch(second, second_count, x, y, z, y_step, normal_noise_input_factor, values);
    index = 0;
    while (index < values.len) : (index += 1) {
        values[index] *= value_factor;
    }
}

fn perlin_value_batch(data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, y_step: f64, coordinate_scale: f64, values: []c.jdouble) void {
    @setFloatMode(.strict);
    const lowest_value_factor = read_f64(data, 0);
    const lowest_input_factor = read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            const shifted_x = wrap(x * coordinate_scale * input_factor) + read_f64(data, origin);
            const shifted_z = wrap(z * coordinate_scale * input_factor) + read_f64(data, origin + 16);
            const integer_x = floor_int(shifted_x);
            const integer_z = floor_int(shifted_z);
            const fraction_x = shifted_x - @as(f64, @floatFromInt(integer_x));
            const fraction_z = shifted_z - @as(f64, @floatFromInt(integer_z));
            const x_hash = permutation_value(permutation, integer_x);
            const next_x_hash = permutation_value(permutation, integer_x + 1);
            const yo = read_f64(data, origin + 8);
            var index: usize = 0;
            while (index < values.len) : (index += 1) {
                const sample_y = y + @as(f64, @floatFromInt(index)) * y_step;
                const shifted_y = wrap(sample_y * coordinate_scale * input_factor) + yo;
                const integer_y = floor_int(shifted_y);
                var end = index + 1;
                while (end < values.len) : (end += 1) {
                    const next_y = y + @as(f64, @floatFromInt(end)) * y_step;
                    const next_shifted_y = wrap(next_y * coordinate_scale * input_factor) + yo;
                    if (floor_int(next_shifted_y) != integer_y) {
                        break;
                    }
                }
                const gradients = GradientCell.init(permutation, x_hash, next_x_hash, integer_y, integer_z);
                while (index < end) : (index += 1) {
                    const current_y = y + @as(f64, @floatFromInt(index)) * y_step;
                    const current_shifted_y = wrap(current_y * coordinate_scale * input_factor) + yo;
                    const fraction_y = current_shifted_y - @as(f64, @floatFromInt(integer_y));
                    values[index] += amplitude * gradients.sample(fraction_x, fraction_y, fraction_z) * value_factor;
                }
                index -= 1;
            }
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
}

fn perlin_value_grid(comptime avx2: bool, data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, coordinate_scale: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    @setFloatMode(.strict);
    if (x_count > max_grid_axis or y_count > max_grid_axis or z_count > max_grid_axis) {
        var x_index: usize = 0;
        while (x_index < x_count) : (x_index += 1) {
            const sample_x = x + @as(f64, @floatFromInt(x_index)) * x_step;
            var z_index: usize = 0;
            while (z_index < z_count) : (z_index += 1) {
                const sample_z = z + @as(f64, @floatFromInt(z_index)) * z_step;
                const offset = (x_index * z_count + z_index) * y_count;
                perlin_value_batch(data, octaves, sample_x, y, sample_z, y_step, coordinate_scale, values[offset .. offset + y_count]);
            }
        }
        return;
    }
    const lowest_value_factor = read_f64(data, 0);
    const lowest_input_factor = read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var integer_x: [max_grid_axis]i32 = undefined;
    var integer_y: [max_grid_axis]i32 = undefined;
    var integer_z: [max_grid_axis]i32 = undefined;
    var fraction_x: [max_grid_axis]f64 = undefined;
    var fraction_y: [max_grid_axis]f64 = undefined;
    var fraction_z: [max_grid_axis]f64 = undefined;
    var smooth_x: [max_grid_axis]f64 = undefined;
    var smooth_y: [max_grid_axis]f64 = undefined;
    var smooth_z: [max_grid_axis]f64 = undefined;
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            prepare_grid_axis(x, x_step, x_count, coordinate_scale, input_factor, read_f64(data, origin), integer_x[0..x_count], fraction_x[0..x_count], smooth_x[0..x_count]);
            prepare_grid_axis(y, y_step, y_count, coordinate_scale, input_factor, read_f64(data, origin + 8), integer_y[0..y_count], fraction_y[0..y_count], smooth_y[0..y_count]);
            prepare_grid_axis(z, z_step, z_count, coordinate_scale, input_factor, read_f64(data, origin + 16), integer_z[0..z_count], fraction_z[0..z_count], smooth_z[0..z_count]);
            var x_start: usize = 0;
            while (x_start < x_count) {
                var x_end = x_start + 1;
                while (x_end < x_count and integer_x[x_end] == integer_x[x_start]) : (x_end += 1) {}
                const x_hash = permutation_value(permutation, integer_x[x_start]);
                const next_x_hash = permutation_value(permutation, integer_x[x_start] + 1);
                var z_start: usize = 0;
                while (z_start < z_count) {
                    var z_end = z_start + 1;
                    while (z_end < z_count and integer_z[z_end] == integer_z[z_start]) : (z_end += 1) {}
                    var y_start: usize = 0;
                    while (y_start < y_count) {
                        var y_end = y_start + 1;
                        while (y_end < y_count and integer_y[y_end] == integer_y[y_start]) : (y_end += 1) {}
                        const gradients = GradientCell.init(permutation, x_hash, next_x_hash, integer_y[y_start], integer_z[z_start]);
                        var x_index = x_start;
                        while (x_index < x_end) : (x_index += 1) {
                            var z_index = z_start;
                            while (z_index < z_end) : (z_index += 1) {
                                const offset = (x_index * z_count + z_index) * y_count;
                                const line = gradients.line(fraction_x[x_index], fraction_z[z_index]);
                                var y_index = y_start;
                                if (comptime avx2) {
                                    while (y_index + 3 < y_end) : (y_index += 4) {
                                        const sampled = line.sample_smoothed4(
                                            .{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3] },
                                            smooth_x[x_index],
                                            .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3] },
                                            smooth_z[z_index],
                                        );
                                        values[offset + y_index] += amplitude * sampled[0] * value_factor;
                                        values[offset + y_index + 1] += amplitude * sampled[1] * value_factor;
                                        values[offset + y_index + 2] += amplitude * sampled[2] * value_factor;
                                        values[offset + y_index + 3] += amplitude * sampled[3] * value_factor;
                                    }
                                }
                                while (y_index + 1 < y_end) : (y_index += 2) {
                                    const sampled = line.sample_smoothed2(
                                        .{ fraction_y[y_index], fraction_y[y_index + 1] },
                                        smooth_x[x_index],
                                        .{ smooth_y[y_index], smooth_y[y_index + 1] },
                                        smooth_z[z_index],
                                    );
                                    values[offset + y_index] += amplitude * sampled[0] * value_factor;
                                    values[offset + y_index + 1] += amplitude * sampled[1] * value_factor;
                                }
                                while (y_index < y_end) : (y_index += 1) {
                                    values[offset + y_index] += amplitude * line.sample_smoothed(fraction_y[y_index], smooth_x[x_index], smooth_y[y_index], smooth_z[z_index]) * value_factor;
                                }
                            }
                        }
                        y_start = y_end;
                    }
                    z_start = z_end;
                }
                x_start = x_end;
            }
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
}

fn prepare_grid_axis(base: f64, step: f64, count: usize, coordinate_scale: f64, input_factor: f64, origin: f64, integers: []i32, fractions: []f64, smooth: []f64) void {
    var index: usize = 0;
    while (index < count) : (index += 1) {
        const sample = base + @as(f64, @floatFromInt(index)) * step;
        const shifted = wrap(sample * coordinate_scale * input_factor) + origin;
        const integer = floor_int(shifted);
        const fraction = shifted - @as(f64, @floatFromInt(integer));
        integers[index] = integer;
        fractions[index] = fraction;
        smooth[index] = smoothstep(fraction);
    }
}

fn normal_noise_value(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64) f64 {
    @setFloatMode(.strict);
    return (perlin_value(first, first_count, x, y, z) + perlin_value(second, second_count, x * normal_noise_input_factor, y * normal_noise_input_factor, z * normal_noise_input_factor)) * value_factor;
}

fn normal_noise_value2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: Vec2, z: f64) Vec2 {
    @setFloatMode(.strict);
    const factor: Vec2 = @splat(value_factor);
    const input_factor: Vec2 = @splat(normal_noise_input_factor);
    return (perlin_value2(first, first_count, x, y, z) + perlin_value2(second, second_count, x * normal_noise_input_factor, y * input_factor, z * normal_noise_input_factor)) * factor;
}

fn perlin_value(data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64) f64 {
    @setFloatMode(.strict);
    const lowest_value_factor = read_f64(data, 0);
    const lowest_input_factor = read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var value: f64 = 0.0;
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            value += amplitude * improved_noise(permutation, read_f64(data, origin), read_f64(data, origin + 8), read_f64(data, origin + 16), wrap(x * input_factor), wrap(y * input_factor), wrap(z * input_factor)) * value_factor;
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
    return value;
}

fn perlin_value2(data: [*]const u8, octaves: usize, x: f64, y: Vec2, z: f64) Vec2 {
    @setFloatMode(.strict);
    const lowest_value_factor = read_f64(data, 0);
    const lowest_input_factor = read_f64(data, 8);
    const amplitude_offset = 16;
    const origin_offset = amplitude_offset + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var value: Vec2 = @splat(0.0);
    var input_factor = lowest_input_factor;
    var value_factor = lowest_value_factor;
    var octave: usize = 0;
    while (octave < octaves) : (octave += 1) {
        if (data[active_offset + octave] != 0) {
            const amplitude = read_f64(data, amplitude_offset + octave * 8);
            const origin = origin_offset + octave * 24;
            const permutation = data + permutation_offset + octave * 256;
            const sample_y: Vec2 = .{ wrap(y[0] * input_factor), wrap(y[1] * input_factor) };
            value += (improved_noise2(permutation, read_f64(data, origin), read_f64(data, origin + 8), read_f64(data, origin + 16), wrap(x * input_factor), sample_y, wrap(z * input_factor)) * @as(Vec2, @splat(amplitude))) * @as(Vec2, @splat(value_factor));
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
    return value;
}

fn improved_noise(permutation: [*]const u8, xo: f64, yo: f64, zo: f64, x: f64, y: f64, z: f64) f64 {
    @setFloatMode(.strict);
    const shifted_x = x + xo;
    const shifted_y = y + yo;
    const shifted_z = z + zo;
    const integer_x = floor_int(shifted_x);
    const integer_y = floor_int(shifted_y);
    const integer_z = floor_int(shifted_z);
    const fraction_x = shifted_x - @as(f64, @floatFromInt(integer_x));
    const fraction_y = shifted_y - @as(f64, @floatFromInt(integer_y));
    const fraction_z = shifted_z - @as(f64, @floatFromInt(integer_z));
    return sample_and_lerp(permutation, integer_x, integer_y, integer_z, fraction_x, fraction_y, fraction_z);
}

fn improved_noise2(permutation: [*]const u8, xo: f64, yo: f64, zo: f64, x: f64, y: Vec2, z: f64) Vec2 {
    @setFloatMode(.strict);
    const shifted_x = x + xo;
    const shifted_y = y + @as(Vec2, @splat(yo));
    const shifted_z = z + zo;
    const integer_x = floor_int(shifted_x);
    const integer_y = [2]i32{ floor_int(shifted_y[0]), floor_int(shifted_y[1]) };
    const integer_z = floor_int(shifted_z);
    const fraction_x = shifted_x - @as(f64, @floatFromInt(integer_x));
    const fraction_y = shifted_y - @as(Vec2, .{ @as(f64, @floatFromInt(integer_y[0])), @as(f64, @floatFromInt(integer_y[1])) });
    const fraction_z = shifted_z - @as(f64, @floatFromInt(integer_z));
    return sample_and_lerp2(permutation, integer_x, integer_y, integer_z, fraction_x, fraction_y, fraction_z);
}

fn sample_and_lerp(permutation: [*]const u8, x: i32, y: i32, z: i32, fraction_x: f64, fraction_y: f64, fraction_z: f64) f64 {
    @setFloatMode(.strict);
    const x_hash = permutation_value(permutation, x);
    const next_x_hash = permutation_value(permutation, x + 1);
    return sample_and_lerp_prepared(permutation, x_hash, next_x_hash, y, z, fraction_x, fraction_y, fraction_z);
}

fn sample_and_lerp_prepared(permutation: [*]const u8, x_hash: i32, next_x_hash: i32, y: i32, z: i32, fraction_x: f64, fraction_y: f64, fraction_z: f64) f64 {
    @setFloatMode(.strict);
    const xy_hash = permutation_value(permutation, x_hash + y);
    const xy_next_hash = permutation_value(permutation, x_hash + y + 1);
    const next_xy_hash = permutation_value(permutation, next_x_hash + y);
    const next_xy_next_hash = permutation_value(permutation, next_x_hash + y + 1);
    const value000 = gradient(permutation_value(permutation, xy_hash + z), fraction_x, fraction_y, fraction_z);
    const value100 = gradient(permutation_value(permutation, next_xy_hash + z), fraction_x - 1.0, fraction_y, fraction_z);
    const value010 = gradient(permutation_value(permutation, xy_next_hash + z), fraction_x, fraction_y - 1.0, fraction_z);
    const value110 = gradient(permutation_value(permutation, next_xy_next_hash + z), fraction_x - 1.0, fraction_y - 1.0, fraction_z);
    const value001 = gradient(permutation_value(permutation, xy_hash + z + 1), fraction_x, fraction_y, fraction_z - 1.0);
    const value101 = gradient(permutation_value(permutation, next_xy_hash + z + 1), fraction_x - 1.0, fraction_y, fraction_z - 1.0);
    const value011 = gradient(permutation_value(permutation, xy_next_hash + z + 1), fraction_x, fraction_y - 1.0, fraction_z - 1.0);
    const value111 = gradient(permutation_value(permutation, next_xy_next_hash + z + 1), fraction_x - 1.0, fraction_y - 1.0, fraction_z - 1.0);
    return lerp3(smoothstep(fraction_x), smoothstep(fraction_y), smoothstep(fraction_z), value000, value100, value010, value110, value001, value101, value011, value111);
}

const GradientCell = struct {
    values: [8]i32,

    fn init(permutation: [*]const u8, x_hash: i32, next_x_hash: i32, y: i32, z: i32) GradientCell {
        const xy_hash = permutation_value(permutation, x_hash + y);
        const xy_next_hash = permutation_value(permutation, x_hash + y + 1);
        const next_xy_hash = permutation_value(permutation, next_x_hash + y);
        const next_xy_next_hash = permutation_value(permutation, next_x_hash + y + 1);
        return .{ .values = .{
            permutation_value(permutation, xy_hash + z),
            permutation_value(permutation, next_xy_hash + z),
            permutation_value(permutation, xy_next_hash + z),
            permutation_value(permutation, next_xy_next_hash + z),
            permutation_value(permutation, xy_hash + z + 1),
            permutation_value(permutation, next_xy_hash + z + 1),
            permutation_value(permutation, xy_next_hash + z + 1),
            permutation_value(permutation, next_xy_next_hash + z + 1),
        } };
    }

    fn sample(self: GradientCell, fraction_x: f64, fraction_y: f64, fraction_z: f64) f64 {
        const value000 = gradient(self.values[0], fraction_x, fraction_y, fraction_z);
        const value100 = gradient(self.values[1], fraction_x - 1.0, fraction_y, fraction_z);
        const value010 = gradient(self.values[2], fraction_x, fraction_y - 1.0, fraction_z);
        const value110 = gradient(self.values[3], fraction_x - 1.0, fraction_y - 1.0, fraction_z);
        const value001 = gradient(self.values[4], fraction_x, fraction_y, fraction_z - 1.0);
        const value101 = gradient(self.values[5], fraction_x - 1.0, fraction_y, fraction_z - 1.0);
        const value011 = gradient(self.values[6], fraction_x, fraction_y - 1.0, fraction_z - 1.0);
        const value111 = gradient(self.values[7], fraction_x - 1.0, fraction_y - 1.0, fraction_z - 1.0);
        return lerp3(smoothstep(fraction_x), smoothstep(fraction_y), smoothstep(fraction_z), value000, value100, value010, value110, value001, value101, value011, value111);
    }

    fn sample_smoothed(self: GradientCell, fraction_x: f64, fraction_y: f64, fraction_z: f64, smooth_x: f64, smooth_y: f64, smooth_z: f64) f64 {
        const value000 = gradient(self.values[0], fraction_x, fraction_y, fraction_z);
        const value100 = gradient(self.values[1], fraction_x - 1.0, fraction_y, fraction_z);
        const value010 = gradient(self.values[2], fraction_x, fraction_y - 1.0, fraction_z);
        const value110 = gradient(self.values[3], fraction_x - 1.0, fraction_y - 1.0, fraction_z);
        const value001 = gradient(self.values[4], fraction_x, fraction_y, fraction_z - 1.0);
        const value101 = gradient(self.values[5], fraction_x - 1.0, fraction_y, fraction_z - 1.0);
        const value011 = gradient(self.values[6], fraction_x, fraction_y - 1.0, fraction_z - 1.0);
        const value111 = gradient(self.values[7], fraction_x - 1.0, fraction_y - 1.0, fraction_z - 1.0);
        return lerp3(smooth_x, smooth_y, smooth_z, value000, value100, value010, value110, value001, value101, value011, value111);
    }

    fn line(self: GradientCell, fraction_x: f64, fraction_z: f64) GradientLine {
        return .{ .values = .{
            gradient_term(self.values[0], fraction_x, fraction_z, 0.0),
            gradient_term(self.values[1], fraction_x - 1.0, fraction_z, 0.0),
            gradient_term(self.values[2], fraction_x, fraction_z, -1.0),
            gradient_term(self.values[3], fraction_x - 1.0, fraction_z, -1.0),
            gradient_term(self.values[4], fraction_x, fraction_z - 1.0, 0.0),
            gradient_term(self.values[5], fraction_x - 1.0, fraction_z - 1.0, 0.0),
            gradient_term(self.values[6], fraction_x, fraction_z - 1.0, -1.0),
            gradient_term(self.values[7], fraction_x - 1.0, fraction_z - 1.0, -1.0),
        } };
    }
};

const GradientTerm = struct {
    base: f64,
    y_offset: f64,
    mode: enum { none, add, subtract },

    fn sample(self: GradientTerm, y: f64) f64 {
        return switch (self.mode) {
            .none => self.base,
            .add => self.base + (y + self.y_offset),
            .subtract => self.base - (y + self.y_offset),
        };
    }

    fn sample2(self: GradientTerm, y: Vec2) Vec2 {
        const base: Vec2 = @splat(self.base);
        const offset: Vec2 = @splat(self.y_offset);
        return switch (self.mode) {
            .none => base,
            .add => base + (y + offset),
            .subtract => base - (y + offset),
        };
    }

    fn sample4(self: GradientTerm, y: Vec4) Vec4 {
        const base: Vec4 = @splat(self.base);
        const offset: Vec4 = @splat(self.y_offset);
        return switch (self.mode) {
            .none => base,
            .add => base + (y + offset),
            .subtract => base - (y + offset),
        };
    }
};

const GradientLine = struct {
    values: [8]GradientTerm,

    fn sample_smoothed(self: GradientLine, fraction_y: f64, smooth_x: f64, smooth_y: f64, smooth_z: f64) f64 {
        return lerp3(smooth_x, smooth_y, smooth_z,
            self.values[0].sample(fraction_y), self.values[1].sample(fraction_y),
            self.values[2].sample(fraction_y), self.values[3].sample(fraction_y),
            self.values[4].sample(fraction_y), self.values[5].sample(fraction_y),
            self.values[6].sample(fraction_y), self.values[7].sample(fraction_y));
    }

    fn sample_smoothed2(self: GradientLine, fraction_y: Vec2, smooth_x: f64, smooth_y: Vec2, smooth_z: f64) Vec2 {
        return lerp3_2(@splat(smooth_x), smooth_y, @splat(smooth_z),
            self.values[0].sample2(fraction_y), self.values[1].sample2(fraction_y),
            self.values[2].sample2(fraction_y), self.values[3].sample2(fraction_y),
            self.values[4].sample2(fraction_y), self.values[5].sample2(fraction_y),
            self.values[6].sample2(fraction_y), self.values[7].sample2(fraction_y));
    }

    fn sample_smoothed4(self: GradientLine, fraction_y: Vec4, smooth_x: f64, smooth_y: Vec4, smooth_z: f64) Vec4 {
        return lerp3_4(@splat(smooth_x), smooth_y, @splat(smooth_z),
            self.values[0].sample4(fraction_y), self.values[1].sample4(fraction_y),
            self.values[2].sample4(fraction_y), self.values[3].sample4(fraction_y),
            self.values[4].sample4(fraction_y), self.values[5].sample4(fraction_y),
            self.values[6].sample4(fraction_y), self.values[7].sample4(fraction_y));
    }
};

fn gradient_term(value: i32, x: f64, z: f64, y_offset: f64) GradientTerm {
    return switch (value & 15) {
        0 => .{ .base = x, .y_offset = y_offset, .mode = .add },
        1 => .{ .base = -x, .y_offset = y_offset, .mode = .add },
        2 => .{ .base = x, .y_offset = y_offset, .mode = .subtract },
        3 => .{ .base = -x, .y_offset = y_offset, .mode = .subtract },
        4 => .{ .base = x + z, .y_offset = y_offset, .mode = .none },
        5 => .{ .base = -x + z, .y_offset = y_offset, .mode = .none },
        6 => .{ .base = x - z, .y_offset = y_offset, .mode = .none },
        7 => .{ .base = -x - z, .y_offset = y_offset, .mode = .none },
        8 => .{ .base = z, .y_offset = y_offset, .mode = .add },
        9 => .{ .base = z, .y_offset = y_offset, .mode = .subtract },
        10 => .{ .base = -z, .y_offset = y_offset, .mode = .add },
        11 => .{ .base = -z, .y_offset = y_offset, .mode = .subtract },
        12 => .{ .base = x, .y_offset = y_offset, .mode = .add },
        13 => .{ .base = z, .y_offset = y_offset, .mode = .subtract },
        14 => .{ .base = -x, .y_offset = y_offset, .mode = .add },
        else => .{ .base = -z, .y_offset = y_offset, .mode = .subtract },
    };
}

fn sample_and_lerp2(permutation: [*]const u8, x: i32, y: [2]i32, z: i32, fraction_x: f64, fraction_y: Vec2, fraction_z: f64) Vec2 {
    @setFloatMode(.strict);
    const x_hash = permutation_value(permutation, x);
    const next_x_hash = permutation_value(permutation, x + 1);
    const xy_hash0 = permutation_value(permutation, x_hash + y[0]);
    const xy_next_hash0 = permutation_value(permutation, x_hash + y[0] + 1);
    const next_xy_hash0 = permutation_value(permutation, next_x_hash + y[0]);
    const next_xy_next_hash0 = permutation_value(permutation, next_x_hash + y[0] + 1);
    const xy_hash1 = permutation_value(permutation, x_hash + y[1]);
    const xy_next_hash1 = permutation_value(permutation, x_hash + y[1] + 1);
    const next_xy_hash1 = permutation_value(permutation, next_x_hash + y[1]);
    const next_xy_next_hash1 = permutation_value(permutation, next_x_hash + y[1] + 1);
    const value000 = gradient2(permutation_value(permutation, xy_hash0 + z), permutation_value(permutation, xy_hash1 + z), fraction_x, fraction_y, fraction_z);
    const value100 = gradient2(permutation_value(permutation, next_xy_hash0 + z), permutation_value(permutation, next_xy_hash1 + z), fraction_x - 1.0, fraction_y, fraction_z);
    const value010 = gradient2(permutation_value(permutation, xy_next_hash0 + z), permutation_value(permutation, xy_next_hash1 + z), fraction_x, fraction_y - @as(Vec2, @splat(1.0)), fraction_z);
    const value110 = gradient2(permutation_value(permutation, next_xy_next_hash0 + z), permutation_value(permutation, next_xy_next_hash1 + z), fraction_x - 1.0, fraction_y - @as(Vec2, @splat(1.0)), fraction_z);
    const value001 = gradient2(permutation_value(permutation, xy_hash0 + z + 1), permutation_value(permutation, xy_hash1 + z + 1), fraction_x, fraction_y, fraction_z - 1.0);
    const value101 = gradient2(permutation_value(permutation, next_xy_hash0 + z + 1), permutation_value(permutation, next_xy_hash1 + z + 1), fraction_x - 1.0, fraction_y, fraction_z - 1.0);
    const value011 = gradient2(permutation_value(permutation, xy_next_hash0 + z + 1), permutation_value(permutation, xy_next_hash1 + z + 1), fraction_x, fraction_y - @as(Vec2, @splat(1.0)), fraction_z - 1.0);
    const value111 = gradient2(permutation_value(permutation, next_xy_next_hash0 + z + 1), permutation_value(permutation, next_xy_next_hash1 + z + 1), fraction_x - 1.0, fraction_y - @as(Vec2, @splat(1.0)), fraction_z - 1.0);
    const smooth_x: Vec2 = @splat(smoothstep(fraction_x));
    const smooth_z: Vec2 = @splat(smoothstep(fraction_z));
    return lerp3_2(smooth_x, smoothstep2(fraction_y), smooth_z, value000, value100, value010, value110, value001, value101, value011, value111);
}

fn read_f64(data: [*]const u8, offset: usize) f64 {
    const value: *align(1) const f64 = @ptrCast(data + offset);
    return value.*;
}

fn wrap(value: f64) f64 {
    return value - @floor(value / 33554432.0 + 0.5) * 33554432.0;
}

fn floor_int(value: f64) i32 {
    return @intFromFloat(@floor(value));
}

fn permutation_value(permutation: [*]const u8, index: i32) i32 {
    return permutation[@as(usize, @intCast(index & 255))];
}

fn gradient(value: i32, x: f64, y: f64, z: f64) f64 {
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

fn gradient2(first: i32, second: i32, x: f64, y: Vec2, z: f64) Vec2 {
    return .{ gradient(first, x, y[0], z), gradient(second, x, y[1], z) };
}

fn smoothstep(value: f64) f64 {
    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
}

fn smoothstep2(value: Vec2) Vec2 {
    return value * value * value * (value * (value * @as(Vec2, @splat(6.0)) - @as(Vec2, @splat(15.0))) + @as(Vec2, @splat(10.0)));
}

fn lerp(delta: f64, start: f64, end: f64) f64 {
    return start + delta * (end - start);
}

fn lerp2(x: f64, y: f64, value00: f64, value10: f64, value01: f64, value11: f64) f64 {
    return lerp(y, lerp(x, value00, value10), lerp(x, value01, value11));
}

fn lerp3(x: f64, y: f64, z: f64, value000: f64, value100: f64, value010: f64, value110: f64, value001: f64, value101: f64, value011: f64, value111: f64) f64 {
    return lerp(z, lerp2(x, y, value000, value100, value010, value110), lerp2(x, y, value001, value101, value011, value111));
}

fn lerp2_2(x: Vec2, y: Vec2, value00: Vec2, value10: Vec2, value01: Vec2, value11: Vec2) Vec2 {
    return lerp_2(y, lerp_2(x, value00, value10), lerp_2(x, value01, value11));
}

fn lerp3_2(x: Vec2, y: Vec2, z: Vec2, value000: Vec2, value100: Vec2, value010: Vec2, value110: Vec2, value001: Vec2, value101: Vec2, value011: Vec2, value111: Vec2) Vec2 {
    return lerp_2(z, lerp2_2(x, y, value000, value100, value010, value110), lerp2_2(x, y, value001, value101, value011, value111));
}

fn lerp2_4(x: Vec4, y: Vec4, value00: Vec4, value10: Vec4, value01: Vec4, value11: Vec4) Vec4 {
    return lerp_4(y, lerp_4(x, value00, value10), lerp_4(x, value01, value11));
}

fn lerp3_4(x: Vec4, y: Vec4, z: Vec4, value000: Vec4, value100: Vec4, value010: Vec4, value110: Vec4, value001: Vec4, value101: Vec4, value011: Vec4, value111: Vec4) Vec4 {
    return lerp_4(z, lerp2_4(x, y, value000, value100, value010, value110), lerp2_4(x, y, value001, value101, value011, value111));
}

fn lerp_2(delta: Vec2, start: Vec2, end: Vec2) Vec2 {
    return start + delta * (end - start);
}

fn lerp_4(delta: Vec4, start: Vec4, end: Vec4) Vec4 {
    return start + delta * (end - start);
}
