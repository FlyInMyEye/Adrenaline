const geode = @import("../features/geode.zig");

pub threadlocal var positions: [@max(geode.max_values, 40 * 40 * 40)]i32 = undefined;
