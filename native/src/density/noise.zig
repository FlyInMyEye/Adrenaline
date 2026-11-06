const backend_runtime = @import("../backend/runtime.zig");

const builtin = @import("builtin");
const backend_abi = @import("../backend/abi.zig");
const core_limits = @import("../core/limits.zig");
const density_read = @import("read.zig");
const noise_normal = @import("../noise/normal.zig");
const noise_scalar = @import("../noise/scalar.zig");

pub const DensityNoise = struct {
    first: [*]const u8,
    first_octaves: u32,
    second: [*]const u8,
    second_octaves: u32,
    factor: f64,

    pub fn read(program: []const u8, counter: *usize) ?DensityNoise {
        const first = density_read.read_program_u64(program, counter) orelse return null;
        const first_octaves = density_read.read_program_u32(program, counter) orelse return null;
        const second = density_read.read_program_u64(program, counter) orelse return null;
        const second_octaves = density_read.read_program_u32(program, counter) orelse return null;
        const factor = density_read.read_program_f64(program, counter) orelse return null;
        if (first == 0 or second == 0 or first_octaves > core_limits.max_octaves or second_octaves > core_limits.max_octaves) return null;
        return .{ .first = @ptrFromInt(@as(usize, @intCast(first))), .first_octaves = first_octaves, .second = @ptrFromInt(@as(usize, @intCast(second))), .second_octaves = second_octaves, .factor = factor };
    }

    pub fn sample(self: DensityNoise, x: f64, y: f64, z: f64) f64 {
        return noise_scalar.normal_noise_value(self.first, self.first_octaves, self.second, self.second_octaves, self.factor, x, y, z);
    }
};

pub fn density_normal_noise_grid(first: [*]const u8, first_octaves: u32, second: [*]const u8, second_octaves: u32, value_factor: f64, x: f64, y: f64, z: f64, x_step: f64, y_step: f64, width: usize, height: usize, values: []f64) void {
    if (comptime builtin.cpu.arch == .x86_64) {
        if (backend_runtime.supports(.avx2)) {
            backend_abi.adrenaline_normal_noise_grid_avx2(first, first_octaves, second, second_octaves, value_factor, x, y, z, x_step, y_step, x_step, width, height, width, values.ptr);
            return;
        }
    }
    noise_normal.normal_noise_grid(first, first_octaves, second, second_octaves, value_factor, x, y, z, x_step, y_step, x_step, width, height, width, values);
}

pub fn density_normal_noise_slice(noise: DensityNoise, x: i32, y: i32, z: i32, step: i32, xz_scale: f64, y_scale: f64, values: []f64) void {
    @setFloatMode(.strict);
    const bits: u64 = @bitCast(@abs(y_scale));
    if (y_scale == 0.0 or (bits & 0x000fffffffffffff == 0 and bits >> 52 >= 959 and bits >> 52 <= 1087)) {
        density_normal_noise_grid(noise.first, noise.first_octaves, noise.second, noise.second_octaves, noise.factor, @as(f64, @floatFromInt(x)) * xz_scale, @as(f64, @floatFromInt(y)) * y_scale, @as(f64, @floatFromInt(z)) * xz_scale, xz_scale, @as(f64, @floatFromInt(step)) * y_scale, 1, values.len, values);
        return;
    }
    for (values, 0..) |*value, index| {
        const block_y = @as(i64, y) + @as(i64, @intCast(index)) * step;
        value.* = noise.sample(@as(f64, @floatFromInt(x)) * xz_scale, @as(f64, @floatFromInt(block_y)) * y_scale, @as(f64, @floatFromInt(z)) * xz_scale);
    }
}
