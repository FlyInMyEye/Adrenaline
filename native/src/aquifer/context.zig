const std = @import("std");
const aquifer_cell = @import("cell.zig");
const aquifer_material = @import("material.zig");
const aquifer_selection = @import("selection.zig");
const core_limits = @import("../core/limits.zig");
const core_vectors = @import("../core/vectors.zig");

const Vec4i = core_vectors.Vec4i;
const Vec8i = core_vectors.Vec8i;
const Vec8d = core_vectors.Vec8d;
pub const AquiferContextEntry = extern struct {
    ox: [12]i16,
    oy: [12]i16,
    oz: [12]i16,
    indices: [12]u16,
    maximum: i32,
    uniform_type: i8,
    valid: u8,
    padding: [2]u8,
};

pub const AquiferContext = extern struct {
    magic: u64,
    count: usize,
    min_x: i32,
    min_y: i32,
    min_z: i32,
    size_x: i32,
    size_z: i32,
    flags: u32,
    entries: [*]const AquiferContextEntry,
    levels: [*]const i32,
    types: [*]const i8,
};
const aquifer_context_magic: u64 = 0x4144514354583031;
pub fn aquifer_context_size(count: usize) usize {
    if (count == 0 or count > core_limits.max_grid_size) return 0;
    return @sizeOf(AquiferContext) + count * (@sizeOf(AquiferContextEntry) + @sizeOf(i32) + @sizeOf(i8));
}
pub fn aquifer_context_init(memory: [*]align(8) u8, capacity: usize, locations: []const i16, levels: []const i32, types: []const i8, min_x: i32, min_y: i32, min_z: i32, sx_raw: i32, sz_raw: i32) bool {
    const count = locations.len;
    const required = aquifer_context_size(count);
    if (required == 0 or capacity < required or levels.len < count or types.len < count or sx_raw <= 1 or sz_raw <= 1 or sx_raw > count or sz_raw > count) return false;
    const ctx: *AquiferContext = @ptrCast(memory);
    const entries: [*]AquiferContextEntry = @ptrCast(@alignCast(memory + @sizeOf(AquiferContext)));
    const out_levels: [*]i32 = @ptrCast(@alignCast(memory + @sizeOf(AquiferContext) + count * @sizeOf(AquiferContextEntry)));
    const out_types: [*]i8 = @ptrCast(memory + @sizeOf(AquiferContext) + count * (@sizeOf(AquiferContextEntry) + @sizeOf(i32)));
    @memcpy(out_levels[0..count], levels[0..count]);
    @memcpy(out_types[0..count], types[0..count]);
    const sx: usize = @intCast(sx_raw);
    const sz: usize = @intCast(sz_raw);
    for (0..count) |key| {
        const e = &entries[key];
        e.valid = 0;
        const lx = key % sx;
        const lz = (key / sx) % sz;
        const ly = key / (sx * sz);
        if (lx + 1 >= sx or lz + 1 >= sz or ly < 1 or ((ly + 1) * sz + lz + 1) * sx + lx + 1 >= count) continue;
        var maximum: i32 = std.math.minInt(i32);
        var first_level: i32 = 0;
        var first_type: i8 = 0;
        var uniform = true;
        inline for (0..2) |ox| {
            inline for (0..3) |oy| {
                inline for (0..2) |oz| {
                    const order = (ox * 3 + oy) * 2 + oz;
                    const index = ((ly + oy - 1) * sz + lz + oz) * sx + lx + ox;
                    const bits: u16 = @bitCast(locations[index]);
                    e.ox[order] = @intCast(ox * 16 + (bits >> 8));
                    e.oy[order] = (@as(i16, oy) - 1) * 12 + @as(i16, @intCast((bits >> 4) & 15));
                    e.oz[order] = @intCast(oz * 16 + (bits & 15));
                    e.indices[order] = @intCast(index);
                    if (order == 0) {
                        first_level = levels[index];
                        first_type = types[index];
                    } else if (levels[index] != first_level or types[index] != first_type) uniform = false;
                    maximum = @max(maximum, levels[index]);
                }
            }
        }
        e.maximum = maximum;
        e.uniform_type = if (uniform) first_type else 0;
        e.valid = 1;
    }
    var short_safe = true;
    for (locations) |encoded| if ((@as(u16, @bitCast(encoded)) >> 8) > 127) {
        short_safe = false;
        break;
    };
    ctx.* = .{ .flags = @intFromBool(short_safe), .magic = aquifer_context_magic, .count = count, .min_x = min_x, .min_y = min_y, .min_z = min_z, .size_x = sx_raw, .size_z = sz_raw, .entries = entries, .levels = out_levels, .types = out_types };
    return true;
}
fn aquifer_context_entry(ctx: *const AquiferContext, gx: i32, gy: i32, gz: i32) ?*const AquiferContextEntry {
    const lx = @as(i64, gx) - ctx.min_x;
    const ly = @as(i64, gy) - ctx.min_y;
    const lz = @as(i64, gz) - ctx.min_z;
    if (lx < 0 or lx + 1 >= ctx.size_x or lz < 0 or lz + 1 >= ctx.size_z or ly < 1) return null;
    const key = (ly * ctx.size_z + lz) * ctx.size_x + lx;
    if (key < 0 or key >= ctx.count) return null;
    const entry = &ctx.entries[@intCast(key)];
    return if (entry.valid != 0) entry else null;
}
fn aquifer_context_search(entry: *const AquiferContextEntry, gx: i32, gy: i32, gz: i32, x: i32, y: i32, z: i32) aquifer_selection.AquiferSearch {
    const rx = x - gx * 16;
    const ry = y - gy * 12;
    const rz = z - gz * 16;
    var result: aquifer_selection.AquiferSearch = undefined;
    result.grid_z = gz;
    result.grid_y = gy;
    result.y = y;
    const ox8: @Vector(8, i16) = @bitCast(entry.ox[0..8].*);
    const oy8: @Vector(8, i16) = @bitCast(entry.oy[0..8].*);
    const oz8: @Vector(8, i16) = @bitCast(entry.oz[0..8].*);
    const dx8 = @as(core_vectors.Vec8i, @intCast(ox8)) - @as(core_vectors.Vec8i, @splat(rx));
    const dy8 = @as(core_vectors.Vec8i, @intCast(oy8)) - @as(core_vectors.Vec8i, @splat(ry));
    const dz8 = @as(core_vectors.Vec8i, @intCast(oz8)) - @as(core_vectors.Vec8i, @splat(rz));
    result.distances[0..8].* = @bitCast(dx8 * dx8 + dy8 * dy8 + dz8 * dz8);
    result.delta_y[0..8].* = @bitCast(dy8);
    result.delta_z[0..8].* = @bitCast(dz8);
    const ox4: @Vector(4, i16) = @bitCast(entry.ox[8..12].*);
    const oy4: @Vector(4, i16) = @bitCast(entry.oy[8..12].*);
    const oz4: @Vector(4, i16) = @bitCast(entry.oz[8..12].*);
    const dx4 = @as(core_vectors.Vec4i, @intCast(ox4)) - @as(core_vectors.Vec4i, @splat(rx));
    const dy4 = @as(core_vectors.Vec4i, @intCast(oy4)) - @as(core_vectors.Vec4i, @splat(ry));
    const dz4 = @as(core_vectors.Vec4i, @intCast(oz4)) - @as(core_vectors.Vec4i, @splat(rz));
    result.distances[8..12].* = @bitCast(dx4 * dx4 + dy4 * dy4 + dz4 * dz4);
    result.delta_y[8..12].* = @bitCast(dy4);
    result.delta_z[8..12].* = @bitCast(dz4);
    inline for (0..12) |i| result.indices[i] = entry.indices[i];
    return result;
}

