const std = @import("std");
const c = @import("../core/jni.zig").c;
const aquifer_material = @import("material.zig");
const aquifer_selection = @import("selection.zig");
const core_limits = @import("../core/limits.zig");
const core_vectors = @import("../core/vectors.zig");

pub fn evaluate_aquifer_cell(density: []const c.jdouble, barrier: []const c.jdouble, candidate_values: []const c.jlong, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, base_y: c.jint, width: usize, height: usize, deferred_indices: []const c.jint, output: []c.jbyte) bool {
    const plane = width * width;
    for (deferred_indices) |raw_index| {
        if (raw_index < 0) {
            return false;
        }
        const index: usize = @intCast(raw_index);
        if (index >= density.len) {
            return false;
        }
        const y_index = index / plane;
        if (y_index >= height) {
            return false;
        }
        const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
        const global_material = aquifer_material.global_aquifer_material(y, global_fluid_level, global_fluid_type) orelse return false;
        if (global_material == 3) {
            output[index] = 3;
            continue;
        }
        if (global_material < 1 or global_material > 3) {
            return false;
        }

        const candidates = aquifer_selection.unpack_aquifer_candidates(candidate_values[index * 2], candidate_values[index * 2 + 1], @min(fluid_levels.len, fluid_types.len)) orelse return false;
        const nearest_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.nearest_index], fluid_types[candidates.nearest_index], y) orelse return false;
        const second_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.second_index], fluid_types[candidates.second_index], y) orelse return false;
        const third_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.third_index], fluid_types[candidates.third_index], y) orelse return false;
        const nearest_similarity = aquifer_material.aquifer_similarity(candidates.nearest_distance, candidates.second_distance);
        if (nearest_similarity <= 0.0) {
            output[index] = @intCast(nearest_type | if (nearest_similarity >= -0.76) @as(u8, 4) else @as(u8, 0));
            continue;
        }
        const below_material = aquifer_material.global_aquifer_material(y - 1, global_fluid_level, global_fluid_type) orelse return false;
        if (nearest_type == 2 and below_material == 3) {
            output[index] = 2 | 4;
            continue;
        }
        if (density[index] + nearest_similarity * aquifer_material.aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type, barrier[index]) > 0.0) {
            output[index] = 0;
            continue;
        }
        const nearest_third_similarity = aquifer_material.aquifer_similarity(candidates.nearest_distance, candidates.third_distance);
        if (nearest_third_similarity > 0.0 and density[index] + nearest_similarity * nearest_third_similarity * aquifer_material.aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type, barrier[index]) > 0.0) {
            output[index] = 0;
            continue;
        }
        const second_third_similarity = aquifer_material.aquifer_similarity(candidates.second_distance, candidates.third_distance);
        if (second_third_similarity > 0.0 and density[index] + nearest_similarity * second_third_similarity * aquifer_material.aquifer_pressure(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type, barrier[index]) > 0.0) {
            output[index] = 0;
            continue;
        }
        output[index] = @intCast(nearest_type | 4);
    }
    return true;
}

pub fn locate_aquifer_cell(density: []const c.jdouble, global_materials: []const c.jbyte, candidate_values: []c.jlong, packed_locations: []const c.jshort, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize) bool {
    var y_index: usize = 0;
    while (y_index < height) : (y_index += 1) {
        const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
        var x_index: usize = 0;
        while (x_index < width) : (x_index += 1) {
            const x: i32 = base_x + @as(i32, @intCast(x_index));
            var z_index: usize = 0;
            var search: ?aquifer_selection.AquiferSearch = null;
            var last_searched_z: usize = 0;
            while (z_index < width) : (z_index += 1) {
                const index = (y_index * width + x_index) * width + z_index;
                if (density[index] > 0.0) {
                    continue;
                }
                const global_material: u8 = @bitCast(global_materials[index]);
                if (global_material == 3) {
                    continue;
                }
                if (global_material < 1 or global_material > 3) {
                    return false;
                }

                const z: i32 = base_z + @as(i32, @intCast(z_index));
                const grid_z = @divFloor(z - 5, 16);
                const candidates = if (search) |*cached| block: {
                    if (cached.grid_z == grid_z) {
                        const steps: i32 = @intCast(z_index - last_searched_z);
                        if (steps == 1) cached.advanceZ() else cached.advanceZBy(steps);
                        break :block cached.candidates();
                    }
                    search = aquifer_selection.aquifer_search(packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, x, y, z) orelse return false;
                    break :block search.?.candidates();
                } else block: {
                    search = aquifer_selection.aquifer_search(packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, x, y, z) orelse return false;
                    break :block search.?.candidates();
                };
                last_searched_z = z_index;
                aquifer_selection.pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return false;
            }
        }
    }
    return true;
}

