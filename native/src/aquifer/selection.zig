const std = @import("std");
const c = @import("../core/jni.zig").c;
const backend_options = @import("../backend/options.zig");
const core_vectors = @import("../core/vectors.zig");

pub fn pack_aquifer_candidates(output: []c.jlong, candidates: AquiferCandidates) ?void {
    if (output.len != 2 or candidates.nearest_index > 0xFFF or candidates.second_index > 0xFFF or candidates.third_index > 0xFFF or candidates.nearest_distance < 0 or candidates.second_distance < 0 or candidates.third_distance < 0 or candidates.nearest_distance > std.math.maxInt(u16) or candidates.second_distance > std.math.maxInt(u16) or candidates.third_distance > std.math.maxInt(u16)) {
        return null;
    }
    const indices: u64 = @as(u64, @intCast(candidates.nearest_index)) | @as(u64, @intCast(candidates.second_index)) << 12 | @as(u64, @intCast(candidates.third_index)) << 24;
    const distances: u64 = @as(u64, @intCast(candidates.nearest_distance)) | @as(u64, @intCast(candidates.second_distance)) << 16 | @as(u64, @intCast(candidates.third_distance)) << 32;
    output[0] = @bitCast(indices);
    output[1] = @bitCast(distances);
}

pub fn unpack_aquifer_candidates(indices_value: c.jlong, distances_value: c.jlong, cache_length: usize) ?AquiferCandidates {
    const indices: u64 = @bitCast(indices_value);
    const distances: u64 = @bitCast(distances_value);
    const result: AquiferCandidates = .{
        .nearest_index = @intCast(indices & 0xFFF),
        .second_index = @intCast((indices >> 12) & 0xFFF),
        .third_index = @intCast((indices >> 24) & 0xFFF),
        .nearest_distance = @intCast(distances & 0xFFFF),
        .second_distance = @intCast((distances >> 16) & 0xFFFF),
        .third_distance = @intCast((distances >> 32) & 0xFFFF),
    };
    if (@max(result.nearest_index, @max(result.second_index, result.third_index)) >= cache_length) return null;
    return result;
}

test "packed candidates reject indices outside the actual cache" {
    const distances: c.jlong = @bitCast(@as(u64, 100) << 16 | @as(u64, 100) << 32);
    for ([_]u6{ 0, 12, 24 }) |shift| {
        try std.testing.expect(unpack_aquifer_candidates(@bitCast(@as(u64, 4095) << shift), distances, 1) == null);
        try std.testing.expect(unpack_aquifer_candidates(@bitCast(@as(u64, 1) << shift), distances, 1) == null);
    }
    try std.testing.expect(unpack_aquifer_candidates(0, distances, 0) == null);
    try std.testing.expect(unpack_aquifer_candidates(0, distances, 1) != null);
    try std.testing.expect(unpack_aquifer_candidates(0xfff, distances, 4096) != null);
}

pub const AquiferCandidates = struct {
    nearest_distance: i32,
    second_distance: i32,
    third_distance: i32,
    nearest_index: usize,
    second_index: usize,
    third_index: usize,
};

