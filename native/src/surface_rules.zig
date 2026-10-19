const c = @cImport({
    @cInclude("jni.h");
});
const std = @import("std");

const Node = extern struct { op: i32, a: i32, b: i32, c: i32, d: i32, yes: i32, no: i32, reserved: i32 };
const Request = extern struct { y: i32, above: i32, below: i32, water: i32, depth: i32, minimum: i32, secondary: f64, biome: i32, reserved: i32, steep: i32, climate: i32, temperature_x: i32, temperature_y: i32, temperature_z: i32, padding: i32 };

const Result = extern struct { state: i32, inputs: i32, bottom: i32 };

const RandomConfig = extern struct { low: u64, high: u64, kind: i32, reserved: i32, padding: u64 };

const Noise = extern struct { first: u64, second: u64, first_count: u32, second_count: u32, factor: f64, minimum: f64, maximum: f64, y_scale: f64, xz_scale: f64 };
const Simplex = extern struct { offset: u32, count: u32, input: f64, value: f64, reserved: u64 };
const Climate = extern struct { base: f32, modifier: i32, temperature: u32, frozen: u32, information: u32, reserved: i32 };
const Program = struct { bytes: []const u8, nodes: []const Node, randoms: []const RandomConfig, noises: []const Noise, simplexes: []const Simplex, climates: []const Climate, indices: []const i32 };
const ColumnCache = extern struct {
    program: u64,
    x: i32,
    z: i32,
    temperature_x: i32,
    temperature_z: i32,
    noise_valid: [16]u64,
    simplex_valid: u64,
    noises: [1024]f64,
    simplexes: [64]f64,
};

comptime {
    if (@sizeOf(ColumnCache) > 16384) @compileError("Surface column cache capacity");
}

const Sample = struct { result: i32, span: usize, inputs: i32 = 0 };