pub fn fill_aquifer_materials_avx2(density: []const f64, materials: []i8, comptime material: i8) void {
    if (density.len >= 32 and !(density[0] > 0.0) and !(density[density.len - 1] > 0.0)) {
        const zero: core_vectors.Vec8d = @splat(0.0);
        var any: @Vector(8, bool) = @splat(false);
        var n: usize = 0;
        while (n + 31 < density.len) : (n += 32) {
            const a: core_vectors.Vec8d = @bitCast(density[n..][0..8].*);
            const b: core_vectors.Vec8d = @bitCast(density[n + 8 ..][0..8].*);
            const c1: core_vectors.Vec8d = @bitCast(density[n + 16 ..][0..8].*);
            const d: core_vectors.Vec8d = @bitCast(density[n + 24 ..][0..8].*);
            any = any | (a > zero) | (b > zero) | (c1 > zero) | (d > zero);
        }
        while (n < density.len and !(density[n] > 0.0)) : (n += 1) {}
        if (n == density.len and !@reduce(.Or, any)) {
            @memset(materials, material);
            return;
        }
    }
    var i: usize = 0;
    while (i + 7 < density.len) : (i += 8) {
        const vals: core_vectors.Vec8d = @bitCast(density[i..][0..8].*);
        const bytes: @Vector(8, i8) = @select(i8, vals > @as(core_vectors.Vec8d, @splat(0.0)), @as(@Vector(8, i8), @splat(0)), @as(@Vector(8, i8), @splat(material)));
        materials[i..][0..8].* = @bitCast(bytes);
    }
    while (i < density.len) : (i += 1) materials[i] = if (density[i] > 0.0) 0 else material;
}

pub fn aquifer_air_bounds_avx2(fluid_levels: []const i32, min_grid_x: i32, min_grid_y: i32, min_grid_z: i32, grid_size_x: i32, grid_size_z: i32, base_x: i32, base_y: i32, base_z: i32, width: usize, height: usize) bool {
    if (height == 0 or width == 0 or fluid_levels.len == 0 or grid_size_x <= 1 or grid_size_z <= 1) return false;
    const min_x = @divFloor(@as(i64, base_x) - 5, 16) - @as(i64, min_grid_x);
    const max_x = @divFloor(@as(i64, base_x) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_x) + 1;
    const min_z = @divFloor(@as(i64, base_z) - 5, 16) - @as(i64, min_grid_z);
    const max_z = @divFloor(@as(i64, base_z) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_z) + 1;
    const min_y = @divFloor(@as(i64, base_y) + 1, 12) - @as(i64, min_grid_y) - 1;
    const max_y = @divFloor(@as(i64, base_y) + @as(i64, @intCast(height)), 12) - @as(i64, min_grid_y) + 1;
    const last_index = @as(i128, max_y) * @as(i128, grid_size_z) * @as(i128, grid_size_x) + @as(i128, max_z) * @as(i128, grid_size_x) + max_x;
    if (!(min_x >= 0 and max_x < grid_size_x and min_z >= 0 and max_z < grid_size_z and min_y >= 0 and last_index < fluid_levels.len)) return false;
    const span = (max_z - min_z) * @as(i64, grid_size_x) + (max_x - min_x) + 1;
    if (span <= 16 and last_index + 7 < fluid_levels.len) {
        var maximum: core_vectors.Vec8i = @splat(std.math.minInt(i32));
        var yrow = min_y;
        while (yrow <= max_y) : (yrow += 1) {
            const index: usize = @intCast((yrow * grid_size_z + min_z) * grid_size_x + min_x);
            maximum = @max(maximum, @as(core_vectors.Vec8i, @bitCast(fluid_levels[index..][0..8].*)));
            if (span > 8) {
                const second = index + @as(usize, @intCast(span - 8));
                maximum = @max(maximum, @as(core_vectors.Vec8i, @bitCast(fluid_levels[second..][0..8].*)));
            }
        }
        if (@as(i64, @reduce(.Max, maximum)) + 5 <= @as(i64, base_y)) return true;
    }
    var iy = min_y;
    while (iy <= max_y) : (iy += 1) {
        var iz = min_z;
        while (iz <= max_z) : (iz += 1) {
            var ix = min_x;
            while (ix <= max_x) : (ix += 1) {
                const index: usize = @intCast((iy * grid_size_z + iz) * grid_size_x + ix);
                if (@as(i64, fluid_levels[index]) + 5 > @as(i64, base_y)) return false;
            }
        }
    }
    return true;
}