pub const AquiferSearch = struct {
    grid_z: i32,
    grid_y: i32,
    y: i32,
    indices: [12]usize,
    distances: [12]i32,
    delta_z: [12]i32,
    delta_y: [12]i32,

    pub fn advanceZ(self: *AquiferSearch) void {
        const one: core_vectors.Vec4i = @splat(1);
        const two: core_vectors.Vec4i = @splat(2);
        inline for (.{ 0, 4, 8 }) |index| {
            const previous: core_vectors.Vec4i = @bitCast(self.delta_z[index .. index + 4].*);
            const distance: core_vectors.Vec4i = @bitCast(self.distances[index .. index + 4].*);
            self.delta_z[index .. index + 4].* = @bitCast(previous - one);
            self.distances[index .. index + 4].* = @bitCast(distance - two * previous + one);
        }
    }

    pub fn advanceZBy(self: *AquiferSearch, steps: i32) void {
        std.debug.assert(steps > 0 and steps < 16);
        const step: core_vectors.Vec4i = @splat(steps);
        const twice_step: core_vectors.Vec4i = @splat(2 * steps);
        const square: core_vectors.Vec4i = @splat(steps * steps);
        inline for (.{ 0, 4, 8 }) |index| {
            const previous: core_vectors.Vec4i = @bitCast(self.delta_z[index .. index + 4].*);
            const distance: core_vectors.Vec4i = @bitCast(self.distances[index .. index + 4].*);
            self.delta_z[index .. index + 4].* = @bitCast(previous - step);
            self.distances[index .. index + 4].* = @bitCast(distance - twice_step * previous + square);
        }
    }

    pub fn advanceYDown(self: *AquiferSearch, y: i32) bool {
        const steps = self.y - y;
        if (steps <= 0) {
            return false;
        }
        const step: core_vectors.Vec4i = @splat(steps);
        const two: core_vectors.Vec4i = @splat(2);
        const square: core_vectors.Vec4i = @splat(steps * steps);
        inline for (.{ 0, 4, 8 }) |index| {
            const previous: core_vectors.Vec4i = @bitCast(self.delta_y[index .. index + 4].*);
            const distance: core_vectors.Vec4i = @bitCast(self.distances[index .. index + 4].*);
            self.delta_y[index .. index + 4].* = @bitCast(previous + step);
            self.distances[index .. index + 4].* = @bitCast(distance + two * previous * step + square);
        }
        self.y = y;
        return true;
    }

    pub fn nearest_two_distances(self: *const AquiferSearch, comptime use_avx2: bool) [2]i32 {
        if (comptime use_avx2) {
            const a: core_vectors.Vec4i = @bitCast(self.distances[0..4].*);
            const b: core_vectors.Vec4i = @bitCast(self.distances[4..8].*);
            const c1: core_vectors.Vec4i = @bitCast(self.distances[8..12].*);
            const low = @min(a, b);
            const high = @max(a, b);
            const smallest = @min(low, c1);
            const middle = @max(low, @min(high, c1));
            const first = @reduce(.Min, smallest);
            const matches: u4 = @bitCast(smallest == @as(core_vectors.Vec4i, @splat(first)));
            const lane: i32 = @intCast(@ctz(matches));
            const second_set = @select(i32, @as(core_vectors.Vec4i, .{ 0, 1, 2, 3 }) == @as(core_vectors.Vec4i, @splat(lane)), middle, smallest);
            return .{ first, @reduce(.Min, second_set) };
        }
        var first: i32 = std.math.maxInt(i32);
        var second: i32 = std.math.maxInt(i32);
        for (self.distances) |distance| {
            if (distance <= first) {
                second = first;
                first = distance;
            } else if (distance < second) second = distance;
        }
        return .{ first, second };
    }

    pub fn candidates(self: *const AquiferSearch) AquiferCandidates {
        return self.candidates_impl(backend_options.is_simd_backend);
    }

    pub fn candidates_impl(self: *const AquiferSearch, comptime use_avx2: bool) AquiferCandidates {
        if (comptime use_avx2) {
            const a: core_vectors.Vec4i = @bitCast(self.distances[0..4].*);
            const b: core_vectors.Vec4i = @bitCast(self.distances[4..8].*);
            const c1: core_vectors.Vec4i = @bitCast(self.distances[8..12].*);
            const zero: core_vectors.Vec4i = @splat(0);
            const limit: core_vectors.Vec4i = @splat((std.math.maxInt(i32) - 15) >> 4);
            if (@reduce(.And, (a >= zero) & (a <= limit) & (b >= zero) & (b <= limit) & (c1 >= zero) & (c1 <= limit))) {
                const shift: @Vector(4, u5) = @splat(4);
                const ka = (a << shift) + @as(core_vectors.Vec4i, .{ 11, 10, 9, 8 });
                const kb = (b << shift) + @as(core_vectors.Vec4i, .{ 7, 6, 5, 4 });
                const kc = (c1 << shift) + @as(core_vectors.Vec4i, .{ 3, 2, 1, 0 });
                const lo = @min(ka, kb);
                const hi = @max(ka, kb);
                var smallest = @min(lo, kc);
                const middle = @max(lo, @min(hi, kc));
                const largest = @max(hi, kc);
                const first = @reduce(.Min, smallest);
                const first_lane = smallest == @as(core_vectors.Vec4i, @splat(first));
                const next = @select(i32, first_lane, largest, middle);
                smallest = @select(i32, first_lane, middle, smallest);
                const second = @reduce(.Min, smallest);
                smallest = @select(i32, smallest == @as(core_vectors.Vec4i, @splat(second)), next, smallest);
                const third = @reduce(.Min, smallest);
                return .{
                    .nearest_distance = first >> 4,
                    .second_distance = second >> 4,
                    .third_distance = third >> 4,
                    .nearest_index = self.indices[@as(usize, @intCast(11 - (first & 15)))],
                    .second_index = self.indices[@as(usize, @intCast(11 - (second & 15)))],
                    .third_index = self.indices[@as(usize, @intCast(11 - (third & 15)))],
                };
            }
        }
        var nearest_distance: i32 = std.math.maxInt(i32);
        var second_distance: i32 = std.math.maxInt(i32);
        var third_distance: i32 = std.math.maxInt(i32);
        var nearest_index: usize = 0;
        var second_index: usize = 0;
        var third_index: usize = 0;
        for (0..12) |order_value| {
            const distance = self.distances[order_value];
            const index = self.indices[order_value];
            if (distance <= nearest_distance) {
                third_distance = second_distance;
                third_index = second_index;
                second_distance = nearest_distance;
                second_index = nearest_index;
                nearest_distance = distance;
                nearest_index = index;
            } else if (distance <= second_distance) {
                third_distance = second_distance;
                third_index = second_index;
                second_distance = distance;
                second_index = index;
            } else if (distance <= third_distance) {
                third_distance = distance;
                third_index = index;
            }
        }
        return .{
            .nearest_distance = nearest_distance,
            .second_distance = second_distance,
            .third_distance = third_distance,
            .nearest_index = nearest_index,
            .second_index = second_index,
            .third_index = third_index,
        };
    }
};

