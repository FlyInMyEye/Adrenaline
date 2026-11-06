const backend_density = @import("../backend/density_dispatch.zig");
const backend_runtime = @import("../backend/runtime.zig");

const std = @import("std");
const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;
const backend_abi = @import("../backend/abi.zig");
const backend_options = @import("../backend/options.zig");
const core_limits = @import("../core/limits.zig");
const density_height = @import("../density/height.zig");
const density_program = @import("../density/program.zig");

pub fn native_density_sampler_evaluate0(env: ?*c.JNIEnv, _: c.jclass, program_buffer: c.jobject, program_length: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    return evaluate_density_jni(env, program_buffer, program_length, null, 0, base_x, base_y, base_z, cell_width, cell_height, output);
}

pub fn native_density_sampler_evaluate_with_inputs0(env: ?*c.JNIEnv, _: c.jclass, program_buffer: c.jobject, program_length: c.jint, input_buffer: c.jobject, input_count: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    return evaluate_density_jni(env, program_buffer, program_length, input_buffer, input_count, base_x, base_y, base_z, cell_width, cell_height, output);
}

pub fn native_density_sampler_preliminary_surface_level0(env: ?*c.JNIEnv, _: c.jclass, program_buffer: c.jobject, program_length: c.jint, input_buffer: c.jobject, input_count: c.jint, cache_count: c.jint, x: c.jint, z: c.jint, min_y: c.jint, max_y: c.jint, step: c.jint) callconv(.c) c.jint {
    @setFloatMode(.strict);
    const failed = std.math.minInt(c.jint);
    if (program_length <= 0 or program_length > core_limits.max_density_program_size or input_count < 0 or input_count > 128 or cache_count < 0 or cache_count > core_limits.max_column_caches or step <= 0 or min_y <= failed or max_y < min_y or @as(i64, max_y) - min_y > 65536) return failed;
    const environment = env orelse return failed;
    const functions = environment.*.*;
    const get_address = functions.GetDirectBufferAddress orelse return failed;
    const get_capacity = functions.GetDirectBufferCapacity orelse return failed;
    if (get_capacity(env, program_buffer) < program_length) return failed;
    const address = get_address(env, program_buffer) orelse return failed;
    const bytes: [*]const u8 = @ptrCast(address);
    const program = bytes[0..@intCast(program_length)];
    var inputs: []const f64 = &.{};
    if (input_count > 0) {
        if (input_buffer == null or get_capacity(env, input_buffer) < @as(i64, input_count) * 16) return failed;
        const input_address = get_address(env, input_buffer) orelse return failed;
        const values: [*]const f64 = @ptrCast(@alignCast(input_address));
        inputs = values[0 .. @as(usize, @intCast(input_count)) * 2];
    }
    const slots: usize = @intCast(cache_count);
    if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
        if (backend_runtime.supports(.avx2)) return backend_abi.adrenaline_preliminary_height_avx2(program.ptr, program.len, inputs.ptr, inputs.len, slots, x, z, min_y, max_y, step);
    }
    return density_height.preliminary_surface_level(program, inputs, slots, x, z, min_y, max_y, step);
}