fn evaluate_span(program: Program, cache: *ColumnCache, entry: i32, r: Request, maximum: usize, x: i32, z: i32) Sample {
    @setFloatMode(.strict);
    const nodes = program.nodes;
    const randoms = program.randoms;
    var pc = entry;
    var inputs: i32 = 0;
    var order: i32 = 0;
    var span = bounded(maximum, @as(i64, r.y) - std.math.minInt(i32) + 1);
    span = bounded(span, @as(i64, std.math.maxInt(i32)) - r.above + 1);
    span = bounded(span, @as(i64, r.below) - std.math.minInt(i32) + 1);
    var left = nodes.len;
    while (left > 0) : (left -= 1) {
        if (pc < 0 or pc >= nodes.len) return .{ .result = -3, .span = 1 };
        const n = nodes[@intCast(pc)];
        var result = false;
        switch (n.op) {
            0 => return .{ .result = n.a, .span = if (n.a < -1) 1 else span, .inputs = inputs | (order << 8) },
            1 => {
                if (n.c != 0) {
                    if (r.reserved & 4 == 0) return .{ .result = -5, .span = 1, .inputs = inputs | (order << 8) };
                    use_input(&inputs, &order, 4, 3);
                }
                const secondary: i32 = if (n.c == 0) 0 else if (std.math.isFinite(r.secondary)) java_integer(((r.secondary - -1.0) / (1.0 - -1.0)) * @as(f64, @floatFromInt(n.c))) else return .{ .result = -2, .span = 1, .inputs = inputs | (order << 8) };
                const limit = 1 +% n.a +% (if (n.b == 0) @as(i32, 0) else r.depth) +% secondary;
                const v = if (n.d == 0) r.above else r.below;
                result = v <= limit;
                if (n.d == 0 and result) span = bounded(span, @as(i64, limit) - v + 1);
                if (n.d != 0 and !result) span = bounded(span, @as(i64, v) - limit);
                if ((n.d == 0 and @as(i64, v) + @as(i64, @intCast(span)) > std.math.maxInt(i32)) or (n.d != 0 and @as(i64, v) - @as(i64, @intCast(span)) < std.math.minInt(i32))) span = 1;
            },
            2 => {
                const lhs = r.y +% (if (n.c == 0) @as(i32, 0) else r.above);
                const rhs = n.a +% r.depth *% n.b;
                result = lhs >= rhs;
                if (n.c == 0 and result) span = bounded(span, @as(i64, lhs) - rhs + 1);
            },
            3 => {
                const lhs = r.y +% (if (n.c == 0) @as(i32, 0) else r.above);
                const rhs = r.water +% n.a +% r.depth *% n.b;
                result = r.water == std.math.minInt(i32) or lhs >= rhs;
                if (r.water != std.math.minInt(i32) and n.c == 0 and result) span = bounded(span, @as(i64, lhs) - rhs + 1);
            },
            4 => result = r.depth <= 0,
            5 => {
                if (r.reserved & 2 == 0) return .{ .result = -4, .span = 1, .inputs = inputs | (order << 8) };
                use_input(&inputs, &order, 2, 2);
                result = r.y >= r.minimum;
                if (result) span = bounded(span, @as(i64, r.y) - r.minimum + 1);
            },
            6, 9 => {
                if (r.y <= n.a) result = true else if (r.y >= n.b) {
                    result = false;
                    span = bounded(span, @as(i64, r.y) - n.b + 1);
                } else if (n.op == 9) {
                    if (n.c < 0 or n.c >= randoms.len) return .{ .result = -3, .span = 1 };
                    const fraction = (@as(f64, @floatFromInt(r.y)) - @as(f64, @floatFromInt(n.a))) / (@as(f64, @floatFromInt(n.b)) - @as(f64, @floatFromInt(n.a)));
                    result = positional_float(randoms[@intCast(n.c)], x, r.y, z) < 1.0 + fraction * (0.0 - 1.0);
                    span = 1;
                } else return .{ .result = -2, .span = 1, .inputs = inputs | (order << 8) };
            },
            7 => {
                if (r.reserved & 1 == 0) return .{ .result = -6, .span = 1, .inputs = inputs | (order << 8) };
                use_input(&inputs, &order, 1, 1);
                pc = n.yes;
                continue;
            },
            10 => {
                if (n.a < 0 or n.a >= program.noises.len or n.b < 0 or n.b >= program.noises.len) return .{ .result = -3, .span = 1 };
                const config = program.noises[@intCast(n.a)];
                const value = cached_noise(program, cache, @intCast(n.a), @intCast(n.b), x, r.y, z);
                result = value >= config.minimum and value <= config.maximum;
                if (config.y_scale != 0.0) span = 1;
            },
            11 => {
                if (r.reserved & 16 == 0) return .{ .result = -8, .span = 1, .inputs = inputs | (order << 8) };
                use_input(&inputs, &order, 16, 5);
                if (r.climate < 0 or r.climate >= program.climates.len) return .{ .result = -3, .span = 1 };
                const climate = program.climates[@intCast(r.climate)];
                result = !(temperature(program, cache, climate, r.temperature_x, r.temperature_y, r.temperature_z) >= @as(f32, 0.15));
                if (r.temperature_y > 80) {
                    span = bounded(span, @as(i64, r.temperature_y) - 80);
                    if (result and span > 1) {
                        const last_y = r.temperature_y - @as(i32, @intCast(span - 1));
                        if (temperature(program, cache, climate, r.temperature_x, last_y, r.temperature_z) >= @as(f32, 0.15)) {
                            var low: usize = 1;
                            var high = span - 1;
                            while (low < high) {
                                const middle = low + (high - low) / 2;
                                if (temperature(program, cache, climate, r.temperature_x, r.temperature_y - @as(i32, @intCast(middle)), r.temperature_z) >= @as(f32, 0.15)) high = middle else low = middle + 1;
                            }
                            span = low;
                        }
                    }
                }
            },
            12 => {
                if (n.a < 0 or n.a >= program.noises.len or n.d < 0 or n.d >= program.noises.len or n.b < 0 or n.c <= 0 or @as(i64, n.b) + n.c > program.indices.len) return .{ .result = -3, .span = 1 };
                const noise = cached_noise(program, cache, @intCast(n.a), @intCast(n.d), x, 0, z) * 4.0;
                if (!std.math.isFinite(noise) or @abs(noise) > 1073741824.0) return .{ .result = -2, .span = 1, .inputs = inputs | (order << 8) };
                const rounded: i32 = @intFromFloat(@floor(noise + 0.5));
                const index = @rem(r.y +% rounded +% n.c, n.c);
                if (index < 0) return .{ .result = -2, .span = 1, .inputs = inputs | (order << 8) };
                const sum = r.y +% rounded +% n.c;
                span = if (sum < 0) 1 else bounded(span, @as(i64, sum) + 1);
                const state = program.indices[@intCast(n.b + index)];
                var run: usize = 1;
                var band = index;
                while (run < span and run < n.c) {
                    band = if (band == 0) n.c - 1 else band - 1;
                    if (program.indices[@intCast(n.b + band)] != state) break;
                    run += 1;
                }
                if (run == n.c) run = span;
                return .{ .result = state, .span = run, .inputs = inputs | (order << 8) };
            },
            13 => {
                if (r.reserved & 8 == 0) return .{ .result = -7, .span = 1, .inputs = inputs | (order << 8) };
                use_input(&inputs, &order, 8, 4);
                result = r.steep != 0;
            },
            14 => {
                if (n.a < 0 or @as(i64, n.a) + 256 > program.indices.len) return .{ .result = -3, .span = 1 };
                const column_index: usize = @intCast((x & 15) * 16 + (z & 15));
                pc = program.indices[@as(usize, @intCast(n.a)) + column_index];
                continue;
            },
            else => return .{ .result = -3, .span = 1 },
        }
        pc = if (result) n.yes else n.no;
    }
    return .{ .result = -3, .span = 1 };
}

