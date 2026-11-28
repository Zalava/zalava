// Chat receives plain JSON; keep the outcome rules independent of presentation.
export function hasActionUpdate(tool) {
  return Boolean(tool.actionOutcome || (tool.approvalStatus && tool.approvalStatus !== "pending"));
}

export function providerReturnedSuccess(tool) {
  return tool.actionOutcome ? tool.actionOutcome === "accepted" : tool.approvalStatus === "succeeded";
}
