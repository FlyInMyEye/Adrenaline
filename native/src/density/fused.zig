const std = @import("std");
const c = @import("../core/jni.zig").c;
const backend_options = @import("../backend/options.zig");
const core_vectors = @import("../core/vectors.zig");
const density_bounds = @import("bounds.zig");
const density_interpolation = @import("interpolation.zig");
const density_math = @import("math.zig");
const density_read = @import("read.zig");

pub fn evaluate_dominant_ridge(program: []const u8, height: usize, output: []c.jdouble, corner_offset: usize) void {
    @setFloatMode(.strict);
    const corners = density_interpolation.DensityCorners.load(program, corner_offset);
    const offset: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 134));
    for (0..height) |y| {
        const fy = @as(f64, @floatFromInt(y)) / @as(f64, @floatFromInt(height));
        const y_values = corners.y(fy);
        for (0..4) |x| {
            const fx = @as(f64, @floatFromInt(x)) / 4.0;
            const result = @abs(density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(y_values, fx))) + offset;
            const row = ((height - 1 - y) * 4 + x) * 4;
            output[row..][0..4].* = @bitCast(result);
        }
    }
}

pub fn evaluate_fused_ridge(program: []const u8, height: usize, output: []c.jdouble) void {
    @setFloatMode(.strict);
    if (comptime backend_options.is_simd_backend) {
        const dominant = density_bounds.ridge_dominant(program);
        if (dominant != 0) return evaluate_dominant_ridge(program, height, output, if (dominant == 1) 1 else 67);
    }
    const first = density_interpolation.DensityCorners.load(program, 1);
    const second = density_interpolation.DensityCorners.load(program, 67);
    const offset: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 134));
    for (0..height) |y| {
        const fy = @as(f64, @floatFromInt(y)) / @as(f64, @floatFromInt(height));
        const first_y = first.y(fy);
        const second_y = second.y(fy);
        for (0..4) |x| {
            const fx = @as(f64, @floatFromInt(x)) / 4.0;
            const a = @abs(density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(first_y, fx)));
            const b = @abs(density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(second_y, fx)));
            const result = @max(a, b) + offset;
            const row = ((height - 1 - y) * 4 + x) * 4;
            output[row..][0..4].* = @bitCast(result);
        }
    }
}

pub fn try_fill_saturated_terrain(comptime choice: density_bounds.TerrainChoice, program: []const u8, output: []f64) bool {
    @setFloatMode(.strict);
    if (!density_bounds.terrain_negative_saturated(program)) return false;
    const negative: core_vectors.Vec4 = @splat(-1.0);
    const squeezed: core_vectors.Vec4 = negative / @as(core_vectors.Vec4, @splat(2.0)) - negative * negative * negative / @as(core_vectors.Vec4, @splat(24.0));
    const inside: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 142));
    const offset = density_read.density_program_value(program, 378);
    if (comptime choice == .inside) {
        const result = density_math.density_min4_finite(squeezed, inside);
        @memset(output, result[0] + offset);
        return true;
    } else if ((choice == .outside or inside[0] >= squeezed[0]) and density_bounds.terrain_thickness_above_floor(program)) {
        @memset(output, squeezed[0] + offset);
        return true;
    }
    return false;
}

pub fn try_fill_constant_terrain(comptime choice: density_bounds.TerrainChoice, program: []const u8, output: []f64, first: f64) bool {
    @setFloatMode(.strict);
    const raw: core_vectors.Vec4 = @splat(first * density_read.density_program_value(program, 66));
    const low: core_vectors.Vec4 = @splat(-1.0);
    const high: core_vectors.Vec4 = @splat(1.0);
    const clamped = @select(f64, raw < low, low, @select(f64, raw > high, high, raw));
    const squeezed = clamped / @as(core_vectors.Vec4, @splat(2.0)) - clamped * clamped * clamped / @as(core_vectors.Vec4, @splat(24.0));
    const inside: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 142));
    const offset = density_read.density_program_value(program, 378);
    if (comptime choice == .inside) {
        const result = density_math.density_min4_finite(squeezed, inside);
        @memset(output, result[0] + offset);
        return true;
    } else if ((choice == .outside or inside[0] > squeezed[0]) and density_bounds.outside_above(program, squeezed[0])) {
        @memset(output, squeezed[0] + offset);
        return true;
    }
    return false;
}

pub fn evaluate_fused_terrain(program: []const u8, height: usize, output: []c.jdouble) void {
    switch (density_bounds.terrain_choice(program)) {
        .inside => evaluate_terrain_full(.inside, program, height, output),
        .outside => evaluate_terrain_full(.outside, program, height, output),
        .mixed => evaluate_terrain_full(.mixed, program, height, output),
        .inside_high => unreachable,
    }
}