pub fn fill_air_avx2(density: []const f64, materials: []i8) void {
    fill_aquifer_materials_avx2(density, materials, 1);
}

pub fn prepare_aquifer_cell_impl(comptime avx2: bool, comptime neon: bool, density: []const c.jdouble, candidate_values: []c.jlong, packed_locations: []const c.jshort, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: []c.jint, materials: []c.jbyte) ?usize {
    if (height > 0 and (aquifer_material.global_aquifer_material(base_y + @as(i32, @intCast(height - 1)), global_fluid_level, global_fluid_type) orelse return null) == 3) {
        const plane = width * width;
        for (0..height) |y_index| {
            for (0..plane) |column_index| {
                const index = y_index * plane + column_index;
                materials[index] = if (density[index] > 0.0) 0 else 3;
            }
        }
        return 0;
    }
    if (comptime avx2) {
        var solid: usize = 0;
        if (density.len >= 8) {
            const block: core_vectors.Vec8d = @bitCast(density[0..8].*);
            if (@reduce(.And, block > @as(core_vectors.Vec8d, @splat(0.0)))) solid = 8;
        }
        while (solid > 0 and solid + 31 < density.len) : (solid += 32) {
            const a: core_vectors.Vec8d = @bitCast(density[solid..][0..8].*);
            const b: core_vectors.Vec8d = @bitCast(density[solid + 8 ..][0..8].*);
            const c1: core_vectors.Vec8d = @bitCast(density[solid + 16 ..][0..8].*);
            const d: core_vectors.Vec8d = @bitCast(density[solid + 24 ..][0..8].*);
            const zero: core_vectors.Vec8d = @splat(0.0);
            if (!@reduce(.And, (a > zero) & (b > zero) & (c1 > zero) & (d > zero))) break;
        }
        while (solid > 0 and solid + 7 < density.len) : (solid += 8) {
            const block: core_vectors.Vec8d = @bitCast(density[solid..][0..8].*);
            if (!@reduce(.And, block > @as(core_vectors.Vec8d, @splat(0.0)))) break;
        }
        while (solid < density.len and density[solid] > 0.0) : (solid += 1) {}
        if (solid == density.len) {
            @memset(materials, 0);
            return 0;
        }
    }

    if (comptime avx2) {
        if (height > 0 and width > 0 and fluid_levels.len > 0 and
            @as(i64, base_y) >= @as(i64, global_fluid_level) + 5 and
            (global_fluid_type == 2 or global_fluid_type == 3) and grid_size_x > 1 and grid_size_z > 1)
        {
            const min_x = @divFloor(@as(i64, base_x) - 5, 16) - @as(i64, min_grid_x);
            const max_x = @divFloor(@as(i64, base_x) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_x) + 1;
            const min_z = @divFloor(@as(i64, base_z) - 5, 16) - @as(i64, min_grid_z);
            const max_z = @divFloor(@as(i64, base_z) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_z) + 1;
            const min_y = @divFloor(@as(i64, base_y) + 1, 12) - @as(i64, min_grid_y) - 1;
            const max_y = @divFloor(@as(i64, base_y) + @as(i64, @intCast(height)), 12) - @as(i64, min_grid_y) + 1;
            const last_index = @as(i128, max_y) * @as(i128, grid_size_z) * @as(i128, grid_size_x) + @as(i128, max_z) * @as(i128, grid_size_x) + max_x;
            if (min_x >= 0 and max_x < grid_size_x and min_z >= 0 and max_z < grid_size_z and min_y >= 0 and last_index < fluid_levels.len) {
                const span = (max_z - min_z) * @as(i64, grid_size_x) + (max_x - min_x) + 1;
                if (span <= 16 and last_index + 7 < fluid_levels.len) {
                    var maximum: core_vectors.Vec8i = @splat(std.math.minInt(i32));
                    var yrow = min_y;
                    while (yrow <= max_y) : (yrow += 1) {
                        const index: usize = @intCast((yrow * grid_size_z + min_z) * grid_size_x + min_x);
                        const first: core_vectors.Vec8i = @bitCast(fluid_levels[index..][0..8].*);
                        maximum = @max(maximum, first);
                        if (span > 8) {
                            const second_index = index + @as(usize, @intCast(span - 8));
                            const second: core_vectors.Vec8i = @bitCast(fluid_levels[second_index..][0..8].*);
                            maximum = @max(maximum, second);
                        }
                    }
                    if (@as(i64, @reduce(.Max, maximum)) + 5 <= @as(i64, base_y)) {
                        fill_aquifer_materials_avx2(density, materials, 1);
                        return 0;
                    }
                }
                var all_below = true;
                var iy = min_y;
                while (iy <= max_y and all_below) : (iy += 1) {
                    var iz = min_z;
                    while (iz <= max_z and all_below) : (iz += 1) {
                        var ix = min_x;
                        while (ix <= max_x) : (ix += 1) {
                            const index: usize = @intCast((iy * grid_size_z + iz) * grid_size_x + ix);
                            if (@as(i64, fluid_levels[index]) + 5 > @as(i64, base_y)) {
                                all_below = false;
                                break;
                            }
                        }
                    }
                }
                if (all_below) {
                    fill_aquifer_materials_avx2(density, materials, 1);
                    return 0;
                }
            }
        }
    }
    if (!avx2 and @as(i64, base_y) >= @as(i64, global_fluid_level) + 5 and
        height > 0 and width > 0 and fluid_levels.len > 0 and fluid_levels.len <= core_limits.max_density_values and
        (global_fluid_type == 2 or global_fluid_type == 3) and grid_size_x > 1 and grid_size_z > 1)
    {
        var level_index: usize = 0;
        var max0: core_vectors.Vec8i = @splat(std.math.minInt(i32));
        var max1: core_vectors.Vec8i = max0;
        var max2: core_vectors.Vec8i = max0;
        var max3: core_vectors.Vec8i = max0;
        while ((comptime avx2) and level_index + 31 < fluid_levels.len) : (level_index += 32) {
            max0 = @max(max0, @as(core_vectors.Vec8i, @bitCast(fluid_levels[level_index..][0..8].*)));
            max1 = @max(max1, @as(core_vectors.Vec8i, @bitCast(fluid_levels[level_index + 8 ..][0..8].*)));
            max2 = @max(max2, @as(core_vectors.Vec8i, @bitCast(fluid_levels[level_index + 16 ..][0..8].*)));
            max3 = @max(max3, @as(core_vectors.Vec8i, @bitCast(fluid_levels[level_index + 24 ..][0..8].*)));
        }
        var max8 = @max(@max(max0, max1), @max(max2, max3));
        while (level_index + 7 < fluid_levels.len) : (level_index += 8) {
            const next: core_vectors.Vec8i = @bitCast(fluid_levels[level_index..][0..8].*);
            max8 = @max(max8, next);
        }
        var max_level: i32 = @reduce(.Max, max8);
        while (level_index < fluid_levels.len) : (level_index += 1) max_level = @max(max_level, fluid_levels[level_index]);
        if (@as(i64, base_y) >= @as(i64, max_level) + 5) {
            const min_x = @divFloor(@as(i64, base_x) - 5, 16) - @as(i64, min_grid_x);
            const max_x = @divFloor(@as(i64, base_x) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_x);
            const min_z = @divFloor(@as(i64, base_z) - 5, 16) - @as(i64, min_grid_z);
            const max_z = @divFloor(@as(i64, base_z) + @as(i64, @intCast(width)) - 6, 16) - @as(i64, min_grid_z);
            const min_y = @divFloor(@as(i64, base_y) + 1, 12) - @as(i64, min_grid_y);
            const max_y = @divFloor(@as(i64, base_y) + @as(i64, @intCast(height)), 12) - @as(i64, min_grid_y);
            const last_index = (@as(i128, max_y) + 1) * @as(i128, grid_size_z) * @as(i128, grid_size_x) +
                (@as(i128, max_z) + 1) * @as(i128, grid_size_x) + @as(i128, max_x) + 1;
            if (min_x >= 0 and max_x + 1 < grid_size_x and min_z >= 0 and max_z + 1 < grid_size_z and
                min_y >= 1 and last_index < fluid_levels.len)
            {
                for (density, materials) |value, *material| material.* = if (value > 0.0) 0 else 1;
                return 0;
            }
        }
    }
    var center_cache: [16]aquifer_selection.AquiferCenters = undefined;
    var center_valid: u16 = 0;
    var deferred_count: usize = 0;
    const plane = width * width;
    const lava_level = @min(@as(i32, -54), global_fluid_level);
    const global_type_code: u8 = @bitCast(global_fluid_type);
    const below_lava_boundary = if (global_type_code == 3) global_fluid_level else lava_level;
    var global_material_cache: [core_limits.max_density_values]u8 = undefined;
    var grid_y_cache: [core_limits.max_density_values]i32 = undefined;
    var fluid_max_cache: [core_limits.max_density_values]i32 = undefined;
    var fluid_uniform_cache: [core_limits.max_density_values]i8 = undefined;
    const cache_fluid_max = fluid_levels.len <= core_limits.max_density_values and grid_size_x > 0 and grid_size_z > 0;
    var fluid_cache_ready = false;
    @memset(global_material_cache[0..height], 0);
    @memset(grid_y_cache[0..height], std.math.minInt(i32));
    var x_index: usize = 0;
    while (x_index < width) : (x_index += 1) {
        const x: i32 = base_x + @as(i32, @intCast(x_index));
        var z_index: usize = 0;
        while (z_index < width) : (z_index += 1) {
            const z: i32 = base_z + @as(i32, @intCast(z_index));
            const column_index = x_index * width + z_index;
            const cell_grid_x = @divFloor(x - 5, 16);
            const cell_grid_z = @divFloor(z - 5, 16);
            const local_grid_x = cell_grid_x - min_grid_x;
            const local_grid_z = cell_grid_z - min_grid_z;
            var search: ?aquifer_selection.AquiferSearch = null;
            var fluid_bound_grid_y: i32 = std.math.minInt(i32);
            var maximum_fluid_level: i32 = std.math.minInt(i32);
            var uniform_fluid_type: i8 = 0;
            var y_index: usize = 0;
            while (y_index < height) : (y_index += 1) {
                const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
                const index = y_index * plane + column_index;
                materials[index] = 0;
                if (density[index] > 0.0) {
                    continue;
                }
                var global_material = global_material_cache[y_index];
                if (global_material == 0) {
                    global_material = aquifer_material.global_aquifer_material(y, global_fluid_level, global_fluid_type) orelse return null;
                    global_material_cache[y_index] = global_material;
                }
                if (global_material == 3) {
                    materials[index] = 3;
                    continue;
                }
                if (global_material < 1 or global_material > 3) {
                    return null;
                }

                var grid_y = grid_y_cache[y_index];
                if (grid_y == std.math.minInt(i32)) {
                    grid_y = @divFloor(y + 1, 12);
                    grid_y_cache[y_index] = grid_y;
                }
                if (fluid_bound_grid_y != grid_y) {
                    var cache_key: ?usize = null;
                    const local_grid_y = grid_y - min_grid_y;
                    if (cache_fluid_max) {
                        if (local_grid_x >= 0 and local_grid_x + 1 < grid_size_x and local_grid_z >= 0 and local_grid_z + 1 < grid_size_z and local_grid_y >= 1) {
                            const key = (@as(usize, @intCast(local_grid_y)) * @as(usize, @intCast(grid_size_z)) + @as(usize, @intCast(local_grid_z))) * @as(usize, @intCast(grid_size_x)) + @as(usize, @intCast(local_grid_x));
                            if (key < fluid_levels.len) cache_key = key;
                        }
                    }
                    if (cache_key) |key| {
                        if (!fluid_cache_ready) {
                            @memset(fluid_max_cache[0..fluid_levels.len], std.math.minInt(i32));
                            fluid_cache_ready = true;
                        }
                        if (fluid_max_cache[key] == std.math.minInt(i32)) {
                            const info = aquifer_material.aquifer_maximum_fluid_level(fluid_levels, fluid_types, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z) orelse return null;
                            fluid_max_cache[key] = info.maximum;
                            fluid_uniform_cache[key] = info.uniform_type;
                        }
                        maximum_fluid_level = fluid_max_cache[key];
                        uniform_fluid_type = fluid_uniform_cache[key];
                    } else {
                        const info = aquifer_material.aquifer_maximum_fluid_level(fluid_levels, fluid_types, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z) orelse return null;
                        maximum_fluid_level = info.maximum;
                        uniform_fluid_type = info.uniform_type;
                    }
                    fluid_bound_grid_y = grid_y;
                }
                if (@as(i64, y) >= @as(i64, maximum_fluid_level) + 5) {
                    materials[index] = 1;
                    continue;
                }

                if (search) |*cached| {
                    if (cached.grid_y != grid_y or !aquifer_selection.advance_y_down(avx2, neon, cached, y)) {
                        search = aquifer_selection.aquifer_search_cached(&center_cache, &center_valid, packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z, x, y, z) orelse return null;
                    }
                } else {
                    search = aquifer_selection.aquifer_search_cached(&center_cache, &center_valid, packed_locations, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, cell_grid_x, grid_y, cell_grid_z, x, y, z) orelse return null;
                }
                if (uniform_fluid_type == 2 or uniform_fluid_type == 3) {
                    const distances = search.?.nearest_two_distances(avx2);
                    const similarity = aquifer_material.aquifer_similarity(distances[0], distances[1]);
                    const fluid: u8 = if (y >= maximum_fluid_level) 1 else @intCast(uniform_fluid_type);
                    materials[index] = @intCast(fluid | if (similarity >= -0.76) @as(u8, 4) else @as(u8, 0));
                    continue;
                }
                const candidates = search.?.candidates();
                const nearest_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.nearest_index], fluid_types[candidates.nearest_index], y) orelse return null;
                const second_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.second_index], fluid_types[candidates.second_index], y) orelse return null;
                const third_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.third_index], fluid_types[candidates.third_index], y) orelse return null;
                const nearest_similarity = aquifer_material.aquifer_similarity(candidates.nearest_distance, candidates.second_distance);
                if (nearest_similarity <= 0.0) {
                    materials[index] = @intCast(nearest_type | if (nearest_similarity >= -0.76) @as(u8, 4) else @as(u8, 0));
                    continue;
                }
                if (nearest_type == 2 and y == below_lava_boundary) {
                    materials[index] = 2 | 4;
                    continue;
                }
                const near_second_pressure = aquifer_material.aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type, 0.0);
                if (fluid_levels[candidates.nearest_index] != fluid_levels[candidates.second_index] and !((nearest_type == 3 and second_type == 2) or (nearest_type == 2 and second_type == 3)) and near_second_pressure >= -4.0 and near_second_pressure <= 4.0) {
                    if (deferred_count >= deferred_indices.len) return null;
                    deferred_indices[deferred_count] = @intCast(index);
                    deferred_count += 1;
                    aquifer_selection.pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                    continue;
                }
                if (density[index] + nearest_similarity * near_second_pressure > 0.0) {
                    continue;
                }

                const nearest_third_similarity = aquifer_material.aquifer_similarity(candidates.nearest_distance, candidates.third_distance);
                if (nearest_third_similarity > 0.0) {
                    const near_third_pressure = aquifer_material.aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type, 0.0);
                    if (fluid_levels[candidates.nearest_index] != fluid_levels[candidates.third_index] and !((nearest_type == 3 and third_type == 2) or (nearest_type == 2 and third_type == 3)) and near_third_pressure >= -4.0 and near_third_pressure <= 4.0) {
                        if (deferred_count >= deferred_indices.len) return null;
                        deferred_indices[deferred_count] = @intCast(index);
                        deferred_count += 1;
                        aquifer_selection.pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                        continue;
                    }
                    if (density[index] + nearest_similarity * nearest_third_similarity * near_third_pressure > 0.0) {
                        continue;
                    }
                }

                const second_third_similarity = aquifer_material.aquifer_similarity(candidates.second_distance, candidates.third_distance);
                if (second_third_similarity > 0.0) {
                    const second_third_pressure = aquifer_material.aquifer_pressure(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type, 0.0);
                    if (fluid_levels[candidates.second_index] != fluid_levels[candidates.third_index] and !((second_type == 3 and third_type == 2) or (second_type == 2 and third_type == 3)) and second_third_pressure >= -4.0 and second_third_pressure <= 4.0) {
                        if (deferred_count >= deferred_indices.len) return null;
                        deferred_indices[deferred_count] = @intCast(index);
                        deferred_count += 1;
                        aquifer_selection.pack_aquifer_candidates(candidate_values[index * 2 .. index * 2 + 2], candidates) orelse return null;
                        continue;
                    }
                    if (density[index] + nearest_similarity * second_third_similarity * second_third_pressure > 0.0) {
                        continue;
                    }
                }
                materials[index] = @intCast(nearest_type | 4);
            }
        }
    }
    return deferred_count;
}

