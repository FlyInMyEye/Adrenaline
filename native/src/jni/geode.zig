const std = @import("std");
const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;
const limits = @import("../core/limits.zig");
const backend_runtime = @import("../backend/runtime.zig");
const backend_abi = @import("../backend/abi.zig");
const geode = @import("../features/geode.zig");
const feature_output = @import("feature_output.zig");

pub fn filter0(env: ?*c.JNIEnv, _: c.jclass, first_address: c.jlong, first_count: c.jint, second_address: c.jlong, second_count: c.jint, factor: f64, x: c.jint, y: c.jint, z: c.jint, size: c.jint, multiplier: f64, threshold: f64, points_array: c.jintArray, output_array: c.jintArray) callconv(.c) c.jint {
    if (first_address == 0 or second_address == 0 or first_count <= 0 or first_count > limits.max_octaves or second_count <= 0 or second_count > limits.max_octaves or size <= 0 or size > 40 or points_array == null or output_array == null or !std.math.isFinite(factor) or !std.math.isFinite(multiplier) or !std.math.isFinite(threshold) or threshold <= 0.0) return -1;
    const side: usize = @intCast(size);
    const total = side * side * side;
    if (total > geode.max_values or @as(i64, x) + size - 1 > std.math.maxInt(i32) or @as(i64, y) + size - 1 > std.math.maxInt(i32) or @as(i64, z) + size - 1 > std.math.maxInt(i32)) return -1;
    const environment = env orelse return -1;
    const f = environment.*.*;
    const get_length = f.GetArrayLength orelse return -1;
    const get_region = f.GetIntArrayRegion orelse return -1;
    const set_region = f.SetIntArrayRegion orelse return -1;
    const n = get_length(env, points_array);
    if (n <= 0 or n > 256 or @rem(n, 4) != 0 or get_length(env, output_array) < total) return -1;
    var points: [256]i32 = undefined;
    get_region(env, points_array, 0, n, &points);
    for (0..@as(usize, @intCast(@divExact(n, 4)))) |i| {
        if (points[i * 4 + 3] < 0) return -1;
    }
    const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
    const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
    const output = feature_output.positions[0..total];
    const point_values = points[0..@intCast(n)];
    const kept = if (builtin.cpu.arch == .x86_64 and backend_runtime.supports(.avx2))
        backend_abi.adrenaline_geode_candidates_avx2(first, @intCast(first_count), second, @intCast(second_count), factor, x, y, z, side, multiplier, threshold, point_values.ptr, point_values.len, output.ptr)
    else
        geode.candidates(false, first, @intCast(first_count), second, @intCast(second_count), factor, x, y, z, side, multiplier, threshold, point_values, output);
    if (kept != 0) set_region(env, output_array, 0, @intCast(kept), output.ptr);
    return @intCast(kept);
}

pub fn register() void {
    @export(&filter0, .{ .name = "Java_net_fly_adrenaline_natives_NativeGeodeSampler_filter0" });
}
