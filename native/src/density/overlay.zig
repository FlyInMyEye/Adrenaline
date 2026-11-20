const std = @import("std");
const bounds = @import("bounds.zig");
const corners = @import("interpolation.zig").DensityCorners;
const fused = @import("fused.zig");
const math = @import("math.zig");
const read = @import("read.zig");
const spline = @import("spline.zig");
const validation = @import("validation.zig");
const Vec = @import("../core/vectors.zig").Vec4;
const number = read.density_program_value;

fn uint(program: []const u8, offset: usize) u32 {
    return std.mem.readInt(u32, program[offset..][0..4], .little);
}

fn flatHeightTerm(p: []const u8, y: i32, tail: usize) ?f64 {
    @setFloatMode(.strict);
    const left = number(p, 483);
    if (!std.math.isFinite(left)) return null;
    if (left < number(p, 493)) return left;
    if (p[505] != 21 or p[530] != 2 or p[539] != 2 or p[548] != 4 or p[549] != 3 or p[550] != 25) return null;
    const count: usize = uint(p, 551);
    if (count < 2 or count > 8 or 555 + count * 8 >= tail) return null;
    const from_y: f64 = @floatFromInt(@as(i32, @bitCast(uint(p, 506))));
    const to_y: f64 = @floatFromInt(@as(i32, @bitCast(uint(p, 510))));
    const from = number(p, 514);
    const to = number(p, 522);
    const shift = number(p, 531) * number(p, 540);
    if (from_y == to_y or !std.math.isFinite(to - from) or !std.math.isFinite(shift)) return null;

    var coordinates: [8]f64 = undefined;
    for (&coordinates, 0..) |*coordinate, dy| {
        const t = (@as(f64, @floatFromInt(y)) + @as(f64, @floatFromInt(dy)) - from_y) / (to_y - from_y);
        coordinate.* = (if (t < 0.0) from else if (t > 1.0) to else from + t * (to - from)) + shift;
    }
    const edge = spline.constant_spline_outside(p, 555, count, &coordinates) orelse return null;
    if (edge.end + 1 != tail or p[edge.end] != 12 or !std.math.isFinite(edge.value)) return null;
    return math.density_min4_finite(@splat(left), @splat(edge.value))[0];
}

const Overlay = struct {
    add_before: f64,
    scale: f64,
    add_after: f64,
    cap: f64,
    height_term: f64,
    final_literal: f64,
    input: []const f64,
    extra_y: [8][4]f64,

    pub inline fn transform(self: *const Overlay, value: f64) f64 {
        @setFloatMode(.strict);
        return (value + self.add_before) * self.scale + self.add_after;
    }

    pub inline fn write(self: *const Overlay, comptime terrain_only: bool, squeezed: Vec, noodle: Vec, y: usize, x: usize, row: usize, output: []f64) void {
        @setFloatMode(.strict);
        const base = if (terrain_only) squeezed else math.density_min4_finite(squeezed, (noodle + @as(Vec, @splat(self.add_before))) * @as(Vec, @splat(self.scale)) + @as(Vec, @splat(self.add_after)));
        const extra = corners.sample(corners.row(self.extra_y[y], @as(f64, @floatFromInt(x)) * 0.25));
        const capped = math.density_min4_finite(@splat(self.cap), extra);
        const last: Vec = if (self.input.len != 0) @bitCast(self.input[row..][0..4].*) else @splat(self.final_literal);
        output[row..][0..4].* = @bitCast((base + (capped + @as(Vec, @splat(self.height_term)))) + last);
    }
};

fn fixedCap(p: []const u8, cap: f64) ?f64 {
    const a: Vec = @bitCast(p[417..][0..32].*);
    const b: Vec = @bitCast(p[449..][0..32].*);
    const magnitude = @reduce(.Max, @max(@abs(a), @abs(b)));
    const lower = @reduce(.Min, @min(a, b)) - (magnitude * 0x1p-40 + std.math.floatMin(f64));
    if (lower > cap) return cap;
    const first = a[0];
    if (first != 0 and @reduce(.And, (a == @as(Vec, @splat(first))) & (b == @as(Vec, @splat(first)))))
        return math.density_min4_finite(@splat(cap), @splat(first))[0];
    return null;
}

