const std = @import("std");
const c = @import("../core/jni.zig").c;
const backend_options = @import("../backend/options.zig");
const core_limits = @import("../core/limits.zig");
const density_interpreter = @import("interpreter.zig");
const density_math = @import("math.zig");
const density_read = @import("read.zig");

pub fn preliminary_surface_level(program: []const u8, inputs: []const f64, slots: usize, x: c.jint, z: c.jint, min_y: c.jint, max_y: c.jint, step: c.jint) c.jint {
    @setFloatMode(.strict);
    var cache_values: [core_limits.max_column_caches]f64 = undefined;
    var cache_ready: [core_limits.max_column_caches]bool = undefined;
    @memset(cache_ready[0..slots], false);
    var value: [1]f64 = undefined;
    const slide: ?HeightSlide = if (backend_options.is_simd_backend) HeightSlide.parse(program, slots) else null;
    var y: i64 = max_y;
    while (y >= min_y) : (y -= step) {
        const sampled = if (slide) |*pattern| pattern.sample(@intCast(y), cache_values[0..slots], cache_ready[0..slots]) else null;
        if (sampled) |v| {
            value[0] = v;
        } else {
            if (!density_interpreter.evaluate_density_program_generic(1, false, program, x, @intCast(y), z, 1, 1, &value, inputs, cache_values[0..slots], cache_ready[0..slots])) return std.math.minInt(c.jint);
        }
        if (value[0] > 0.390625) return @intCast(y);
    }
    return std.math.maxInt(c.jint);
}

pub const HeightSlide = struct {
    gradients: [3][4]f64,
    parameters: [8]f64,
    slots: [2]usize,

    pub fn parse(program: []const u8, slots: usize) ?HeightSlide {
        @setFloatMode(.strict);
        if (program.len < 218) return null;
        const offsets = [_]usize{ 0, 25, 39, 64, 78, 103, 112, 113, 127, 136, 137, 138, 147, 148, 157, 158, 175, 184, 185, 186, 195, 196, 205, 206, 207, 216, 217 };
        const opcodes = [_]u8{ 21, 19, 21, 19, 21, 32, 3, 19, 32, 20, 10, 2, 4, 2, 3, 5, 2, 3, 20, 2, 3, 2, 3, 20, 2, 3, 0 };
        inline for (offsets, opcodes) |offset, opcode| if (program[offset] != opcode) return null;
        inline for (.{ 26, 65, 114 }) |offset| if (program[offset] != 1) return null;
        inline for (.{ 35, 74, 123 }, .{ 207, 186, 137 }) |offset, target| {
            if (std.mem.readInt(u32, program[offset..][0..4], .little) != target) return null;
        }
        var result: HeightSlide = undefined;
        result.slots = .{ std.mem.readInt(u32, program[104..][0..4], .little), std.mem.readInt(u32, program[128..][0..4], .little) };
        if (result.slots[0] >= slots or result.slots[1] >= slots) return null;
        if (std.mem.readInt(u32, program[108..][0..4], .little) >= program.len or std.mem.readInt(u32, program[132..][0..4], .little) >= program.len) return null;
        inline for (.{ 1, 40, 79 }, 0..) |offset, i| {
            const from_y: i32 = @bitCast(std.mem.readInt(u32, program[offset..][0..4], .little));
            const to_y: i32 = @bitCast(std.mem.readInt(u32, program[offset + 4 ..][0..4], .little));
            const first: f64 = @floatFromInt(from_y);
            const last: f64 = @floatFromInt(to_y);
            result.gradients[i] = .{ first, last - first, density_read.density_program_value(program, offset + 8), density_read.density_program_value(program, offset + 16) };
        }
        inline for (.{ 139, 149, 159, 167, 176, 187, 197, 208 }, 0..) |offset, i| result.parameters[i] = density_read.density_program_value(program, offset);
        return result;
    }

    pub fn sample_gradient(data: [4]f64, y: f64) f64 {
        @setFloatMode(.strict);
        if (data[1] > 0.0) {
            if (y < data[0]) return data[2];
            if (y > data[0] + data[1]) return data[3];
        } else if (data[1] < 0.0) {
            if (y > data[0]) return data[2];
            if (y < data[0] + data[1]) return data[3];
        }
        const t = (y - data[0]) / data[1];
        return if (t < 0.0) data[2] else if (t > 1.0) data[3] else data[2] + t * (data[3] - data[2]);
    }

    pub fn sample(self: *const HeightSlide, y: c.jint, values: []const f64, ready: []const bool) ?f64 {
        @setFloatMode(.strict);
        const block_y = @as(f64, @floatFromInt(y)) + 0.0;
        var bottom = sample_gradient(self.gradients[0], block_y);
        if (bottom == 0.0) {
            bottom = 0.0;
        } else {
            var top = sample_gradient(self.gradients[1], block_y);
            if (top == 0.0) {
                top = 0.0;
            } else {
                if (!ready[self.slots[0]]) return null;
                var terrain = sample_gradient(self.gradients[2], block_y) + values[self.slots[0]];
                if (terrain == 0.0) {
                    terrain = 0.0;
                } else {
                    if (!ready[self.slots[1]]) return null;
                    terrain *= values[self.slots[1]];
                }
                if (terrain <= 0.0) terrain *= 0.25;
                terrain *= self.parameters[0];
                terrain += self.parameters[1];
                terrain = density_math.density_clamp(terrain, self.parameters[2], self.parameters[3]);
                terrain += self.parameters[4];
                top *= terrain;
            }
            top += self.parameters[5];
            top += self.parameters[6];
            bottom *= top;
        }
        return bottom + self.parameters[7];
    }
};
