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
const Vec8d = @Vector(8, f64);
const Vec16d = @Vector(16, f64);
const Vec16i = @Vector(16, i32);
const Vec4f = @Vector(4, f32);
const Vec8f = @Vector(8, f32);
const Vec4i = @Vector(4, i32);
const Vec8i = @Vector(8, i32);
const avx_only = @hasDecl(@import("root"), "adrenaline_avx2");
const small_only = @hasDecl(@import("root"), "adrenaline_small");

extern fn adrenaline_has_avx2() c_int;
extern fn adrenaline_blended_noise_grid_avx2(min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const BlendedNoiseConfig, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;

extern fn adrenaline_normal_noise_batch_small_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, count: usize, values: [*]f64) callconv(.c) void;
extern fn adrenaline_normal_noise_batch_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, count: usize, values: [*]f64) callconv(.c) void;

extern fn adrenaline_normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;
extern fn adrenaline_normal_noise_grid_approx_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f64) callconv(.c) void;
extern fn adrenaline_normal_noise_grid_approx_float_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: [*]f32) callconv(.c) void;
extern fn adrenaline_prepare_aquifer_cell_avx2(density: [*]const c.jdouble, candidates: [*]c.jlong, packed_locations: [*]const c.jshort, packed_location_count: usize, fluid_levels: [*]const c.jint, fluid_types: [*]const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: [*]c.jint, materials: [*]c.jbyte) callconv(.c) usize;
extern fn adrenaline_fused_terrain_avx2(program: [*]const u8, height: usize, output: [*]c.jdouble) callconv(.c) void;

comptime {
    if (!avx_only) {
        @export(&JNI_OnLoad, .{ .name = "JNI_OnLoad" });
        @export(&Java_net_fly_adrenaline_natives_AdrenalineNatives_capabilities0, .{ .name = "Java_net_fly_adrenaline_natives_AdrenalineNatives_capabilities0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_address0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_address0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_sample0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sample0" });
        @export(&Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleGrid0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleGrid0" });
        @export(&Java_net_fly_adrenaline_natives_BlendedNativeSampler_sampleGrid0, .{ .name = "Java_net_fly_adrenaline_natives_BlendedNativeSampler_sampleGrid0" });
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
    if (comptime builtin.cpu.arch == .x86_64) {
        if (adrenaline_has_avx2() != 0) {
            if (@abs(y_step) <= 0.125) {
                adrenaline_normal_noise_batch_small_avx2(first, first_count, second, second_count, value_factor, x, y, z, y_step, length, values);
            } else {
                adrenaline_normal_noise_batch_avx2(first, first_count, second, second_count, value_factor, x, y, z, y_step, length, values);
            }
        } else {
            normal_noise_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..length]);
        }
    } else {
        normal_noise_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, values[0..length]);
    }
    return c.JNI_TRUE;
}

