const core_vectors = @import("../core/vectors.zig");
const density_read = @import("read.zig");

pub fn is_fused_terrain_program(program: []const u8) bool {
    if (program.len != 388) return false;
    if (program[376] != 12 or program[377] != 2 or program[386] != 3 or program[387] != 0) return false;
    if (!(@abs(density_read.density_program_value(program, 378)) <= 1.0e100)) return false;
    return is_terrain_prefix(program);
}

pub fn is_terrain_prefix(program: []const u8) bool {
    if (program.len < 376) return false;
    const offsets = [_]usize{ 0, 65, 74, 75, 76, 141, 150, 215, 280, 281, 346, 347, 348, 357, 358, 359 };
    const opcodes = [_]u8{ 15, 2, 4, 11, 15, 2, 15, 15, 6, 15, 6, 13, 2, 4, 3, 14 };
    inline for (offsets, opcodes) |offset, opcode| {
        if (program[offset] != opcode) return false;
    }
    inline for (.{ 1, 77, 151, 216, 282 }) |offset| {
        inline for (0..2) |i| {
            const values: core_vectors.Vec4 = @bitCast(program[offset + i * 32 ..][0..32].*);
            if (!@reduce(.And, @abs(values) <= @as(core_vectors.Vec4, @splat(1.0e100)))) return false;
        }
    }
    inline for (.{ 66, 142, 349 }) |offset| {
        if (!(@abs(density_read.density_program_value(program, offset)) <= 1.0e100)) return false;
    }
    return true;
}

pub fn is_fused_ridge_program(program: []const u8) bool {
    if (program.len != 144) return false;
    const offsets = [_]usize{ 0, 65, 66, 131, 132, 133, 142, 143 };
    const opcodes = [_]u8{ 15, 6, 15, 6, 13, 2, 3, 0 };
    inline for (offsets, opcodes) |offset, opcode| {
        if (program[offset] != opcode) return false;
    }
    inline for (.{ 1, 67 }) |offset| {
        inline for (0..2) |i| {
            const values: core_vectors.Vec4 = @bitCast(program[offset + i * 32 ..][0..32].*);
            if (!@reduce(.And, @abs(values) <= @as(core_vectors.Vec4, @splat(1.0e100)))) return false;
        }
    }
    return @abs(density_read.density_program_value(program, 134)) <= 1.0e100;
}
