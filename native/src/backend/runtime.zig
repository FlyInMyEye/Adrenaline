const builtin = @import("builtin");
const abi = @import("abi.zig");
pub const InstructionSet = @import("options.zig").InstructionSet;

pub inline fn supports(comptime instruction_set: InstructionSet) bool {
    return switch (instruction_set) {
        .portable => true,
        .avx2 => if (comptime builtin.cpu.arch == .x86_64) abi.adrenaline_has_avx2() != 0 else false,
    };
}