fn Java_net_fly_adrenaline_natives_BlendedNativeSampler_sampleGrid0(env: ?*c.JNIEnv, _: c.jclass, min_address: c.jlong, max_address: c.jlong, main_address: c.jlong, xz_multiplier: c.jdouble, y_multiplier: c.jdouble, xz_factor: c.jdouble, y_factor: c.jdouble, smear_scale_multiplier: c.jdouble, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    if (min_address == 0 or max_address == 0 or main_address == 0 or x_count <= 0 or y_count <= 0 or z_count <= 0 or
        x_count > max_grid_axis or y_count > max_grid_axis or z_count > max_grid_axis) return c.JNI_FALSE;
    const nx: usize = @intCast(x_count);
    const ny: usize = @intCast(y_count);
    const nz: usize = @intCast(z_count);
    const total = nx * ny * nz;
    if (total > max_grid_size) return c.JNI_FALSE;
    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < @as(c.jsize, @intCast(total))) return c.JNI_FALSE;
    const get_critical = functions.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release_critical = functions.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const output_address = get_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_critical(env, output, output_address, 0);
    const config: BlendedNoiseConfig = .{ .xz_multiplier = xz_multiplier, .y_multiplier = y_multiplier, .xz_factor = xz_factor, .y_factor = y_factor, .smear_scale_multiplier = smear_scale_multiplier };
    const min: [*]const u8 = @ptrFromInt(@as(usize, @intCast(min_address)));
    const max: [*]const u8 = @ptrFromInt(@as(usize, @intCast(max_address)));
    const main: [*]const u8 = @ptrFromInt(@as(usize, @intCast(main_address)));
    const values: [*]f64 = @ptrCast(@alignCast(output_address));
    if (comptime builtin.cpu.arch == .x86_64) {
        if (adrenaline_has_avx2() != 0) {
            adrenaline_blended_noise_grid_avx2(min, max, main, &config, base_x, base_y, base_z, step_x, step_y, step_z, nx, ny, nz, values);
            return c.JNI_TRUE;
        }
    }
    blended_noise_grid_impl(false, min, max, main, &config, base_x, base_y, base_z, step_x, step_y, step_z, nx, ny, nz, values[0..total]);
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
    if (fluid_count <= 0 or fluid_count > max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, barrier_values) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2)) or get_array_length(env, deferred_indices) < deferred_count or get_array_length(env, output) < value_count or get_array_length(env, fluid_types) < fluid_count) {
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
    if (location_count <= 0 or location_count > max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, global_materials) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2))) {
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
    if (fluid_count <= 0 or fluid_count > max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, global_materials) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2)) or get_array_length(env, fluid_types) < fluid_count or get_array_length(env, output) < value_count) {
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
    if (location_count <= 0 or location_count > max_grid_size or get_array_length(env, density_values) < value_count or get_array_length(env, candidates) < @as(c.jsize, @intCast(total * 2)) or get_array_length(env, deferred_indices) < value_count or get_array_length(env, deferred_count) < 1 or get_array_length(env, output) < value_count or get_array_length(env, fluid_levels) < location_count or get_array_length(env, fluid_types) < location_count) {
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
    if (height > 0 and (global_aquifer_material(base_y + @as(i32, @intCast(height - 1)), global_fluid_level, global_fluid_type) orelse return null) == 3) {
        const plane = width * width;
        for (0..height) |y_index| {
            for (0..plane) |column_index| {
                const index = y_index * plane + column_index;
                materials[index] = if (density[index] > 0.0) 0 else 3;
            }
        }
        return 0;
    }
    if (@as(i64, base_y) >= @as(i64, global_fluid_level) + 5 and
        height > 0 and width > 0 and fluid_levels.len > 0 and fluid_levels.len <= max_density_values and
        (global_fluid_type == 2 or global_fluid_type == 3) and grid_size_x > 1 and grid_size_z > 1)
    {
        var level_index: usize = 0;
        var max8: Vec8i = @splat(std.math.minInt(i32));
        while (level_index + 7 < fluid_levels.len) : (level_index += 8) {
            const next: Vec8i = @bitCast(fluid_levels[level_index..][0..8].*);
            max8 = @max(max8, next);
        }
        var max_level: i32 = @reduce(.Max, max8);
        while (level_index < fluid_levels.len) : (level_index += 1) max_level = @max(max_level, fluid_levels[level_index]);
        if (@as(i64, base_y) >= @as(i64, max_level) + 5) {
            const min_x = @divFloor(@as(i64, base_x) - 5, 16) - @as(i64, min_grid_x);
            const max_x = @divFloor(@as(i64, base_x) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_x);
            const min_z = @divFloor(@as(i64, base_z) - 5, 16) - @as(i64, min_grid_z);
            const max_z = @divFloor(@as(i64, base_z) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_z);
            const min_y = @divFloor(@as(i64, base_y) + 1, 12) - @as(i64, min_grid_y);
            const max_y = @divFloor(@as(i64, base_y) + @as(i64, @intCast(height)), 12) - @as(i64, min_grid_y);
            const last_index = (@as(i128, max_y) + 1) * @as(i128, grid_size_z) * @as(i128, grid_size_x) +
                (@as(i128, max_z) + 1) * @as(i128, grid_size_x) + @as(i128, max_x) + 1;
            if (min_x >= 0 and max_x + 1 < grid_size_x and min_z >= 0 and max_z + 1 < grid_size_z and
                min_y >= 1 and last_index < fluid_levels.len)
            {
                for (density, materials) |value, *material| material.* = if (value > 0.0) 0 else 1;
                return 0;
            }
        }
    }
    var center_cache: [16]AquiferCenters = undefined;
    var center_valid: u16 = 0;
    var deferred_count: usize = 0;
    const plane = width * width;
    const lava_level = @min(@as(i32, -54), global_fluid_level);
    const global_type_code: u8 = @bitCast(global_fluid_type);
    const below_lava_boundary = if (global_type_code == 3) global_fluid_level else lava_level;
    var global_material_cache: [max_density_values]u8 = undefined;
    var grid_y_cache: [max_density_values]i32 = undefined;
    var fluid_max_cache: [max_density_values]i32 = undefined;
    const cache_fluid_max = fluid_levels.len <= max_density_values and grid_size_x > 0 and grid_size_z > 0;
    var fluid_cache_ready = false;
    @memset(global_material_cache[0..height], 0);
    @memset(grid_y_cache[0..height], std.math.minInt(i32));
    var x_index: usize = 0;
    while (x_index < width) : (x_index += 1) {
        const x: i32 = base_x + @as(i32, @intCast(x_index));
        var z_index: usize = 0;
        while (z_index < width) : (z_index += 1) {
            const z: i32 = base_z + @as(i32, @intCast(z_index));
            const column_index = x_index * width + z_index;
            const cell_grid_x = @divFloor(x - 5, 16);
            const cell_grid_z = @divFloor(z - 5, 16);
            const local_grid_x = cell_grid_x - min_grid_x;
            const local_grid_z = cell_grid_z - min_grid_z;
            var search: ?AquiferSearch = null;
            var fluid_bound_grid_y: i32 = std.math.minInt(i32);
            var maximum_fluid_level: i32 = std.math.minInt(i32);
            var y_index: usize = 0;
            while (y_index < height) : (y_index += 1) {
                const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
                const index = y_index * plane + column_index;
                materials[index] = 0;
                if (density[index] > 0.0) {
                    continue;
                }
                var global_material = global_material_cache[y_index];
                if (global_material == 0) {
                    global_material = global_aquifer_material(y, global_fluid_level, global_fluid_type) orelse return null;
                    global_material_cache[y_index] = global_material;
                }
                if (global_material == 3) {
                    materials[index] = 3;
                    continue;
                }
                if (global_material < 1 or global_material > 3) {
                    return null;
                }

                var grid_y = grid_y_cache[y_index];
                if (grid_y == std.math.minInt(i32)) {
                    grid_y = @divFloor(y + 1, 12);
                    grid_y_cache[y_index] = grid_y;
                }
                if (fluid_bound_grid_y != grid_y) {
                    var cache_key: ?usize = null;
                    const local_grid_y = grid_y - min_grid_y;
                    if (cache_fluid_max) {
                        if (local_grid_x >= 0 and local_grid_x + 1 < grid_size_x and local_grid_z >= 0 and local_grid_z + 1 < grid_size_z and local_grid_y >= 1) {
                            const key = (@as(usize, @intCast(local_grid_y)) * @as(usize, @intCast(grid_size_z)) + @as(usize, @intCast(local_grid_z))) * @as(usize, @intCast(grid_size_x)) + @as(usize, @intCast(local_grid_x));
                            if (key < fluid_levels.len) cache_key = key;
                        }
                    }
                    if (cache_key) |key| {
                        if (!fluid_cache_ready) {
                            @memset(fluid_max_cache[0..fluid_levels.len], std.math.minInt(i32));
                            fluid_cache_ready = true;
                        }
                        if (fluid_max_cache[key] == std.math.minInt(i32)) {
                            fluid_max_cache[key] = aquifer_maximum_fluid_level(fluid_levels, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z) orelse return null;
                        }
                        maximum_fluid_level = fluid_max_cache[key];
                    } else {
                        maximum_fluid_level = aquifer_maximum_fluid_level(fluid_levels, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z) orelse return null;
                    }
                    fluid_bound_grid_y = grid_y;
                }
                if (@as(i64, y) >= @as(i64, maximum_fluid_level) + 5) {
                    materials[index] = 1;
                    continue;
                }

                const candidates = if (search) |*cached| block: {
                    if (cached.grid_y == grid_y and advance_y_down(avx2, neon, cached, y)) {
                        break :block cached.candidates();
                    }
                    search = aquifer_search_cached(&center_cache, &center_valid, packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z, x, y, z) orelse return null;
                    break :block search.?.candidates();
                } else block: {
                    search = aquifer_search_cached(&center_cache, &center_valid, packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z, x, y, z) orelse return null;
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
                if (nearest_type == 2 and y == below_lava_boundary) {
                    materials[index] = 2 | 4;
                    continue;
                }
                const near_second_pressure = aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type, 0.0);
                if (fluid_levels[candidates.nearest_index] != fluid_levels[candidates.second_index] and !((nearest_type == 3 and second_type == 2) or (nearest_type == 2 and second_type == 3)) and near_second_pressure >= -4.0 and near_second_pressure <= 4.0) {
                    if (deferred_count >= deferred_indices.len) return null;
                    deferred_indices[deferred_count] = @intCast(index);
                    deferred_count += 1;
                    pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                    continue;
                }
                if (density[index] + nearest_similarity * near_second_pressure > 0.0) {
                    continue;
                }

                const nearest_third_similarity = aquifer_similarity(candidates.nearest_distance, candidates.third_distance);
                if (nearest_third_similarity > 0.0) {
                    const near_third_pressure = aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type, 0.0);
                    if (fluid_levels[candidates.nearest_index] != fluid_levels[candidates.third_index] and !((nearest_type == 3 and third_type == 2) or (nearest_type == 2 and third_type == 3)) and near_third_pressure >= -4.0 and near_third_pressure <= 4.0) {
                        if (deferred_count >= deferred_indices.len) return null;
                        deferred_indices[deferred_count] = @intCast(index);
                        deferred_count += 1;
                        pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                        continue;
                    }
                    if (density[index] + nearest_similarity * nearest_third_similarity * near_third_pressure > 0.0) {
                        continue;
                    }
                }

                const second_third_similarity = aquifer_similarity(candidates.second_distance, candidates.third_distance);
                if (second_third_similarity > 0.0) {
                    const second_third_pressure = aquifer_pressure(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type, 0.0);
                    if (fluid_levels[candidates.second_index] != fluid_levels[candidates.third_index] and !((second_type == 3 and third_type == 2) or (second_type == 2 and third_type == 3)) and second_third_pressure >= -4.0 and second_third_pressure <= 4.0) {
                        if (deferred_count >= deferred_indices.len) return null;
                        deferred_indices[deferred_count] = @intCast(index);
                        deferred_count += 1;
                        pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                        continue;
                    }
                    if (density[index] + nearest_similarity * second_third_similarity * second_third_pressure > 0.0) {
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
    if (output.len != 2 or candidates.nearest_index > 0xFFF or candidates.second_index > 0xFFF or candidates.third_index > 0xFFF or candidates.nearest_distance < 0 or candidates.second_distance < 0 or candidates.third_distance < 0 or candidates.nearest_distance > std.math.maxInt(u16) or candidates.second_distance > std.math.maxInt(u16) or candidates.third_distance > std.math.maxInt(u16)) {
        return null;
    }
    const indices: u64 = @as(u64, @intCast(candidates.nearest_index)) | @as(u64, @intCast(candidates.second_index)) << 12 | @as(u64, @intCast(candidates.third_index)) << 24;
    const distances: u64 = @as(u64, @intCast(candidates.nearest_distance)) | @as(u64, @intCast(candidates.second_distance)) << 16 | @as(u64, @intCast(candidates.third_distance)) << 32;
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
        const one: Vec4i = @splat(1);
        const two: Vec4i = @splat(2);
        inline for (.{ 0, 4, 8 }) |index| {
            const previous: Vec4i = @bitCast(self.delta_z[index .. index + 4].*);
            const distance: Vec4i = @bitCast(self.distances[index .. index + 4].*);
            self.delta_z[index .. index + 4].* = @bitCast(previous - one);
            self.distances[index .. index + 4].* = @bitCast(distance - two * previous + one);
        }
    }

    fn advanceYDown(self: *AquiferSearch, y: i32) bool {
        const steps = self.y - y;
        if (steps <= 0) {
            return false;
        }
        const step: Vec4i = @splat(steps);
        const two: Vec4i = @splat(2);
        const square: Vec4i = @splat(steps * steps);
        inline for (.{ 0, 4, 8 }) |index| {
            const previous: Vec4i = @bitCast(self.delta_y[index .. index + 4].*);
            const distance: Vec4i = @bitCast(self.distances[index .. index + 4].*);
            self.delta_y[index .. index + 4].* = @bitCast(previous + step);
            self.distances[index .. index + 4].* = @bitCast(distance + two * previous * step + square);
        }
        self.y = y;
        return true;
    }

    fn candidates(self: *const AquiferSearch) AquiferCandidates {
        var nearest_distance: i32 = std.math.maxInt(i32);
        var second_distance: i32 = std.math.maxInt(i32);
        var third_distance: i32 = std.math.maxInt(i32);
        var nearest_index: usize = 0;
        var second_index: usize = 0;
        var third_index: usize = 0;
        for (0..12) |order_value| {
            const distance = self.distances[order_value];
            const index = self.indices[order_value];
            if (distance <= nearest_distance) {
                third_distance = second_distance;
                third_index = second_index;
                second_distance = nearest_distance;
                second_index = nearest_index;
                nearest_distance = distance;
                nearest_index = index;
            } else if (distance <= second_distance) {
                third_distance = second_distance;
                third_index = second_index;
                second_distance = distance;
                second_index = index;
            } else if (distance <= third_distance) {
                third_distance = distance;
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
    if (local_grid_x < 0 or local_grid_y < 1 or local_grid_z < 0 or local_grid_x + 1 >= grid_size_x or local_grid_z + 1 >= grid_size_z) {
        return null;
    }
    const size_x: usize = @intCast(grid_size_x);
    const size_z: usize = @intCast(grid_size_z);
    const local_x: usize = @intCast(local_grid_x);
    const local_y: usize = @intCast(local_grid_y);
    const local_z: usize = @intCast(local_grid_z);
    const last_index = ((local_y + 1) * size_z + local_z + 1) * size_x + local_x + 1;
    if (last_index >= packed_locations.len) return null;
    var result: AquiferSearch = undefined;
    result.grid_z = grid_z;
    result.grid_y = grid_y;
    result.y = y;
    inline for (0..2) |ox| {
        inline for (0..3) |oy| {
            const row = ((local_y + oy - 1) * size_z + local_z) * size_x + local_x + ox;
            inline for (0..2) |oz| {
                const order = (ox * 3 + oy) * 2 + oz;
                const cache_index = row + oz * size_x;
                const packed_value: u16 = @bitCast(packed_locations.ptr[cache_index]);
                const center_x = (grid_x + @as(i32, ox)) * 16 + @as(i32, packed_value >> 8);
                const center_y = (grid_y + @as(i32, oy) - 1) * 12 + @as(i32, (packed_value >> 4) & 15);
                const center_z = (grid_z + @as(i32, oz)) * 16 + @as(i32, packed_value & 15);
                const delta_x = center_x - x;
                const delta_y = center_y - y;
                const delta_z = center_z - z;
                result.indices[order] = cache_index;
                result.distances[order] = delta_x * delta_x + delta_y * delta_y + delta_z * delta_z;
                result.delta_z[order] = delta_z;
                result.delta_y[order] = delta_y;
            }
        }
    }
    return result;
}

const AquiferCenters = struct {
    grid_x: i32,
    grid_y: i32,
    grid_z: i32,
    indices: [12]usize,
    x: [12]i32,
    y: [12]i32,
    z: [12]i32,
};

fn aquifer_search_cached(cache: *[16]AquiferCenters, valid: *u16, packed_locations: []const c.jshort, min_grid_x: i32, min_grid_y: i32, min_grid_z: i32, grid_size_x: i32, grid_size_z: i32, grid_x: i32, grid_y: i32, grid_z: i32, x: i32, y: i32, z: i32) ?AquiferSearch {
    const slot: usize = @intCast((@as(u32, @bitCast(grid_y)) & 3) * 4 + (@as(u32, @bitCast(grid_x)) & 1) * 2 + (@as(u32, @bitCast(grid_z)) & 1));
    const mask: u16 = @as(u16, 1) << @as(u4, @intCast(slot));
    const centers = &cache[slot];
    if (valid.* & mask == 0 or centers.grid_x != grid_x or centers.grid_y != grid_y or centers.grid_z != grid_z) {
        const local_x = grid_x - min_grid_x;
        const local_y = grid_y - min_grid_y;
        const local_z = grid_z - min_grid_z;
        if (local_x < 0 or local_y < 1 or local_z < 0 or local_x + 1 >= grid_size_x or local_z + 1 >= grid_size_z) return null;
        const sx: usize = @intCast(grid_size_x);
        const sz: usize = @intCast(grid_size_z);
        const lx: usize = @intCast(local_x);
        const ly: usize = @intCast(local_y);
        const lz: usize = @intCast(local_z);
        if (((ly + 1) * sz + lz + 1) * sx + lx + 1 >= packed_locations.len) return null;
        inline for (0..2) |ox| {
            inline for (0..3) |oy| {
                const row = ((ly + oy - 1) * sz + lz) * sx + lx + ox;
                inline for (0..2) |oz| {
                    const order = (ox * 3 + oy) * 2 + oz;
                    const index = row + oz * sx;
                    const packed_value: u16 = @bitCast(packed_locations.ptr[index]);
                    centers.indices[order] = index;
                    centers.x[order] = (grid_x + @as(i32, ox)) * 16 + @as(i32, packed_value >> 8);
                    centers.y[order] = (grid_y + @as(i32, oy) - 1) * 12 + @as(i32, (packed_value >> 4) & 15);
                    centers.z[order] = (grid_z + @as(i32, oz)) * 16 + @as(i32, packed_value & 15);
                }
            }
        }
        centers.grid_x = grid_x;
        centers.grid_y = grid_y;
        centers.grid_z = grid_z;
        valid.* |= mask;
    }
    var result: AquiferSearch = undefined;
    result.grid_z = grid_z;
    result.grid_y = grid_y;
    result.y = y;
    inline for (0..12) |order| {
        const dx = centers.x[order] - x;
        const dy = centers.y[order] - y;
        const dz = centers.z[order] - z;
        result.indices[order] = centers.indices[order];
        result.distances[order] = dx * dx + dy * dy + dz * dz;
        result.delta_z[order] = dz;
        result.delta_y[order] = dy;
    }
    return result;
}

fn aquifer_maximum_fluid_level(fluid_levels: []const c.jint, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, grid_x: i32, grid_y: i32, grid_z: i32) ?i32 {
    const local_grid_x = grid_x - min_grid_x;
    const local_grid_y = grid_y - min_grid_y;
    const local_grid_z = grid_z - min_grid_z;
    if (local_grid_x < 0 or local_grid_y < 1 or local_grid_z < 0 or local_grid_x + 1 >= grid_size_x or local_grid_z + 1 >= grid_size_z) {
        return null;
    }
    const size_x: usize = @intCast(grid_size_x);
    const size_z: usize = @intCast(grid_size_z);
    var maximum: i32 = std.math.minInt(i32);
    var offset_x: i32 = 0;
    while (offset_x <= 1) : (offset_x += 1) {
        var offset_y: i32 = -1;
        while (offset_y <= 1) : (offset_y += 1) {
            const local_y = local_grid_y + offset_y;
            if (local_y < 0) return null;
            const row: usize = (@as(usize, @intCast(local_y)) * size_z + @as(usize, @intCast(local_grid_z))) * size_x + @as(usize, @intCast(local_grid_x + offset_x));
            var offset_z: i32 = 0;
            while (offset_z <= 1) : (offset_z += 1) {
                const cache_index = row + @as(usize, @intCast(offset_z)) * size_x;
                if (cache_index >= fluid_levels.len) return null;
                maximum = @max(maximum, fluid_levels[cache_index]);
            }
        }
    }
    return maximum;
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
    else if (3.0 + boundary > 0.0) (3.0 + boundary) / 3.0 else (3.0 + boundary) / 10.0;
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
    else if (3.0 + boundary > 0.0) (3.0 + boundary) / 3.0 else (3.0 + boundary) / 10.0;
    const barrier_value = if (pressure < -2.0 or pressure > 2.0) 0.0 else barrier;
    return 2.0 * (barrier_value + pressure);
}

const DensityRangeFrame = struct {
    base: usize,
    outside: usize,
    end: usize,
    minimum: f64,
    maximum: f64,
    mode: u8,
};

fn evaluate_density_program(program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: []c.jdouble) bool {
    if (width == 4 and is_fused_terrain_program(program)) {
        if (comptime builtin.cpu.arch == .x86_64 and !avx_only) {
            if (adrenaline_has_avx2() != 0) {
                adrenaline_fused_terrain_avx2(program.ptr, height, output.ptr);
                return true;
            }
        }
        evaluate_fused_terrain(program, height, output);
        return true;
    }
    return evaluate_density_program_generic(program, base_x, base_y, base_z, width, height, output);
}

fn is_fused_terrain_program(program: []const u8) bool {
    if (program.len != 388) return false;
    const offsets = [_]usize{ 0, 65, 74, 75, 76, 141, 150, 215, 280, 281, 346, 347, 348, 357, 358, 359, 376, 377, 386, 387 };
    const opcodes = [_]u8{ 15, 2, 4, 11, 15, 2, 15, 15, 6, 15, 6, 13, 2, 4, 3, 14, 12, 2, 3, 0 };
    inline for (offsets, opcodes) |offset, opcode| {
        if (program[offset] != opcode) return false;
    }
    inline for (.{ 1, 77, 151, 216, 282 }) |offset| {
        inline for (0..8) |i| {
            if (!(@abs(density_program_value(program, offset + i * 8)) <= 1.0e100)) return false;
        }
    }
    inline for (.{ 66, 142, 349, 378 }) |offset| {
        if (!(@abs(density_program_value(program, offset)) <= 1.0e100)) return false;
    }
    return true;
}

fn density_program_value(program: []const u8, offset: usize) f64 {
    return @bitCast(std.mem.readInt(u64, program[offset..][0..8], .little));
}

const DensityCorners = struct {
    values: [8]f64,

    fn load(program: []const u8, offset: usize) DensityCorners {
        var result: DensityCorners = undefined;
        inline for (0..8) |i| result.values[i] = density_program_value(program, offset + i * 8);
        return result;
    }

    fn y(self: DensityCorners, fy: f64) [4]f64 {
        const a = self.values;
        return .{ a[0] + fy * (a[4] - a[0]), a[2] + fy * (a[6] - a[2]), a[1] + fy * (a[5] - a[1]), a[3] + fy * (a[7] - a[3]) };
    }

    fn row(corners: [4]f64, fx: f64) [2]f64 {
        return .{ corners[0] + fx * (corners[1] - corners[0]), corners[2] + fx * (corners[3] - corners[2]) };
    }

    fn sample(row_values: [2]f64) Vec4 {
        const z: Vec4 = .{ 0.0, 0.25, 0.5, 0.75 };
        return @as(Vec4, @splat(row_values[0])) + z * @as(Vec4, @splat(row_values[1] - row_values[0]));
    }
};

fn density_min4(left: Vec4, right: Vec4) Vec4 {
    const zero: Vec4 = @splat(0.0);
    const signed_zero: Vec4 = @bitCast(@as(@Vector(4, u64), @bitCast(left)) | @as(@Vector(4, u64), @bitCast(right)));
    const result = @select(f64, (left == zero) & (right == zero), signed_zero, @min(left, right));
    return @select(f64, left != left, left, @select(f64, right != right, right, result));
}

fn density_max4(left: Vec4, right: Vec4) Vec4 {
    const zero: Vec4 = @splat(0.0);
    const signed_zero: Vec4 = @bitCast(@as(@Vector(4, u64), @bitCast(left)) & @as(@Vector(4, u64), @bitCast(right)));
    const result = @select(f64, (left == zero) & (right == zero), signed_zero, @max(left, right));
    return @select(f64, left != left, left, @select(f64, right != right, right, result));
}

pub fn evaluate_fused_terrain(program: []const u8, height: usize, output: []c.jdouble) void {
    @setFloatMode(.strict);
    const terrain = DensityCorners.load(program, 1);
    const toggle = DensityCorners.load(program, 77);
    const thickness = DensityCorners.load(program, 151);
    const ridge_a = DensityCorners.load(program, 216);
    const ridge_b = DensityCorners.load(program, 282);
    const terrain_scale: Vec4 = @splat(density_program_value(program, 66));
    const inside_value: Vec4 = @splat(density_program_value(program, 142));
    const ridge_scale: Vec4 = @splat(density_program_value(program, 349));
    const minimum: Vec4 = @splat(density_program_value(program, 360));
    const maximum: Vec4 = @splat(density_program_value(program, 368));
    const offset: Vec4 = @splat(density_program_value(program, 378));
    const low: Vec4 = @splat(-1.0);
    const high: Vec4 = @splat(1.0);
    var y: usize = 0;
    while (y < height) : (y += 1) {
        const fy = @as(f64, @floatFromInt(y)) / @as(f64, @floatFromInt(height));
        const terrain_y = terrain.y(fy);
        const toggle_y = toggle.y(fy);
        var thickness_y: [4]f64 = undefined;
        var ridge_a_y: [4]f64 = undefined;
        var ridge_b_y: [4]f64 = undefined;
        var outside_ready = false;
        var x: usize = 0;
        while (x < 4) : (x += 1) {
            const fx = @as(f64, @floatFromInt(x)) / 4.0;
            const raw = DensityCorners.sample(DensityCorners.row(terrain_y, fx)) * terrain_scale;
            const clamped = @select(f64, raw < low, low, @select(f64, raw > high, high, raw));
            const squeezed = clamped / @as(Vec4, @splat(2.0)) - clamped * clamped * clamped / @as(Vec4, @splat(24.0));
            const choice = DensityCorners.sample(DensityCorners.row(toggle_y, fx));
            const inside = (choice >= minimum) & (choice < maximum);
            var noodle = inside_value;
            if (!@reduce(.And, inside)) {
                if (!outside_ready) {
                    thickness_y = thickness.y(fy);
                    ridge_a_y = ridge_a.y(fy);
                    ridge_b_y = ridge_b.y(fy);
                    outside_ready = true;
                }
                const a = @abs(DensityCorners.sample(DensityCorners.row(ridge_a_y, fx)));
                const b = @abs(DensityCorners.sample(DensityCorners.row(ridge_b_y, fx)));
                const outside = DensityCorners.sample(DensityCorners.row(thickness_y, fx)) + density_max4(a, b) * ridge_scale;
                noodle = @select(f64, inside, inside_value, outside);
            }
            const result = density_min4(squeezed, noodle) + offset;
            const row = ((height - 1 - y) * 4 + x) * 4;
            output[row..][0..4].* = @bitCast(result);
        }
    }
}

noinline fn evaluate_density_program_generic(program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: []c.jdouble) bool {
    var stack: [max_density_stack][max_density_values]f64 = undefined;
    var interpolation_width: [max_density_values]f64 = undefined;
    var interpolation_height: [max_density_values]f64 = undefined;
    var interpolation_ready = false;
    var stack_size: usize = 0;
    var program_counter: usize = 0;
    var range_frames: [max_density_stack]DensityRangeFrame = undefined;
    var range_count: usize = 0;
    const total = output.len;
    while (program_counter < program.len) {
        const opcode = read_program_u8(program, &program_counter) orelse return false;
        switch (opcode) {
            0 => {
                if (stack_size != 1 or range_count != 0 or program_counter != program.len) {
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
                @memset(stack[stack_size][0..total], value);
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
            20 => {
                if (stack_size < 2) return false;
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* = if (left.* == 0.0) 0.0 else left.* * right;
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
                    left.* = if (std.math.isNan(left.*)) left.* else if (std.math.isNan(right)) right
                        else if (left.* == 0.0 and right == 0.0) @bitCast(@as(u64, @bitCast(left.*)) | @as(u64, @bitCast(right)))
                        else @min(left.*, right);
                }
                stack_size -= 1;
            },
            13 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* = if (std.math.isNan(left.*)) left.* else if (std.math.isNan(right)) right
                        else if (left.* == 0.0 and right == 0.0) @bitCast(@as(u64, @bitCast(left.*)) & @as(u64, @bitCast(right)))
                        else @max(left.*, right);
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
            16 => {
                if (stack_size == 0 or range_count >= range_frames.len) return false;
                const minimum = read_program_f64(program, &program_counter) orelse return false;
                const maximum = read_program_f64(program, &program_counter) orelse return false;
                const outside: usize = read_program_u32(program, &program_counter) orelse return false;
                const end: usize = read_program_u32(program, &program_counter) orelse return false;
                if (outside <= program_counter or end <= outside or end >= program.len) return false;
                var any_inside = false;
                var any_outside = false;
                for (stack[stack_size - 1][0..total]) |value| {
                    if (value >= minimum and value < maximum) {
                        any_inside = true;
                    } else {
                        any_outside = true;
                    }
                    if (any_inside and any_outside) break;
                }
                const mode: u8 = if (any_inside and any_outside) 0 else if (any_inside) 1 else 2;
                range_frames[range_count] = .{ .base = stack_size - 1, .outside = outside, .end = end, .minimum = minimum, .maximum = maximum, .mode = mode };
                range_count += 1;
                if (mode != 0) stack_size -= 1;
                if (mode == 2) program_counter = outside;
            },
            17 => {
                if (range_count == 0) return false;
                const frame = range_frames[range_count - 1];
                if (program_counter != frame.outside) return false;
                if (frame.mode == 1) {
                    if (stack_size != frame.base + 1) return false;
                    program_counter = frame.end;
                    range_count -= 1;
                } else if (frame.mode != 0 or stack_size != frame.base + 2) {
                    return false;
                }
            },
            18 => {
                if (range_count == 0) return false;
                const frame = range_frames[range_count - 1];
                if (program_counter != frame.end) return false;
                if (frame.mode == 0) {
                    if (stack_size != frame.base + 3) return false;
                    for (stack[frame.base][0..total], stack[frame.base + 1][0..total], stack[frame.base + 2][0..total]) |*input, inside, outside| {
                        input.* = if (input.* >= frame.minimum and input.* < frame.maximum) inside else outside;
                    }
                    stack_size -= 2;
                } else if (frame.mode != 2 or stack_size != frame.base + 1) {
                    return false;
                }
                range_count -= 1;
            },
            19 => {
                if (stack_size == 0) return false;
                const operation = read_program_u8(program, &program_counter) orelse return false;
                const bound = read_program_f64(program, &program_counter) orelse return false;
                const end: usize = read_program_u32(program, &program_counter) orelse return false;
                if (operation < 1 or operation > 3 or end <= program_counter or end >= program.len) return false;
                var skip = true;
                for (stack[stack_size - 1][0..total]) |value| {
                    const shortcut = switch (operation) {
                        1 => value == 0.0,
                        2 => value < bound,
                        3 => value > bound,
                        else => unreachable,
                    };
                    if (!shortcut) {
                        skip = false;
                        break;
                    }
                }
                if (skip) {
                    if (operation == 1) @memset(stack[stack_size - 1][0..total], 0.0);
                    program_counter = end;
                }
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
                if (!interpolation_ready) {
                    var interpolation_index: usize = 0;
                    while (interpolation_index < width) : (interpolation_index += 1) {
                        interpolation_width[interpolation_index] = @as(f64, @floatFromInt(interpolation_index)) / @as(f64, @floatFromInt(width));
                    }
                    interpolation_index = 0;
                    while (interpolation_index < height) : (interpolation_index += 1) {
                        interpolation_height[interpolation_index] = @as(f64, @floatFromInt(interpolation_index)) / @as(f64, @floatFromInt(height));
                    }
                    interpolation_ready = true;
                }
                if (stack_size == 0 and program_counter + 1 == program.len and program[program_counter] == 0) {
                    var direct_y: usize = 0;
                    while (direct_y < height) : (direct_y += 1) {
                        const fy = interpolation_height[direct_y];
                        const corner00 = noise000 + fy * (noise010 - noise000);
                        const corner10 = noise100 + fy * (noise110 - noise100);
                        const corner01 = noise001 + fy * (noise011 - noise001);
                        const corner11 = noise101 + fy * (noise111 - noise101);
                        var direct_x: usize = 0;
                        while (direct_x < width) : (direct_x += 1) {
                            const fx = interpolation_width[direct_x];
                            const corner0 = corner00 + fx * (corner10 - corner00);
                            const corner1 = corner01 + fx * (corner11 - corner01);
                            const row = ((height - 1 - direct_y) * width + direct_x) * width;
                            if (width == 4) {
                                const z4: Vec4 = @bitCast(interpolation_width[0..4].*);
                                const result = @as(Vec4, @splat(corner0)) + z4 * @as(Vec4, @splat(corner1 - corner0));
                                output[row..][0..4].* = @bitCast(result);
                                continue;
                            }
                            var direct_z: usize = 0;
                            while (direct_z < width) : (direct_z += 1) {
                                const fz = interpolation_width[direct_z];
                                output[row + direct_z] = corner0 + fz * (corner1 - corner0);
                            }
                        }
                    }
                    return true;
                }
                var y_corners: [max_density_values][4]f64 = undefined;
                var y_index: usize = 0;
                while (y_index < height) : (y_index += 1) {
                    const y_lerp = interpolation_height[y_index];
                    y_corners[y_index] = .{
                        noise000 + y_lerp * (noise010 - noise000),
                        noise100 + y_lerp * (noise110 - noise100),
                        noise001 + y_lerp * (noise011 - noise001),
                        noise101 + y_lerp * (noise111 - noise101),
                    };
                }
                var x_corners: [max_density_values][2]f64 = undefined;
                var x_index: usize = 0;
                while (x_index < width) : (x_index += 1) {
                    const x_lerp = interpolation_width[x_index];
                    y_index = 0;
                    while (y_index < height) : (y_index += 1) {
                        const corners = y_corners[y_index];
                        x_corners[y_index] = .{
                            corners[0] + x_lerp * (corners[1] - corners[0]),
                            corners[2] + x_lerp * (corners[3] - corners[2]),
                        };
                    }
                    var z_index: usize = 0;
                    while (z_index < width) : (z_index += 1) {
                        const z_lerp = interpolation_width[z_index];
                        const offset = (x_index * width + z_index) * height;
                        y_index = 0;
                        while (y_index < height) : (y_index += 1) {
                            const corners = x_corners[y_index];
                            stack[stack_size][offset + y_index] = corners[0] + z_lerp * (corners[1] - corners[0]);
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

fn scale_f64_values(comptime avx2: bool, values: []f64, factor: f64) void {
    @setFloatMode(.strict);
    var index: usize = 0;
    if (comptime avx2) {
        const factor4: Vec4 = @splat(factor);
        while (index + 3 < values.len) : (index += 4) {
            const current: Vec4 = @bitCast(values[index..][0..4].*);
            values[index..][0..4].* = @bitCast(current * factor4);
        }
    }
    const factor2: Vec2 = @splat(factor);
    while (index + 1 < values.len) : (index += 2) {
        const current: Vec2 = @bitCast(values[index..][0..2].*);
        values[index..][0..2].* = @bitCast(current * factor2);
    }
    while (index < values.len) : (index += 1) {
        values[index] *= factor;
    }
}

fn scale_f32_values(comptime avx2: bool, values: []f32, factor: f32) void {
    var index: usize = 0;
    if (comptime avx2) {
        const factor8: Vec8f = @splat(factor);
        while (index + 7 < values.len) : (index += 8) {
            const current: Vec8f = @bitCast(values[index..][0..8].*);
            values[index..][0..8].* = @bitCast(current * factor8);
        }
    }
    const factor4: Vec4f = @splat(factor);
    while (index + 3 < values.len) : (index += 4) {
        const current: Vec4f = @bitCast(values[index..][0..4].*);
        values[index..][0..4].* = @bitCast(current * factor4);
    }
    while (index < values.len) : (index += 1) {
        values[index] *= factor;
    }
}

fn normal_noise_grid(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []c.jdouble) void {
    normal_noise_grid_impl(false, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

pub fn normal_noise_grid_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    normal_noise_grid_impl(true, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, y_count, z_count, values);
}

fn normal_noise_grid_impl(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    if (y_step == 0.0 and y_count > 1) {
        const columns = x_count * z_count;
        normal_noise_grid_impl(avx2, first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_count, 1, z_count, values[0..columns]);
        var column = columns;
        while (column > 0) {
            column -= 1;
            const value = values[column];
            @memset(values[column * y_count ..][0..y_count], value);
        }
        return;
    }
    @memset(values, 0.0);
    perlin_value_grid(avx2, first, first_count, x, y, z, x_step, y_step, z_step, 1.0, x_count, y_count, z_count, values);
    perlin_value_grid(avx2, second, second_count, x, y, z, x_step, y_step, z_step, normal_noise_input_factor, x_count, y_count, z_count, values);
    scale_f64_values(avx2, values, value_factor);
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
    @memset(values, 0.0);
    perlin_value_grid_approx(avx2, first, first_count, @floatCast(x), @floatCast(y), @floatCast(z), @floatCast(x_step), @floatCast(y_step), @floatCast(z_step), 1.0, x_count, y_count, z_count, values);
    perlin_value_grid_approx(avx2, second, second_count, @floatCast(x), @floatCast(y), @floatCast(z), @floatCast(x_step), @floatCast(y_step), @floatCast(z_step), @floatCast(normal_noise_input_factor), x_count, y_count, z_count, values);
    const factor: f32 = @floatCast(value_factor);
    scale_f32_values(avx2, values, factor);
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
                                    const amplitude8: Vec8f = @splat(amplitude);
                                    const octave_factor8: Vec8f = @splat(octave_factor);
                                    while (y_index + 7 < y_end) : (y_index += 8) {
                                        const sampled = line.sample8(.{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3], fraction_y[y_index + 4], fraction_y[y_index + 5], fraction_y[y_index + 6], fraction_y[y_index + 7] }, smooth_x[x_index], .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3], smooth_y[y_index + 4], smooth_y[y_index + 5], smooth_y[y_index + 6], smooth_y[y_index + 7] }, smooth_z[z_index]);
                                        const value_index = offset + y_index;
                                        const current: Vec8f = @bitCast(values[value_index..][0..8].*);
                                        values[value_index..][0..8].* = @bitCast(current + amplitude8 * sampled * octave_factor8);
                                    }
                                }
                                const amplitude4: Vec4f = @splat(amplitude);
                                const octave_factor4: Vec4f = @splat(octave_factor);
                                while (y_index + 3 < y_end) : (y_index += 4) {
                                    const sampled = line.sample4(.{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3] }, smooth_x[x_index], .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3] }, smooth_z[z_index]);
                                    const value_index = offset + y_index;
                                    const current: Vec4f = @bitCast(values[value_index..][0..4].*);
                                    values[value_index..][0..4].* = @bitCast(current + amplitude4 * sampled * octave_factor4);
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
            quick_hash(seed, x, y, z),           quick_hash(seed, x +% 1, y, z),
            quick_hash(seed, x, y +% 1, z),      quick_hash(seed, x +% 1, y +% 1, z),
            quick_hash(seed, x, y, z +% 1),      quick_hash(seed, x +% 1, y, z +% 1),
            quick_hash(seed, x, y +% 1, z +% 1), quick_hash(seed, x +% 1, y +% 1, z +% 1),
        } };
    }

    fn line(self: QuickGradientCell, x: f32, z: f32) QuickGradientLine {
        return .{ .values = .{
            quick_gradient_term(self.values[0], x, z, 0.0),        quick_gradient_term(self.values[1], x - 1.0, z, 0.0),
            quick_gradient_term(self.values[2], x, z, -1.0),       quick_gradient_term(self.values[3], x - 1.0, z, -1.0),
            quick_gradient_term(self.values[4], x, z - 1.0, 0.0),  quick_gradient_term(self.values[5], x - 1.0, z - 1.0, 0.0),
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

pub const BlendedNoiseConfig = extern struct {
    xz_multiplier: f64,
    y_multiplier: f64,
    xz_factor: f64,
    y_factor: f64,
    smear_scale_multiplier: f64,
};

pub fn blended_noise_grid_avx2(min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const BlendedNoiseConfig, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    blended_noise_grid_impl(true, min, max, main, config, base_x, base_y, base_z, step_x, step_y, step_z, x_count, y_count, z_count, values);
}

fn blended_noise_grid_impl(comptime avx2: bool, min: [*]const u8, max: [*]const u8, main: [*]const u8, config: *const BlendedNoiseConfig, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    @setFloatMode(.strict);
    var limit_y: [max_grid_axis]f64 = undefined;
    var main_y: [max_grid_axis]f64 = undefined;
    for (0..y_count) |i| {
        const y = base_y +% @as(c.jint, @intCast(i)) *% step_y;
        limit_y[i] = @as(f64, @floatFromInt(y)) * config.y_multiplier;
        main_y[i] = limit_y[i] / config.y_factor;
    }
    var limit_x: [max_grid_axis]f64 = undefined;
    var main_x: [max_grid_axis]f64 = undefined;
    var limit_z: [max_grid_axis]f64 = undefined;
    var main_z: [max_grid_axis]f64 = undefined;
    for (0..x_count) |i| {
        limit_x[i] = @as(f64, @floatFromInt(base_x +% @as(c.jint, @intCast(i)) *% step_x)) * config.xz_multiplier;
        main_x[i] = limit_x[i] / config.xz_factor;
    }
    for (0..z_count) |i| {
        limit_z[i] = @as(f64, @floatFromInt(base_z +% @as(c.jint, @intCast(i)) *% step_z)) * config.xz_multiplier;
        main_z[i] = limit_z[i] / config.xz_factor;
    }
    const smear = config.y_multiplier * config.smear_scale_multiplier;
    var selector: [max_grid_size]f64 = undefined;
    var low: [max_grid_size]f64 = undefined;
    var high: [max_grid_size]f64 = undefined;
    const total = values.len;
    @memset(selector[0..total], 0.0);
    blended_octaves(avx2, .all, main, 8, main_x[0..x_count], main_z[0..z_count], main_y[0..y_count], smear / config.y_factor, selector[0..total], selector[0..total]);
    for (selector[0..total]) |*entry| entry.* = (entry.* / 10.0 + 1.0) / 2.0;
    @memset(low[0..total], 0.0);
    @memset(high[0..total], 0.0);
    blended_octaves(avx2, .lower, min, 16, limit_x[0..x_count], limit_z[0..z_count], limit_y[0..y_count], smear, selector[0..total], low[0..total]);
    blended_octaves(avx2, .upper, max, 16, limit_x[0..x_count], limit_z[0..z_count], limit_y[0..y_count], smear, selector[0..total], high[0..total]);
    for (values, low[0..total], high[0..total], selector[0..total]) |*entry, l, h, q| {
        const start = l / 512.0;
        const end = h / 512.0;
        entry.* = (if (q < 0.0) start else if (q > 1.0) end else lerp(q, start, end)) / 128.0;
    }
}

const BlendedStack = enum { all, lower, upper };

fn blended_active(comptime kind: BlendedStack, selector: []const f64, index: usize) bool {
    return switch (kind) {
        .all => true,
        .lower => !(selector[index] >= 1.0),
        .upper => !(selector[index] <= 0.0),
    };
}

fn blended_octaves(comptime avx2: bool, comptime kind: BlendedStack, data: [*]const u8, octaves: usize, raw_x: []const f64, raw_z: []const f64, raw_y: []const f64, smear: f64, selectors: []const f64, outputs: []f64) void {
    @setFloatMode(.strict);
    const origin_offset = 16 + octaves * 8;
    const active_offset = origin_offset + octaves * 24;
    const permutation_offset = active_offset + octaves;
    var input_factor: f64 = 1.0;
    var integer_y: [max_grid_axis]i32 = undefined;
    var adjusted_y: [max_grid_axis]f64 = undefined;
    var fade_y: [max_grid_axis]f64 = undefined;
    for (0..octaves) |level| {
        const octave = octaves - 1 - level;
        defer input_factor /= 2.0;
        if (data[active_offset + octave] == 0) continue;
        const origin = origin_offset + octave * 24;
        const permutation = data + permutation_offset + octave * 256;
        var integer_z: [max_grid_axis]i32 = undefined;
        var fraction_z: [max_grid_axis]f64 = undefined;
        var fade_z: [max_grid_axis]f64 = undefined;
        for (raw_z, 0..) |z, i| {
            const shifted = wrap(z * input_factor) + read_f64(data, origin + 16);
            integer_z[i] = floor_int(shifted);
            fraction_z[i] = shifted - @as(f64, @floatFromInt(integer_z[i]));
            fade_z[i] = smoothstep(fraction_z[i]);
        }
        const yo = read_f64(data, origin + 8);
        const y_scale = smear * input_factor;
        for (raw_y, 0..) |y, i| {
            const y_max = y * input_factor;
            const shifted = wrap(y_max) + yo;
            const iy = floor_int(shifted);
            const fy = shifted - @as(f64, @floatFromInt(iy));
            const limit = if (y_max >= 0.0 and y_max < fy) y_max else fy;
            const offset = if (y_scale != 0.0) @as(f64, @floatFromInt(floor_int(limit / y_scale + @as(f64, @as(f32, 1.0e-7))))) * y_scale else 0.0;
            integer_y[i] = iy;
            adjusted_y[i] = fy - offset;
            fade_y[i] = smoothstep(fy);
        }
        for (raw_x, 0..) |x, ix| {
            const shifted_x = wrap(x * input_factor) + read_f64(data, origin);
            const integer_x = floor_int(shifted_x);
            const fx = shifted_x - @as(f64, @floatFromInt(integer_x));
            const sx = smoothstep(fx);
            const x_hash = permutation_value(permutation, integer_x);
            const next_x_hash = permutation_value(permutation, integer_x + 1);
            var xy0: [max_grid_axis]i32 = undefined;
            var xy1: [max_grid_axis]i32 = undefined;
            var xn0: [max_grid_axis]i32 = undefined;
            var xn1: [max_grid_axis]i32 = undefined;
            for (integer_y[0..raw_y.len], 0..) |iy, j| {
                xy0[j] = permutation_value(permutation, x_hash + iy);
                xy1[j] = permutation_value(permutation, x_hash + iy + 1);
                xn0[j] = permutation_value(permutation, next_x_hash + iy);
                xn1[j] = permutation_value(permutation, next_x_hash + iy + 1);
            }
            for (raw_z, 0..) |_, iz_index| {
                const iz = integer_z[iz_index];
                const fz = fraction_z[iz_index];
                const sz = fade_z[iz_index];
                const offset = (ix * raw_z.len + iz_index) * raw_y.len;
                const selector = selectors[offset..][0..raw_y.len];
                const output = outputs[offset..][0..raw_y.len];
                var previous_y: i32 = std.math.minInt(i32);
                var line: GradientLine = undefined;
                var i: usize = 0;
                while (i < output.len) {
                    if (!blended_active(kind, selector, i)) {
                        i += 1;
                        continue;
                    }
                    if (comptime avx2) {
                        if (i + 3 < output.len and blended_active(kind, selector, i + 1) and blended_active(kind, selector, i + 2) and blended_active(kind, selector, i + 3)) {
                            const ys: Vec4i = @bitCast(integer_y[i..][0..4].*);
                            const y4: Vec4 = @bitCast(adjusted_y[i..][0..4].*);
                            const fade4: Vec4 = @bitCast(fade_y[i..][0..4].*);
                            const sampled = if (@reduce(.And, ys == @as(Vec4i, @splat(ys[0])))) block: {
                                if (ys[0] != previous_y) {
                                    line = GradientCell.init_cached(permutation, xy0[i], xy1[i], xn0[i], xn1[i], iz).line_paired(fx, fz);
                                    previous_y = ys[0];
                                }
                                break :block line.sample_smoothed_shifted4(y4, sx, fade4, sz);
                            } else blended_sample(4, permutation, @bitCast(xy0[i..][0..4].*), @bitCast(xy1[i..][0..4].*), @bitCast(xn0[i..][0..4].*), @bitCast(xn1[i..][0..4].*), iz, fx, y4, fz, sx, fade4, sz);
                            const current: Vec4 = @bitCast(output[i..][0..4].*);
                            output[i..][0..4].* = @bitCast(current + sampled / @as(Vec4, @splat(input_factor)));
                            i += 4;
                            continue;
                        }
                    }
                    if (i + 1 < output.len and blended_active(kind, selector, i + 1) and integer_y[i] != integer_y[i + 1]) {
                        const y2: Vec2 = @bitCast(adjusted_y[i..][0..2].*);
                        const fade2: Vec2 = @bitCast(fade_y[i..][0..2].*);
                        const sampled = blended_sample(2, permutation, @bitCast(xy0[i..][0..2].*), @bitCast(xy1[i..][0..2].*), @bitCast(xn0[i..][0..2].*), @bitCast(xn1[i..][0..2].*), iz, fx, y2, fz, sx, fade2, sz);
                        const current: Vec2 = @bitCast(output[i..][0..2].*);
                        output[i..][0..2].* = @bitCast(current + sampled / @as(Vec2, @splat(input_factor)));
                        i += 2;
                        continue;
                    }
                    const iy = integer_y[i];
                    if (iy != previous_y) {
                        line = GradientCell.init_cached(permutation, xy0[i], xy1[i], xn0[i], xn1[i], iz).line_paired(fx, fz);
                        previous_y = iy;
                    }
                    var end = i + 1;
                    while (end < output.len and blended_active(kind, selector, end) and integer_y[end] == iy) : (end += 1) {}
                    while (i + 1 < end) : (i += 2) {
                        const y2: Vec2 = @bitCast(adjusted_y[i..][0..2].*);
                        const fade2: Vec2 = @bitCast(fade_y[i..][0..2].*);
                        const sampled = line.sample_smoothed_shifted2(y2, sx, fade2, sz);
                        const current: Vec2 = @bitCast(output[i..][0..2].*);
                        output[i..][0..2].* = @bitCast(current + sampled / @as(Vec2, @splat(input_factor)));
                    }
                    while (i < end) : (i += 1) {
                        output[i] += line.sample_smoothed_shifted(adjusted_y[i], sx, fade_y[i], sz) / input_factor;
                    }
                }
            }
        }
    }
}

fn blended_sample(comptime lanes: usize, permutation: [*]const u8, xy: @Vector(lanes, i32), xy_next: @Vector(lanes, i32), next_xy: @Vector(lanes, i32), next_xy_next: @Vector(lanes, i32), z: i32, fx: f64, fy: @Vector(lanes, f64), fz: f64, sx: f64, sy: @Vector(lanes, f64), sz: f64) @Vector(lanes, f64) {
    @setFloatMode(.strict);
    const V = @Vector(lanes, f64);
    var hashes: [8]@Vector(lanes, i32) = undefined;
    inline for (0..lanes) |lane| {
        hashes[0][lane] = permutation_value(permutation, xy[lane] + z);
        hashes[1][lane] = permutation_value(permutation, next_xy[lane] + z);
        hashes[2][lane] = permutation_value(permutation, xy_next[lane] + z);
        hashes[3][lane] = permutation_value(permutation, next_xy_next[lane] + z);
        hashes[4][lane] = permutation_value(permutation, xy[lane] + z + 1);
        hashes[5][lane] = permutation_value(permutation, next_xy[lane] + z + 1);
        hashes[6][lane] = permutation_value(permutation, xy_next[lane] + z + 1);
        hashes[7][lane] = permutation_value(permutation, next_xy_next[lane] + z + 1);
    }
    const x0: V = @splat(fx);
    const x1: V = @splat(fx - 1.0);
    const y1 = fy - @as(V, @splat(1.0));
    const z0: V = @splat(fz);
    const z1: V = @splat(fz - 1.0);
    const v000 = blended_gradient(lanes, hashes[0], x0, fy, z0);
    const v100 = blended_gradient(lanes, hashes[1], x1, fy, z0);
    const v010 = blended_gradient(lanes, hashes[2], x0, y1, z0);
    const v110 = blended_gradient(lanes, hashes[3], x1, y1, z0);
    const v001 = blended_gradient(lanes, hashes[4], x0, fy, z1);
    const v101 = blended_gradient(lanes, hashes[5], x1, fy, z1);
    const v011 = blended_gradient(lanes, hashes[6], x0, y1, z1);
    const v111 = blended_gradient(lanes, hashes[7], x1, y1, z1);
    return if (lanes == 4) lerp3_4(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111) else lerp3_2(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111);
}

fn blended_gradient(comptime lanes: usize, hash: @Vector(lanes, i32), x: @Vector(lanes, f64), y: @Vector(lanes, f64), z: @Vector(lanes, f64)) @Vector(lanes, f64) {
    const I = @Vector(lanes, i32);
    const h = hash & @as(I, @splat(15));
    const u = @select(f64, h < @as(I, @splat(8)), x, y);
    const v = @select(f64, h < @as(I, @splat(4)), y, @select(f64, (h == @as(I, @splat(12))) | (h == @as(I, @splat(14))), x, z));
    return @select(f64, (h & @as(I, @splat(1))) == @as(I, @splat(0)), u, -u) + @select(f64, (h & @as(I, @splat(2))) == @as(I, @splat(0)), v, -v);
}

fn sample_distinct_y(comptime lanes: usize, permutation: [*]const u8, x_hash: i32, next_x_hash: i32, y: @Vector(lanes, i32), z: i32, fx: f64, fy: @Vector(lanes, f64), fz: f64, sx: f64, sy: @Vector(lanes, f64), sz: f64) @Vector(lanes, f64) {
    @setFloatMode(.strict);
    const V = @Vector(lanes, f64);
    var hashes: [8]@Vector(lanes, i32) = undefined;
    inline for (0..lanes) |lane| {
        const xy = permutation_value(permutation, x_hash + y[lane]);
        const xy_next = permutation_value(permutation, x_hash + y[lane] + 1);
        const next_xy = permutation_value(permutation, next_x_hash + y[lane]);
        const next_xy_next = permutation_value(permutation, next_x_hash + y[lane] + 1);
        hashes[0][lane] = permutation_value(permutation, xy + z);
        hashes[1][lane] = permutation_value(permutation, next_xy + z);
        hashes[2][lane] = permutation_value(permutation, xy_next + z);
        hashes[3][lane] = permutation_value(permutation, next_xy_next + z);
        hashes[4][lane] = permutation_value(permutation, xy + z + 1);
        hashes[5][lane] = permutation_value(permutation, next_xy + z + 1);
        hashes[6][lane] = permutation_value(permutation, xy_next + z + 1);
        hashes[7][lane] = permutation_value(permutation, next_xy_next + z + 1);
    }
    const x0: V = @splat(fx);
    const x1: V = @splat(fx - 1.0);
    const y1 = fy - @as(V, @splat(1.0));
    const z0: V = @splat(fz);
    const z1: V = @splat(fz - 1.0);
    const v000 = gradient_vector(lanes, hashes[0], x0, fy, z0);
    const v100 = gradient_vector(lanes, hashes[1], x1, fy, z0);
    const v010 = gradient_vector(lanes, hashes[2], x0, y1, z0);
    const v110 = gradient_vector(lanes, hashes[3], x1, y1, z0);
    const v001 = gradient_vector(lanes, hashes[4], x0, fy, z1);
    const v101 = gradient_vector(lanes, hashes[5], x1, fy, z1);
    const v011 = gradient_vector(lanes, hashes[6], x0, y1, z1);
    const v111 = gradient_vector(lanes, hashes[7], x1, y1, z1);
    return if (lanes == 8) lerp3_8(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111) else if (lanes == 4) lerp3_4(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111) else lerp3_2(@splat(sx), sy, @splat(sz), v000, v100, v010, v110, v001, v101, v011, v111);
}

fn gradient_vector(comptime lanes: usize, hash: @Vector(lanes, i32), x: @Vector(lanes, f64), y: @Vector(lanes, f64), z: @Vector(lanes, f64)) @Vector(lanes, f64) {
    const I = @Vector(lanes, i32);
    const h = hash & @as(I, @splat(15));
    const u = @select(f64, h < @as(I, @splat(8)), x, y);
    const v = @select(f64, h < @as(I, @splat(4)), y, @select(f64, (h == @as(I, @splat(12))) | (h == @as(I, @splat(14))), x, z));
    return @select(f64, (h & @as(I, @splat(1))) == @as(I, @splat(0)), u, -u) + @select(f64, (h & @as(I, @splat(2))) == @as(I, @splat(0)), v, -v);
}

pub fn normal_noise_batch_avx2(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, values: []f64) void {
    normal_noise_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, values);
}

fn normal_noise_batch(first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, value_factor: f64, x: f64, y: f64, z: f64, y_step: f64, values: []c.jdouble) void {
    @memset(values, 0.0);
    perlin_value_batch(first, first_count, x, y, z, y_step, 1.0, values);
    perlin_value_batch(second, second_count, x, y, z, y_step, normal_noise_input_factor, values);
    scale_f64_values(false, values, value_factor);
}

fn perlin_value_batch(data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, y_step: f64, coordinate_scale: f64, values: []c.jdouble) void {
    if (@abs(y_step) >= 2.0) {
        return perlin_value_batch_impl(true, data, octaves, x, y, z, y_step, coordinate_scale, values);
    }
    return perlin_value_batch_impl(false, data, octaves, x, y, z, y_step, coordinate_scale, values);
}

fn perlin_value_batch_impl(comptime paired: bool, data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, y_step: f64, coordinate_scale: f64, values: []c.jdouble) void {
    @setFloatMode(.strict);
    if (values.len == 0) return;
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
            const smooth_x = smoothstep(fraction_x);
            const smooth_z = smoothstep(fraction_z);
            const x_hash = permutation_value(permutation, integer_x);
            const next_x_hash = permutation_value(permutation, integer_x + 1);
            const yo = read_f64(data, origin + 8);
            const first_y_scaled = y * coordinate_scale * input_factor;
            const last_y_scaled = (y + @as(f64, @floatFromInt(values.len - 1)) * y_step) * coordinate_scale * input_factor;
            const no_wrap_y = std.math.isFinite(first_y_scaled) and std.math.isFinite(last_y_scaled) and
                @abs(first_y_scaled) < 16777216.0 and @abs(last_y_scaled) < 16777216.0;

            var previous_integer_y: i32 = std.math.minInt(i32);
            var line: GradientLine = undefined;
            var index: usize = 0;
            while (index < values.len) {
                var shifted_y: f64 = undefined;
                var integer_y: i32 = undefined;
                if (comptime avx_only and !small_only) {
                    if (index + 7 < values.len and @abs(y_step * coordinate_scale * input_factor) >= 1.0) {
                        const indices: Vec8d = @floatFromInt(@as(Vec8i, .{ @intCast(index), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3), @intCast(index + 4), @intCast(index + 5), @intCast(index + 6), @intCast(index + 7) }));
                        const sample_y4 = @as(Vec8d, @splat(y)) + indices * @as(Vec8d, @splat(y_step));
                        const scaled4 = sample_y4 * @as(Vec8d, @splat(coordinate_scale)) * @as(Vec8d, @splat(input_factor));
                        const wrapped4 = if (no_wrap_y) scaled4 - @as(Vec8d, @splat(0.0)) else scaled4 - @floor(scaled4 / @as(Vec8d, @splat(33554432.0)) + @as(Vec8d, @splat(0.5))) * @as(Vec8d, @splat(33554432.0));
                        const shifted4 = wrapped4 + @as(Vec8d, @splat(yo));
                        const cells: Vec8i = @intFromFloat(@floor(shifted4));
                        const fy = shifted4 - @as(Vec8d, @floatFromInt(cells));
                        const smooth: Vec8d = .{ smoothstep(fy[0]), smoothstep(fy[1]), smoothstep(fy[2]), smoothstep(fy[3]), smoothstep(fy[4]), smoothstep(fy[5]), smoothstep(fy[6]), smoothstep(fy[7]) };
                        const sampled = sample_distinct_y(8, permutation, x_hash, next_x_hash, cells, integer_z, fraction_x, fy, fraction_z, smooth_x, smooth, smooth_z);
                        const current: Vec8d = @bitCast(values[index..][0..8].*);
                        values[index..][0..8].* = @bitCast(current + @as(Vec8d, @splat(amplitude)) * sampled * @as(Vec8d, @splat(value_factor)));
                        index += 8;
                        continue;
                    }
                    if (index + 3 < values.len and @abs(y_step * coordinate_scale * input_factor) >= 1.0) {
                        const indices: Vec4 = @floatFromInt(@as(Vec4i, .{ @intCast(index), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3) }));
                        const sample_y4 = @as(Vec4, @splat(y)) + indices * @as(Vec4, @splat(y_step));
                        const scaled4 = sample_y4 * @as(Vec4, @splat(coordinate_scale)) * @as(Vec4, @splat(input_factor));
                        const wrapped4 = if (no_wrap_y) scaled4 - @as(Vec4, @splat(0.0)) else scaled4 - @floor(scaled4 / @as(Vec4, @splat(33554432.0)) + @as(Vec4, @splat(0.5))) * @as(Vec4, @splat(33554432.0));
                        const shifted4 = wrapped4 + @as(Vec4, @splat(yo));
                        const cells: Vec4i = @intFromFloat(@floor(shifted4));
                        const fy = shifted4 - @as(Vec4, @floatFromInt(cells));
                        const smooth: Vec4 = .{ smoothstep(fy[0]), smoothstep(fy[1]), smoothstep(fy[2]), smoothstep(fy[3]) };
                        const sampled = sample_distinct_y(4, permutation, x_hash, next_x_hash, cells, integer_z, fraction_x, fy, fraction_z, smooth_x, smooth, smooth_z);
                        const current: Vec4 = @bitCast(values[index..][0..4].*);
                        values[index..][0..4].* = @bitCast(current + @as(Vec4, @splat(amplitude)) * sampled * @as(Vec4, @splat(value_factor)));
                        index += 4;
                        continue;
                    }
                }
                if (comptime small_only) {
                    if (index + 15 < values.len and @abs(y_step * coordinate_scale * input_factor) < 0.25) {
                        const indices: Vec16d = @floatFromInt(@as(Vec16i, .{ @intCast(index + 0), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3), @intCast(index + 4), @intCast(index + 5), @intCast(index + 6), @intCast(index + 7), @intCast(index + 8), @intCast(index + 9), @intCast(index + 10), @intCast(index + 11), @intCast(index + 12), @intCast(index + 13), @intCast(index + 14), @intCast(index + 15) }));
                        const sample_y16 = @as(Vec16d, @splat(y)) + indices * @as(Vec16d, @splat(y_step));
                        const scaled16 = sample_y16 * @as(Vec16d, @splat(coordinate_scale)) * @as(Vec16d, @splat(input_factor));
                        const wrapped16 = if (no_wrap_y) scaled16 - @as(Vec16d, @splat(0.0)) else scaled16 - @floor(scaled16 / @as(Vec16d, @splat(33554432.0)) + @as(Vec16d, @splat(0.5))) * @as(Vec16d, @splat(33554432.0));
                        const shifted16 = wrapped16 + @as(Vec16d, @splat(yo));
                        const integer16: Vec16i = @intFromFloat(@floor(shifted16));
                        const cell_y = integer16[0];
                        if (@reduce(.And, integer16 == @as(Vec16i, @splat(cell_y)))) {
                            if (cell_y != previous_integer_y) {
                                const gradients = GradientCell.init(permutation, x_hash, next_x_hash, cell_y, integer_z);
                                line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                                previous_integer_y = cell_y;
                            }
                            const fractions = shifted16 - @as(Vec16d, @splat(@as(f64, @floatFromInt(cell_y))));
                            const smooth: Vec16d = .{ smoothstep(fractions[0]), smoothstep(fractions[1]), smoothstep(fractions[2]), smoothstep(fractions[3]), smoothstep(fractions[4]), smoothstep(fractions[5]), smoothstep(fractions[6]), smoothstep(fractions[7]), smoothstep(fractions[8]), smoothstep(fractions[9]), smoothstep(fractions[10]), smoothstep(fractions[11]), smoothstep(fractions[12]), smoothstep(fractions[13]), smoothstep(fractions[14]), smoothstep(fractions[15]) };
                            const sampled = line.sample_smoothed16(fractions, smooth_x, smooth, smooth_z);
                            inline for (0..16) |lane| {
                                values[index + lane] += amplitude * sampled[lane] * value_factor;
                            }
                            index += 16;
                            continue;
                        }
                    }
                    if (index + 7 < values.len and @abs(y_step * coordinate_scale * input_factor) < 0.5) {
                        const indices: Vec8d = @floatFromInt(@as(Vec8i, .{
                            @intCast(index),     @intCast(index + 1), @intCast(index + 2), @intCast(index + 3),
                            @intCast(index + 4), @intCast(index + 5), @intCast(index + 6), @intCast(index + 7),
                        }));
                        const sample_y8 = @as(Vec8d, @splat(y)) + indices * @as(Vec8d, @splat(y_step));
                        const scaled8 = sample_y8 * @as(Vec8d, @splat(coordinate_scale)) * @as(Vec8d, @splat(input_factor));
                        const wrapped8 = if (no_wrap_y) scaled8 - @as(Vec8d, @splat(0.0)) else scaled8 - @floor(scaled8 / @as(Vec8d, @splat(33554432.0)) + @as(Vec8d, @splat(0.5))) * @as(Vec8d, @splat(33554432.0));
                        const shifted8 = wrapped8 + @as(Vec8d, @splat(yo));
                        const integer8: Vec8i = @intFromFloat(@floor(shifted8));
                        const cell_y = integer8[0];
                        if (@reduce(.And, integer8 == @as(Vec8i, @splat(cell_y)))) {
                            if (cell_y != previous_integer_y) {
                                const gradients = GradientCell.init(permutation, x_hash, next_x_hash, cell_y, integer_z);
                                line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                                previous_integer_y = cell_y;
                            }
                            const fractions = shifted8 - @as(Vec8d, @splat(@as(f64, @floatFromInt(cell_y))));
                            const smooth: Vec8d = .{
                                smoothstep(fractions[0]), smoothstep(fractions[1]), smoothstep(fractions[2]), smoothstep(fractions[3]),
                                smoothstep(fractions[4]), smoothstep(fractions[5]), smoothstep(fractions[6]), smoothstep(fractions[7]),
                            };
                            const sampled = line.sample_smoothed8(fractions, smooth_x, smooth, smooth_z);
                            inline for (0..8) |lane| {
                                values[index + lane] += amplitude * sampled[lane] * value_factor;
                            }
                            index += 8;
                            continue;
                        }
                    }
                }
                if (index + 3 < values.len and @abs(y_step * coordinate_scale * input_factor) < 1.0) {
                    const indices: Vec4 = @floatFromInt(@as(Vec4i, .{
                        @intCast(index), @intCast(index + 1), @intCast(index + 2), @intCast(index + 3),
                    }));
                    const sample_y4 = @as(Vec4, @splat(y)) + indices * @as(Vec4, @splat(y_step));
                    const scaled = sample_y4 * @as(Vec4, @splat(coordinate_scale)) * @as(Vec4, @splat(input_factor));
                    const wrapped4 = if (no_wrap_y) scaled - @as(Vec4, @splat(0.0)) else scaled - @floor(scaled / @as(Vec4, @splat(33554432.0)) + @as(Vec4, @splat(0.5))) * @as(Vec4, @splat(33554432.0));
                    const shifted4 = wrapped4 + @as(Vec4, @splat(yo));
                    const integer4: Vec4i = @intFromFloat(@floor(shifted4));
                    integer_y = integer4[0];
                    shifted_y = shifted4[0];
                    if (@reduce(.And, integer4 == @as(Vec4i, @splat(integer_y)))) {
                        if (integer_y != previous_integer_y) {
                            const gradients = GradientCell.init(permutation, x_hash, next_x_hash, integer_y, integer_z);
                            line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                            previous_integer_y = integer_y;
                        }
                        const fractions = shifted4 - @as(Vec4, @splat(@as(f64, @floatFromInt(integer_y))));
                        const smooth: Vec4 = .{
                            smoothstep(fractions[0]), smoothstep(fractions[1]),
                            smoothstep(fractions[2]), smoothstep(fractions[3]),
                        };
                        const sampled = line.sample_smoothed4(fractions, smooth_x, smooth, smooth_z);
                        inline for (0..4) |lane| {
                            values[index + lane] += amplitude * sampled[lane] * value_factor;
                        }
                        index += 4;
                        continue;
                    }
                } else {
                    const sample_y = y + @as(f64, @floatFromInt(index)) * y_step;
                    const scaled_y = sample_y * coordinate_scale * input_factor;
                    shifted_y = (if (no_wrap_y) scaled_y - 0.0 else wrap(scaled_y)) + yo;
                    integer_y = floor_int(shifted_y);
                }
                if (integer_y != previous_integer_y) {
                    const gradients = GradientCell.init(permutation, x_hash, next_x_hash, integer_y, integer_z);
                    line = if (comptime paired) gradients.line_paired(fraction_x, fraction_z) else gradients.line(fraction_x, fraction_z);
                    previous_integer_y = integer_y;
                }
                const fraction_y = shifted_y - @as(f64, @floatFromInt(integer_y));
                const sampled = line.sample_smoothed(fraction_y, smooth_x, smoothstep(fraction_y), smooth_z);
                values[index] += amplitude * sampled * value_factor;
                index += 1;
            }
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
}

inline fn perlin_value_grid(comptime avx2: bool, data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, coordinate_scale: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
    if (comptime avx2) {
        const last_factor = read_f64(data, 8) * @as(f64, @floatFromInt(@as(u64, 1) << @as(u6, @intCast(octaves -| 1))));
        if (y_count == 1 or @abs(y_step * coordinate_scale * last_factor) >= 1.0) {
            if (comptime avx2) {
                if (z_count >= 4 and y_count > 1 and @abs(z_step * coordinate_scale * read_f64(data, 8)) < 1.0) return perlin_value_grid_impl(avx2, true, true, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
            }
            return perlin_value_grid_impl(avx2, true, false, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
        }
    }
    if (comptime avx2) {
        if (z_count >= 4 and y_count > 1 and @abs(z_step * coordinate_scale * read_f64(data, 8)) < 1.0) return perlin_value_grid_impl(avx2, false, true, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
    }
    return perlin_value_grid_impl(avx2, false, false, data, octaves, x, y, z, x_step, y_step, z_step, coordinate_scale, x_count, y_count, z_count, values);
}

fn perlin_value_grid_impl(comptime avx2: bool, comptime packed_corners: bool, comptime z4_enabled: bool, data: [*]const u8, octaves: usize, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, z_step: f64, coordinate_scale: f64, x_count: usize, y_count: usize, z_count: usize, values: []f64) void {
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
                            if (comptime avx2 and z4_enabled) {
                                while (z_index + 3 < z_end and y_end > y_start + 1) : (z_index += 4) {
                                    const fz4: Vec4 = @bitCast(fraction_z[z_index..][0..4].*);
                                    const sz4: Vec4 = @bitCast(smooth_z[z_index..][0..4].*);
                                    const line4 = gradients.line_z4(fraction_x[x_index], fz4);
                                    var yi = y_start;
                                    while (yi < y_end) : (yi += 1) {
                                        const sampled = line4.sample(fraction_y[yi], smooth_x[x_index], smooth_y[yi], sz4);
                                        const first_index = (x_index * z_count + z_index) * y_count + yi;
                                        const current: Vec4 = .{ values[first_index], values[first_index + y_count], values[first_index + 2 * y_count], values[first_index + 3 * y_count] };
                                        const result = current + @as(Vec4, @splat(amplitude)) * sampled * @as(Vec4, @splat(value_factor));
                                        values[first_index] = result[0];
                                        values[first_index + y_count] = result[1];
                                        values[first_index + 2 * y_count] = result[2];
                                        values[first_index + 3 * y_count] = result[3];
                                    }
                                }
                            }
                            while (z_index < z_end) : (z_index += 1) {
                                const offset = (x_index * z_count + z_index) * y_count;
                                if (packed_corners and y_end == y_start + 1) {
                                    values[offset + y_start] += amplitude * gradients.sample_packed(fraction_x[x_index], fraction_y[y_start], fraction_z[z_index], smooth_x[x_index], smooth_y[y_start], smooth_z[z_index]) * value_factor;
                                    continue;
                                }
                                const line = if (x_count * y_count * z_count >= 256)
                                    gradients.line_paired(fraction_x[x_index], fraction_z[z_index])
                                else
                                    gradients.line(fraction_x[x_index], fraction_z[z_index]);
                                var y_index = y_start;
                                if (comptime avx2) {
                                    const amplitude8: Vec8d = @splat(amplitude);
                                    const value_factor8: Vec8d = @splat(value_factor);
                                    while (y_index + 7 < y_end) : (y_index += 8) {
                                        const sampled = line.sample_smoothed8(
                                            .{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3], fraction_y[y_index + 4], fraction_y[y_index + 5], fraction_y[y_index + 6], fraction_y[y_index + 7] },
                                            smooth_x[x_index],
                                            .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3], smooth_y[y_index + 4], smooth_y[y_index + 5], smooth_y[y_index + 6], smooth_y[y_index + 7] },
                                            smooth_z[z_index],
                                        );
                                        const value_index = offset + y_index;
                                        const current: Vec8d = @bitCast(values[value_index..][0..8].*);
                                        values[value_index..][0..8].* = @bitCast(current + amplitude8 * sampled * value_factor8);
                                    }
                                    const amplitude4: Vec4 = @splat(amplitude);
                                    const value_factor4: Vec4 = @splat(value_factor);
                                    while (y_index + 3 < y_end) : (y_index += 4) {
                                        const sampled = line.sample_smoothed_shifted4(
                                            .{ fraction_y[y_index], fraction_y[y_index + 1], fraction_y[y_index + 2], fraction_y[y_index + 3] },
                                            smooth_x[x_index],
                                            .{ smooth_y[y_index], smooth_y[y_index + 1], smooth_y[y_index + 2], smooth_y[y_index + 3] },
                                            smooth_z[z_index],
                                        );
                                        const value_index = offset + y_index;
                                        const current: Vec4 = @bitCast(values[value_index..][0..4].*);
                                        values[value_index..][0..4].* = @bitCast(current + amplitude4 * sampled * value_factor4);
                                    }
                                }
                                const amplitude2: Vec2 = @splat(amplitude);
                                const value_factor2: Vec2 = @splat(value_factor);
                                while (y_index + 1 < y_end) : (y_index += 2) {
                                    const sampled = line.sample_smoothed_shifted2(
                                        .{ fraction_y[y_index], fraction_y[y_index + 1] },
                                        smooth_x[x_index],
                                        .{ smooth_y[y_index], smooth_y[y_index + 1] },
                                        smooth_z[z_index],
                                    );
                                    const value_index = offset + y_index;
                                    const current: Vec2 = @bitCast(values[value_index..][0..2].*);
                                    values[value_index..][0..2].* = @bitCast(current + amplitude2 * sampled * value_factor2);
                                }
                                while (y_index < y_end) : (y_index += 1) {
                                    values[offset + y_index] += amplitude * line.sample_smoothed_shifted(fraction_y[y_index], smooth_x[x_index], smooth_y[y_index], smooth_z[z_index]) * value_factor;
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
    if (count == 0) return;
    const last = base + @as(f64, @floatFromInt(count - 1)) * step;
    const first_scaled = base * coordinate_scale * input_factor;
    const last_scaled = last * coordinate_scale * input_factor;
    const no_wrap = std.math.isFinite(first_scaled) and std.math.isFinite(last_scaled) and
        @abs(first_scaled) < 16777216.0 and @abs(last_scaled) < 16777216.0;
    var index: usize = 0;
    while (index < count) : (index += 1) {
        const sample = base + @as(f64, @floatFromInt(index)) * step;
        const scaled = sample * coordinate_scale * input_factor;
        const shifted = (if (no_wrap) scaled - 0.0 else wrap(scaled)) + origin;
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

    fn init_cached(permutation: [*]const u8, xy: i32, xy_next: i32, next_xy: i32, next_xy_next: i32, z: i32) GradientCell {
        return .{ .values = .{
            permutation_value(permutation, xy + z),          permutation_value(permutation, next_xy + z),
            permutation_value(permutation, xy_next + z),     permutation_value(permutation, next_xy_next + z),
            permutation_value(permutation, xy + z + 1),      permutation_value(permutation, next_xy + z + 1),
            permutation_value(permutation, xy_next + z + 1), permutation_value(permutation, next_xy_next + z + 1),
        } };
    }

    fn sample_packed(self: GradientCell, x: f64, y: f64, z: f64, sx: f64, sy: f64, sz: f64) f64 {
        @setFloatMode(.strict);
        const hashes: Vec8i = @bitCast(self.values);
        const xx: Vec8d = .{ x, x - 1.0, x, x - 1.0, x, x - 1.0, x, x - 1.0 };
        const yy: Vec8d = .{ y, y, y - 1.0, y - 1.0, y, y, y - 1.0, y - 1.0 };
        const zz: Vec8d = .{ z, z, z, z, z - 1.0, z - 1.0, z - 1.0, z - 1.0 };
        const dots = gradient_vector(8, hashes, xx, yy, zz);
        const x0 = @shuffle(f64, dots, undefined, @as(@Vector(4, i32), .{ 0, 2, 4, 6 }));
        const x1 = @shuffle(f64, dots, undefined, @as(@Vector(4, i32), .{ 1, 3, 5, 7 }));
        const vx = x0 + @as(Vec4, @splat(sx)) * (x1 - x0);
        const y0 = @shuffle(f64, vx, undefined, @as(@Vector(2, i32), .{ 0, 2 }));
        const y1 = @shuffle(f64, vx, undefined, @as(@Vector(2, i32), .{ 1, 3 }));
        const vy = y0 + @as(Vec2, @splat(sy)) * (y1 - y0);
        return vy[0] + sz * (vy[1] - vy[0]);
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

    inline fn line_paired(self: GradientCell, fraction_x: f64, fraction_z: f64) GradientLine {
        const pair00 = gradient_terms_pair(self.values[0], self.values[2], fraction_x, fraction_z);
        const pair10 = gradient_terms_pair(self.values[1], self.values[3], fraction_x - 1.0, fraction_z);
        const pair01 = gradient_terms_pair(self.values[4], self.values[6], fraction_x, fraction_z - 1.0);
        const pair11 = gradient_terms_pair(self.values[5], self.values[7], fraction_x - 1.0, fraction_z - 1.0);
        return .{ .values = .{
            pair00[0], pair10[0], pair00[1], pair10[1],
            pair01[0], pair11[0], pair01[1], pair11[1],
        } };
    }

    fn line_z4(self: GradientCell, fraction_x: f64, fraction_z: Vec4) GradientLineZ4 {
        const fx0: Vec4 = @splat(fraction_x);
        const fx1: Vec4 = @splat(fraction_x - 1.0);
        const fz1 = fraction_z - @as(Vec4, @splat(1.0));
        return .{ .values = .{
            gradient_term_z4(self.values[0], fx0, fraction_z),
            gradient_term_z4(self.values[1], fx1, fraction_z),
            gradient_term_z4(self.values[2], fx0, fraction_z),
            gradient_term_z4(self.values[3], fx1, fraction_z),
            gradient_term_z4(self.values[4], fx0, fz1),
            gradient_term_z4(self.values[5], fx1, fz1),
            gradient_term_z4(self.values[6], fx0, fz1),
            gradient_term_z4(self.values[7], fx1, fz1),
        } };
    }

    inline fn line(self: GradientCell, fraction_x: f64, fraction_z: f64) GradientLine {
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
    mode: enum { none, add, subtract },

    fn sample_shifted(self: GradientTerm, shifted: f64) f64 {
        const base: f64 = self.base;
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    fn sample_shifted2(self: GradientTerm, shifted: Vec2) Vec2 {
        const base: Vec2 = @as(Vec2, @splat(self.base));
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    fn sample_shifted16(self: GradientTerm, shifted: Vec16d) Vec16d {
        const base: Vec16d = @splat(self.base);
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    fn sample_shifted8(self: GradientTerm, shifted: Vec8d) Vec8d {
        const base: Vec8d = @splat(self.base);
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
    fn sample_shifted4(self: GradientTerm, shifted: Vec4) Vec4 {
        const base: Vec4 = @as(Vec4, @splat(self.base));
        return switch (self.mode) {
            .none => base,
            .add => base + shifted,
            .subtract => base - shifted,
        };
    }
};

fn gradient_terms_pair(first: i32, second: i32, x: f64, z: f64) [2]GradientTerm {
    const descriptors = [16]u8{ 8, 9, 16, 17, 2, 3, 4, 5, 14, 22, 15, 23, 8, 22, 9, 23 };
    const bases = [8]f64{ x, -x, x + z, -x + z, x - z, -x - z, z, -z };
    const first_desc = descriptors[@as(usize, @intCast(first & 15))];
    const second_desc = descriptors[@as(usize, @intCast(second & 15))];
    return .{
        .{ .base = bases[first_desc & 7], .mode = @enumFromInt(first_desc >> 3) },
        .{ .base = bases[second_desc & 7], .mode = @enumFromInt(second_desc >> 3) },
    };
}

const GradientLine = struct {
    values: [8]GradientTerm,

    fn sample_smoothed(self: GradientLine, fraction_y: f64, smooth_x: f64, smooth_y: f64, smooth_z: f64) f64 {
        const y0 = fraction_y + 0.0;
        const y1 = fraction_y + -1.0;
        return lerp3(smooth_x, smooth_y, smooth_z, self.values[0].sample_shifted(y0), self.values[1].sample_shifted(y0), self.values[2].sample_shifted(y1), self.values[3].sample_shifted(y1), self.values[4].sample_shifted(y0), self.values[5].sample_shifted(y0), self.values[6].sample_shifted(y1), self.values[7].sample_shifted(y1));
    }

    fn sample_smoothed2(self: GradientLine, fraction_y: Vec2, smooth_x: f64, smooth_y: Vec2, smooth_z: f64) Vec2 {
        const y0 = fraction_y + @as(Vec2, @splat(0.0));
        const y1 = fraction_y + @as(Vec2, @splat(-1.0));
        return lerp3_2(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted2(y0), self.values[1].sample_shifted2(y0), self.values[2].sample_shifted2(y1), self.values[3].sample_shifted2(y1), self.values[4].sample_shifted2(y0), self.values[5].sample_shifted2(y0), self.values[6].sample_shifted2(y1), self.values[7].sample_shifted2(y1));
    }

    fn sample_smoothed4(self: GradientLine, fraction_y: Vec4, smooth_x: f64, smooth_y: Vec4, smooth_z: f64) Vec4 {
        const y0 = fraction_y + @as(Vec4, @splat(0.0));
        const y1 = fraction_y + @as(Vec4, @splat(-1.0));
        return lerp3_4(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted4(y0), self.values[1].sample_shifted4(y0), self.values[2].sample_shifted4(y1), self.values[3].sample_shifted4(y1), self.values[4].sample_shifted4(y0), self.values[5].sample_shifted4(y0), self.values[6].sample_shifted4(y1), self.values[7].sample_shifted4(y1));
    }
    fn sample_smoothed16(self: GradientLine, fraction_y: Vec16d, smooth_x: f64, smooth_y: Vec16d, smooth_z: f64) Vec16d {
        const y0 = fraction_y + @as(Vec16d, @splat(0.0));
        const y1 = fraction_y + @as(Vec16d, @splat(-1.0));
        return lerp3_16(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted16(y0), self.values[1].sample_shifted16(y0), self.values[2].sample_shifted16(y1), self.values[3].sample_shifted16(y1), self.values[4].sample_shifted16(y0), self.values[5].sample_shifted16(y0), self.values[6].sample_shifted16(y1), self.values[7].sample_shifted16(y1));
    }
    fn sample_smoothed8(self: GradientLine, fraction_y: Vec8d, smooth_x: f64, smooth_y: Vec8d, smooth_z: f64) Vec8d {
        const y0 = fraction_y + @as(Vec8d, @splat(0.0));
        const y1 = fraction_y + @as(Vec8d, @splat(-1.0));
        return lerp3_8(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted8(y0), self.values[1].sample_shifted8(y0), self.values[2].sample_shifted8(y1), self.values[3].sample_shifted8(y1), self.values[4].sample_shifted8(y0), self.values[5].sample_shifted8(y0), self.values[6].sample_shifted8(y1), self.values[7].sample_shifted8(y1));
    }
    fn sample_smoothed_shifted(self: GradientLine, fraction_y: f64, smooth_x: f64, smooth_y: f64, smooth_z: f64) f64 {
        const y0 = fraction_y + 0.0;
        const y1 = fraction_y + -1.0;
        return lerp3(smooth_x, smooth_y, smooth_z, self.values[0].sample_shifted(y0), self.values[1].sample_shifted(y0), self.values[2].sample_shifted(y1), self.values[3].sample_shifted(y1), self.values[4].sample_shifted(y0), self.values[5].sample_shifted(y0), self.values[6].sample_shifted(y1), self.values[7].sample_shifted(y1));
    }

    fn sample_smoothed_shifted2(self: GradientLine, fraction_y: Vec2, smooth_x: f64, smooth_y: Vec2, smooth_z: f64) Vec2 {
        const y0 = fraction_y + @as(Vec2, @splat(0.0));
        const y1 = fraction_y + @as(Vec2, @splat(-1.0));
        return lerp3_2(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted2(y0), self.values[1].sample_shifted2(y0), self.values[2].sample_shifted2(y1), self.values[3].sample_shifted2(y1), self.values[4].sample_shifted2(y0), self.values[5].sample_shifted2(y0), self.values[6].sample_shifted2(y1), self.values[7].sample_shifted2(y1));
    }

    fn sample_smoothed_shifted4(self: GradientLine, fraction_y: Vec4, smooth_x: f64, smooth_y: Vec4, smooth_z: f64) Vec4 {
        const y0 = fraction_y + @as(Vec4, @splat(0.0));
        const y1 = fraction_y + @as(Vec4, @splat(-1.0));
        return lerp3_4(@splat(smooth_x), smooth_y, @splat(smooth_z), self.values[0].sample_shifted4(y0), self.values[1].sample_shifted4(y0), self.values[2].sample_shifted4(y1), self.values[3].sample_shifted4(y1), self.values[4].sample_shifted4(y0), self.values[5].sample_shifted4(y0), self.values[6].sample_shifted4(y1), self.values[7].sample_shifted4(y1));
    }
};

const GradientTermZ4 = struct {
    base: Vec4,
    mode: enum { none, add, subtract },

    fn sample(self: GradientTermZ4, shifted: Vec4) Vec4 {
        return switch (self.mode) {
            .none => self.base,
            .add => self.base + shifted,
            .subtract => self.base - shifted,
        };
    }
};

const GradientLineZ4 = struct {
    values: [8]GradientTermZ4,

    fn sample(self: GradientLineZ4, y: f64, sx: f64, sy: f64, sz: Vec4) Vec4 {
        const y0: Vec4 = @splat(y + 0.0);
        const y1: Vec4 = @splat(y + -1.0);
        return lerp3_4(@splat(sx), @splat(sy), sz, self.values[0].sample(y0), self.values[1].sample(y0), self.values[2].sample(y1), self.values[3].sample(y1), self.values[4].sample(y0), self.values[5].sample(y0), self.values[6].sample(y1), self.values[7].sample(y1));
    }
};

fn gradient_term_z4(value: i32, x: Vec4, z: Vec4) GradientTermZ4 {
    return switch (value & 15) {
        0 => .{ .base = x, .mode = .add },
        1 => .{ .base = -x, .mode = .add },
        2 => .{ .base = x, .mode = .subtract },
        3 => .{ .base = -x, .mode = .subtract },
        4 => .{ .base = x + z, .mode = .none },
        5 => .{ .base = -x + z, .mode = .none },
        6 => .{ .base = x - z, .mode = .none },
        7 => .{ .base = -x - z, .mode = .none },
        8 => .{ .base = z, .mode = .add },
        9 => .{ .base = z, .mode = .subtract },
        10 => .{ .base = -z, .mode = .add },
        11 => .{ .base = -z, .mode = .subtract },
        12 => .{ .base = x, .mode = .add },
        13 => .{ .base = z, .mode = .subtract },
        14 => .{ .base = -x, .mode = .add },
        else => .{ .base = -z, .mode = .subtract },
    };
}

fn gradient_term(value: i32, x: f64, z: f64, y_offset: f64) GradientTerm {
    _ = y_offset;
    return switch (value & 15) {
        0 => .{ .base = x, .mode = .add },
        1 => .{ .base = -x, .mode = .add },
        2 => .{ .base = x, .mode = .subtract },
        3 => .{ .base = -x, .mode = .subtract },
        4 => .{ .base = x + z, .mode = .none },
        5 => .{ .base = -x + z, .mode = .none },
        6 => .{ .base = x - z, .mode = .none },
        7 => .{ .base = -x - z, .mode = .none },
        8 => .{ .base = z, .mode = .add },
        9 => .{ .base = z, .mode = .subtract },
        10 => .{ .base = -z, .mode = .add },
        11 => .{ .base = -z, .mode = .subtract },
        12 => .{ .base = x, .mode = .add },
        13 => .{ .base = z, .mode = .subtract },
        14 => .{ .base = -x, .mode = .add },
        else => .{ .base = -z, .mode = .subtract },
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

fn lerp_8(delta: Vec8d, start: Vec8d, end: Vec8d) Vec8d {
    return start + delta * (end - start);
}
fn lerp2_8(x: Vec8d, y: Vec8d, value00: Vec8d, value10: Vec8d, value01: Vec8d, value11: Vec8d) Vec8d {
    return lerp_8(y, lerp_8(x, value00, value10), lerp_8(x, value01, value11));
}
fn lerp3_8(x: Vec8d, y: Vec8d, z: Vec8d, value000: Vec8d, value100: Vec8d, value010: Vec8d, value110: Vec8d, value001: Vec8d, value101: Vec8d, value011: Vec8d, value111: Vec8d) Vec8d {
    return lerp_8(z, lerp2_8(x, y, value000, value100, value010, value110), lerp2_8(x, y, value001, value101, value011, value111));
}

fn lerp_16(delta: Vec16d, start: Vec16d, end: Vec16d) Vec16d {
    return start + delta * (end - start);
}
fn lerp2_16(x: Vec16d, y: Vec16d, value00: Vec16d, value10: Vec16d, value01: Vec16d, value11: Vec16d) Vec16d {
    return lerp_16(y, lerp_16(x, value00, value10), lerp_16(x, value01, value11));
}
fn lerp3_16(x: Vec16d, y: Vec16d, z: Vec16d, value000: Vec16d, value100: Vec16d, value010: Vec16d, value110: Vec16d, value001: Vec16d, value101: Vec16d, value011: Vec16d, value111: Vec16d) Vec16d {
    return lerp_16(z, lerp2_16(x, y, value000, value100, value010, value110), lerp2_16(x, y, value001, value101, value011, value111));
}
