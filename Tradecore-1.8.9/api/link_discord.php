<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/discord.php';

tc_require_method('POST');
tc_require_signature();
$input = tc_input();
tc_require_legacy_version($input);

$playerUuid = strtolower(trim((string) ($input['playerUuid'] ?? '')));
$playerName = trim((string) ($input['playerName'] ?? ''));
$discordId = trim((string) ($input['discordId'] ?? ''));
if (!tc_valid_uuid($playerUuid) || !preg_match('/^[A-Za-z0-9_]{1,16}$/', $playerName)) {
    tc_error(400, 'Ungültige Minecraft-Spielerdaten.');
}
if (!preg_match('/^[0-9]{17,20}$/', $discordId)) {
    tc_error(400, 'Ungültige Discord-ID.');
}

try {
    $pdo = tc_db();
    $byUuid = $pdo->prepare('SELECT id, discord_id FROM users WHERE minecraft_uuid = :uuid LIMIT 1');
    $byUuid->execute([':uuid' => $playerUuid]);
    $uuidUser = $byUuid->fetch();
    if ($uuidUser && (string) $uuidUser['discord_id'] === $discordId) {
        tc_json(['success' => true, 'message' => 'Diese Accounts sind bereits miteinander verknüpft.']);
    }
    if ($uuidUser && !empty($uuidUser['discord_id'])) {
        tc_error(409, 'Dieser Minecraft-Account ist bereits mit einem anderen Discord-Account verknüpft.');
    }

    $byDiscord = $pdo->prepare('SELECT id, minecraft_uuid FROM users WHERE discord_id = :discord_id LIMIT 1');
    $byDiscord->execute([':discord_id' => $discordId]);
    $discordUser = $byDiscord->fetch();
    if ($discordUser && !empty($discordUser['minecraft_uuid']) && (string) $discordUser['minecraft_uuid'] !== $playerUuid) {
        tc_error(409, 'Dieser Discord-Account ist bereits mit einem anderen Minecraft-Account verknüpft.');
    }
    if (!tc_discord_member($discordId)) {
        tc_error(403, 'Du bist nicht auf dem TradeCore-Discord-Server.');
    }

    if (!$discordUser) {
        $insertUser = $pdo->prepare('INSERT INTO users (discord_id) VALUES (:discord_id)');
        $insertUser->execute([':discord_id' => $discordId]);
    }

    $token = bin2hex(random_bytes(32));
    $pdo->prepare('DELETE FROM ' . TC_VERIFICATION_TABLE . ' WHERE player_uuid = :uuid OR expires_at <= NOW()')
        ->execute([':uuid' => $playerUuid]);
    $request = $pdo->prepare(
        'INSERT INTO ' . TC_VERIFICATION_TABLE .
        ' (token, player_uuid, player_name, discord_id, expires_at)
         VALUES (:token, :uuid, :player_name, :discord_id, DATE_ADD(NOW(), INTERVAL 15 MINUTE))'
    );
    $request->execute([
        ':token' => $token,
        ':uuid' => $playerUuid,
        ':player_name' => $playerName,
        ':discord_id' => $discordId,
    ]);

    $confirmationUrl = tc_public_base_url() . '/confirm_link.php?token=' . rawurlencode($token);
    if (!tc_discord_send_link_dm($discordId, $playerName, $confirmationUrl)) {
        $pdo->prepare('DELETE FROM ' . TC_VERIFICATION_TABLE . ' WHERE token = :token')->execute([':token' => $token]);
        tc_error(403, 'Die Discord-DM konnte nicht gesendet werden. Bitte erlaube Direktnachrichten.');
    }

    tc_json([
        'success' => true,
        'message' => 'Bestätigungslink gesendet. Bitte prüfe deine Discord-DMs.',
        'edition' => '1.8.9',
    ]);
} catch (Throwable $error) {
    tc_log('link_discord', $error->getMessage());
    tc_error(500, 'Die Account-Verknüpfung konnte nicht gestartet werden.');
}
