pub fn register() void {
    inline for (.{
        @import("runtime.zig"), @import("noise.zig"),   @import("density.zig"),
        @import("aquifer.zig"), @import("context.zig"), @import("surface.zig"),
        @import("geode.zig"),
    }) |stage| stage.register();
}
