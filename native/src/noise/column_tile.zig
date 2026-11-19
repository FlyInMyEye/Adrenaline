const std = @import("std");
const core_limits = @import("../core/limits.zig");
const core_math = @import("../core/math.zig");
const core_vectors = @import("../core/vectors.zig");
const noise_gradients = @import("gradients.zig");

const Vec4 = core_vectors.Vec4;
const Vec4i = core_vectors.Vec4i;

pub const Axis = struct {
    cells: []const i32,
    fractions: []const f64,
    fades: []const f64,
};

pub fn interior(values: []const f64) bool {
    for (values) |value| if (!(value > 0.0 and value < 1.0)) return false;
    return true;
}

const Gradient = struct {
    base: Vec4,
    slope: Vec4,

    fn init(hash: Vec4i, x: Vec4, z: Vec4) Gradient {
        const h = hash & @as(Vec4i, @splat(15));
        const xy = (h < @as(Vec4i, @splat(4))) | (h == @as(Vec4i, @splat(12))) | (h == @as(Vec4i, @splat(14)));
        const xz = (h >= @as(Vec4i, @splat(4))) & (h < @as(Vec4i, @splat(8)));
        const sign_x = ((h & @as(Vec4i, @splat(1))) != @as(Vec4i, @splat(0))) | (h == @as(Vec4i, @splat(14)));
        const sign_z = (h & @as(Vec4i, @splat(2))) != @as(Vec4i, @splat(0));
        const sx = @select(f64, sign_x, -x, x);
        const sz = @select(f64, sign_z, -z, z);
        const y_negative = @select(bool, xy, (h < @as(Vec4i, @splat(4))) & sign_z, (h & @as(Vec4i, @splat(1))) != @as(Vec4i, @splat(0)));
        return .{
            .base = @select(f64, xy, sx, @select(f64, xz, sx + sz, sz)),
            .slope = @select(f64, xz, @as(Vec4, @splat(0)), @select(f64, y_negative, @as(Vec4, @splat(-1)), @as(Vec4, @splat(1)))),
        };
    }

    inline fn eval(self: Gradient, y: Vec4) Vec4 {
        @setFloatMode(.strict);
        return self.slope * y + self.base;
    }
};

fn perm(p: [*]const u8, indices: Vec4i) Vec4i {
    var result: Vec4i = undefined;
    inline for (0..4) |lane| result[lane] = core_math.permutation_value(p, indices[lane]);
    return result;
}

const Plane = struct {
    corners: [4]Gradient,

    fn init(p: [*]const u8, hx: Vec4i, hx1: Vec4i, iy: i32, iz: Vec4i, x: Vec4, z: Vec4) Plane {
        const y: Vec4i = @splat(iy);
        const h0 = perm(p, hx +% y) +% iz;
        const h1 = perm(p, hx1 +% y) +% iz;
        const one: Vec4 = @splat(1);
        const step: Vec4i = @splat(1);
        return .{ .corners = .{
            Gradient.init(perm(p, h0), x, z),               Gradient.init(perm(p, h1), x - one, z),
            Gradient.init(perm(p, h0 +% step), x, z - one), Gradient.init(perm(p, h1 +% step), x - one, z - one),
        } };
    }

    inline fn eval(self: *const Plane, y: Vec4, sx: Vec4) [2]Vec4 {
        const a = self.corners[0].eval(y);
        const b = self.corners[2].eval(y);
        return .{ a + sx * (self.corners[1].eval(y) - a), b + sx * (self.corners[3].eval(y) - b) };
    }
};

