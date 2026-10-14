<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

tc_require_method('POST');
tc_require_signature();
$input = tc_input();
tc_require_legacy_version($input);

$playerUuid = strtolower(trim((string) ($input['playerUuid'] ?? '')));
if (!tc_valid_uuid($playerUuid)) {
    tc_error(400, 'Ungültige Minecraft-UUID.');
}

$curl = curl_init('https://profile.griefergames.live/minecraft/' . rawurlencode($playerUuid));
curl_setopt_array($curl, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_FOLLOWLOCATION => true,
    CURLOPT_CONNECTTIMEOUT => 4,
    CURLOPT_TIMEOUT => 8,
    CURLOPT_USERAGENT => 'TradeCore-1.8.9-Activity/1.0',
]);
$html = curl_exec($curl);
$status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
$curlError = curl_error($curl);
curl_close($curl);

if (!is_string($html) || $html === '' || $status !== 200) {
    tc_log('activity_status', $curlError !== '' ? $curlError : 'Profil antwortete mit HTTP ' . $status);
    tc_error(503, 'Der Aktivitätsstatus ist momentan nicht verfügbar.');
}

preg_match_all(
    '/Zuletzt\s*online[^<]*<\/p>\s*<p class="stat-block-text">\s*([0-9]{2}\.[0-9]{2}\.[0-9]{4})\s*<\/p>/i',
    $html,
    $matches
);
$dates = array_unique($matches[1] ?? []);
$active = in_array(date('d.m.Y'), $dates, true) || in_array(date('d.m.Y', strtotime('-1 day')), $dates, true);

tc_json([
    'success' => true,
    'active' => $active,
    'status' => $active ? 'AKTIV' : 'INAKTIV',
    'checked_at' => time(),
    'edition' => '1.8.9',
]);
