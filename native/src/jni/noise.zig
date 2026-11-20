const backend_noise = @import("../backend/noise_dispatch.zig");

const c = @import("../core/jni.zig").c;
const core_limits = @import("../core/limits.zig");
const noise_types = @import("../noise/types.zig");

threadlocal var noise_output: extern union {
    doubles: [core_limits.max_grid_size]f64,
    floats: [core_limits.max_grid_size]f32,
} align(32) = undefined;

fn grid_size(x: c.jint, y: c.jint, z: c.jint) ?c.jsize {
    const max = core_limits.max_grid_size;
    if (x <= 0 or x > max or y <= 0 or y > max or z <= 0 or z > max) return null;
    const total = @as(i64, x) * y * z;
    return if (total <= max) @intCast(total) else null;
}

pub fn perlin_native_sampler_sample0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, y_step: c.jdouble, count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (output == null or first_address == 0 or second_address == 0 or count <= 0 or count > core_limits.max_batch_size or first_octaves < 0 or first_octaves > core_limits.max_octaves or second_octaves < 0 or second_octaves > core_limits.max_octaves) {
        return c.JNI_FALSE;
    }

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < count) {
        return c.JNI_FALSE;
    }
    const set_region = functions.SetDoubleArrayRegion orelse return c.JNI_FALSE;

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const length: usize = @intCast(count);
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values = &noise_output.doubles;
    backend_noise.normal_batch(first, first_count, second, second_count, value_factor, x, y, z, y_step, length, values);
    set_region(env, output, 0, count, values);
    return c.JNI_TRUE;
}

pub fn blended_native_sampler_sample_grid0(env: ?*c.JNIEnv, _: c.jclass, min_address: c.jlong, max_address: c.jlong, main_address: c.jlong, xz_multiplier: c.jdouble, y_multiplier: c.jdouble, xz_factor: c.jdouble, y_factor: c.jdouble, smear_scale_multiplier: c.jdouble, base_x: c.jint, base_y: c.jint, base_z: c.jint, step_x: c.jint, step_y: c.jint, step_z: c.jint, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    if (output == null or min_address == 0 or max_address == 0 or main_address == 0 or
        x_count > core_limits.max_grid_axis or y_count > core_limits.max_grid_axis or z_count > core_limits.max_grid_axis) return c.JNI_FALSE;
    const total = grid_size(x_count, y_count, z_count) orelse return c.JNI_FALSE;
    const nx: usize = @intCast(x_count);
    const ny: usize = @intCast(y_count);
    const nz: usize = @intCast(z_count);
    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < total) return c.JNI_FALSE;
    const set_region = functions.SetDoubleArrayRegion orelse return c.JNI_FALSE;
    const config: noise_types.BlendedNoiseConfig = .{ .xz_multiplier = xz_multiplier, .y_multiplier = y_multiplier, .xz_factor = xz_factor, .y_factor = y_factor, .smear_scale_multiplier = smear_scale_multiplier };
    const min: [*]const u8 = @ptrFromInt(@as(usize, @intCast(min_address)));
    const max: [*]const u8 = @ptrFromInt(@as(usize, @intCast(max_address)));
    const main: [*]const u8 = @ptrFromInt(@as(usize, @intCast(main_address)));
    const values = &noise_output.doubles;
    backend_noise.blended_grid(min, max, main, &config, base_x, base_y, base_z, step_x, step_y, step_z, nx, ny, nz, values);
    set_region(env, output, 0, total, values);
    return c.JNI_TRUE;
}

pub fn perlin_native_sampler_sample_grid0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, x_step: c.jdouble, y_step: c.jdouble, z_step: c.jdouble, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (output == null or first_address == 0 or second_address == 0 or first_octaves < 0 or first_octaves > core_limits.max_octaves or second_octaves < 0 or second_octaves > core_limits.max_octaves) {
        return c.JNI_FALSE;
    }

    const total = grid_size(x_count, y_count, z_count) orelse return c.JNI_FALSE;
    const x_length: usize = @intCast(x_count);
    const y_length: usize = @intCast(y_count);
    const z_length: usize = @intCast(z_count);

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < total) {
        return c.JNI_FALSE;
    }
    const set_region = functions.SetDoubleArrayRegion orelse return c.JNI_FALSE;

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values = &noise_output.doubles;
    backend_noise.normal_grid(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values);
    set_region(env, output, 0, total, values);
    return c.JNI_TRUE;
}

pub fn perlin_native_sampler_sample_approximate_grid0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, x_step: c.jdouble, y_step: c.jdouble, z_step: c.jdouble, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    if (output == null or first_address == 0 or second_address == 0 or first_octaves < 0 or first_octaves > core_limits.max_octaves or second_octaves < 0 or second_octaves > core_limits.max_octaves) {
        return c.JNI_FALSE;
    }

    const total = grid_size(x_count, y_count, z_count) orelse return c.JNI_FALSE;
    const x_length: usize = @intCast(x_count);
    const y_length: usize = @intCast(y_count);
    const z_length: usize = @intCast(z_count);

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < total) {
        return c.JNI_FALSE;
    }
    const set_region = functions.SetDoubleArrayRegion orelse return c.JNI_FALSE;

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values = &noise_output.doubles;
    backend_noise.approximate_grid(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values);
    set_region(env, output, 0, total, values);
    return c.JNI_TRUE;
}

pub fn perlin_native_sampler_sample_approximate_grid_float0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_octaves: c.jint, second_address: c.jlong, second_octaves: c.jint, value_factor: c.jdouble, x: c.jdouble, y: c.jdouble, z: c.jdouble, x_step: c.jdouble, y_step: c.jdouble, z_step: c.jdouble, x_count: c.jint, y_count: c.jint, z_count: c.jint, output: c.jfloatArray) callconv(.c) c.jboolean {
    if (output == null or first_address == 0 or second_address == 0 or first_octaves < 0 or first_octaves > core_limits.max_octaves or second_octaves < 0 or second_octaves > core_limits.max_octaves) {
        return c.JNI_FALSE;
    }

    const total = grid_size(x_count, y_count, z_count) orelse return c.JNI_FALSE;
    const x_length: usize = @intCast(x_count);
    const y_length: usize = @intCast(y_count);
    const z_length: usize = @intCast(z_count);

    const environment = env orelse return c.JNI_FALSE;
    const functions = environment.*.*;
    const get_array_length = functions.GetArrayLength orelse return c.JNI_FALSE;
    if (get_array_length(env, output) < total) {
        return c.JNI_FALSE;
    }
    const set_region = functions.SetFloatArrayRegion orelse return c.JNI_FALSE;

    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const first_count: usize = @intCast(first_octaves);
    const second_count: usize = @intCast(second_octaves);
    const values = &noise_output.floats;
    backend_noise.approximate_grid_f32(first, first_count, second, second_count, value_factor, x, y, z, x_step, y_step, z_step, x_length, y_length, z_length, values);
    set_region(env, output, 0, total, values);
    return c.JNI_TRUE;
}

pub fn register() void {
    @export(&perlin_native_sampler_sample0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sample0" });
    @export(&blended_native_sampler_sample_grid0, .{ .name = "Java_net_fly_adrenaline_natives_BlendedNativeSampler_sampleGrid0" });
    @export(&perlin_native_sampler_sample_grid0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleGrid0" });
    @export(&perlin_native_sampler_sample_approximate_grid0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGrid0" });
    @export(&perlin_native_sampler_sample_approximate_grid_float0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_sampleApproximateGridFloat0" });
}
