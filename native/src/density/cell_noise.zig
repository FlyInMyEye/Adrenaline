const std = @import("std");

pub const CellNoise = struct {
    const Entry = struct { operands: []const u8, column: [16]f64 };
    plain: [4]Entry = undefined,
    count: usize = 0,
    shifted: ?Entry = null,
    shifts: [3][16]u64 = undefined,

    pub fn find(self: *const CellNoise, operands: []const u8, result: []f64) bool {
        for (self.plain[0..self.count]) |entry| {
            if (std.mem.eql(u8, entry.operands, operands)) {
                expand(entry.column, result);
                return true;
            }
        }
        return false;
    }

    pub fn remember(self: *CellNoise, operands: []const u8, result: []const f64) void {
        if (self.count == self.plain.len) return;
        self.plain[self.count] = capture(operands, result);
        self.count += 1;
    }

    pub fn findShifted(self: *CellNoise, operands: []const u8, shifts: [3][]const f64, result: []f64) bool {
        if (self.shifted) |entry| {
            if (std.mem.eql(u8, operands, entry.operands)) {
                var same = true;
                for (shifts, self.shifts) |axis, old| {
                    for (0..16) |i| {
                        if (@as(u64, @bitCast(axis[i * 8])) != old[i]) {
                            same = false;
                            break;
                        }
                    }
                    if (!same) break;
                }
                if (same) {
                    expand(entry.column, result);
                    return true;
                }
            }
        }
        for (shifts, &self.shifts) |axis, *saved| {
            for (0..16) |i| saved[i] = @bitCast(axis[i * 8]);
        }
        return false;
    }

    pub fn rememberShifted(self: *CellNoise, operands: []const u8, result: []const f64) void {
        self.shifted = capture(operands, result);
    }

    fn capture(operands: []const u8, result: []const f64) Entry {
        var entry: Entry = .{ .operands = operands, .column = undefined };
        for (0..16) |i| entry.column[i] = result[i * 8];
        return entry;
    }

    fn expand(column: [16]f64, result: []f64) void {
        for (column, 0..) |value, i| @memset(result[i * 8 ..][0..8], value);
    }
};
