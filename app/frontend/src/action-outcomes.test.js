import assert from "node:assert/strict";
import { test } from "node:test";
import { hasActionUpdate, providerReturnedSuccess } from "./action-outcomes.js";

test("an execution outcome takes precedence over an earlier approval", () => {
  assert.equal(providerReturnedSuccess({approvalStatus: "succeeded", actionOutcome: "unknown"}), false);
  assert.equal(providerReturnedSuccess({approvalStatus: "succeeded", actionOutcome: "accepted"}), true);
  assert.equal(providerReturnedSuccess({approvalStatus: "succeeded"}), true);
  assert.equal(providerReturnedSuccess({approvalStatus: "failed"}), false);
});

test("only resolved approvals or action outcomes produce an update", () => {
  assert.equal(hasActionUpdate({approvalStatus: "pending"}), false);
  assert.equal(hasActionUpdate({}), false);
  assert.equal(hasActionUpdate({approvalStatus: "denied"}), true);
  assert.equal(hasActionUpdate({approvalStatus: "pending", actionOutcome: "unknown"}), true);
});