pub fn evaluate_terrain_full(comptime choice: density_bounds.TerrainChoice, program: []const u8, height: usize, output: []c.jdouble) void {
    if (comptime backend_options.is_simd_backend) {
        if (height == 8 and try_fill_saturated_terrain(choice, program, output)) return;
        if (height == 8 and density_bounds.terrain_positive_saturated(program)) {
            const one: core_vectors.Vec4 = @splat(1.0);
            const squeezed = one / @as(core_vectors.Vec4, @splat(2.0)) - one * one * one / @as(core_vectors.Vec4, @splat(24.0));
            if (choice == .inside) {
                const result = density_math.density_min4_finite(squeezed, @as(core_vectors.Vec4, @splat(density_read.density_program_value(program, 142))));
                @memset(output, result[0] + density_read.density_program_value(program, 378));
                return;
            }
            if ((choice == .outside or density_read.density_program_value(program, 142) >= squeezed[0]) and density_bounds.outside_above(program, squeezed[0])) {
                @memset(output, squeezed[0] + density_read.density_program_value(program, 378));
                return;
            }
            evaluate_terrain_impl(false, true, true, choice, program, height, output, &.{});
            return;
        }
    }
    if (height == 8) {
        if (comptime backend_options.is_simd_backend) {
            const first = density_read.density_program_value(program, 1);
            const a: core_vectors.Vec4 = @bitCast(program[1..][0..32].*);
            const b: core_vectors.Vec4 = @bitCast(program[33..][0..32].*);

            if (first != 0.0 and @reduce(.And, a == @as(core_vectors.Vec4, @splat(first))) and @reduce(.And, b == @as(core_vectors.Vec4, @splat(first)))) {
                if (try_fill_constant_terrain(choice, program, output, first)) return;
                if (choice == .inside and density_read.density_program_value(program, 142) >= 0.5)
                    evaluate_terrain_impl(false, true, true, .inside_high, program, height, output, &.{})
                else
                    evaluate_terrain_impl(false, true, true, choice, program, height, output, &.{});
                return;
            }
        }
        if (comptime backend_options.is_simd_backend and choice != .inside) {
            const ceiling = density_bounds.terrain_squeezed_upper_bound(program);
            if ((choice == .outside or density_read.density_program_value(program, 142) >= ceiling) and density_bounds.outside_above(program, ceiling)) {
                evaluate_terrain_impl(false, true, false, .inside_high, program, height, output, &.{});
                return;
            }
        }
        if (comptime backend_options.is_simd_backend and choice == .inside) {
            if (density_read.density_program_value(program, 142) >= 0.5) {
                evaluate_terrain_impl(false, true, false, .inside_high, program, height, output, &.{});
                return;
            }
        }
        evaluate_terrain_impl(false, true, false, choice, program, height, output, &.{});
    } else {
        evaluate_terrain_impl(false, false, false, choice, program, height, output, &.{});
    }
}

pub fn evaluate_terrain_prefix(program: []const u8, height: usize, terrain: []c.jdouble, noodle: []c.jdouble) void {
    evaluate_terrain_impl(true, false, false, .mixed, program, height, terrain, noodle);
}