pub fn prepare_aquifer_cell_avx2(density: []const c.jdouble, candidate_values: []c.jlong, packed_locations: []const c.jshort, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, global_fluid_level: c.jint, global_fluid_type: c.jbyte, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, deferred_indices: []c.jint, materials: []c.jbyte) ?usize {
    return prepare_aquifer_cell_impl(true, false, density, candidate_values, packed_locations, fluid_levels, fluid_types, global_fluid_level, global_fluid_type, min_grid_x, min_grid_y, min_grid_z, grid_size_x, grid_size_z, base_x, base_y, base_z, width, height, deferred_indices, materials);
}

pub fn classify_aquifer_cell(density: []const c.jdouble, global_materials: []const c.jbyte, candidate_values: []const c.jlong, fluid_levels: []const c.jint, fluid_types: []const c.jbyte, base_y: c.jint, width: usize, height: usize, output: []c.jbyte) bool {
    var y_index: usize = 0;
    while (y_index < height) : (y_index += 1) {
        const y: i32 = base_y + @as(i32, @intCast(height - 1 - y_index));
        var x_index: usize = 0;
        while (x_index < width) : (x_index += 1) {
            var z_index: usize = 0;
            while (z_index < width) : (z_index += 1) {
                const index = (y_index * width + x_index) * width + z_index;
                output[index] = 0;
                if (density[index] > 0.0) {
                    continue;
                }
                const global_material: u8 = @bitCast(global_materials[index]);
                if (global_material == 3) {
                    continue;
                }
                if (global_material < 1 or global_material > 3) {
                    return false;
                }

                const candidates = aquifer_selection.unpack_aquifer_candidates(candidate_values[index * 2], candidate_values[index * 2 + 1], @min(fluid_levels.len, fluid_types.len)) orelse return false;
                const nearest_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.nearest_index], fluid_types[candidates.nearest_index], y) orelse return false;
                const second_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.second_index], fluid_types[candidates.second_index], y) orelse return false;
                const third_type = aquifer_material.aquifer_type_at(fluid_levels[candidates.third_index], fluid_types[candidates.third_index], y) orelse return false;
                const nearest_similarity = aquifer_material.aquifer_similarity(candidates.nearest_distance, candidates.second_distance);
                if (nearest_similarity <= 0.0) {
                    continue;
                }

                var requirements: u8 = 0;
                if (nearest_type == 2) {
                    requirements |= 2;
                }
                if (aquifer_material.aquifer_pressure_uses_barrier(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type)) {
                    output[index] = @intCast(requirements | 1);
                    continue;
                }
                if (density[index] + nearest_similarity * aquifer_material.aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.second_index], second_type, 0.0) > 0.0) {
                    output[index] = @intCast(requirements);
                    continue;
                }

                const nearest_third_similarity = aquifer_material.aquifer_similarity(candidates.nearest_distance, candidates.third_distance);
                if (nearest_third_similarity > 0.0) {
                    if (aquifer_material.aquifer_pressure_uses_barrier(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type)) {
                        output[index] = @intCast(requirements | 1);
                        continue;
                    }
                    if (density[index] + nearest_similarity * nearest_third_similarity * aquifer_material.aquifer_pressure(y, fluid_levels[candidates.nearest_index], nearest_type, fluid_levels[candidates.third_index], third_type, 0.0) > 0.0) {
                        output[index] = @intCast(requirements);
                        continue;
                    }
                }

                const second_third_similarity = aquifer_material.aquifer_similarity(candidates.second_distance, candidates.third_distance);
                if (second_third_similarity > 0.0 and aquifer_material.aquifer_pressure_uses_barrier(y, fluid_levels[candidates.second_index], second_type, fluid_levels[candidates.third_index], third_type)) {
                    requirements |= 1;
                }
                output[index] = @intCast(requirements);
            }
        }
    }
    return true;
}
