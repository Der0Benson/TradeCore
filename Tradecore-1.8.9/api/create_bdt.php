<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/discord.php';

tc_require_method('POST');
tc_require_signature();
$input = tc_input();
tc_require_legacy_version($input);

$itemId = strtolower(trim((string) ($input['itemId'] ?? '')));
$itemName = trim((string) ($input['itemDisplayName'] ?? ''));
$reward = trim((string) ($input['gewinn'] ?? ''));
$playerUuid = strtolower(trim((string) ($input['playerUuid'] ?? '')));

if (!preg_match('~^[a-z0-9_.-]+:[a-z0-9_./-]+(?:#[0-9]{1,2})?$~', $itemId)) {
    tc_error(400, 'Ungültige Block- oder Entity-ID.');
}
$metadataPosition = strrpos($itemId, '#');
if ($metadataPosition !== false && (int) substr($itemId, $metadataPosition + 1) > 15) {
    tc_error(400, 'Ungültiger 1.8.9-Metadatenwert.');
}
if ($itemName === '' || strlen($itemName) > 191 || $reward === '' || strlen($reward) > 191) {
    tc_error(400, 'Name oder Gewinn fehlt beziehungsweise ist zu lang.');
}
if (!tc_valid_uuid($playerUuid)) {
    tc_error(400, 'Ungültige Minecraft-UUID.');
}

// Kompatibilität mit bereits veröffentlichten Builds, die den technischen
// 1.8.9-Namen "Fence" statt des ausgewählten Anzeigenamens gesendet haben.
if ($itemId === 'minecraft:fence' && strcasecmp($itemName, 'Fence') === 0) {
    $itemName = 'Eichenholzzaun';
}

try {
    $pdo = tc_db();
    $userStatement = $pdo->prepare('SELECT id, discord_id FROM users WHERE minecraft_uuid = :uuid LIMIT 1');
    $userStatement->execute([':uuid' => $playerUuid]);
    $user = $userStatement->fetch();
    if (!$user || empty($user['discord_id'])) {
        tc_error(403, 'Dein Minecraft-Account ist noch nicht mit Discord verknüpft.', [
            'requires_verification' => true,
            'edition' => '1.8.9',
        ]);
    }

    try {
        $banStatement = $pdo->prepare('SELECT 1 FROM Banned_Users WHERE player_uuid = :uuid LIMIT 1');
        $banStatement->execute([':uuid' => $playerUuid]);
        if ($banStatement->fetchColumn()) {
            tc_error(403, 'Dein Account ist für diese Aktion gesperrt.');
        }
    } catch (PDOException $ignored) {
        // Installationen ohne optionale Sperrliste duerfen weiterarbeiten.
    }

    if (!tc_discord_member((string) $user['discord_id'])) {
        tc_error(403, 'Du bist nicht auf dem TradeCore-Discord-Server.');
    }

    $insert = $pdo->prepare(
        'INSERT INTO ' . TC_BDT_TABLE .
        ' (bdt_date, item_id, item_name, gewinn, submitted_by_uuid)
         VALUES (CURDATE(), :item_id, :item_name, :gewinn, :uuid)'
    );
    try {
        $insert->execute([
            ':item_id' => $itemId,
            ':item_name' => $itemName,
            ':gewinn' => $reward,
            ':uuid' => $playerUuid,
        ]);
    } catch (PDOException $error) {
        if ($error->getCode() === '23000') {
            tc_error(409, 'Für heute existiert bereits ein eigener 1.8.9-BdT.');
        }
        throw $error;
    }

    $bdtId = (int) $pdo->lastInsertId();
    $bdt = [
        'id' => $bdtId,
        'bdt_date' => date('Y-m-d'),
        'item_id' => $itemId,
        'item_name' => $itemName,
        'gewinn' => $reward,
    ];

    try {
        $previousMessageStatement = $pdo->query(
            'SELECT discord_message_id FROM ' . TC_BDT_TABLE .
            " WHERE bdt_date < CURDATE()
                AND discord_message_id IS NOT NULL
                AND discord_message_id <> ''
              ORDER BY bdt_date DESC, id DESC
              LIMIT 1"
        );
        $previousMessageId = $previousMessageStatement->fetchColumn();
        $messageId = tc_discord_announce_bdt(
            $bdt,
            $previousMessageId === false ? null : (string) $previousMessageId
        );
        if ($messageId !== null) {
            $update = $pdo->prepare('UPDATE ' . TC_BDT_TABLE . ' SET discord_message_id = :message_id WHERE id = :id');
            $update->execute([':message_id' => $messageId, ':id' => $bdtId]);
        }
    } catch (Throwable $discordError) {
        tc_log('create_bdt_discord', $discordError->getMessage());
    }

    tc_json([
        'success' => true,
        'message' => 'Der 1.8.9-Block des Tages wurde erstellt.',
        'bdt_id' => $bdtId,
        'edition' => '1.8.9',
    ], 201);
} catch (Throwable $error) {
    tc_log('create_bdt', $error->getMessage());
    tc_error(500, 'Der 1.8.9-BdT konnte nicht erstellt werden.');
}
