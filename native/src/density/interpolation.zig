const core_vectors = @import("../core/vectors.zig");
const density_read = @import("read.zig");

pub const DensityCorners = struct {
    values: [8]f64,

    pub fn load(program: []const u8, offset: usize) DensityCorners {
        var result: DensityCorners = undefined;
        inline for (0..8) |i| result.values[i] = density_read.density_program_value(program, offset + i * 8);
        return result;
    }

    pub fn y(self: DensityCorners, fy: f64) [4]f64 {
        const a = self.values;
        return .{ a[0] + fy * (a[4] - a[0]), a[2] + fy * (a[6] - a[2]), a[1] + fy * (a[5] - a[1]), a[3] + fy * (a[7] - a[3]) };
    }

    pub fn row(corners: [4]f64, fx: f64) [2]f64 {
        return .{ corners[0] + fx * (corners[1] - corners[0]), corners[2] + fx * (corners[3] - corners[2]) };
    }

    pub fn sample(row_values: [2]f64) core_vectors.Vec4 {
        const z: core_vectors.Vec4 = .{ 0.0, 0.25, 0.5, 0.75 };
        return @as(core_vectors.Vec4, @splat(row_values[0])) + z * @as(core_vectors.Vec4, @splat(row_values[1] - row_values[0]));
    }
};
