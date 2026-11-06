const rules = @import("../surface/rules.zig");
pub fn register() void {
    @export(&rules.column, .{ .name = "Java_net_fly_adrenaline_natives_NativeSurfaceSampler_columnAddress5" });
}
