const backend_runtime = @import("../backend/runtime.zig");

const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;
const backend_abi = @import("../backend/abi.zig");
const core_limits = @import("../core/limits.zig");

threadlocal var scratch: extern union {
    table: extern struct {
        locations: [core_limits.max_grid_size]i16,
        levels: [core_limits.max_grid_size]i32,
        types: [core_limits.max_grid_size]i8,
    },
    cell: extern struct {
        density: [core_limits.max_density_values]f64,
        candidates: [core_limits.max_density_values * 2]i64,
        current_candidates: [core_limits.max_density_values * 2]i64,
        deferred: [core_limits.max_density_values]i32,
        materials: [core_limits.max_density_values]i8,
    },
} align(32) = undefined;

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
    const get_locations = f.GetShortArrayRegion orelse return c.JNI_FALSE;
    const get_levels = f.GetIntArrayRegion orelse return c.JNI_FALSE;
    const get_types = f.GetByteArrayRegion orelse return c.JNI_FALSE;
    const count = get_len(env, locations);
    if (count <= 0 or count > core_limits.max_grid_size or get_len(env, levels) < count or get_len(env, types) < count) return c.JNI_FALSE;
    const size = backend_abi.adrenaline_aquifer_context_size(@intCast(count));
    const capacity = get_capacity(env, buffer);
    if (capacity < 0 or capacity < size) return c.JNI_FALSE;
    const raw = get_address(env, buffer) orelse return c.JNI_FALSE;
    if (@intFromPtr(raw) % 8 != 0) return c.JNI_FALSE;
    const table = &scratch.table;
    get_locations(env, locations, 0, count, &table.locations);
    get_levels(env, levels, 0, count, &table.levels);
    get_types(env, types, 0, count, &table.types);
    return @intFromBool(backend_abi.adrenaline_aquifer_context_init(@ptrCast(@alignCast(raw)), @intCast(capacity), &table.locations, @intCast(count), &table.levels, &table.types, min_x, min_y, min_z, sx, sz) != 0);
}

pub fn aquifer_context_prepare_jni(env: ?*c.JNIEnv, _: c.jclass, buffer: c.jobject, density: c.jdoubleArray, candidates: c.jlongArray, global_level: c.jint, global_type: c.jbyte, base_x: c.jint, base_y: c.jint, base_z: c.jint, cell_width: c.jint, cell_height: c.jint, deferred: c.jintArray, output: c.jbyteArray) callconv(.c) c.jint {
    @setFloatMode(.strict);
    if (comptime builtin.cpu.arch != .x86_64) return -1;
    if (!backend_runtime.supports(.avx2) or cell_width <= 0 or cell_width > 32 or cell_height <= 0 or cell_height > 1024 or buffer == null or density == null or candidates == null or deferred == null or output == null) return -1;
    const width: usize = @intCast(cell_width);
    const height: usize = @intCast(cell_height);
    const total = width * width * height;
    if (total > core_limits.max_density_values or @abs(@as(i64, base_x)) >= 0x3fff0000 or @abs(@as(i64, base_y)) >= 0x3fff0000 or @abs(@as(i64, base_z)) >= 0x3fff0000) return -1;
    const environment = env orelse return -1;
    const f = environment.*.*;
    const get_len = f.GetArrayLength orelse return -1;
    const get_address = f.GetDirectBufferAddress orelse return -1;
    const get_capacity = f.GetDirectBufferCapacity orelse return -1;
    const get_density = f.GetDoubleArrayRegion orelse return -1;
    const get_candidates = f.GetLongArrayRegion orelse return -1;
    const set_candidates = f.SetLongArrayRegion orelse return -1;
    const set_int = f.SetIntArrayRegion orelse return -1;
    const set_materials = f.SetByteArrayRegion orelse return -1;
    if (get_len(env, density) < total or get_len(env, candidates) < total * 2 or get_len(env, deferred) < total or get_len(env, output) < total) return -1;
    const capacity = get_capacity(env, buffer);
    if (capacity < 0) return -1;
    const raw = get_address(env, buffer) orelse return -1;
    if (@intFromPtr(raw) % 8 != 0) return -1;
    const memory: [*]align(8) const u8 = @ptrCast(@alignCast(raw));
    if (backend_abi.adrenaline_aquifer_context_validate(memory, @intCast(capacity)) == 0) return -1;
    const cell = &scratch.cell;
    get_density(env, density, 0, @intCast(total), &cell.density);
    const n = if ((global_type == 2 or global_type == 3) and cell.density[0] > 0.0 and backend_abi.adrenaline_all_solid_avx2(&cell.density, total) != 0) solid: {
        @memset(cell.materials[0..total], 0);
        break :solid 0;
    } else backend_abi.adrenaline_aquifer_context_prepare(memory, &cell.density, &cell.candidates, global_level, global_type, base_x, base_y, base_z, width, height, &cell.deferred, &cell.materials);
    if (n > total) return -1;
    if (n != 0) {
        const indices = cell.deferred[0..n];
        var first = indices[0];
        var last = first;
        for (indices[1..]) |index| {
            first = @min(first, index);
            last = @max(last, index);
        }
        const offset: usize = @intCast(first * 2);
        const span: c.jsize = (last - first + 1) * 2;
        if (n == 1) {
            set_candidates(env, candidates, @intCast(offset), span, cell.candidates[offset..].ptr);
        } else {
            const current = cell.current_candidates[0..@intCast(span)];
            get_candidates(env, candidates, @intCast(offset), span, current.ptr);
            for (indices) |index| {
                const source: usize = @intCast(index * 2);
                const target = source - offset;
                current[target..][0..2].* = cell.candidates[source..][0..2].*;
            }
            set_candidates(env, candidates, @intCast(offset), span, current.ptr);
        }
        set_int(env, deferred, 0, @intCast(n), &cell.deferred);
    }
    set_materials(env, output, 0, @intCast(total), &cell.materials);
    return @intCast(n);
}

pub fn register() void {
    @export(&aquifer_context_bytes, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_contextBytes0" });
    @export(&aquifer_context_create, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_createContext0" });
    @export(&aquifer_context_prepare_jni, .{ .name = "Java_net_fly_adrenaline_natives_NativeAquiferSampler_prepareContextResult0" });
}
