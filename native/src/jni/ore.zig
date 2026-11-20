const c = @import("../core/jni.zig").c;
const feature_output = @import("feature_output.zig");
const Vec = @Vector(4, f64);
threadlocal var visited: [40 * 40]u64 = undefined;

fn collect0(env: ?*c.JNIEnv, _: c.jclass, balls_array: c.jdoubleArray, count: c.jint, output_array: c.jintArray, origin_x: c.jint, origin_y: c.jint, origin_z: c.jint, width: c.jint, height: c.jint, min_y: c.jint, max_y: c.jint) callconv(.c) c.jint {
    @setFloatMode(.strict);
    if (env == null or balls_array == null or output_array == null or count <= 0 or count > 64 or width <= 0 or width > 40 or height <= 0 or height > 40) return -1;
    if (@abs(@as(i64, origin_x)) > 30_001_000 or @abs(@as(i64, origin_z)) > 30_001_000 or
        @abs(@as(i64, origin_y)) > 65_536 or min_y < -65_536 or max_y > 65_536 or max_y <= min_y) return -1;
    const f = env.?.*.*;
    const get_length = f.GetArrayLength orelse return -1;
    const get_region = f.GetDoubleArrayRegion orelse return -1;
    const set_region = f.SetIntArrayRegion orelse return -1;
    const capacity = get_length(env, output_array);
    if (get_length(env, balls_array) < 4 * count or capacity <= 0) return -1;
    var balls: [4 * 64]f64 = undefined;
    get_region(env, balls_array, 0, 4 * count, &balls);
    const output = feature_output.positions[0..@min(@as(usize, @intCast(capacity)), feature_output.positions.len)];
    @memset(visited[0..@intCast(width * height)], 0);
    var length: usize = 0;
    const lanes: Vec = .{ 0.0, 1.0, 2.0, 3.0 };
    for (0..@intCast(count)) |i| {
        const x = balls[i * 4];
        const y = balls[i * 4 + 1];
        const z = balls[i * 4 + 2];
        const r = balls[i * 4 + 3];
        if (r < 0.0) continue;
        if (!(r <= 8.0 and @abs(x - @as(f64, @floatFromInt(origin_x))) <= 128.0 and
            @abs(y - @as(f64, @floatFromInt(origin_y))) <= 128.0 and
            @abs(z - @as(f64, @floatFromInt(origin_z))) <= 128.0)) return -1;
        const x0 = @max(origin_x, @as(i32, @intFromFloat(@floor(x - r))));
        const y0 = @max(origin_y, @as(i32, @intFromFloat(@floor(y - r))));
        const z0 = @max(origin_z, @as(i32, @intFromFloat(@floor(z - r))));
        const x1 = @max(x0, @as(i32, @intFromFloat(@floor(x + r))));
        const y1 = @min(@max(y0, @as(i32, @intFromFloat(@floor(y + r)))), max_y - 1);
        const z1 = @max(z0, @as(i32, @intFromFloat(@floor(z + r))));
        const min_y_clamped = @max(y0, min_y);
        if (min_y_clamped > y1) continue;
        if (x1 - origin_x >= width or y1 - origin_y >= height or z1 - origin_z >= width) return -1;
        const z_count: usize = @intCast(z1 - z0 + 1);
        const groups = (z_count + 3) / 4;
        var z_squared: [10]Vec = undefined;
        for (0..groups) |group| {
            const base: Vec = @splat(@floatFromInt(z0 + @as(i32, @intCast(group * 4))));
            const dz = ((base + lanes + @as(Vec, @splat(0.5))) - @as(Vec, @splat(z))) / @as(Vec, @splat(r));
            z_squared[group] = dz * dz;
        }
        var y_squared: [40]f64 = undefined;
        for (0..@intCast(y1 - min_y_clamped + 1)) |row| {
            const block_y: f64 = @floatFromInt(min_y_clamped + @as(i32, @intCast(row)));
            const dy = (block_y + 0.5 - y) / r;
            y_squared[row] = dy * dy;
        }
        const z_limit = (@as(u64, 1) << @as(u6, @intCast(z_count))) - 1;
        const z_offset: u6 = @intCast(z0 - origin_z);
        const sphere: i32 = @as(i32, @intCast(i)) << 24;
        var xx = x0;
        while (xx <= x1) : (xx += 1) {
            const dx = (@as(f64, @floatFromInt(xx)) + 0.5 - x) / r;
            const dx2 = dx * dx;
            if (!(dx2 < 1.0)) continue;
            var yy = min_y_clamped;
            while (yy <= y1) : (yy += 1) {
                const xy = dx2 + y_squared[@intCast(yy - min_y_clamped)];
                if (!(xy < 1.0)) continue;
                const row = &visited[@intCast((xx - origin_x) + (yy - origin_y) * width)];
                var inside: u64 = 0;
                for (z_squared[0..groups], 0..) |squared, group| {
                    const mask: u4 = @bitCast(@as(Vec, @splat(xy)) + squared < @as(Vec, @splat(1.0)));
                    inside |= @as(u64, mask) << @as(u6, @intCast(group * 4));
                }
                const eligible = (inside & z_limit) << z_offset;
                var fresh = eligible & ~row.*;
                row.* |= eligible;
                const xy_sphere = sphere | ((yy - origin_y) << 8) | (xx - origin_x);
                while (fresh != 0) : (fresh &= fresh - 1) {
                    if (length >= output.len) return -1;
                    output[length] = xy_sphere | (@as(i32, @intCast(@ctz(fresh))) << 16);
                    length += 1;
                }
            }
        }
    }
    if (length != 0) set_region(env, output_array, 0, @intCast(length), output.ptr);
    return @intCast(length);
}

pub fn register() void {
    @export(&collect0, .{ .name = "Java_net_fly_adrenaline_worldgen_OreCandidateBatch_collect0" });
}