fn bounded(span: usize, limit: i64) usize {
    return if (limit < 1) 1 else @min(span, @as(usize, @intCast(limit)));
}

pub fn column(_: ?*c.JNIEnv, _: c.jclass, program_address: c.jlong, program_length: c.jint, input_address: c.jlong, output_address: c.jlong, cache_address: c.jlong, count: c.jint, x: c.jint, z: c.jint) callconv(.c) c.jint {
    if (count == 0 or count < -4096 or count > 128 or program_length < 64 or program_length > 8 * 1024 * 1024) return -1;
    if (program_address == 0 or input_address == 0 or output_address == 0 or cache_address == 0) return -1;
    const program_bits: u64 = @bitCast(program_address);
    const input_bits: u64 = @bitCast(input_address);
    const output_bits: u64 = @bitCast(output_address);
    const cache_bits: u64 = @bitCast(cache_address);
    if ((program_bits | input_bits | output_bits | cache_bits) & 7 != 0) return -1;
    const length: usize = @intCast(if (count < 0) -count else count);
    const pcap: usize = @intCast(program_length);
    const p: [*]const i32 = @ptrFromInt(program_bits);
    if (p[0] != 7 or p[1] <= 0 or p[1] > 16384 or p[2] <= 0 or p[2] > 65536) return -1;
    const entry_count: usize = @intCast(p[1]);
    const node_count: usize = @intCast(p[2]);
    for (3..15) |index| if (p[index] < 0) return -1;
    const offset: usize = @intCast(p[3]);
    const random_count: usize = @intCast(p[4]);
    const noise_count: usize = @intCast(p[5]);
    const simplex_count: usize = @intCast(p[6]);
    const climate_count: usize = @intCast(p[7]);
    const noise_offset: usize = @intCast(p[8]);
    const simplex_offset: usize = @intCast(p[9]);
    const climate_offset: usize = @intCast(p[10]);
    const index_offset: usize = @intCast(p[11]);
    const index_count: usize = @intCast(p[12]);
    const simplex_data: usize = @intCast(p[13]);
    const simplex_bytes: usize = @intCast(p[14]);
    if (random_count > 1024 or noise_count > 1024 or simplex_count > 16 or climate_count > 16384 or index_count > 262144) return -1;
    if (64 + entry_count * 4 > offset or offset % 8 != 0 or offset + node_count * 32 + random_count * 32 != noise_offset or noise_offset + noise_count * @sizeOf(Noise) != simplex_offset or simplex_offset + simplex_count * @sizeOf(Simplex) != climate_offset or climate_offset + climate_count * @sizeOf(Climate) != index_offset or index_offset + index_count * 4 > simplex_data or simplex_data % 8 != 0 or simplex_data + simplex_bytes != pcap) return -1;
    const raw: [*]const u8 = @ptrCast(p);
    const nodes: [*]const Node = @ptrCast(@alignCast(raw + offset));
    const randoms: [*]const RandomConfig = @ptrCast(@alignCast(raw + offset + node_count * 32));
    const noises: [*]const Noise = @ptrCast(@alignCast(raw + noise_offset));
    const simplexes: [*]const Simplex = @ptrCast(@alignCast(raw + simplex_offset));
    const climates: [*]const Climate = @ptrCast(@alignCast(raw + climate_offset));
    const indices: [*]const i32 = @ptrCast(@alignCast(raw + index_offset));
    const cache: *ColumnCache = @ptrFromInt(cache_bits);
    if (cache.program != program_bits) {
        for (noises[0..noise_count]) |noise| {
            if (noise.first == 0 or noise.second == 0 or noise.first_count > 32 or noise.second_count > 32) return -1;
        }
        for (simplexes[0..simplex_count]) |simplex| {
            if (simplex.offset < simplex_data or simplex.count == 0 or simplex.count > 32 or @as(usize, simplex.offset) + simplex.count * 264 > pcap) return -1;
        }
    }
    const program = Program{ .bytes = raw[0..pcap], .nodes = nodes[0..node_count], .randoms = randoms[0..random_count], .noises = noises[0..noise_count], .simplexes = simplexes[0..simplex_count], .climates = climates[0..climate_count], .indices = indices[0..index_count] };
    const input: *const Request = @ptrFromInt(input_bits);
    const output: [*]Result = @ptrFromInt(output_bits);
    if (cache.program != program_bits or cache.x != x or cache.z != z) {
        cache.program = program_bits;
        cache.x = x;
        cache.z = z;
        @memset(&cache.noise_valid, 0);
        cache.simplex_valid = 0;
    }
    const base = input.*;
    if (base.biome < 0 or base.biome >= entry_count) return -1;
    const entry = p[16 + @as(usize, @intCast(base.biome))];
    var produced: usize = 0;
    var consumed: usize = 0;
    for (0..8) |_| {
        if (consumed >= length) break;
        var request = base;
        const delta: i32 = @intCast(consumed);
        request.y -%= delta;
        request.temperature_y -%= delta;
        request.above +%= delta;
        request.below -%= delta;
        const result = evaluate_span(program, cache, entry, request, length - consumed, x, z);
        if (result.result == -3) return -1;
        consumed += result.span;
        const bottom = base.y -% @as(i32, @intCast(consumed - 1));
        if (produced > 0 and result.result >= -1 and output[produced - 1].state == result.result and output[produced - 1].inputs == result.inputs) {
            output[produced - 1].bottom = bottom;
        } else {
            output[produced] = .{ .state = result.result, .inputs = result.inputs, .bottom = bottom };
            produced += 1;
        }
        if (result.result < -1) break;
    }
    return @intCast(produced);
}