pub fn accumulate(comptime masked: bool, masks: []const u64, permutation: [*]const u8, xs: Axis, ys: Axis, zs: Axis, amplitude: f64, factor: f64, out: []f64) void {
    @setFloatMode(.strict);
    const columns = xs.cells.len * zs.cells.len;
    var start: usize = 0;
    while (start < columns) : (start += 4) {
        var x: Vec4 = undefined;
        var z: Vec4 = undefined;
        var fade_x: Vec4 = undefined;
        var fade_z: Vec4 = undefined;
        var lattice_x: Vec4i = undefined;
        var lattice_z: Vec4i = undefined;
        inline for (0..4) |lane| {
            const column = @min(start + lane, columns - 1);
            const xi = column / zs.cells.len;
            const zi = column % zs.cells.len;
            lattice_x[lane] = xs.cells[xi];
            lattice_z[lane] = zs.cells[zi];
            x[lane] = xs.fractions[xi];
            z[lane] = zs.fractions[zi];
            fade_x[lane] = xs.fades[xi];
            fade_z[lane] = zs.fades[zi];
        }
        if (masked) {
            var active: u64 = 0;
            for (start..@min(columns, start + 4)) |column| active |= masks[column];
            if (active == 0) continue;
        }
        const hx = perm(permutation, lattice_x);
        const hx1 = perm(permutation, lattice_x +% @as(Vec4i, @splat(1)));
        var low = Plane.init(permutation, hx, hx1, ys.cells[0], lattice_z, x, z);
        var high = Plane.init(permutation, hx, hx1, ys.cells[0] +% 1, lattice_z, x, z);
        var tile: [core_limits.max_grid_axis]Vec4 = undefined;
        var j: usize = 0;
        while (j < ys.cells.len) {
            const lattice_y = ys.cells[j];
            const begin = j;
            while (j < ys.cells.len and ys.cells[j] == lattice_y) {
                j += 1;
            }
            for (begin..j) |i| {
                const lower = low.eval(@splat(ys.fractions[i]), fade_x);
                const upper = high.eval(@splat(ys.fractions[i] - 1), fade_x);
                const fade_y: Vec4 = @splat(ys.fades[i]);
                const a = lower[0] + fade_y * (upper[0] - lower[0]);
                const b = lower[1] + fade_y * (upper[1] - lower[1]);
                const sample = a + fade_z * (b - a);
                tile[i] = @as(Vec4, @splat(amplitude)) * sample * @as(Vec4, @splat(factor));
            }
            if (j < ys.cells.len) {
                low = if (ys.cells[j] == lattice_y +% 1) high else Plane.init(permutation, hx, hx1, ys.cells[j], lattice_z, x, z);
                high = Plane.init(permutation, hx, hx1, ys.cells[j] +% 1, lattice_z, x, z);
            }
        }
        var row: usize = 0;
        while (row + 4 <= ys.cells.len) : (row += 4) {
            const a = tile[row];
            const b = tile[row + 1];
            const c = tile[row + 2];
            const d = tile[row + 3];
            inline for (0..4) |lane| {
                if (start + lane < columns) {
                    const dest = out[(start + lane) * ys.cells.len + row ..][0..4];
                    const current: Vec4 = @bitCast(dest.*);
                    const updated = current + @as(Vec4, .{ a[lane], b[lane], c[lane], d[lane] });
                    const active: u4 = if (masked) @as(u4, @truncate(masks[start + lane] >> @as(u6, @intCast(row)))) else 0xf;
                    dest.* = @bitCast(@select(f64, @as(@Vector(4, bool), @bitCast(active)), updated, current));
                }
            }
        }
        while (row < ys.cells.len) : (row += 1) {
            inline for (0..4) |lane| {
                if (start + lane < columns and (!masked or masks[start + lane] >> @as(u6, @intCast(row)) & 1 != 0)) {
                    out[(start + lane) * ys.cells.len + row] += tile[row][lane];
                }
            }
        }
    }
}

test "column tiles agree bitwise with individual lattice samples" {
    var permutation: [256]u8 = undefined;
    for (&permutation, 0..) |*entry, i| entry.* = @truncate(i * 73 + 23);
    const xs = Axis{ .cells = &.{ -257, 17, 17 }, .fractions = &.{ 0.5, 0.25, 0.625 }, .fades = &.{ 0.5, 0.1, 0.73 } };
    const zs = Axis{ .cells = &.{ 255, -258, 4 }, .fractions = &.{ 0.25, 0.5, 0.875 }, .fades = &.{ 0.1, 0.5, 0.98 } };
    const ys = Axis{ .cells = &.{ 0, 0, 1, 1, 1, -1, -3, -3, -3 }, .fractions = &.{ 0.25, 0.5, 0.125, 0.375, 0.75, -0.25, 0.01, 0.63, 0.98 }, .fades = &.{ 0.1, 0.5, 0.05, 0.3, 0.9, 0.01, 0.001, 0.7, 0.999 } };
    const masks = [_]u64{ 0, 0, 0, 0, 0x155, 0xaa, 0x100, 0x1ff, 0x0f };
    inline for (.{ false, true }) |masked| {
        var actual = [_]f64{-0.25} ** 81;
        accumulate(masked, &masks, &permutation, xs, ys, zs, -0.75, 0.8125, &actual);
        for (xs.cells, 0..) |gx, xi| {
            const hx = core_math.permutation_value(&permutation, gx);
            const hx1 = core_math.permutation_value(&permutation, gx + 1);
            for (zs.cells, 0..) |gz, zi| {
                const column = xi * zs.cells.len + zi;
                for (ys.cells, 0..) |gy, yi| {
                    const cell = noise_gradients.GradientCell.init(&permutation, hx, hx1, gy, gz);
                    const v = cell.sample_smoothed(xs.fractions[xi], ys.fractions[yi], zs.fractions[zi], xs.fades[xi], ys.fades[yi], zs.fades[zi]);
                    const expected = if (!masked or (masks[column] >> @as(u6, @intCast(yi))) & 1 != 0) -0.25 + -0.75 * v * 0.8125 else -0.25;
                    try std.testing.expectEqual(@as(u64, @bitCast(expected)), @as(u64, @bitCast(actual[column * ys.cells.len + yi])));
                }
            }
        }
    }
    try std.testing.expect(!interior(&.{ 0.25, -0.0 }));
    try std.testing.expect(!interior(&.{ 1.0, 0.75 }));
}
