const backend_density = @import("../backend/density_dispatch.zig");

const c = @import("../core/jni.zig").c;
const core_limits = @import("../core/limits.zig");
const density_interpreter = @import("interpreter.zig");

pub fn evaluate_density_program(program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: []c.jdouble) bool {
    return backend_density.evaluate(program, base_x, base_y, base_z, width, height, output, &.{});
}

pub fn evaluate_density_cell(program: []const u8, base_x: c.jint, base_y: c.jint, base_z: c.jint, width: usize, height: usize, output: []c.jdouble, inputs: []const f64) bool {
    if (width == 4 and height == 8) {
        return density_interpreter.evaluate_density_program_generic(128, true, program, base_x, base_y, base_z, width, height, output, inputs, &.{}, &.{});
    }
    if (output.len <= 128) {
        return density_interpreter.evaluate_density_program_generic(128, false, program, base_x, base_y, base_z, width, height, output, inputs, &.{}, &.{});
    }
    return density_interpreter.evaluate_density_program_generic(core_limits.max_density_values, false, program, base_x, base_y, base_z, width, height, output, inputs, &.{}, &.{});
}

pub fn evaluate_density_slice(program: []const u8, x: i32, y: i32, z: i32, step: i32, output: []f64, inputs: []const f64) bool {
    if (output.len <= 64) return density_interpreter.evaluate_density_slice(64, program, x, y, z, step, output, inputs);
    return density_interpreter.evaluate_density_slice(core_limits.max_grid_axis, program, x, y, z, step, output, inputs);
}

pub fn evaluate_density_lattice(program: []const u8, x: i32, y: i32, z: i32, width: usize, height: usize, step_xz: i32, step_y: i32, output: []f64, inputs: []const f64) bool {
    return density_interpreter.evaluate_density_lattice(1280, program, x, y, z, width, height, step_xz, step_y, output, inputs);
}