fn java_integer(value: f64) i32 {
    if (value >= std.math.maxInt(i32)) return std.math.maxInt(i32);
    if (value <= std.math.minInt(i32)) return std.math.minInt(i32);
    return @intFromFloat(value);
}

fn use_input(inputs: *i32, order: *i32, flag: i32, id: i32) void {
    if (inputs.* & flag == 0) {
        const shifts = [32]u5{ 0, 3, 3, 6, 3, 6, 6, 9, 3, 6, 6, 9, 6, 9, 9, 12, 3, 6, 6, 9, 6, 9, 9, 12, 6, 9, 9, 12, 9, 12, 12, 15 };
        const index = shifts[@as(usize, @intCast(inputs.*))];
        order.* |= id << index;
        inputs.* |= flag;
    }
}

fn positional_float(config: RandomConfig, x: i32, y: i32, z: i32) f64 {
    @setFloatMode(.strict);
    const x_hash: i32 = x *% 3129871;
    const x_seed: i64 = x_hash;
    var seed: i64 = x_seed ^ (@as(i64, z) *% 116129781) ^ @as(i64, y);
    seed = seed *% seed *% 42317861 +% seed *% 11;
    var low = @as(u64, @bitCast(seed >> 16)) ^ config.low;
    var bits: u32 = undefined;
    if (config.kind == 1) {
        const initial = (low ^ 25214903917) & 281474976710655;
        bits = @intCast(((initial *% 25214903917 +% 11) & 281474976710655) >> 24);
    } else {
        var high = config.high;
        if (low | high == 0) {
            low = 11400714819323198485;
            high = 7640891576956012809;
        }
        const next = std.math.rotl(u64, low +% high, 17) +% low;
        bits = @intCast(next >> 40);
    }
    const value: f32 = @as(f32, @floatFromInt(bits)) * 0x1.0p-24;
    return value;
}