test "uniform aquifer nearest two match scalar including ties" {
    var rng: u32 = 0x87654321;
    for (0..8192) |run| {
        var search: AquiferSearch = undefined;
        for (0..12) |i| {
            rng ^= rng << 13;
            rng ^= rng >> 17;
            rng ^= rng << 5;
            search.distances[i] = if (run % 9 == 0) std.math.maxInt(i32) else if (run % 11 == 0 and i == 5) std.math.minInt(i32) else @as(i32, @intCast(rng % 64));
        }
        try std.testing.expectEqualDeep(search.nearest_two_distances(false), search.nearest_two_distances(true));
    }
}

test "AVX2 aquifer selection agrees with scalar ties and bounds" {
    var rng: u32 = 0xabc12345;
    for (0..4096) |run| {
        var search: AquiferSearch = undefined;
        for (0..12) |i| {
            rng ^= rng << 13;
            rng ^= rng >> 17;
            rng ^= rng << 5;
            search.indices[i] = 100 + i;
            search.distances[i] = if (run % 9 == 0) std.math.maxInt(i32) else if (run % 11 == 0 and i == 5) -1 else @as(i32, @intCast(rng % 128));
        }
        const scalar = search.candidates_impl(false);
        const vector = search.candidates_impl(true);
        try std.testing.expectEqualDeep(scalar, vector);
    }
}

