const std = @import("std");
const c = @import("../core/jni.zig").c;
const limits = @import("../core/limits.zig");
const math = @import("../core/math.zig");
const scalar = @import("../noise/scalar.zig");

const stride = 40;
const max_channels = 6;

const Noise = struct {
    first: [*]const u8,
    second: [*]const u8,
    first_count: usize,
    second_count: usize,
    factor: f64,
    scale: f64,

    fn decode(bytes: []const u8) ?Noise {
        const first = std.mem.readInt(u64, bytes[0..8], .little);
        const second = std.mem.readInt(u64, bytes[8..16], .little);
        const first_count = std.mem.readInt(u32, bytes[16..20], .little);
        const second_count = std.mem.readInt(u32, bytes[20..24], .little);
        const factor = math.read_f64(bytes.ptr, 24);
        const scale = math.read_f64(bytes.ptr, 32);
        if (first == 0 or second == 0 or first_count > limits.max_octaves or second_count > limits.max_octaves or
            !std.math.isFinite(factor) or !std.math.isFinite(scale)) return null;
        return .{
            .first = @ptrFromInt(@as(usize, @intCast(first))),
            .second = @ptrFromInt(@as(usize, @intCast(second))),
            .first_count = first_count,
            .second_count = second_count,
            .factor = factor,
            .scale = scale,
        };
    }

    fn sample(self: Noise, x: f64, y: f64, z: f64) f64 {
        return scalar.normal_noise_value(self.first, self.first_count, self.second, self.second_count, self.factor, x, y, z);
    }
};

fn sampleBatch0(env: ?*c.JNIEnv, _: c.jclass, plan: c.jobject, x: c.jint, z: c.jint, output: c.jdoubleArray) callconv(.c) c.jboolean {
    @setFloatMode(.strict);
    const f = (env orelse return c.JNI_FALSE).*.*;
    if (plan == null or output == null) return c.JNI_FALSE;
    const count = f.GetArrayLength.?(env, output);
    if (count < 2 or count > max_channels or f.GetDirectBufferCapacity.?(env, plan) != (count + 2) * stride) return c.JNI_FALSE;

    const bytes: [*]const u8 = @ptrCast(f.GetDirectBufferAddress.?(env, plan) orelse return c.JNI_FALSE);
    const dx: f64 = @floatFromInt(x);
    const dz: f64 = @floatFromInt(z);
    const shift_x = Noise.decode(bytes[0..stride]) orelse return c.JNI_FALSE;
    const shift_z = Noise.decode(bytes[stride..][0..stride]) orelse return c.JNI_FALSE;
    const offset_x = shift_x.sample(dx * 0.25, 0.0, dz * 0.25) * 4.0;
    const offset_z = shift_z.sample(dz * 0.25, dx * 0.25, 0.0) * 4.0;
    if (!(@abs(offset_x) <= 4096.0 and @abs(offset_z) <= 4096.0)) return c.JNI_FALSE;

    var values: [max_channels]f64 = undefined;
    const results = values[0..@intCast(count)];
    for (results, 2..) |*value, i| {
        const noise = Noise.decode(bytes[i * stride ..][0..stride]) orelse return c.JNI_FALSE;
        const sample = noise.sample(dx * noise.scale + offset_x, 0.0, dz * noise.scale + offset_z);
        if (!std.math.isFinite(sample)) return c.JNI_FALSE;
        value.* = sample;
    }

    const set_region = f.SetDoubleArrayRegion orelse return c.JNI_FALSE;
    set_region(env, output, 0, count, results.ptr);
    return c.JNI_TRUE;
}

pub fn register() void {
    @export(&sampleBatch0, .{ .name = "Java_net_fly_adrenaline_natives_NativeClimateSampler_sampleBatch0" });
}
