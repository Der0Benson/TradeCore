package de.tradecore.legacy.config;

/** Zentrale Schaltstelle für die getrennten 1.8.9-Endpunkte. */
public final class ApiEndpoints {
    private static final String BASE_URL = "https://mc-tradecore.de/API/1.8.9";

    private ApiEndpoints() {}

    public static String latest() { return BASE_URL + "/latest.php"; }
    public static String getBdt() { return BASE_URL + "/get_bdt.php"; }
    public static String createBdt() { return BASE_URL + "/create_bdt.php"; }
    public static String voteBdt() { return BASE_URL + "/submit_bdt_vote.php"; }
    public static String linkDiscord() { return BASE_URL + "/link_discord.php"; }
    public static String activityStatus() { return BASE_URL + "/activity_status.php"; }
}
