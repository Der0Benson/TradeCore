package de.tradecore.legacy.model;

public final class ActionResult {
    public final boolean success;
    public final String message;

    public ActionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
