const backend_runtime = @import("../backend/runtime.zig");

const std = @import("std");
const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;
const backend_abi = @import("../backend/abi.zig");
const core_limits = @import("../core/limits.zig");

pub fn aquifer_context_bytes(_: ?*c.JNIEnv, _: c.jclass, count: c.jint) callconv(.c) c.jint {
    if (comptime builtin.cpu.arch != .x86_64) return 0;
    if (count <= 0 or count > core_limits.max_grid_size or !backend_runtime.supports(.avx2)) return 0;
    return @intCast(backend_abi.adrenaline_aquifer_context_size(@intCast(count)));
}

pub fn aquifer_context_create(env: ?*c.JNIEnv, _: c.jclass, buffer: c.jobject, locations: c.jshortArray, levels: c.jintArray, types: c.jbyteArray, min_x: c.jint, min_y: c.jint, min_z: c.jint, sx: c.jint, sz: c.jint) callconv(.c) c.jboolean {
    if (comptime builtin.cpu.arch != .x86_64) return c.JNI_FALSE;
    if (!backend_runtime.supports(.avx2) or buffer == null or locations == null or levels == null or types == null) return c.JNI_FALSE;
    const environment = env orelse return c.JNI_FALSE;
    const f = environment.*.*;
    const get_len = f.GetArrayLength orelse return c.JNI_FALSE;
    const get_address = f.GetDirectBufferAddress orelse return c.JNI_FALSE;
    const get_capacity = f.GetDirectBufferCapacity orelse return c.JNI_FALSE;
    const pin = f.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release = f.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    const count = get_len(env, locations);
    if (count <= 0 or count > core_limits.max_grid_size or get_len(env, levels) < count or get_len(env, types) < count) return c.JNI_FALSE;
    const size = backend_abi.adrenaline_aquifer_context_size(@intCast(count));
    const capacity = get_capacity(env, buffer);
    if (capacity < 0 or capacity < size) return c.JNI_FALSE;
    const raw = get_address(env, buffer) orelse return c.JNI_FALSE;
    if (@intFromPtr(raw) % 8 != 0) return c.JNI_FALSE;
    const l = pin(env, locations, null) orelse return c.JNI_FALSE;
    defer release(env, locations, l, c.JNI_ABORT);
    const v = pin(env, levels, null) orelse return c.JNI_FALSE;
    defer release(env, levels, v, c.JNI_ABORT);
    const t = pin(env, types, null) orelse return c.JNI_FALSE;
    defer release(env, types, t, c.JNI_ABORT);
    return if (backend_abi.adrenaline_aquifer_context_init(@ptrCast(@alignCast(raw)), @intCast(capacity), @ptrCast(@alignCast(l)), @intCast(count), @ptrCast(@alignCast(v)), @ptrCast(t), min_x, min_y, min_z, sx, sz) != 0) c.JNI_TRUE else c.JNI_FALSE;
}

pub fn aquifer_context_prepare_jni(env: ?*c.JNIEnv, _: c.jclass, buffer: c.jobject, density: c.jdoubleArray, candidates: c.jlongArray, global_level: c.jint, global_type: c.jbyte, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, deferred: c.jintArray, count_out: c.jintArray, output: c.jbyteArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    if (comptime builtin.cpu.arch != .x86_64) return c.JNI_FALSE;
    if (!backend_runtime.supports(.avx2) or cell_width <= 0 or cell_width > 32 or cell_height <= 0 or cell_height > 1024 or buffer == null or density == null or candidates == null or deferred == null or count_out == null or output == null) return c.JNI_FALSE;
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total > core_limits.max_density_values or @abs(@as(i64, base_x)) >= 0x3fff0000 or @abs(@as(i64, base_y)) >= 0x3fff0000 or @abs(@as(i64, base_z)) >= 0x3fff0000) return c.JNI_FALSE;
    const environment = env orelse return c.JNI_FALSE;
    const f = environment.*.*;
    const get_len = f.GetArrayLength orelse return c.JNI_FALSE;
    const get_address = f.GetDirectBufferAddress orelse return c.JNI_FALSE;
    const get_capacity = f.GetDirectBufferCapacity orelse return c.JNI_FALSE;
    const pin = f.GetPrimitiveArrayCritical orelse return c.JNI_FALSE;
    const release = f.ReleasePrimitiveArrayCritical orelse return c.JNI_FALSE;
    if (get_len(env, density) < total or get_len(env, candidates) < total * 2 or get_len(env, deferred) < total or get_len(env, count_out) < 1 or get_len(env, output) < total) return c.JNI_FALSE;
    const capacity = get_capacity(env, buffer);
    if (capacity < 0) return c.JNI_FALSE;
    const raw = get_address(env, buffer) orelse return c.JNI_FALSE;
    if (@intFromPtr(raw) % 8 != 0) return c.JNI_FALSE;
    const memory: [*]align(8) const u8 = @ptrCast(@alignCast(raw));
    if (backend_abi.adrenaline_aquifer_context_validate(memory, @intCast(capacity)) == 0) return c.JNI_FALSE;
    const dp = pin(env, density, null) orelse return c.JNI_FALSE;
    defer release(env, density, dp, c.JNI_ABORT);
    const d: [*]const f64 = @ptrCast(@alignCast(dp));
    if ((global_type == 2 or global_type == 3) and d[0] > 0.0 and backend_abi.adrenaline_all_solid_avx2(d, total) != 0) {
        const cp = pin(env, count_out, null) orelse return c.JNI_FALSE;
        defer release(env, count_out, cp, 0);
        const op = pin(env, output, null) orelse return c.JNI_FALSE;
        defer release(env, output, op, 0);
        const counts: [*]i32 = @ptrCast(@alignCast(cp));
        const out: [*]i8 = @ptrCast(op);
        @memset(out[0..total], 0);
        counts[0] = 0;
        return c.JNI_TRUE;
    }
    const ca = pin(env, candidates, null) orelse return c.JNI_FALSE;
    defer release(env, candidates, ca, 0);
    const de = pin(env, deferred, null) orelse return c.JNI_FALSE;
    defer release(env, deferred, de, 0);
    const co = pin(env, count_out, null) orelse return c.JNI_FALSE;
    defer release(env, count_out, co, 0);
    const ou = pin(env, output, null) orelse return c.JNI_FALSE;
    defer release(env, output, ou, 0);
    const n = backend_abi.adrenaline_aquifer_context_prepare(memory, d, @ptrCast(@alignCast(ca)), global_level, global_type, base_x, base_y, base_z, width, height, @ptrCast(@alignCast(de)), @ptrCast(ou));
    if (n == std.math.maxInt(usize)) return c.JNI_FALSE;
    const counts: [*]i32 = @ptrCast(@alignCast(co));
    counts[0] = @intCast(n);
    return c.JNI_TRUE;
}

pub fn register() void {
    @export(&aquifer_context_bytes, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_contextBytes0" });
    @export(&aquifer_context_create, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_createContext0" });
    @export(&aquifer_context_prepare_jni, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_prepareContext0" });
}
