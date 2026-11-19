const density_read = @import("read.zig");
const density_validation = @import("validation.zig");

const min_supported_sample = -0x1p16;
const max_negative_sample = -0x1p-24;

pub fn supports_terrain_bounds(program: []const u8) bool {
    return density_validation.is_fused_terrain_program(program) and
        density_read.density_program_value(program, 66) > 0 and density_read.density_program_value(program, 378) <= 0;
}

fn negative_plane(left_near: f64, left_far: f64, right_near: f64, right_far: f64) bool {
    const corners: @Vector(4, f64) = .{ left_near, left_far, right_near, right_far };
    return @reduce(.And, (corners >= @as(@Vector(4, f64), @splat(min_supported_sample))) &
        (corners <= @as(@Vector(4, f64), @splat(max_negative_sample))));
}

pub fn first_air_cell(columns: [4][]const f64, first_dry: usize, count: usize) usize {
    var plane = count;
    while (true) {
        if (!negative_plane(columns[0][plane], columns[1][plane], columns[2][plane], columns[3][plane])) {
            return @min(plane + 1, count);
        }
        if (plane == first_dry) return first_dry;
        plane -= 1;
    }
}
