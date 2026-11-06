const backend_runtime = @import("../backend/runtime.zig");

const std = @import("std");
const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;
const backend_abi = @import("../backend/abi.zig");
const backend_options = @import("../backend/options.zig");
const core_limits = @import("../core/limits.zig");
const core_vectors = @import("../core/vectors.zig");
const density_frames = @import("frames.zig");
const density_fused = @import("fused.zig");
const density_math = @import("math.zig");
const density_noise = @import("noise.zig");
const density_read = @import("read.zig");
const density_spline = @import("spline.zig");
const density_validation = @import("validation.zig");
const noise_blended = @import("../noise/blended.zig");
const noise_types = @import("../noise/types.zig");

pub noinline fn evaluate_density_program_generic(comptime capacity: usize, comptime fixed_cell: bool, program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, requested_width: usize, requested_height: usize, output: []c.jdouble, inputs: []const f64, cache_values: []f64, cache_ready: []bool) bool {
    return evaluate_density_grid(capacity, fixed_cell, false, program, base_x, base_y, base_z, requested_width, requested_height, 1, 1, output, inputs, cache_values, cache_ready);
}

pub fn evaluate_density_slice(comptime capacity: usize, program: []const u8, x: i32, y: i32, z: i32, step: i32, output: []f64, inputs: []const f64) bool {
    return evaluate_density_grid(capacity, false, true, program, x, y, z, 1, output.len, 1, step, output, inputs, &.{}, &.{});
}

pub fn evaluate_density_lattice(comptime capacity: usize, program: []const u8, x: i32, y: i32, z: i32, width: usize, height: usize, step_xz: i32, step_y: i32, output: []f64, inputs: []const f64) bool {
    return evaluate_density_grid(capacity, false, true, program, x, y, z, width, height, step_xz, step_y, output, inputs, &.{}, &.{});
}

fn LatticeStack(comptime capacity: usize) type {
    return struct {
        threadlocal var values: [core_limits.max_density_stack][capacity]f64 = undefined;
    };
}