fn noise_value(config: Noise, x: i32, y: i32, z: i32) f64 {
    @setFloatMode(.strict);
    const first: [*]const u8 = @ptrFromInt(config.first);
    const second: [*]const u8 = @ptrFromInt(config.second);
    return @import("adrenaline_native.zig").normal_noise_value(first, config.first_count, second, config.second_count, config.factor, @as(f64, @floatFromInt(x)) * config.xz_scale, if (config.y_scale == 0.0) 0.0 else @as(f64, @floatFromInt(y)) * config.y_scale, @as(f64, @floatFromInt(z)) * config.xz_scale);
}

fn cached_noise(program: Program, cache: *ColumnCache, index: usize, slot: usize, x: i32, y: i32, z: i32) f64 {
    const config = program.noises[index];
    if (config.y_scale != 0.0) return noise_value(config, x, y, z);
    const word = slot >> 6;
    const flag = @as(u64, 1) << @as(u6, @intCast(slot & 63));
    if (cache.noise_valid[word] & flag == 0) {
        cache.noises[slot] = noise_value(config, x, y, z);
        cache.noise_valid[word] |= flag;
    }
    return cache.noises[slot];
}

fn temperature_noise(program: Program, cache: *ColumnCache, index: u32, kind: u2, x: i32, z: i32) f64 {
    @setFloatMode(.strict);
    if (index >= program.simplexes.len) return std.math.nan(f64);
    const key = index * 4 + kind;
    const flag = @as(u64, 1) << @as(u6, @intCast(key));
    if (cache.simplex_valid & flag == 0) {
        var nx: f64 = undefined;
        var nz: f64 = undefined;
        if (kind == 0) {
            nx = @as(f32, @floatFromInt(x)) / 8.0;
            nz = @as(f32, @floatFromInt(z)) / 8.0;
        } else {
            const scale: f64 = switch (kind) { 1 => 0.05, 2 => 0.2, 3 => 0.09, else => unreachable };
            nx = @as(f64, @floatFromInt(x)) * scale;
            nz = @as(f64, @floatFromInt(z)) * scale;
        }
        cache.simplexes[key] = simplex_value(program, index, nx, nz);
        cache.simplex_valid |= flag;
    }
    return cache.simplexes[key];
}