fn fixedSqueeze(p: []const u8) ?f64 {
    @setFloatMode(.strict);
    const a: Vec = @bitCast(p[1..][0..32].*);
    const b: Vec = @bitCast(p[33..][0..32].*);
    const first = a[0];
    const u = if (first != 0 and @reduce(.And, (a == @as(Vec, @splat(first))) & (b == @as(Vec, @splat(first)))))
        math.density_clamp(first * number(p, 66), -1.0, 1.0)
    else if (bounds.terrain_negative_saturated(p)) @as(f64, -1.0) else if (bounds.terrain_positive_saturated(p)) @as(f64, 1.0) else return null;
    return u / 2.0 - u * u * u / 24.0;
}

pub fn evaluate(p: []const u8, y: i32, input: []const f64, output: []f64) bool {
    @setFloatMode(.strict);
    if (p.len < 575 or output.len != 128) return false;
    const offsets = .{ 376, 385, 386, 395, 396, 405, 406, 407, 416, 481, 482, 491, 492 };
    const opcodes = .{ 2, 3, 2, 4, 2, 3, 12, 2, 15, 12, 2, 19, 2 };
    inline for (offsets, opcodes) |offset, opcode| if (p[offset] != opcode) return false;
    const tail: usize = uint(p, 501);
    if (tail <= 505 or tail >= p.len or (p.len - tail != 19 and p.len - tail != 23)) return false;
    if (p[tail] != 2 or p[tail + 9] != 4 or p[tail + 10] != 3 or p[tail + 11] != 3 or p[p.len - 2] != 3 or p[p.len - 1] != 0) return false;
    var final_literal: f64 = 0.0;
    var slot: []const f64 = &.{};
    if (p.len - tail == 19) {
        if (p[tail + 12] != 29) return false;
        const index: usize = uint(p, tail + 13);
        if (index >= input.len / 128) return false;
        slot = input[index * 128 ..][0..128];
    } else {
        if (p[tail + 12] != 2) return false;
        final_literal = number(p, tail + 13);
        if (!std.math.isFinite(final_literal)) return false;
    }
    if (!validation.is_terrain_prefix(p)) return false;
    inline for (.{ 377, 387, 397, 408 }) |offset| if (!(@abs(number(p, offset)) <= 1.0e100)) return false;
    const extra = corners.load(p, 417);
    for (extra.values) |value| if (!(@abs(value) <= 1.0e100)) return false;
    const height_term = (flatHeightTerm(p, y, tail) orelse return false) * number(p, tail + 1);
    if (!std.math.isFinite(height_term)) return false;
    var overlay = Overlay{
        .add_before = number(p, 377),
        .scale = number(p, 387),
        .add_after = number(p, 397),
        .cap = number(p, 408),
        .height_term = height_term,
        .input = slot,
        .final_literal = final_literal,
        .extra_y = undefined,
    };
    const choice = bounds.terrain_choice(p);
    const inside_high = overlay.transform(number(p, 142)) > 0.5;
    const outside_floor: f64 = if (overlay.transform(0.5) > 0.46) 0.5 else 1.0;
    const terrain_only = (choice == .outside or inside_high) and (choice == .inside or
        (overlay.scale >= 0.0 and overlay.transform(outside_floor) > 0.46 and bounds.outside_above(p, outside_floor)));
    if (terrain_only) {
        if (fixedCap(p, overlay.cap)) |cap| {
            if (fixedSqueeze(p)) |base| {
                const constant = base + (cap + height_term);
                if (slot.len == 0) {
                    @memset(output, constant + final_literal);
                } else for (output, slot) |*out, value| out.* = constant + value;
                return true;
            }
        }
    }
    for (&overlay.extra_y, 0..) |*ys, dy| ys.* = extra.y(@as(f64, @floatFromInt(dy)) * 0.125);
    if (terrain_only) {
        fused.evaluate_terrain_rows(false, true, false, .inside_high, p, 8, output, &.{}, &overlay);
    } else switch (choice) {
        inline .inside, .outside, .mixed => |branch| fused.evaluate_terrain_rows(false, true, false, branch, p, 8, output, &.{}, &overlay),
        .inside_high => unreachable,
    }
    return true;
}