pub fn evaluate_density_jni(env: ?*c.JNIEnv, program_buffer: c.jobject, program_length: c.jint, input_buffer: c.jobject, input_count: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, output: c.jdoubleArray) c.jboolean {
    @setFloatMode(.strict);
    if (program_length <= 0 or program_length > core_limits.max_density_program_size or cell_width <= 0 or cell_width > 32 or cell_height <= 0 or cell_height > core_limits.max_density_values or input_count < 0 or input_count > 8192) {
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
    var inputs: []const f64 = &.{};
    if (input_count > 0) {
        const input_length = @as(usize, @intCast(input_count)) * total;
        if (input_buffer == null or get_direct_buffer_capacity(env, input_buffer) < @as(c.jlong, @intCast(input_length * @sizeOf(f64)))) return c.JNI_FALSE;
        const input_address = get_direct_buffer_address(env, input_buffer) orelse return c.JNI_FALSE;
        const input_values: [*]const f64 = @ptrCast(@alignCast(input_address));
        inputs = input_values[0..input_length];
    }
    const output_address = get_primitive_array_critical(env, output, null) orelse return c.JNI_FALSE;
    defer release_primitive_array_critical(env, output, output_address, 0);

    const program: [*]const u8 = @ptrCast(@alignCast(program_address));
    const values: [*]c.jdouble = @ptrCast(@alignCast(output_address));
    return if (backend_density.evaluate(program[0..length], base_x, base_y, base_z, width, height, values[0..total], inputs)) c.JNI_TRUE else c.JNI_FALSE;
}

pub fn native_density_sampler_evaluate_slice0(env: ?*c.JNIEnv, _: c.jclass, program_buffer: c.jobject, program_length: c.jint, input_buffer: c.jobject, input_count: c.jint, x: c.jint, y: c.jint, z: c.jint, step: c.jint, count: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    if (program_buffer == null or output == null or program_length <= 0 or program_length > core_limits.max_density_program_size or input_count < 0 or input_count > 128 or count <= 0 or count > core_limits.max_grid_axis or step <= 0) return c.JNI_FALSE;
    const last_y = @as(i64, y) + @as(i64, count - 1) * step;
    if (last_y < std.math.minInt(i32) or last_y > std.math.maxInt(i32)) return c.JNI_FALSE;
    const environment = env orelse return c.JNI_FALSE;
    const f = environment.*.*;
    const get_address = f.GetDirectBufferAddress orelse return c.JNI_FALSE;
    const get_capacity = f.GetDirectBufferCapacity orelse return c.JNI_FALSE;
    const get_length = f.GetArrayLength orelse return c.JNI_FALSE;
    const pin = f.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release = f.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    if (get_capacity(env, program_buffer) < program_length or get_length(env, output) < count) return c.JNI_FALSE;
    const program_address = get_address(env, program_buffer) orelse return c.JNI_FALSE;
    const program: [*]const u8 = @ptrCast(program_address);
    var inputs: []const f64 = &.{};
    if (input_count > 0) {
        const n = @as(usize, @intCast(input_count)) * @as(usize, @intCast(count));
        if (input_buffer == null or get_capacity(env, input_buffer) < @as(i64, @intCast(n * @sizeOf(f64)))) return c.JNI_FALSE;
        const address = get_address(env, input_buffer) orelse return c.JNI_FALSE;
        const values: [*]const f64 = @ptrCast(@alignCast(address));
        inputs = values[0..n];
    }
    const address = pin(env, output, null) orelse return c.JNI_FALSE;
    defer release(env, output, address, 0);
    const values: [*]f64 = @ptrCast(@alignCast(address));
    return @intFromBool(backend_density.evaluate_slice(program[0..@intCast(program_length)], x, y, z, step, values[0..@intCast(count)], inputs));
}

pub fn density_scalar_entry(program: [*]const u8, program_length: usize, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: [*]f64) callconv(.c) c_int {
    return if (density_program.evaluate_density_cell(program[0..program_length], base_x, base_y, base_z, width, height, output[0 .. width * width * height], &.{})) 1 else 0;
}

pub fn register() void {
    @export(&native_density_sampler_evaluate_slice0, .{ .name = "Java_net_fly_adrenaline_natives_NativeDensitySampler_evaluateSlice0" });
    @export(&native_density_sampler_evaluate0, .{ .name = "Java_net_fly_adrenaline_natives_NativeDensitySampler_evaluate0" });
    @export(&native_density_sampler_evaluate_with_inputs0, .{ .name = "Java_net_fly_adrenaline_natives_NativeDensitySampler_evaluateWithInputs0" });
    @export(&native_density_sampler_preliminary_surface_level0, .{ .name = "Java_net_fly_adrenaline_natives_NativeDensitySampler_preliminarySurfaceLevel0" });
    @export(&density_scalar_entry, .{ .name = "adrenaline_density_scalar" });
}
