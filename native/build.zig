const std = @import("std");
const builtin = @import("builtin");

const avx2_roots = [_][]const u8{
    "adrenaline_avx2", "adrenaline_avx2_small", "aquifer_context_avx2",
};

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{ .default_target = .{
        .cpu_arch = builtin.target.cpu.arch,
        .os_tag = builtin.target.os.tag,
        .abi = builtin.target.abi,
        .cpu_model = .baseline,
    } });
    const optimize = b.standardOptimizeOption(.{});
    const java_home = b.option([]const u8, "java-home", "JDK directory containing include/jni.h") orelse
        b.graph.env_map.get("JAVA_HOME") orelse @panic("Set JAVA_HOME or -Djava-home=/path/to/jdk");
    const platform = switch (target.result.os.tag) {
        .windows => "win32",
        .macos => "darwin",
        else => "linux",
    };
    const includes = [_][]const u8{
        b.pathJoin(&.{ java_home, "include" }),
        b.pathJoin(&.{ java_home, "include", platform }),
    };
    const portable = module(b, "src/adrenaline_native.zig", target, optimize, includes);
    const lib = b.addLibrary(.{ .name = "adrenaline_native", .linkage = .dynamic, .root_module = portable });
    portable.addCSourceFile(.{ .file = b.path("src/cpu_features.c"), .flags = &.{} });

    if (target.result.cpu.arch == .x86_64) {
        const avx2_target = b.resolveTargetQuery(.{
            .cpu_arch = .x86_64,
            .os_tag = target.result.os.tag,
            .abi = target.result.abi,
            .cpu_model = .{ .explicit = &std.Target.x86.cpu.haswell },
        });
        for (avx2_roots) |name| {
            const object = b.addObject(.{
                .name = name,
                .root_module = module(b, b.fmt("src/{s}.zig", .{name}), avx2_target, optimize, includes),
            });
            lib.addObject(object);
        }
        const unit_tests = b.addTest(.{
            .root_module = module(b, "src/tests.zig", avx2_target, optimize, includes),
        });
        const run = b.addRunArtifact(unit_tests);
        b.step("test", "Run exact-output kernel tests on an AVX2 host").dependOn(&run.step);
    }
    b.installArtifact(lib);
}

fn module(b: *std.Build, source: []const u8, target: std.Build.ResolvedTarget, optimize: std.builtin.OptimizeMode, includes: [2][]const u8) *std.Build.Module {
    const result = b.createModule(.{
        .root_source_file = b.path(source),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
        .pic = true,
    });
    for (includes) |path| result.addIncludePath(.{ .cwd_relative = path });
    return result;
}
