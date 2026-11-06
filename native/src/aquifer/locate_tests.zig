const std = @import("std");
const cell = @import("cell.zig");
const selection = @import("selection.zig");

fn checkLocate(comptime width: usize, comptime height: usize, base_z: i32, pattern: u32, random: bool) !void {
    const count = width * width * height;
    const sx = 10;
    const sz = 10;
    var locations: [sx * sz * 6]i16 = undefined;
    var rng: u32 = pattern +% 1;
    for (&locations) |*value| {
        rng = rng *% 1664525 +% 1013904223;
        const bits: u16 = if (pattern == 0) 0 else @as(u16, @intCast(rng % 10)) << 8 |
            @as(u16, @intCast((rng >> 8) % 9)) << 4 |
            @as(u16, @intCast((rng >> 16) % 10));
        value.* = @bitCast(bits);
    }
    var density: [count]f64 = undefined;
    var materials: [count]i8 = undefined;
    const canary: i64 = 0x5a5a5a5a5a5a5a5a;
    var actual = [_]i64{canary} ** (count * 2);
    var expected = actual;
    for (0..height) |yi| {
        for (0..width) |xi| {
            var ternary = pattern;
            for (0..width) |zi| {
                const at = (yi * width + xi) * width + zi;
                rng = rng *% 1664525 +% 1013904223;
                const mode = if (random) rng % 3 else ternary % 3;
                ternary /= 3;
                density[at] = if (mode == 1) 0.5 else switch (rng & 3) {
                    0 => @as(f64, 0.0),
                    1 => @as(f64, -0.0),
                    2 => std.math.nan(f64),
                    else => -0.25,
                };
                materials[at] = if (mode == 2) 3 else if (rng & 1 == 0) 1 else 2;
                if (density[at] > 0.0 or materials[at] == 3) continue;

                const fresh = selection.aquifer_search(
                    &locations,
                    -4,
                    -2,
                    -4,
                    sx,
                    sz,
                    -8 + @as(i32, @intCast(xi)),
                    -5 + @as(i32, @intCast(height - 1 - yi)),
                    base_z + @as(i32, @intCast(zi)),
                ) orelse return error.InvalidReferenceSearch;
                try std.testing.expect(selection.pack_aquifer_candidates(expected[at * 2 ..][0..2], fresh.candidates_impl(false)) != null);
            }
        }
    }
    try std.testing.expect(cell.locate_aquifer_cell(
        &density,
        &materials,
        &actual,
        &locations,
        -4,
        -2,
        -4,
        sx,
        sz,
        -8,
        -5,
        base_z,
        width,
        height,
    ));

    try std.testing.expectEqualSlices(i64, &expected, &actual);
}

test "locate cached search handles every four-position skip pattern" {
    for (0..81) |pattern| {
        for ([_]i32{ -17, -1, 1, 3, 5, 17, 19, 21 }) |base_z| {
            try checkLocate(4, 2, base_z, @intCast(pattern), false);
        }
    }
}

test "locate cached search matches fresh positions across longer gaps and grids" {
    for (1..16) |gap| {
        var pattern: u32 = 0;
        var power: u32 = 1;
        for (0..16) |zi| {
            const mode: u32 = if (zi == 0 or zi == gap) 0 else 1 + @as(u32, @intCast(zi % 2));
            pattern += mode * power;
            power *= 3;
        }
        try checkLocate(16, 1, 5, pattern, false);
    }
    for (0..64) |pattern| {
        const z = @as(i32, @intCast(pattern)) - 32;
        try checkLocate(8, 2, z, @intCast(pattern), true);
        try checkLocate(16, 2, z, @intCast(pattern), true);
        try checkLocate(32, 1, z, @intCast(pattern), true);
    }
}
