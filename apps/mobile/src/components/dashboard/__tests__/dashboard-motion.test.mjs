import { describe, it } from 'node:test';
import assert from 'node:assert';

import { MOTION } from '../../../constants/motion.ts';

describe('Eparly Dashboard Motion & Micro-Interactions System', () => {
  it('verifies standardized spring physics tokens adhere to Apple HIG and editorial guidelines', () => {
    // Press spring: quick and snappy touch release
    assert.ok(MOTION.spring.press.damping >= 16);
    assert.ok(MOTION.spring.press.stiffness >= 240);
    assert.strictEqual(MOTION.spring.press.mass, 0.9);

    // Selection spring: smooth, fluid state transitions (segmented bar & chart pillar)
    assert.ok(MOTION.spring.selection.damping >= 20);
    assert.ok(MOTION.spring.selection.stiffness >= 180);
    assert.strictEqual(MOTION.spring.selection.mass, 1.0);

    // Content spring: gentle layout settling without aggressive bounce
    assert.ok(MOTION.spring.content.damping >= 22);
    assert.ok(MOTION.spring.content.stiffness >= 160);
  });

  it('verifies subtle, non-distorting scale tokens', () => {
    // Card press should be very subtle (0.985) to avoid disorienting content distortion
    assert.strictEqual(MOTION.scale.cardPress, 0.985);
    // Button press should feel tactile yet restrained (0.95 - 0.975)
    assert.strictEqual(MOTION.scale.buttonPress, 0.95);
    assert.strictEqual(MOTION.scale.actionPress, 0.975);
    // Unpressed state is unity
    assert.strictEqual(MOTION.scale.unpressed, 1.0);
  });

  it('verifies timing duration tokens are under 250ms for optimal perceptual speed', () => {
    assert.ok(MOTION.duration.instant <= 120);
    assert.ok(MOTION.duration.fast <= 200);
    assert.ok(MOTION.duration.standard <= 250);
  });
});