pub fn evaluate_terrain_impl(comptime prefix: bool, comptime fixed_height: bool, comptime constant_terrain: bool, comptime branch: density_bounds.TerrainChoice, program: []const u8, requested_height: usize, output: []c.jdouble, noodle_output: []c.jdouble) void {
    @setFloatMode(.strict);
    const height = if (fixed_height) 8 else requested_height;
    const ridge_choice: u8 = if (backend_options.is_simd_backend and !prefix and branch != .inside and branch != .inside_high) density_bounds.terrain_ridge_dominant(program) else 0;
    const terrain = density_interpolation.DensityCorners.load(program, 1);
    const toggle = density_interpolation.DensityCorners.load(program, 77);
    const thickness = density_interpolation.DensityCorners.load(program, 151);
    const ridge_a = density_interpolation.DensityCorners.load(program, 216);
    const ridge_b = density_interpolation.DensityCorners.load(program, 282);
    const terrain_scale: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 66));
    const inside_value: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 142));
    const ridge_scale: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 349));
    const minimum: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 360));
    const maximum: core_vectors.Vec4 = @splat(density_read.density_program_value(program, 368));
    const offset: core_vectors.Vec4 = @splat(if (prefix) 0.0 else density_read.density_program_value(program, 378));
    const low: core_vectors.Vec4 = @splat(-1.0);
    const high: core_vectors.Vec4 = @splat(1.0);
    const fixed_squeezed: core_vectors.Vec4 = if (constant_terrain) block: {
        const raw = density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(terrain.y(0.0), 0.0)) * terrain_scale;
        const clamped = @select(f64, raw < low, low, @select(f64, raw > high, high, raw));
        break :block clamped / @as(core_vectors.Vec4, @splat(2.0)) - clamped * clamped * clamped / @as(core_vectors.Vec4, @splat(24.0));
    } else undefined;
    var y: usize = 0;
    while (y < height) : (y += 1) {
        const fy = @as(f64, @floatFromInt(y)) / @as(f64, @floatFromInt(height));
        const terrain_y = terrain.y(fy);
        const toggle_y = toggle.y(fy);
        var thickness_y: [4]f64 = undefined;
        var ridge_a_y: [4]f64 = undefined;
        var ridge_b_y: [4]f64 = undefined;
        var outside_ready = false;
        var x: usize = 0;
        while (x < 4) : (x += 1) {
            const fx = @as(f64, @floatFromInt(x)) / 4.0;
            const squeezed = if (constant_terrain) fixed_squeezed else block: {
                const raw = density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(terrain_y, fx)) * terrain_scale;
                const clamped = @select(f64, raw < low, low, @select(f64, raw > high, high, raw));
                break :block clamped / @as(core_vectors.Vec4, @splat(2.0)) - clamped * clamped * clamped / @as(core_vectors.Vec4, @splat(24.0));
            };
            const inside = if (branch == .inside or branch == .inside_high) @as(@Vector(4, bool), @splat(true)) else if (branch == .outside) @as(@Vector(4, bool), @splat(false)) else block: {
                const choice = density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(toggle_y, fx));
                break :block (choice >= minimum) & (choice < maximum);
            };
            var noodle = inside_value;
            if (!@reduce(.And, inside)) {
                if (!outside_ready) {
                    thickness_y = thickness.y(fy);
                    if (ridge_choice != 2) ridge_a_y = ridge_a.y(fy);
                    if (ridge_choice != 1) ridge_b_y = ridge_b.y(fy);
                    outside_ready = true;
                }
                const a: core_vectors.Vec4 = if (ridge_choice == 2) @splat(0.0) else @abs(density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(ridge_a_y, fx)));
                const b: core_vectors.Vec4 = if (ridge_choice == 1) @splat(0.0) else @abs(density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(ridge_b_y, fx)));
                const outside = density_interpolation.DensityCorners.sample(density_interpolation.DensityCorners.row(thickness_y, fx)) + @max(a, b) * ridge_scale;
                noodle = @select(f64, inside, inside_value, outside);
            }
            if (prefix) {
                inline for (0..4) |z| {
                    const index = (x * 4 + z) * height + y;
                    output[index] = squeezed[z];
                    noodle_output[index] = noodle[z];
                }
            } else {
                const result = (if (branch == .inside_high) squeezed else density_math.density_min4_finite(squeezed, noodle)) + offset;
                const row = ((height - 1 - y) * 4 + x) * 4;
                output[row..][0..4].* = @bitCast(result);
            }
        }
    }
}

test "constant terrain specialization matches normal interpolation" {
    const constants = [_]f64{ 1.0, -1.0, 0.25, -0.25, 1.0e-300, -1.0e-300, 1.0e100, -1.0e100 };
    for (constants) |constant| {
        var program = [_]u8{0} ** 388;
        for (0..8) |i| {
            std.mem.writeInt(u64, program[1 + i * 8 ..][0..8], @bitCast(constant), .little);
            inline for (.{ 77, 151, 216, 282 }) |offset| {
                const value = @as(f64, @floatFromInt(i)) / 7.0 - 0.5;
                std.mem.writeInt(u64, program[offset + i * 8 ..][0..8], @bitCast(value), .little);
            }
        }
        inline for (.{ .{ 66, 0.8 }, .{ 142, -0.1 }, .{ 349, 0.7 }, .{ 360, -0.2 }, .{ 368, 0.2 }, .{ 378, 0.3 } }) |entry| {
            std.mem.writeInt(u64, program[entry[0]..][0..8], @bitCast(@as(f64, entry[1])), .little);
        }
        inline for (.{ density_bounds.TerrainChoice.inside, density_bounds.TerrainChoice.outside, density_bounds.TerrainChoice.mixed }) |choice| {
            var expected: [128]f64 = undefined;
            var actual: [128]f64 = undefined;
            evaluate_terrain_impl(false, true, false, choice, &program, 8, &expected, &.{});
            evaluate_terrain_impl(false, true, true, choice, &program, 8, &actual, &.{});
            for (expected, actual) |a, b| {
                try std.testing.expectEqual(@as(u64, @bitCast(a)), @as(u64, @bitCast(b)));
            }
        }
    }
}
