const backend_runtime = @import("runtime.zig");

const builtin = @import("builtin");
const c = @import("../core/jni.zig").c;
const backend_abi = @import("abi.zig");
const backend_options = @import("options.zig");
const density_fused = @import("../density/fused.zig");
const density_validation = @import("../density/validation.zig");

const density_program = @import("../density/program.zig");

pub inline fn evaluate(program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: []c.jdouble, inputs: []const f64) bool {
    if (width == 4 and density_validation.is_fused_terrain_program(program)) {
        if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
            if (backend_runtime.supports(.avx2)) {
                backend_abi.adrenaline_fused_terrain_avx2(program.ptr, height, output.ptr);
                return true;
            }
        }
        density_fused.evaluate_fused_terrain(program, height, output);
        return true;
    }
    if (width == 4 and density_validation.is_fused_ridge_program(program)) {
        if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
            if (backend_runtime.supports(.avx2)) {
                backend_abi.adrenaline_fused_ridge_avx2(program.ptr, height, output.ptr);
                return true;
            }
        }
        density_fused.evaluate_fused_ridge(program, height, output);
        return true;
    }
    if (width == 4 and height == 8) {
        if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
            if (inputs.len == 0 and backend_runtime.supports(.avx2)) {
                return backend_abi.adrenaline_density_cell_avx2(program.ptr, program.len, base_x, base_y, base_z, width, height, output.ptr) != 0;
            }
        }
    }
    return density_program.evaluate_density_cell(program, base_x, base_y, base_z, width, height, output, inputs);
}

pub inline fn evaluate_slice(program: []const u8, x: i32, y: i32, z: i32, step: i32, output: []f64, inputs: []const f64) bool {
    if (comptime builtin.cpu.arch == .x86_64 and !backend_options.is_simd_backend) {
        if (backend_runtime.supports(.avx2)) return backend_abi.adrenaline_density_slice_avx2(program.ptr, program.len, x, y, z, step, output.len, inputs.ptr, inputs.len, output.ptr) != 0;
    }
    return density_program.evaluate_density_slice(program, x, y, z, step, output, inputs);
}