fn evaluate_density_grid(comptime capacity: usize, comptime fixed_cell: bool, comptime slice: bool, program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, requested_width: usize, requested_height: usize, step_xz: i32, step_y: i32, output: []c.jdouble, inputs: []const f64, cache_values: []f64, cache_ready: []bool) bool {
    @setFloatMode(.strict);
    const width = if (fixed_cell) 4 else requested_width;
    const height = if (fixed_cell) 8 else requested_height;
    var local_stack: [if (slice and capacity > 256) 0 else core_limits.max_density_stack][capacity]f64 = undefined;
    const stack: [][capacity]f64 = if (slice and capacity > 256) &LatticeStack(capacity).values else &local_stack;
    var interpolation_width: [@max(4, capacity)]f64 = undefined;
    var interpolation_height: [capacity]f64 = undefined;
    var interpolation_ready = false;
    var call_frames: [core_limits.max_density_stack]density_frames.DensityCallFrame = undefined;
    var call_count: usize = 0;
    var stack_size: usize = 0;
    var program_counter: usize = 0;
    var range_frames: [core_limits.max_density_stack]density_frames.DensityRangeFrame = undefined;
    var range_count: usize = 0;
    var spline_frames: [core_limits.max_density_stack]density_frames.DensitySplineFrame = undefined;
    var spline_count: usize = 0;
    const total = output.len;
    if (fixed_cell and density_validation.is_terrain_prefix(program)) {
        if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
            if (backend_runtime.supports(.avx2)) {
                backend_abi.adrenaline_terrain_prefix_avx2(program.ptr, height, &stack[0], &stack[1]);
            } else {
                density_fused.evaluate_terrain_prefix(program, height, stack[0][0..total], stack[1][0..total]);
            }
        } else {
            density_fused.evaluate_terrain_prefix(program, height, stack[0][0..total], stack[1][0..total]);
        }
        stack_size = 2;
        program_counter = 376;
    }
    while (program_counter < program.len) {
        const opcode = density_read.read_program_u8(program, &program_counter) orelse return false;
        switch (opcode) {
            0 => {
                if (stack_size != 1 or range_count != 0 or spline_count != 0 or call_count != 0 or (program_counter != program.len and (capacity != 1 or cache_ready.len == 0))) {
                    return false;
                }
                if (slice) {
                    @memcpy(output, stack[0][0..total]);
                    return true;
                }
                var y_index: usize = 0;
                while (y_index < height) : (y_index += 1) {
                    const output_y = height - 1 - y_index;
                    var x_index: usize = 0;
                    while (x_index < width) : (x_index += 1) {
                        var z_index: usize = 0;
                        while (z_index < width) : (z_index += 1) {
                            output[(output_y * width + x_index) * width + z_index] = stack[0][(x_index * width + z_index) * height + y_index];
                        }
                    }
                }
                return true;
            },
            1 => {
                if (stack_size >= core_limits.max_density_stack) {
                    return false;
                }
                const first_address = density_read.read_program_u64(program, &program_counter) orelse return false;
                const first_octaves = density_read.read_program_u32(program, &program_counter) orelse return false;
                const second_address = density_read.read_program_u64(program, &program_counter) orelse return false;
                const second_octaves = density_read.read_program_u32(program, &program_counter) orelse return false;
                const value_factor = density_read.read_program_f64(program, &program_counter) orelse return false;
                const xz_scale = density_read.read_program_f64(program, &program_counter) orelse return false;
                const y_scale = density_read.read_program_f64(program, &program_counter) orelse return false;
                if (first_address == 0 or second_address == 0 or first_octaves > core_limits.max_octaves or second_octaves > core_limits.max_octaves) {
                    return false;
                }
                const first: [*]const u8 = @ptrFromInt(@as(usize, @intCast(first_address)));
                const second: [*]const u8 = @ptrFromInt(@as(usize, @intCast(second_address)));
                if (slice and width == 1) {
                    density_noise.density_normal_noise_slice(.{ .first = first, .first_octaves = first_octaves, .second = second, .second_octaves = second_octaves, .factor = value_factor }, base_x, base_y, base_z, step_y, xz_scale, y_scale, stack[stack_size][0..total]);
                } else {
                    density_noise.density_normal_noise_grid(first, first_octaves, second, second_octaves, value_factor, @as(f64, @floatFromInt(base_x)) * xz_scale, @as(f64, @floatFromInt(base_y)) * y_scale, @as(f64, @floatFromInt(base_z)) * xz_scale, xz_scale * @as(f64, @floatFromInt(step_xz)), y_scale * @as(f64, @floatFromInt(step_y)), width, height, stack[stack_size][0..total]);
                }
                stack_size += 1;
            },
            2 => {
                if (stack_size >= core_limits.max_density_stack) {
                    return false;
                }
                var value = density_read.read_program_f64(program, &program_counter) orelse return false;
                if (comptime backend_options.is_simd_backend and capacity != 1) {
                    if (!std.math.isFinite(value)) return if (slice) false else backend_abi.adrenaline_density_scalar(program.ptr, program.len, base_x, base_y, base_z, width, height, output.ptr) != 0;
                }
                if (comptime backend_options.is_simd_backend and capacity == 1) {
                    if (stack_size != 0 and program_counter < program.len) {
                        switch (program[program_counter]) {
                            3 => {
                                stack[stack_size - 1][0] += value;
                                program_counter += 1;
                                continue;
                            },
                            4 => {
                                stack[stack_size - 1][0] *= value;
                                program_counter += 1;
                                continue;
                            },
                            else => {},
                        }
                    }
                }
                if (capacity != 1 and stack_size + 1 < core_limits.max_density_stack) {
                    while (program_counter + 10 <= program.len and program[program_counter] == 2 and std.math.isFinite(value)) {
                        const binary = program[program_counter + 9];
                        if (binary != 3 and binary != 4) break;
                        const right = density_read.density_program_value(program, program_counter + 1);
                        if (!std.math.isFinite(right)) break;
                        value = if (binary == 3) value + right else value * right;
                        program_counter += 10;
                    }
                }
                if (comptime backend_options.is_simd_backend and capacity != 1) {
                    if (!std.math.isFinite(value)) return if (slice) false else backend_abi.adrenaline_density_scalar(program.ptr, program.len, base_x, base_y, base_z, width, height, output.ptr) != 0;
                }
                if (capacity != 1 and !std.math.isNan(value) and stack_size != 0 and program_counter < program.len) {
                    const next = program[program_counter];
                    if (next == 3) {
                        for (stack[stack_size - 1][0..total]) |*entry| {
                            entry.* += value;
                        }
                        program_counter += 1;
                        continue;
                    } else if (next == 4) {
                        for (stack[stack_size - 1][0..total]) |*entry| {
                            entry.* *= value;
                        }
                        program_counter += 1;
                        continue;
                    }
                }
                @memset(stack[stack_size][0..total], value);
                stack_size += 1;
            },
            3 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* += right;
                }
                stack_size -= 1;
            },
            4 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* *= right;
                }
                stack_size -= 1;
            },
            20 => {
                if (stack_size < 2) return false;
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* = if (left.* == 0.0) 0.0 else left.* * right;
                }
                stack_size -= 1;
            },
            5 => {
                if (stack_size == 0) {
                    return false;
                }
                const minimum = density_read.read_program_f64(program, &program_counter) orelse return false;
                const maximum = density_read.read_program_f64(program, &program_counter) orelse return false;
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* = density_math.density_clamp(entry.*, minimum, maximum);
                }
            },
            6 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* = @abs(entry.*);
                }
            },
            7 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* *= entry.*;
                }
            },
            8 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    entry.* = entry.* * entry.* * entry.*;
                }
            },
            9 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    if (entry.* <= 0.0) {
                        entry.* *= 0.5;
                    }
                }
            },
            10 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    if (entry.* <= 0.0) {
                        entry.* *= 0.25;
                    }
                }
            },
            11 => {
                if (stack_size == 0) {
                    return false;
                }
                for (stack[stack_size - 1][0..total]) |*entry| {
                    const value = density_math.density_clamp(entry.*, -1.0, 1.0);
                    entry.* = value / 2.0 - value * value * value / 24.0;
                }
            },
            12 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* = if (std.math.isNan(left.*)) left.* else if (std.math.isNan(right)) right else if (left.* == 0.0 and right == 0.0) @bitCast(@as(u64, @bitCast(left.*)) | @as(u64, @bitCast(right))) else @min(left.*, right);
                }
                stack_size -= 1;
            },
            13 => {
                if (stack_size < 2) {
                    return false;
                }
                for (stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*left, right| {
                    left.* = if (std.math.isNan(left.*)) left.* else if (std.math.isNan(right)) right else if (left.* == 0.0 and right == 0.0) @bitCast(@as(u64, @bitCast(left.*)) & @as(u64, @bitCast(right))) else @max(left.*, right);
                }
                stack_size -= 1;
            },
            14 => {
                if (stack_size < 3) {
                    return false;
                }
                const minimum = density_read.read_program_f64(program, &program_counter) orelse return false;
                const maximum = density_read.read_program_f64(program, &program_counter) orelse return false;
                for (stack[stack_size - 3][0..total], stack[stack_size - 2][0..total], stack[stack_size - 1][0..total]) |*input, inside, outside| {
                    input.* = if (input.* >= minimum and input.* < maximum) inside else outside;
                }
                stack_size -= 2;
            },
            16 => {
                if (stack_size == 0 or range_count >= range_frames.len) return false;
                const minimum = density_read.read_program_f64(program, &program_counter) orelse return false;
                const maximum = density_read.read_program_f64(program, &program_counter) orelse return false;
                const outside: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                const end: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                if (outside <= program_counter or end <= outside or end >= program.len) return false;
                var any_inside = false;
                var any_outside = false;
                for (stack[stack_size - 1][0..total]) |value| {
                    if (value >= minimum and value < maximum) {
                        any_inside = true;
                    } else {
                        any_outside = true;
                    }
                    if (any_inside and any_outside) break;
                }
                const mode: u8 = if (any_inside and any_outside) 0 else if (any_inside) 1 else 2;
                range_frames[range_count] = .{ .base = stack_size - 1, .outside = outside, .end = end, .minimum = minimum, .maximum = maximum, .mode = mode };
                range_count += 1;
                if (mode != 0) stack_size -= 1;
                if (mode == 2) program_counter = outside;
            },
            17 => {
                if (range_count == 0) return false;
                const frame = range_frames[range_count - 1];
                if (program_counter != frame.outside) return false;
                if (frame.mode == 1) {
                    if (stack_size != frame.base + 1) return false;
                    program_counter = frame.end;
                    range_count -= 1;
                } else if (frame.mode != 0 or stack_size != frame.base + 2) {
                    return false;
                }
            },
            18 => {
                if (range_count == 0) return false;
                const frame = range_frames[range_count - 1];
                if (program_counter != frame.end) return false;
                if (frame.mode == 0) {
                    if (stack_size != frame.base + 3) return false;
                    for (stack[frame.base][0..total], stack[frame.base + 1][0..total], stack[frame.base + 2][0..total]) |*input, inside, outside| {
                        input.* = if (input.* >= frame.minimum and input.* < frame.maximum) inside else outside;
                    }
                    stack_size -= 2;
                } else if (frame.mode != 2 or stack_size != frame.base + 1) {
                    return false;
                }
                range_count -= 1;
            },
            19 => {
                if (stack_size == 0) return false;
                const operation = density_read.read_program_u8(program, &program_counter) orelse return false;
                const bound = density_read.read_program_f64(program, &program_counter) orelse return false;
                const end: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                if (operation < 1 or operation > 3 or end <= program_counter or end >= program.len) return false;
                var skip = true;
                for (stack[stack_size - 1][0..total]) |value| {
                    const shortcut = switch (operation) {
                        1 => value == 0.0,
                        2 => value < bound,
                        3 => value > bound,
                        else => unreachable,
                    };
                    if (!shortcut) {
                        skip = false;
                        break;
                    }
                }
                if (skip) {
                    if (operation == 1) @memset(stack[stack_size - 1][0..total], 0.0);
                    program_counter = end;
                }
            },
            15 => {
                if (slice) return false;
                if (stack_size >= core_limits.max_density_stack) {
                    return false;
                }
                const noise000 = density_read.read_program_f64(program, &program_counter) orelse return false;
                const noise001 = density_read.read_program_f64(program, &program_counter) orelse return false;
                const noise100 = density_read.read_program_f64(program, &program_counter) orelse return false;
                const noise101 = density_read.read_program_f64(program, &program_counter) orelse return false;
                const noise010 = density_read.read_program_f64(program, &program_counter) orelse return false;
                const noise011 = density_read.read_program_f64(program, &program_counter) orelse return false;
                const noise110 = density_read.read_program_f64(program, &program_counter) orelse return false;
                const noise111 = density_read.read_program_f64(program, &program_counter) orelse return false;
                if (comptime backend_options.is_simd_backend and capacity != 1) {
                    if (!std.math.isFinite(noise000) or !std.math.isFinite(noise001) or !std.math.isFinite(noise100) or !std.math.isFinite(noise101) or !std.math.isFinite(noise010) or !std.math.isFinite(noise011) or !std.math.isFinite(noise110) or !std.math.isFinite(noise111)) {
                        return if (slice) false else backend_abi.adrenaline_density_scalar(program.ptr, program.len, base_x, base_y, base_z, width, height, output.ptr) != 0;
                    }
                }
                if (!interpolation_ready) {
                    var interpolation_index: usize = 0;
                    while (interpolation_index < width) : (interpolation_index += 1) {
                        interpolation_width[interpolation_index] = @as(f64, @floatFromInt(interpolation_index)) / @as(f64, @floatFromInt(width));
                    }
                    interpolation_index = 0;
                    while (interpolation_index < height) : (interpolation_index += 1) {
                        interpolation_height[interpolation_index] = @as(f64, @floatFromInt(interpolation_index)) / @as(f64, @floatFromInt(height));
                    }
                    interpolation_ready = true;
                }
                if (stack_size == 0 and program_counter + 1 == program.len and program[program_counter] == 0) {
                    var direct_y: usize = 0;
                    while (direct_y < height) : (direct_y += 1) {
                        const fy = interpolation_height[direct_y];
                        const corner00 = noise000 + fy * (noise010 - noise000);
                        const corner10 = noise100 + fy * (noise110 - noise100);
                        const corner01 = noise001 + fy * (noise011 - noise001);
                        const corner11 = noise101 + fy * (noise111 - noise101);
                        var direct_x: usize = 0;
                        while (direct_x < width) : (direct_x += 1) {
                            const fx = interpolation_width[direct_x];
                            const corner0 = corner00 + fx * (corner10 - corner00);
                            const corner1 = corner01 + fx * (corner11 - corner01);
                            const row = ((height - 1 - direct_y) * width + direct_x) * width;
                            if (width == 4) {
                                const z4: core_vectors.Vec4 = @bitCast(interpolation_width[0..4].*);
                                const result = @as(core_vectors.Vec4, @splat(corner0)) + z4 * @as(core_vectors.Vec4, @splat(corner1 - corner0));
                                output[row..][0..4].* = @bitCast(result);
                                continue;
                            }
                            var direct_z: usize = 0;
                            while (direct_z < width) : (direct_z += 1) {
                                const fz = interpolation_width[direct_z];
                                output[row + direct_z] = corner0 + fz * (corner1 - corner0);
                            }
                        }
                    }
                    return true;
                }
                var y_corners: [capacity][4]f64 = undefined;
                var y_index: usize = 0;
                while (y_index < height) : (y_index += 1) {
                    const y_lerp = interpolation_height[y_index];
                    y_corners[y_index] = .{
                        noise000 + y_lerp * (noise010 - noise000),
                        noise100 + y_lerp * (noise110 - noise100),
                        noise001 + y_lerp * (noise011 - noise001),
                        noise101 + y_lerp * (noise111 - noise101),
                    };
                }
                var x_corners: [capacity][2]f64 = undefined;
                var x_index: usize = 0;
                while (x_index < width) : (x_index += 1) {
                    const x_lerp = interpolation_width[x_index];
                    y_index = 0;
                    while (y_index < height) : (y_index += 1) {
                        const corners = y_corners[y_index];
                        x_corners[y_index] = .{
                            corners[0] + x_lerp * (corners[1] - corners[0]),
                            corners[2] + x_lerp * (corners[3] - corners[2]),
                        };
                    }
                    var z_index: usize = 0;
                    while (z_index < width) : (z_index += 1) {
                        const z_lerp = interpolation_width[z_index];
                        const offset = (x_index * width + z_index) * height;
                        y_index = 0;
                        while (y_index < height) : (y_index += 1) {
                            const corners = x_corners[y_index];
                            stack[stack_size][offset + y_index] = corners[0] + z_lerp * (corners[1] - corners[0]);
                        }
                    }
                }
                stack_size += 1;
            },
            21 => {
                if (stack_size >= core_limits.max_density_stack) return false;
                const from_y: i32 = @bitCast(density_read.read_program_u32(program, &program_counter) orelse return false);
                const to_y: i32 = @bitCast(density_read.read_program_u32(program, &program_counter) orelse return false);
                const from = density_read.read_program_f64(program, &program_counter) orelse return false;
                const to = density_read.read_program_f64(program, &program_counter) orelse return false;
                if (comptime backend_options.is_simd_backend and capacity != 1) {
                    if (!std.math.isFinite(from) or !std.math.isFinite(to)) return if (slice) false else backend_abi.adrenaline_density_scalar(program.ptr, program.len, base_x, base_y, base_z, width, height, output.ptr) != 0;
                }
                for (0..height) |y| {
                    const block_y = @as(f64, @floatFromInt(base_y)) + @as(f64, @floatFromInt(y)) * @as(f64, @floatFromInt(step_y));
                    const t = (block_y - @as(f64, @floatFromInt(from_y))) / (@as(f64, @floatFromInt(to_y)) - @as(f64, @floatFromInt(from_y)));
                    const value = if (t < 0.0) from else if (t > 1.0) to else from + t * (to - from);
                    for (0..width * width) |column| stack[stack_size][column * height + y] = value;
                }
                stack_size += 1;
            },
            22 => {
                if (stack_size >= core_limits.max_density_stack) return false;
                const kind = density_read.read_program_u8(program, &program_counter) orelse return false;
                const noise = density_noise.DensityNoise.read(program, &program_counter) orelse return false;
                if (kind > 2) return false;
                if (kind != 2) {
                    if (slice and width == 1) {
                        density_noise.density_normal_noise_slice(noise, base_x, if (kind == 0) base_y else 0, base_z, step_y, 0.25, if (kind == 0) 0.25 else 0.0, stack[stack_size][0..total]);
                    } else {
                        density_noise.density_normal_noise_grid(noise.first, noise.first_octaves, noise.second, noise.second_octaves, noise.factor, @as(f64, @floatFromInt(base_x)) * 0.25, if (kind == 0) @as(f64, @floatFromInt(base_y)) * 0.25 else 0.0, @as(f64, @floatFromInt(base_z)) * 0.25, 0.25 * @as(f64, @floatFromInt(step_xz)), if (kind == 0) 0.25 * @as(f64, @floatFromInt(step_y)) else 0.0, width, height, stack[stack_size][0..total]);
                    }
                    for (stack[stack_size][0..total]) |*value| value.* *= 4.0;
                } else {
                    for (0..width) |x| {
                        for (0..width) |z| {
                            const value = noise.sample((@as(f64, @floatFromInt(base_z)) + @as(f64, @floatFromInt(z)) * @as(f64, @floatFromInt(step_xz))) * 0.25, (@as(f64, @floatFromInt(base_x)) + @as(f64, @floatFromInt(x)) * @as(f64, @floatFromInt(step_xz))) * 0.25, 0.0) * 4.0;
                            @memset(stack[stack_size][(x * width + z) * height ..][0..height], value);
                        }
                    }
                }
                stack_size += 1;
            },
            23 => {
                if (stack_size < 3) return false;
                const height_independent = density_read.read_program_u8(program, &program_counter) orelse return false;
                if (height_independent > 1) return false;
                const noise = density_noise.DensityNoise.read(program, &program_counter) orelse return false;
                const xz_scale = density_read.read_program_f64(program, &program_counter) orelse return false;
                const y_scale = density_read.read_program_f64(program, &program_counter) orelse return false;
                for (0..width) |x| {
                    for (0..width) |z| {
                        for (0..if (height_independent == 1) @as(usize, 1) else height) |y| {
                            const i = (x * width + z) * height + y;
                            const sx = (@as(f64, @floatFromInt(base_x)) + @as(f64, @floatFromInt(x)) * @as(f64, @floatFromInt(step_xz))) * xz_scale + stack[stack_size - 3][i];
                            const sy = (@as(f64, @floatFromInt(base_y)) + @as(f64, @floatFromInt(y)) * @as(f64, @floatFromInt(step_y))) * y_scale + stack[stack_size - 2][i];
                            const sz = (@as(f64, @floatFromInt(base_z)) + @as(f64, @floatFromInt(z)) * @as(f64, @floatFromInt(step_xz))) * xz_scale + stack[stack_size - 1][i];
                            stack[stack_size - 3][i] = noise.sample(sx, sy, sz);
                        }
                        if (height_independent == 1) {
                            const offset = (x * width + z) * height;
                            @memset(stack[stack_size - 3][offset..][0..height], stack[stack_size - 3][offset]);
                        }
                    }
                }
                stack_size -= 2;
            },
            24 => {
                if (stack_size == 0) return false;
                const kind = density_read.read_program_u8(program, &program_counter) orelse return false;
                const noise = density_noise.DensityNoise.read(program, &program_counter) orelse return false;
                if (kind > 1) return false;
                for (0..width) |x| {
                    for (0..width) |z| {
                        for (0..height) |y| {
                            const i = (x * width + z) * height + y;
                            const value = stack[stack_size - 1][i];
                            const rarity: f64 = if (kind == 0)
                                (if (value < -0.5) 0.75 else if (value < 0.0) 1.0 else if (value < 0.5) 1.5 else 2.0)
                            else
                                (if (value < -0.75) 0.5 else if (value < -0.5) 0.75 else if (value < 0.5) 1.0 else if (value < 0.75) 2.0 else 3.0);
                            const nx = (@as(f64, @floatFromInt(base_x)) + @as(f64, @floatFromInt(x)) * @as(f64, @floatFromInt(step_xz))) / rarity;
                            const ny = (@as(f64, @floatFromInt(base_y)) + @as(f64, @floatFromInt(y)) * @as(f64, @floatFromInt(step_y))) / rarity;
                            const nz = (@as(f64, @floatFromInt(base_z)) + @as(f64, @floatFromInt(z)) * @as(f64, @floatFromInt(step_xz))) / rarity;
                            stack[stack_size - 1][i] = rarity * @abs(noise.sample(nx, ny, nz));
                        }
                    }
                }
            },
            25 => {
                if (stack_size == 0 or stack_size + 3 > core_limits.max_density_stack or spline_count >= core_limits.max_density_stack) return false;
                const count: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                if (count == 0 or count > 8192 or count * 8 > program.len - program_counter) return false;
                if (comptime backend_options.is_simd_backend and capacity != 1) {
                    var point: usize = 0;
                    while (point + 4 <= count) : (point += 4) {
                        const data: core_vectors.Vec8f = @bitCast(program[program_counter + point * 8 ..][0..32].*);
                        if (!@reduce(.And, @abs(data) < @as(core_vectors.Vec8f, @splat(std.math.inf(f32))))) return if (slice) false else backend_abi.adrenaline_density_scalar(program.ptr, program.len, base_x, base_y, base_z, width, height, output.ptr) != 0;
                    }
                    while (point < count) : (point += 1) {
                        if (!std.math.isFinite(density_read.density_program_float(program, program_counter + point * 8)) or !std.math.isFinite(density_read.density_program_float(program, program_counter + point * 8 + 4))) return if (slice) false else backend_abi.adrenaline_density_scalar(program.ptr, program.len, base_x, base_y, base_z, width, height, output.ptr) != 0;
                    }
                }
                const base = stack_size - 1;
                if (count <= 8 and stack_size + 3 < core_limits.max_density_stack) {
                    if (comptime backend_options.is_simd_backend and capacity != 1) {
                        if (density_spline.constant_spline_outside(program, program_counter, count, stack[base][0..total])) |shortcut| {
                            @memset(stack[base][0..total], shortcut.value);
                            program_counter = shortcut.end;
                            continue;
                        }
                    }
                    var children: [8]f32 = undefined;
                    if (density_spline.constant_spline_children(program, program_counter, count, &children)) |end| {
                        if (count == 1 and density_read.density_program_float(program, program_counter + 4) == 0.0) {
                            @memset(stack[base][0..total], children[0]);
                        } else for (stack[base][0..total]) |*coordinate| {
                            const value: f32 = @floatCast(coordinate.*);
                            var low: usize = 0;
                            var high = count;
                            while (low < high) {
                                const middle = low + (high - low) / 2;
                                const location = density_read.density_program_float(program, program_counter + middle * 8);
                                if (value < location) high = middle else low = middle + 1;
                            }
                            const left = children[if (low == 0) 0 else low - 1];
                            const right = children[@min(low, count - 1)];
                            coordinate.* = density_spline.density_spline_value(program, program_counter, count, value, @as(isize, @intCast(low)) - 1, left, right);
                        }
                        program_counter = end;
                        continue;
                    }
                }
                spline_frames[spline_count] = .{ .base = base, .knots = program_counter, .count = count, .point = 0 };
                spline_count += 1;
                const small_sorted = count == 1 or (count == 4 and
                    density_read.density_program_float(program, program_counter) <= density_read.density_program_float(program, program_counter + 8) and
                    density_read.density_program_float(program, program_counter + 8) <= density_read.density_program_float(program, program_counter + 16) and
                    density_read.density_program_float(program, program_counter + 16) <= density_read.density_program_float(program, program_counter + 24));
                if (fixed_cell and backend_options.is_simd_backend and small_sorted) {
                    var index: usize = 0;
                    while (index < total) : (index += 4) {
                        const original: core_vectors.Vec4 = @bitCast(stack[base][index..][0..4].*);
                        const value: core_vectors.Vec4f = @floatCast(original);
                        var interval: core_vectors.Vec4i = @splat(-1);
                        for (0..count) |point| {
                            const location: core_vectors.Vec4f = @splat(density_read.density_program_float(program, program_counter + point * 8));
                            interval += @select(i32, value < location, @as(core_vectors.Vec4i, @splat(0)), @as(core_vectors.Vec4i, @splat(1)));
                        }
                        stack[base][index..][0..4].* = @bitCast(@as(core_vectors.Vec4, @floatCast(value)));
                        stack[base + 1][index..][0..4].* = @bitCast(@as(core_vectors.Vec4, @floatFromInt(interval)));
                    }
                } else for (stack[base][0..total], stack[base + 1][0..total]) |*coordinate, *interval| {
                    const value: f32 = @floatCast(coordinate.*);
                    coordinate.* = value;
                    var low: usize = 0;
                    var high = count;
                    while (low < high) {
                        const middle = low + (high - low) / 2;
                        const location = density_read.density_program_float(program, program_counter + middle * 8);
                        if (value < location) high = middle else low = middle + 1;
                    }
                    interval.* = @as(f64, @floatFromInt(low)) - 1.0;
                }
                @memset(stack[base + 2][0..total], 0.0);
                @memset(stack[base + 3][0..total], 0.0);
                stack_size += 3;
                program_counter += count * 8;
            },
            26 => {
                if (spline_count == 0) return false;
                const point: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                const end: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                const frame = &spline_frames[spline_count - 1];
                if (point >= frame.count or end <= program_counter or end >= program.len or stack_size != frame.base + 4) return false;
                frame.point = point;
                var needed = false;
                if (comptime backend_options.is_simd_backend and capacity != 1) {
                    needed = if (frame.count == 1) total != 0 else if (point == 0)
                        density_spline.spline_any_point4(0, stack[frame.base + 1][0..total], point, frame.count)
                    else if (point == frame.count - 1)
                        density_spline.spline_any_point4(1, stack[frame.base + 1][0..total], point, frame.count)
                    else
                        density_spline.spline_any_point4(2, stack[frame.base + 1][0..total], point, frame.count);
                } else for (stack[frame.base + 1][0..total]) |interval| {
                    if (density_spline.spline_needs_point(interval, point, frame.count)) {
                        needed = true;
                        break;
                    }
                }
                if (!needed) program_counter = end;
            },
            27 => {
                if (spline_count == 0) return false;
                const frame = spline_frames[spline_count - 1];
                if (stack_size != frame.base + 5) return false;
                const point = @as(f64, @floatFromInt(frame.point));
                var index: usize = 0;
                if (comptime backend_options.is_simd_backend and capacity != 1) {
                    const target: core_vectors.Vec4 = @splat(point);
                    const zero: core_vectors.Vec4 = @splat(0.0);
                    while (index + 4 <= total) : (index += 4) {
                        const interval: core_vectors.Vec4 = @bitCast(stack[frame.base + 1][index..][0..4].*);
                        const old_left: core_vectors.Vec4 = @bitCast(stack[frame.base + 2][index..][0..4].*);
                        const old_right: core_vectors.Vec4 = @bitCast(stack[frame.base + 3][index..][0..4].*);
                        const child: core_vectors.Vec4 = @bitCast(stack[frame.base + 4][index..][0..4].*);
                        const rounded: core_vectors.Vec4f = @floatCast(child);
                        const value: core_vectors.Vec4 = @floatCast(rounded);
                        const left_mask = (interval == target) | ((interval < zero) & @as(@Vector(4, bool), @splat(frame.point == 0)));
                        const right_mask = (interval + @as(core_vectors.Vec4, @splat(1.0)) == target) & (interval >= zero);
                        stack[frame.base + 2][index..][0..4].* = @bitCast(@select(f64, left_mask, value, old_left));
                        stack[frame.base + 3][index..][0..4].* = @bitCast(@select(f64, right_mask, value, old_right));
                    }
                }
                for (stack[frame.base + 1][index..total], stack[frame.base + 2][index..total], stack[frame.base + 3][index..total], stack[frame.base + 4][index..total]) |interval, *left, *right, child| {
                    const value: f32 = @floatCast(child);
                    if (interval == point or interval < 0.0 and frame.point == 0) left.* = value;
                    if (interval + 1.0 == point and interval >= 0.0) right.* = value;
                }
                stack_size -= 1;
            },
            28 => {
                if (spline_count == 0) return false;
                const frame = spline_frames[spline_count - 1];
                if (stack_size != frame.base + 4) return false;
                for (stack[frame.base][0..total], stack[frame.base + 1][0..total], stack[frame.base + 2][0..total], stack[frame.base + 3][0..total]) |*coordinate, interval, left, right| {
                    coordinate.* = density_spline.density_spline_value(program, frame.knots, frame.count, @floatCast(coordinate.*), @intFromFloat(interval), @floatCast(left), @floatCast(right));
                }
                stack_size -= 3;
                spline_count -= 1;
            },
            29 => {
                if (stack_size >= core_limits.max_density_stack) return false;
                const slot: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                if (slot >= inputs.len / total) return false;
                const values = inputs[slot * total ..][0..total];
                if (slice) {
                    @memcpy(stack[stack_size][0..total], values);
                    stack_size += 1;
                    continue;
                }
                for (0..width) |x| {
                    for (0..width) |z| {
                        for (0..height) |y| {
                            stack[stack_size][(x * width + z) * height + y] = values[((height - 1 - y) * width + x) * width + z];
                        }
                    }
                }
                stack_size += 1;
            },
            30 => {
                if (stack_size >= core_limits.max_density_stack) return false;
                const min_address = density_read.read_program_u64(program, &program_counter) orelse return false;
                const max_address = density_read.read_program_u64(program, &program_counter) orelse return false;
                const main_address = density_read.read_program_u64(program, &program_counter) orelse return false;
                const config = noise_types.BlendedNoiseConfig{
                    .xz_multiplier = density_read.read_program_f64(program, &program_counter) orelse return false,
                    .y_multiplier = density_read.read_program_f64(program, &program_counter) orelse return false,
                    .xz_factor = density_read.read_program_f64(program, &program_counter) orelse return false,
                    .y_factor = density_read.read_program_f64(program, &program_counter) orelse return false,
                    .smear_scale_multiplier = density_read.read_program_f64(program, &program_counter) orelse return false,
                };
                if (min_address == 0 or max_address == 0 or main_address == 0) return false;
                const min: [*]const u8 = @ptrFromInt(@as(usize, @intCast(min_address)));
                const max: [*]const u8 = @ptrFromInt(@as(usize, @intCast(max_address)));
                const main: [*]const u8 = @ptrFromInt(@as(usize, @intCast(main_address)));
                if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
                    if (backend_runtime.supports(.avx2)) {
                        backend_abi.adrenaline_blended_noise_grid_avx2(min, max, main, &config, base_x, base_y, base_z, step_xz, step_y, step_xz, width, height, width, &stack[stack_size]);
                        stack_size += 1;
                        continue;
                    }
                }
                noise_blended.blended_noise_grid_impl(false, min, max, main, &config, base_x, base_y, base_z, step_xz, step_y, step_xz, width, height, width, stack[stack_size][0..total]);
                stack_size += 1;
            },
            31 => {
                if (stack_size == 0) return false;
                for (stack[stack_size - 1][0..total]) |*value| value.* = 1.0 / value.*;
            },
            32 => {
                if (capacity != 1 or stack_size >= core_limits.max_density_stack) return false;
                const slot: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                const entry: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                if (slot >= cache_ready.len or entry >= program.len) return false;
                if (cache_ready[slot]) {
                    stack[stack_size][0] = cache_values[slot];
                    stack_size += 1;
                } else {
                    if (call_count >= call_frames.len) return false;
                    call_frames[call_count] = .{ .counter = program_counter, .base = stack_size, .slot = slot };
                    call_count += 1;
                    program_counter = entry;
                }
            },
            33 => {
                if (capacity != 1 or call_count == 0) return false;
                const frame = call_frames[call_count - 1];
                if (stack_size != frame.base + 1) return false;
                cache_values[frame.slot] = stack[stack_size - 1][0];
                cache_ready[frame.slot] = true;
                call_count -= 1;
                program_counter = frame.counter;
            },
            34 => {
                if (capacity != 1 or stack_size >= core_limits.max_density_stack) return false;
                const slot: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                const end: usize = density_read.read_program_u32(program, &program_counter) orelse return false;
                if (slot >= inputs.len / 2 or end <= program_counter or end >= program.len) return false;
                if (inputs[slot * 2 + 1] == 1.0) {
                    stack[stack_size][0] = inputs[slot * 2];
                    stack_size += 1;
                    program_counter = end;
                }
            },
            else => return false,
        }
    }
    return false;
}
