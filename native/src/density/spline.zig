const std = @import("std");
const core_vectors = @import("../core/vectors.zig");
const density_read = @import("read.zig");

pub fn spline_needs_point(interval: f64, point: usize, count: usize) bool {
    const index: isize = @intFromFloat(interval);
    return if (index < 0) point == 0 else if (index >= count - 1) point == count - 1 else point == index or point == index + 1;
}

pub inline fn spline_any_point4(comptime edge: u2, intervals: []const f64, point: usize, count: usize) bool {
    const target: core_vectors.Vec4 = @splat(@floatFromInt(if (edge == 1) count - 2 else point));
    var i: usize = 0;
    while (i + 4 <= intervals.len) : (i += 4) {
        const values = @trunc(@as(core_vectors.Vec4, @bitCast(intervals[i..][0..4].*)));
        const needed = if (edge == 0) values <= target else if (edge == 1) values >= target else (values == target) | (values + @as(core_vectors.Vec4, @splat(1.0)) == target);
        if (@reduce(.Or, needed)) return true;
    }
    for (intervals[i..]) |interval| if (spline_needs_point(interval, point, count)) return true;
    return false;
}

pub const ConstantSplineShortcut = struct { end: usize, value: f32 };

pub fn constant_spline_outside(program: []const u8, knots: usize, count: usize, coordinates: []const f64) ?ConstantSplineShortcut {
    if (count < 2) return null;
    const first = density_read.density_program_float(program, knots);
    const last = density_read.density_program_float(program, knots + (count - 1) * 8);
    var previous = first;
    for (1..count) |point| {
        const location = density_read.density_program_float(program, knots + point * 8);
        if (!(previous <= location)) return null;
        previous = location;
    }
    var below = density_read.density_program_float(program, knots + 4) == 0.0;
    var above = density_read.density_program_float(program, knots + (count - 1) * 8 + 4) == 0.0;
    var i: usize = 0;
    while (i + 4 <= coordinates.len and (below or above)) : (i += 4) {
        const original: core_vectors.Vec4 = @bitCast(coordinates[i..][0..4].*);
        const value: core_vectors.Vec4f = @floatCast(original);
        below = below and @reduce(.And, value < @as(core_vectors.Vec4f, @splat(first)));
        above = above and @reduce(.And, value >= @as(core_vectors.Vec4f, @splat(last)));
    }
    for (coordinates[i..]) |coordinate| {
        const value: f32 = @floatCast(coordinate);
        below = below and value < first;
        above = above and value >= last;
        if (!below and !above) return null;
    }
    if (!below and !above) return null;
    const target: usize = if (below) 0 else count - 1;
    var value: f32 = undefined;
    var pc = knots + count * 8;
    for (0..count) |point| {
        if (pc + 9 > program.len or program[pc] != 26) return null;
        if (std.mem.readInt(u32, program[pc + 1 ..][0..4], .little) != point) return null;
        const end: usize = std.mem.readInt(u32, program[pc + 5 ..][0..4], .little);
        if (end <= pc + 9 or end >= program.len) return null;
        if (point == target) {
            if (end != pc + 19 or program[pc + 9] != 2 or program[pc + 18] != 27) return null;
            const literal = density_read.density_program_value(program, pc + 10);
            if (!std.math.isFinite(literal)) return null;
            value = @floatCast(literal);
        }
        pc = end;
    }
    if (program[pc] != 28) return null;
    return .{ .end = pc + 1, .value = value };
}

pub fn constant_spline_children(program: []const u8, knots: usize, count: usize, children: *[8]f32) ?usize {
    var pc = knots + count * 8;
    for (0..count) |point| {
        if (!std.math.isFinite(density_read.density_program_float(program, knots + point * 8)) or !std.math.isFinite(density_read.density_program_float(program, knots + point * 8 + 4))) return null;
        if (pc + 19 > program.len or program[pc] != 26 or program[pc + 9] != 2 or program[pc + 18] != 27) return null;
        if (std.mem.readInt(u32, program[pc + 1 ..][0..4], .little) != point or std.mem.readInt(u32, program[pc + 5 ..][0..4], .little) != pc + 19) return null;
        const child = density_read.density_program_value(program, pc + 10);
        if (!std.math.isFinite(child)) return null;
        children[point] = @floatCast(child);
        pc += 19;
    }
    if (pc >= program.len or program[pc] != 28) return null;
    return pc + 1;
}

pub fn density_spline_value(program: []const u8, knots: usize, count: usize, coordinate: f32, interval: isize, left: f32, right: f32) f32 {
    @setFloatMode(.strict);
    if (interval < 0 or interval >= count - 1) {
        const point: usize = if (interval < 0) 0 else count - 1;
        const location = density_read.density_program_float(program, knots + point * 8);
        const derivative = density_read.density_program_float(program, knots + point * 8 + 4);
        return if (derivative == 0.0) left else left + derivative * (coordinate - location);
    }
    const index: usize = @intCast(interval);
    const start = density_read.density_program_float(program, knots + index * 8);
    const end = density_read.density_program_float(program, knots + (index + 1) * 8);
    const first_derivative = density_read.density_program_float(program, knots + index * 8 + 4);
    const second_derivative = density_read.density_program_float(program, knots + (index + 1) * 8 + 4);
    const width = end - start;
    const t = (coordinate - start) / width;
    const difference = right - left;
    const first = first_derivative * width - difference;
    const second = -second_derivative * width + difference;
    return left + t * difference + t * (1.0 - t) * (first + t * (second - first));
}
