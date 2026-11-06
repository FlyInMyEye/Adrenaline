pub const DensityRangeFrame = struct {
    base: usize,
    outside: usize,
    end: usize,
    minimum: f64,
    maximum: f64,
    mode: u8,
};

pub const DensityCallFrame = struct {
    counter: usize,
    base: usize,
    slot: usize,
};

pub const DensitySplineFrame = struct {
    base: usize,
    knots: usize,
    count: usize,
    point: usize,
};