pub fn advance_y_down(comptime avx2: bool, comptime neon: bool, search: *AquiferSearch, y: i32) bool {
    const steps = search.y - y;
    if (steps <= 0) {
        return false;
    }
    if (comptime avx2) {
        const step8: core_vectors.Vec8i = @splat(steps);
        const step4: core_vectors.Vec4i = @splat(steps);
        const two8: core_vectors.Vec8i = @splat(2);
        const two4: core_vectors.Vec4i = @splat(2);
        const squared8: core_vectors.Vec8i = @splat(steps * steps);
        const squared4: core_vectors.Vec4i = @splat(steps * steps);
        const previous8: core_vectors.Vec8i = @bitCast(search.delta_y[0..8].*);
        const previous4: core_vectors.Vec4i = @bitCast(search.delta_y[8..12].*);
        const distance8: core_vectors.Vec8i = @bitCast(search.distances[0..8].*);
        const distance4: core_vectors.Vec4i = @bitCast(search.distances[8..12].*);
        search.delta_y[0..8].* = @bitCast(previous8 + step8);
        search.delta_y[8..12].* = @bitCast(previous4 + step4);
        search.distances[0..8].* = @bitCast(distance8 + two8 * previous8 * step8 + squared8);
        search.distances[8..12].* = @bitCast(distance4 + two4 * previous4 * step4 + squared4);
        search.y = y;
        return true;
    }
    if (comptime neon) {
        const step: core_vectors.Vec4i = @splat(steps);
        const two: core_vectors.Vec4i = @splat(2);
        const square: core_vectors.Vec4i = @splat(steps * steps);
        inline for (.{ 0, 4, 8 }) |index| {
            const previous: core_vectors.Vec4i = @bitCast(search.delta_y[index .. index + 4].*);
            const distance: core_vectors.Vec4i = @bitCast(search.distances[index .. index + 4].*);
            search.distances[index .. index + 4].* = @bitCast(distance + two * previous * step + square);
            search.delta_y[index .. index + 4].* = @bitCast(previous + step);
        }
        search.y = y;
        return true;
    }
    return search.advanceYDown(y);
}

pub fn aquifer_search(packed_locations: []const c.jshort, min_grid_x: c.jint, min_grid_y: c.jint, min_grid_z: c.jint, grid_size_x: c.jint, grid_size_z: c.jint, x: i32, y: i32, z: i32) ?AquiferSearch {
    const grid_x = @divFloor(x - 5, 16);
    const grid_y = @divFloor(y + 1, 12);
    const grid_z = @divFloor(z - 5, 16);
    const local_grid_x = grid_x - min_grid_x;
    const local_grid_y = grid_y - min_grid_y;
    const local_grid_z = grid_z - min_grid_z;
    if (local_grid_x < 0 or local_grid_y < 1 or local_grid_z < 0 or local_grid_x + 1 >= grid_size_x or local_grid_z + 1 >= grid_size_z) {
        return null;
    }
    const size_x: usize = @intCast(grid_size_x);
    const size_z: usize = @intCast(grid_size_z);
    const local_x: usize = @intCast(local_grid_x);
    const local_y: usize = @intCast(local_grid_y);
    const local_z: usize = @intCast(local_grid_z);
    const last_index = ((local_y + 1) * size_z + local_z + 1) * size_x + local_x + 1;
    if (last_index >= packed_locations.len) return null;
    var result: AquiferSearch = undefined;
    result.grid_z = grid_z;
    result.grid_y = grid_y;
    result.y = y;
    inline for (0..2) |ox| {
        inline for (0..3) |oy| {
            const row = ((local_y + oy - 1) * size_z + local_z) * size_x + local_x + ox;
            inline for (0..2) |oz| {
                const order = (ox * 3 + oy) * 2 + oz;
                const cache_index = row + oz * size_x;
                const packed_value: u16 = @bitCast(packed_locations.ptr[cache_index]);
                const center_x = (grid_x + @as(i32, ox)) * 16 + @as(i32, packed_value >> 8);
                const center_y = (grid_y + @as(i32, oy) - 1) * 12 + @as(i32, (packed_value >> 4) & 15);
                const center_z = (grid_z + @as(i32, oz)) * 16 + @as(i32, packed_value & 15);
                const delta_x = center_x - x;
                const delta_y = center_y - y;
                const delta_z = center_z - z;
                result.indices[order] = cache_index;
                result.distances[order] = delta_x * delta_x + delta_y * delta_y + delta_z * delta_z;
                result.delta_z[order] = delta_z;
                result.delta_y[order] = delta_y;
            }
        }
    }
    return result;
}

