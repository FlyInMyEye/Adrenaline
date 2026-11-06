pub const InstructionSet = enum { portable, avx2 };
pub const Options = struct {
    instruction_set: InstructionSet = .portable,
    small_batch: bool = false,
};
const root = @import("root");
pub const selected: Options = if (@hasDecl(root, "native_backend")) root.native_backend else .{};
pub const is_simd_backend = selected.instruction_set != .portable;
pub const small_batch = selected.small_batch;
