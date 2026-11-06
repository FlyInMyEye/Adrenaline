const std = @import("std");

pub fn density_program_value(program: []const u8, offset: usize) f64 {
    return @bitCast(std.mem.readInt(u64, program[offset..][0..8], .little));
}

pub fn density_program_float(program: []const u8, offset: usize) f32 {
    return @bitCast(std.mem.readInt(u32, program[offset..][0..4], .little));
}

pub fn read_program_u8(program: []const u8, program_counter: *usize) ?u8 {
    if (program_counter.* >= program.len) {
        return null;
    }
    const value = program[program_counter.*];
    program_counter.* += 1;
    return value;
}

pub fn read_program_u32(program: []const u8, program_counter: *usize) ?u32 {
    if (program.len -| program_counter.* < 4) {
        return null;
    }
    const start = program_counter.*;
    program_counter.* += 4;
    return @as(u32, program[start]) | @as(u32, program[start + 1]) << 8 | @as(u32, program[start + 2]) << 16 | @as(u32, program[start + 3]) << 24;
}

pub fn read_program_u64(program: []const u8, program_counter: *usize) ?u64 {
    if (program.len -| program_counter.* < 8) {
        return null;
    }
    const start = program_counter.*;
    program_counter.* += 8;
    var value: u64 = 0;
    inline for (0..8) |index| {
        value |= @as(u64, program[start + index]) << @as(u6, @intCast(index * 8));
    }
    return value;
}

pub fn read_program_f64(program: []const u8, program_counter: *usize) ?f64 {
    const bits = read_program_u64(program, program_counter) orelse return null;
    return @bitCast(bits);
}