pub const AquiferCenters = struct {
    grid_x: i32,
    grid_y: i32,
    grid_z: i32,
    indices: [12]usize,
    x: [12]i32,
    y: [12]i32,
    z: [12]i32,
};

pub fn aquifer_search_cached(cache: *[16]AquiferCenters, valid: *u16, packed_locations: []const c.jshort, min_grid_x: i32, min_grid_y: i32, min_grid_z: i32, grid_size_x: i32, grid_size_z: i32, grid_x: i32, grid_y: i32, grid_z: i32, x: i32, y: i32, z: i32) ?AquiferSearch {
    const slot: usize = @intCast((@as(u32, @bitCast(grid_y)) & 3) * 4 + (@as(u32, @bitCast(grid_x)) & 1) * 2 + (@as(u32, @bitCast(grid_z)) & 1));
    const mask: u16 = @as(u16, 1) << @as(u4, @intCast(slot));
    const centers = &cache[slot];
    if (valid.* & mask == 0 or centers.grid_x != grid_x or centers.grid_y != grid_y or centers.grid_z != grid_z) {
        const local_x = grid_x - min_grid_x;
        const local_y = grid_y - min_grid_y;
        const local_z = grid_z - min_grid_z;
        if (local_x < 0 or local_y < 1 or local_z < 0 or local_x + 1 >= grid_size_x or local_z + 1 >= grid_size_z) return null;
        const sx: usize = @intCast(grid_size_x);
        const sz: usize = @intCast(grid_size_z);
        const lx: usize = @intCast(local_x);
        const ly: usize = @intCast(local_y);
        const lz: usize = @intCast(local_z);
        if (((ly + 1) * sz + lz + 1) * sx + lx + 1 >= packed_locations.len) return null;
        inline for (0..2) |ox| {
            inline for (0..3) |oy| {
                const row = ((ly + oy - 1) * sz + lz) * sx + lx + ox;
                inline for (0..2) |oz| {
                    const order = (ox * 3 + oy) * 2 + oz;
                    const index = row + oz * sx;
                    const packed_value: u16 = @bitCast(packed_locations.ptr[index]);
                    centers.indices[order] = index;
                    centers.x[order] = (grid_x + @as(i32, ox)) * 16 + @as(i32, packed_value >> 8);
                    centers.y[order] = (grid_y + @as(i32, oy) - 1) * 12 + @as(i32, (packed_value >> 4) & 15);
                    centers.z[order] = (grid_z + @as(i32, oz)) * 16 + @as(i32, packed_value & 15);
                }
            }
        }
        centers.grid_x = grid_x;
        centers.grid_y = grid_y;
        centers.grid_z = grid_z;
        valid.* |= mask;
    }
    var result: AquiferSearch = undefined;
    result.grid_z = grid_z;
    result.grid_y = grid_y;
    result.y = y;
    inline for (0..12) |order| {
        const dx = centers.x[order] - x;
        const dy = centers.y[order] - y;
        const dz = centers.z[order] - z;
        result.indices[order] = centers.indices[order];
        result.distances[order] = dx * dx + dy * dy + dz * dz;
        result.delta_z[order] = dz;
        result.delta_y[order] = dy;
    }
    return result;
}
