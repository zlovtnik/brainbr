/// Ported from TransitionMath.kt
pub fn blended_burden(
    legacy_taxes: &serde_json::Value,
    reform_taxes: &serde_json::Value,
    legacy_weight: f64,
    reform_weight: f64,
) -> (f64, f64, f64) {
    let legacy_total = json_sum(legacy_taxes);
    let reform_total = json_sum(reform_taxes);
    (
        legacy_total * legacy_weight,
        reform_total * reform_weight,
        legacy_total * legacy_weight + reform_total * reform_weight,
    )
}

pub fn compute_risk_score(legacy_total: f64, reform_total: f64, audit_confidence: Option<f64>) -> i32 {
    const DELTA_NORMALIZER: f64 = 30.0;
    const DELTA_WEIGHT: f64 = 0.6;
    const CONFIDENCE_WEIGHT: f64 = 0.4;

    let legacy_total = if legacy_total.is_finite() { legacy_total } else { 0.0 };
    let reform_total = if reform_total.is_finite() { reform_total } else { 0.0 };

    let delta = (reform_total - legacy_total).abs();
    let normalized_delta = (delta / DELTA_NORMALIZER).min(1.0);

    let confidence = audit_confidence.unwrap_or(0.5);
    let clamped_confidence = if confidence.is_finite() { confidence.clamp(0.0, 1.0) } else { 0.5 };

    let raw = (DELTA_WEIGHT * normalized_delta + CONFIDENCE_WEIGHT * (1.0 - clamped_confidence)) * 10.0;
    let raw = if raw.is_finite() { raw } else { 10.0 };
    (raw.ceil() as i32).clamp(1, 10)
}

/// Recursively sums all numeric values in a JSON value (object, array, or scalar).
fn json_sum(v: &serde_json::Value) -> f64 {
    match v {
        serde_json::Value::Number(n) => n.as_f64().unwrap_or(0.0),
        serde_json::Value::Object(m) => m.values().map(json_sum).sum(),
        serde_json::Value::Array(arr) => arr.iter().map(json_sum).sum(),
        _ => 0.0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn risk_score_bounds() {
        assert_eq!(compute_risk_score(0.0, 0.0, Some(1.0)), 1);
        assert_eq!(compute_risk_score(0.0, 100.0, Some(0.0)), 10);
    }

    #[test]
    fn risk_score_nan_inputs_are_safe() {
        let score = compute_risk_score(f64::NAN, f64::NAN, Some(f64::NAN));
        assert!((1..=10).contains(&score));
    }

    #[test]
    fn blended_burden_2026() {
        let legacy = serde_json::json!({"icms": 10.0, "pis": 2.0});
        let reform = serde_json::json!({"ibs": 5.0});
        let (lc, rc, total) = blended_burden(&legacy, &reform, 0.9, 0.1);
        assert!((lc - 10.8).abs() < 1e-9);
        assert!((rc - 0.5).abs() < 1e-9);
        assert!((total - 11.3).abs() < 1e-9);
    }

    #[test]
    fn json_sum_nested() {
        let v = serde_json::json!({"a": 1.0, "b": {"c": 2.0, "d": [3.0, 4.0]}});
        assert!((json_sum(&v) - 10.0).abs() < 1e-9);
    }
}
