const std = @import("std");
const noise_normal = @import("../noise/normal.zig");
const core_vectors = @import("../core/vectors.zig");

pub const max_values = 65536;
threadlocal var noise_values: [max_values]f64 = undefined;

pub fn candidates(comptime avx2: bool, first: [*]const u8, first_count: usize, second: [*]const u8, second_count: usize, factor: f64, x: i32, y: i32, z: i32, size: usize, multiplier: f64, threshold: f64, points: []const i32, output: []i32) usize {
    @setFloatMode(.strict);
    const total = size * size * size;
    const values = noise_values[0..total];
    if (avx2) {
        noise_normal.normal_noise_grid_avx2(first, first_count, second, second_count, factor, @floatFromInt(x), @floatFromInt(y), @floatFromInt(z), 1, 1, 1, size, size, size, values);
    } else {
        noise_normal.normal_noise_grid(first, first_count, second, second_count, factor, @floatFromInt(x), @floatFromInt(y), @floatFromInt(z), 1, 1, 1, size, size, size, values);
    }
    const margin = @max(1.0, @abs(threshold) + @abs(multiplier) * @as(f64, @floatFromInt(points.len / 4))) * 0x1p-36;
    const limit = threshold - margin;
    var written: usize = 0;
    for (0..size) |iz| {
        for (0..size) |iy| {
            var ix: usize = 0;
            if (avx2) {
                const Vec = core_vectors.Vec4;
                while (ix + 3 < size) : (ix += 4) {
                    const xs: Vec = .{ @floatFromInt(ix), @floatFromInt(ix + 1), @floatFromInt(ix + 2), @floatFromInt(ix + 3) };
                    const noise: Vec = .{ values[(ix * size + iz) * size + iy], values[((ix + 1) * size + iz) * size + iy], values[((ix + 2) * size + iz) * size + iy], values[((ix + 3) * size + iz) * size + iy] };
                    const scaled = noise * @as(Vec, @splat(multiplier));
                    var density: Vec = @splat(0.0);
                    var p: usize = 0;
                    while (p < points.len) : (p += 4) {
                        const dx = xs - @as(Vec, @splat(@floatFromInt(points[p])));
                        const dy: f64 = @as(f64, @floatFromInt(iy)) - @as(f64, @floatFromInt(points[p + 1]));
                        const dz: f64 = @as(f64, @floatFromInt(iz)) - @as(f64, @floatFromInt(points[p + 2]));
                        const distance = ((dx * dx + @as(Vec, @splat(dy * dy))) + @as(Vec, @splat(dz * dz))) + @as(Vec, @splat(@floatFromInt(points[p + 3])));
                        density += @as(Vec, @splat(1.0)) / @sqrt(distance) + scaled;
                    }
                    const keep = !(density < @as(Vec, @splat(limit)));
                    inline for (0..4) |lane| {
                        if (keep[lane]) {
                            output[written] = @intCast((iz * size + iy) * size + ix + lane);
                            written += 1;
                        }
                    }
                }
            }
            while (ix < size) : (ix += 1) {
                const noise = values[(ix * size + iz) * size + iy] * multiplier;
                var density: f64 = 0.0;
                var p: usize = 0;
                while (p < points.len) : (p += 4) {
                    const dx: f64 = @as(f64, @floatFromInt(ix)) - @as(f64, @floatFromInt(points[p]));
                    const dy: f64 = @as(f64, @floatFromInt(iy)) - @as(f64, @floatFromInt(points[p + 1]));
                    const dz: f64 = @as(f64, @floatFromInt(iz)) - @as(f64, @floatFromInt(points[p + 2]));
                    density += 1.0 / @sqrt(((dx * dx + dy * dy) + dz * dz) + @as(f64, @floatFromInt(points[p + 3]))) + noise;
                }
                if (!(density < limit)) {
                    output[written] = @intCast((iz * size + iy) * size + ix);
                    written += 1;
                }
            }
        }
    }
    return written;
}
