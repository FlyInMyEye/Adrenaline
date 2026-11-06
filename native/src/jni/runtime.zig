const backend_runtime = @import("../backend/runtime.zig");

const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;

pub fn JNI_OnLoad(_: ?*anyopaque, _: ?*anyopaque) callconv(.c) c.jint {
    return c.JNI_VERSION_1_8;
}

pub fn adrenaline_natives_capabilities0(_: ?*c.JNIEnv, _: c.jclass) callconv(.c) c.jint {
    if (comptime builtin.cpu.arch == .x86_64) {
        return if (backend_runtime.supports(.avx2)) 1 else 0;
    }
    if (comptime builtin.cpu.arch == .aarch64) {
        return 2;
    }
    return 0;
}

pub fn perlin_native_sampler_address0(env: ?*c.JNIEnv, _: c.jclass, buffer: c.jobject) callconv(.c) c.jlong {
    const environment = env orelse return 0;
    const get_direct_buffer_address = environment.*.*.GetDirectBufferAddress orelse return 0;
    const address = get_direct_buffer_address(env, buffer) orelse return 0;
    return @intCast(@intFromPtr(address));
}

pub fn register() void {
    @export(&JNI_OnLoad, .{ .name = "JNI_OnLoad" });
    @export(&adrenaline_natives_capabilities0, .{ .name = "Java_net_fly_adrenaline_natives_AdrenalineNatives_capabilities0" });
    @export(&perlin_native_sampler_address0, .{ .name = "Java_net_fly_adrenaline_natives_PerlinNativeSampler_address0" });
    @export(&perlin_native_sampler_address0, .{ .name = "Java_net_fly_adrenaline_natives_NativeSurfaceSampler_address0" });
}