pub fn prepare_aquifer_context(ctx: *const AquiferContext, density: []const f64, candidate_values: []i64, global_fluid_level: i32, global_fluid_type: i8, base_x: i32, base_y: i32, base_z: i32, width: usize, height: usize, deferred_indices: []i32, materials: []i8) ?usize {
    const avx2 = true;
    const neon = false;
    const fluid_levels = ctx.levels[0..ctx.count];
    const fluid_types = ctx.types[0..ctx.count];
    const min_grid_x = ctx.min_x;
    const min_grid_y = ctx.min_y;
    const min_grid_z = ctx.min_z;
    const grid_size_x = ctx.size_x;
    const grid_size_z = ctx.size_z;
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
                        aquifer_cell.fill_aquifer_materials_avx2(density, materials, 1);
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
                    aquifer_cell.fill_aquifer_materials_avx2(density, materials, 1);
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
    if (width == 4 and height == 8 and try_context_uniform_tile(ctx, density, global_fluid_level, global_fluid_type, base_x, base_y, base_z, materials)) return 0;
    var deferred_count: usize = 0;
    const plane = width * width;
    const lava_level = @min(@as(i32, -54), global_fluid_level);
    const global_type_code: u8 = @bitCast(global_fluid_type);
    const below_lava_boundary = if (global_type_code == 3) global_fluid_level else lava_level;
    var global_material_cache: [core_limits.max_density_values]u8 = undefined;
    var grid_y_cache: [core_limits.max_density_values]i32 = undefined;
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
            var search: ?aquifer_selection.AquiferSearch = null;
            var current_entry: *const AquiferContextEntry = undefined;
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
                    current_entry = aquifer_context_entry(ctx, cell_grid_x, grid_y, cell_grid_z) orelse return null;
                    maximum_fluid_level = current_entry.maximum;
                    uniform_fluid_type = current_entry.uniform_type;
                    fluid_bound_grid_y = grid_y;
                }
                if (@as(i64, y) >= @as(i64, maximum_fluid_level) + 5) {
                    materials[index] = 1;
                    continue;
                }

                if (search) |*cached| {
                    if (cached.grid_y != grid_y or !aquifer_selection.advance_y_down(avx2, neon, cached, y)) {
                        search = aquifer_context_search(current_entry, cell_grid_x, grid_y, cell_grid_z, x, y, z);
                    }
                } else {
                    search = aquifer_context_search(current_entry, cell_grid_x, grid_y, cell_grid_z, x, y, z);
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

const UniformAquiferShortTileGroup = struct {
    dx: [12]i32,
    dy: [12]i32,
    dz: [12]i32,
    horizontal: [1][12]@Vector(16, i16),
    start: usize,
    count: usize,
    level: i32,
    fluid: i8,
};

fn try_context_short_tile(ctx: *const AquiferContext, density: []const f64, global_level: i32, global_type: i8, base_x: i32, base_y: i32, base_z: i32, materials: []i8) bool {
    if (global_type != 2 and global_type != 3) return false;
    const gx = @divFloor(base_x - 5, 16);
    const gz = @divFloor(base_z - 5, 16);
    if (gx != @divFloor(base_x - 2, 16) or gz != @divFloor(base_z - 2, 16)) return false;
    const top_y = base_y + 7;
    var groups: [2]UniformAquiferShortTileGroup = undefined;
    var group_count: usize = 0;
    var cursor: usize = 0;
    while (cursor < 8) {
        if (group_count >= groups.len) return false;
        const y = top_y - @as(i32, @intCast(cursor));
        const gy = @divFloor(y + 1, 12);
        const entry = aquifer_context_entry(ctx, gx, gy, gz) orelse return false;
        if (entry.uniform_type != 2 and entry.uniform_type != 3) return false;
        const group = &groups[group_count];
        group.start = cursor;
        group.count = @min(8 - cursor, @as(usize, @intCast(y - gy * 12 + 2)));
        group.level = entry.maximum;
        group.fluid = entry.uniform_type;
        inline for (0..12) |i| {
            group.dx[i] = gx * 16 + @as(i32, entry.ox[i]) - base_x;
            group.dy[i] = gy * 12 + @as(i32, entry.oy[i]) - top_y;
            group.dz[i] = gz * 16 + @as(i32, entry.oz[i]) - base_z;
        }
        cursor += group.count;
        group_count += 1;
    }

    const z_offsets: @Vector(16, i16) = .{ 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3 };
    const x_offsets: @Vector(16, i16) = .{ 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3 };
    for (groups[0..group_count]) |*group| {
        for (0..1) |x| {
            inline for (0..12) |i| {
                const dx = @as(@Vector(16, i16), @splat(@intCast(group.dx[i] - @as(i32, @intCast(x * 4))))) - x_offsets;
                const dz = @as(@Vector(16, i16), @splat(@intCast(group.dz[i]))) - z_offsets;
                group.horizontal[x][i] = dx * dx + dz * dz;
            }
        }
    }

    const zero: @Vector(16, i8) = @splat(0);
    for (groups[0..group_count]) |*group| {
        for (group.start..group.start + group.count) |row| {
            const y = top_y - @as(i32, @intCast(row));
            const global = aquifer_material.global_aquifer_material(y, global_level, global_type) orelse unreachable;
            const shortcut: i8 = if (global == 3) 3 else if (@as(i64, y) >= @as(i64, group.level) + 5) 1 else 0;
            var vertical: [12]i32 = undefined;
            inline for (0..12) |i| {
                const dy = group.dy[i] + @as(i32, @intCast(row));
                vertical[i] = dy * dy;
            }
            for (0..1) |x| {
                const index = row * 16 + x * 16;
                const values: @Vector(16, f64) = @bitCast(density[index..][0..16].*);
                const solid = values > @as(@Vector(16, f64), @splat(0.0));
                if (@reduce(.And, solid)) {
                    @memset(materials[index..][0..16], 0);
                    continue;
                }
                if (shortcut != 0) {
                    materials[index..][0..16].* = @bitCast(@select(i8, solid, zero, @as(@Vector(16, i8), @splat(shortcut))));
                    continue;
                }
                var first: @Vector(16, i16) = @splat(std.math.maxInt(i16));
                var second = first;
                inline for (0..12) |i| {
                    const distances = group.horizontal[x][i] + @as(@Vector(16, i16), @splat(@intCast(vertical[i])));
                    second = @min(second, @max(first, distances));
                    first = @min(first, distances);
                }
                const close = second - first <= @as(@Vector(16, i16), @splat(44));
                const fluid: i8 = if (y >= group.level) 1 else group.fluid;
                const regular = @as(@Vector(16, i8), @splat(fluid)) | @select(i8, close, @as(@Vector(16, i8), @splat(4)), zero);
                materials[index..][0..16].* = @bitCast(@select(i8, solid, zero, regular));
            }
        }
    }
    return true;
}

const UniformAquiferTileGroup = struct {
    dx: [12]i32,
    dy: [12]i32,
    dz: [12]i32,
    horizontal: [2][12]core_vectors.Vec8i,
    start: usize,
    count: usize,
    level: i32,
    fluid: i8,
};

fn try_context_uniform_tile_wide(ctx: *const AquiferContext, density: []const f64, global_level: i32, global_type: i8, base_x: i32, base_y: i32, base_z: i32, materials: []i8) bool {
    if (ctx.flags & 1 != 0) return try_context_short_tile(ctx, density, global_level, global_type, base_x, base_y, base_z, materials);
    if (global_type != 2 and global_type != 3) return false;
    const gx = @divFloor(base_x - 5, 16);
    const gz = @divFloor(base_z - 5, 16);
    if (gx != @divFloor(base_x - 2, 16) or gz != @divFloor(base_z - 2, 16)) return false;
    const top_y = base_y + 7;
    var groups: [2]UniformAquiferTileGroup = undefined;
    var group_count: usize = 0;
    var cursor: usize = 0;
    while (cursor < 8) {
        if (group_count >= groups.len) return false;
        const y = top_y - @as(i32, @intCast(cursor));
        const gy = @divFloor(y + 1, 12);
        const entry = aquifer_context_entry(ctx, gx, gy, gz) orelse return false;
        if (entry.uniform_type != 2 and entry.uniform_type != 3) return false;
        const group = &groups[group_count];
        group.start = cursor;
        group.count = @min(8 - cursor, @as(usize, @intCast(y - gy * 12 + 2)));
        group.level = entry.maximum;
        group.fluid = entry.uniform_type;
        inline for (0..12) |i| {
            group.dx[i] = gx * 16 + @as(i32, entry.ox[i]) - base_x;
            group.dy[i] = gy * 12 + @as(i32, entry.oy[i]) - top_y;
            group.dz[i] = gz * 16 + @as(i32, entry.oz[i]) - base_z;
        }
        cursor += group.count;
        group_count += 1;
    }

    const z_offsets: core_vectors.Vec8i = .{ 0, 1, 2, 3, 0, 1, 2, 3 };
    const x_offsets: core_vectors.Vec8i = .{ 0, 0, 0, 0, 1, 1, 1, 1 };
    for (groups[0..group_count]) |*group| {
        for (0..2) |x| {
            inline for (0..12) |i| {
                const dx = @as(core_vectors.Vec8i, @splat(group.dx[i] - @as(i32, @intCast(x * 2)))) - x_offsets;
                const dz = @as(core_vectors.Vec8i, @splat(group.dz[i])) - z_offsets;
                group.horizontal[x][i] = dx * dx + dz * dz;
            }
        }
    }

    const zero: @Vector(8, i8) = @splat(0);
    for (groups[0..group_count]) |*group| {
        for (group.start..group.start + group.count) |row| {
            const y = top_y - @as(i32, @intCast(row));
            const global = aquifer_material.global_aquifer_material(y, global_level, global_type) orelse unreachable;
            const shortcut: i8 = if (global == 3) 3 else if (@as(i64, y) >= @as(i64, group.level) + 5) 1 else 0;
            var vertical: [12]i32 = undefined;
            inline for (0..12) |i| {
                const dy = group.dy[i] + @as(i32, @intCast(row));
                vertical[i] = dy * dy;
            }
            for (0..2) |x| {
                const index = row * 16 + x * 8;
                const values: core_vectors.Vec8d = @bitCast(density[index..][0..8].*);
                const solid = values > @as(core_vectors.Vec8d, @splat(0.0));
                if (@reduce(.And, solid)) {
                    @memset(materials[index..][0..8], 0);
                    continue;
                }
                if (shortcut != 0) {
                    materials[index..][0..8].* = @bitCast(@select(i8, solid, zero, @as(@Vector(8, i8), @splat(shortcut))));
                    continue;
                }
                var first: core_vectors.Vec8i = @splat(std.math.maxInt(i32));
                var second = first;
                inline for (0..12) |i| {
                    const distances = group.horizontal[x][i] + @as(core_vectors.Vec8i, @splat(vertical[i]));
                    second = @min(second, @max(first, distances));
                    first = @min(first, distances);
                }
                const close = second - first <= @as(core_vectors.Vec8i, @splat(44));
                const fluid: i8 = if (y >= group.level) 1 else group.fluid;
                const regular = @as(@Vector(8, i8), @splat(fluid)) | @select(i8, close, @as(@Vector(8, i8), @splat(4)), zero);
                materials[index..][0..8].* = @bitCast(@select(i8, solid, zero, regular));
            }
        }
    }
    return true;
}

fn try_context_uniform_tile(ctx: *const AquiferContext, density: []const f64, global_level: i32, global_type: i8, base_x: i32, base_y: i32, base_z: i32, materials: []i8) bool {
    if (ctx.flags & 1 == 0) return try_context_uniform_tile_wide(ctx, density, global_level, global_type, base_x, base_y, base_z, materials);
    if (@divFloor(base_x - 5, 16) == @divFloor(base_x - 2, 16) and @divFloor(base_z - 5, 16) == @divFloor(base_z - 2, 16)) return try_context_short_tile(ctx, density, global_level, global_type, base_x, base_y, base_z, materials);
    return try_context_short_boundary_tile(ctx, density, global_level, global_type, base_x, base_y, base_z, materials);
}

const ContextShortBoundaryGroup = struct {
    horizontal: [1][12]@Vector(16, i16),
    dy: [1][12]@Vector(16, i16),
    level: i32,
    fluid: i8,
    start: usize,
    count: usize,
};
fn try_context_short_boundary_tile(ctx: *const AquiferContext, density: []const f64, global_level: i32, global_type: i8, base_x: i32, base_y: i32, base_z: i32, materials: []i8) bool {
    if (global_type != 2 and global_type != 3) return false;
    const top_y = base_y + 7;
    var groups: [2]ContextShortBoundaryGroup = undefined;
    var group_count: usize = 0;
    var cursor: usize = 0;
    while (cursor < 8) {
        if (group_count >= 2) return false;
        const y = top_y - @as(i32, @intCast(cursor));
        const gy = @divFloor(y + 1, 12);
        const group = &groups[group_count];
        group.start = cursor;
        group.count = @min(8 - cursor, @as(usize, @intCast(y - gy * 12 + 2)));
        for (0..1) |pair| {
            var entries: [16]*const AquiferContextEntry = undefined;
            var rx: @Vector(16, i16) = undefined;
            var rz: @Vector(16, i16) = undefined;
            inline for (0..16) |lane| {
                const x = base_x + @as(i32, @intCast(pair * 4 + lane / 4));
                const z = base_z + @as(i32, lane % 4);
                const gx = @divFloor(x - 5, 16);
                const gz = @divFloor(z - 5, 16);
                const e = aquifer_context_entry(ctx, gx, gy, gz) orelse return false;
                if (e.uniform_type != 2 and e.uniform_type != 3) return false;
                entries[lane] = e;
                rx[lane] = @intCast(x - gx * 16);
                rz[lane] = @intCast(z - gz * 16);
                if (lane == 0) {
                    group.level = e.maximum;
                    group.fluid = e.uniform_type;
                } else if (group.level != e.maximum or group.fluid != e.uniform_type) return false;
            }
            inline for (0..12) |i| {
                var dx: @Vector(16, i16) = undefined;
                var dz: @Vector(16, i16) = undefined;
                inline for (0..16) |lane| {
                    dx[lane] = entries[lane].ox[i];
                    dz[lane] = entries[lane].oz[i];
                    group.dy[pair][i][lane] = @intCast(gy * 12 + @as(i32, entries[lane].oy[i]) - top_y);
                }
                dx -= rx;
                dz -= rz;
                group.horizontal[pair][i] = dx * dx + dz * dz;
            }
        }
        cursor += group.count;
        group_count += 1;
    }
    const zero: @Vector(16, i8) = @splat(0);
    for (groups[0..group_count]) |*group| {
        for (group.start..group.start + group.count) |row| {
            const y = top_y - @as(i32, @intCast(row));
            const global = aquifer_material.global_aquifer_material(y, global_level, global_type) orelse unreachable;
            for (0..1) |pair| {
                const index = row * 16 + pair * 16;
                const values: @Vector(16, f64) = @bitCast(density[index..][0..16].*);
                const solid = values > @as(@Vector(16, f64), @splat(0.0));
                if (@reduce(.And, solid)) {
                    @memset(materials[index..][0..16], 0);
                    continue;
                }
                if (global == 3 or @as(i64, y) >= @as(i64, group.level) + 5) {
                    materials[index..][0..16].* = @bitCast(@select(i8, solid, zero, @as(@Vector(16, i8), @splat(if (global == 3) @as(i8, 3) else @as(i8, 1)))));
                    continue;
                }
                var first: @Vector(16, i16) = @splat(std.math.maxInt(i16));
                var second = first;
                inline for (0..12) |i| {
                    const dy = group.dy[pair][i] + @as(@Vector(16, i16), @splat(@intCast(row)));
                    const distances = group.horizontal[pair][i] + dy * dy;
                    second = @min(second, @max(first, distances));
                    first = @min(first, distances);
                }
                const close = second - first <= @as(@Vector(16, i16), @splat(44));
                const fluid: i8 = if (y >= group.level) 1 else group.fluid;
                const regular = @as(@Vector(16, i8), @splat(fluid)) | @select(i8, close, @as(@Vector(16, i8), @splat(4)), zero);
                materials[index..][0..16].* = @bitCast(@select(i8, solid, zero, regular));
            }
        }
    }
    return true;
}