fn temperature(program: Program, cache: *ColumnCache, climate: Climate, x: i32, y: i32, z: i32) f32 {
    @setFloatMode(.strict);
    if (cache.temperature_x != x or cache.temperature_z != z) {
        cache.temperature_x = x;
        cache.temperature_z = z;
        cache.simplex_valid = 0;
    }
    var base = climate.base;
    if (climate.modifier == 1) {
        const frozen = temperature_noise(program, cache, climate.frozen, 1, x, z) * 7.0;
        const information = temperature_noise(program, cache, climate.information, 2, x, z);
        if (frozen + information < 0.3 and temperature_noise(program, cache, climate.information, 3, x, z) < 0.8) base = 0.2;
    }
    if (y > 80) {
        const noise: f32 = @floatCast(temperature_noise(program, cache, climate.temperature, 0, x, z) * 8.0);
        const delta: f32 = (noise + @as(f32, @floatFromInt(y)) - 80.0) * @as(f32, 0.05) / 40.0;
        base -= delta;
    }
    return base;
}

fn simplex_value(program: Program, index: u32, x: f64, z: f64) f64 {
    @setFloatMode(.strict);
    if (index >= program.simplexes.len) return std.math.nan(f64);
    const config = program.simplexes[index];
    var input = config.input;
    var value = config.value;
    var result: f64 = 0.0;
    for (0..config.count) |octave| {
        const offset = config.offset + octave * 264;
        if (program.bytes[offset + 256] != 0) result += simplex_sample(program.bytes[offset..][0..256], x * input, z * input) * value;
        input /= 2.0;
        value *= 2.0;
    }
    return result;
}

fn simplex_sample(permutation: *const [256]u8, x: f64, z: f64) f64 {
    @setFloatMode(.strict);
    const f2 = 0.3660254037844386;
    const g2 = 0.21132486540518713;
    const skew = (x + z) * f2;
    const ix: i32 = @intFromFloat(@floor(x + skew));
    const iz: i32 = @intFromFloat(@floor(z + skew));
    const unskew = @as(f64, @floatFromInt(ix +% iz)) * g2;
    const dx = x - (@as(f64, @floatFromInt(ix)) - unskew);
    const dz = z - (@as(f64, @floatFromInt(iz)) - unskew);
    const sx: i32 = if (dx > dz) 1 else 0;
    const sz: i32 = 1 - sx;
    const mx = dx - @as(f64, @floatFromInt(sx)) + g2;
    const mz = dz - @as(f64, @floatFromInt(sz)) + g2;
    const ex = dx - 1.0 + 2.0 * g2;
    const ez = dz - 1.0 + 2.0 * g2;
    const px: u32 = @as(u32, @bitCast(ix)) & 255;
    const pz: u32 = @as(u32, @bitCast(iz)) & 255;
    const a = permutation[(px + permutation[pz]) & 255] % 12;
    const b = permutation[(px + @as(u32, @intCast(sx)) + permutation[(pz + @as(u32, @intCast(sz))) & 255]) & 255] % 12;
    const d = permutation[(px + 1 + permutation[(pz + 1) & 255]) & 255] % 12;
    return 70.0 * (simplex_corner(a, dx, dz) + simplex_corner(b, mx, mz) + simplex_corner(d, ex, ez));
}

fn simplex_corner(index: u8, x: f64, z: f64) f64 {
    @setFloatMode(.strict);
    const gx = [12]f64{ 1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0 };
    const gz = [12]f64{ 1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1 };
    const gy = [12]f64{ 0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1 };
    var weight = 0.5 - x * x - z * z - 0.0;
    if (weight < 0.0) return 0.0;
    weight *= weight;
    return weight * weight * (gx[index] * x + gz[index] * z + gy[index] * 0.0);
}
