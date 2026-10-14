<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';

tc_require_method('GET');
tc_require_signature();

$version = trim((string) ($_GET['minecraftVersion'] ?? '1.8.9'));
if ($version !== '1.8.9') {
    tc_error(400, 'Dieser Endpunkt ist nur für Minecraft 1.8.9 bestimmt.');
}

$playerUuid = trim((string) ($_GET['playerUuid'] ?? ''));
if ($playerUuid !== '' && !tc_valid_uuid($playerUuid)) {
    tc_error(400, 'Ungültige Minecraft-UUID.');
}

try {
    $statement = tc_db()->prepare(
        'SELECT b.id AS bdt_id, b.item_id, b.item_name, b.gewinn,
            SUM(CASE WHEN v.vote_type = \'schnell\' THEN 1 ELSE 0 END) AS schnell_votes,
            SUM(CASE WHEN v.vote_type = \'langsam\' THEN 1 ELSE 0 END) AS langsam_votes,
            MAX(CASE WHEN v.player_uuid = :player_uuid THEN 1 ELSE 0 END) AS has_voted
         FROM ' . TC_BDT_TABLE . ' b
         LEFT JOIN ' . TC_BDT_VOTES_TABLE . ' v ON v.bdt_id = b.id
         WHERE b.bdt_date = CURDATE()
         GROUP BY b.id, b.item_id, b.item_name, b.gewinn
         LIMIT 1'
    );
    $statement->execute([':player_uuid' => $playerUuid]);
    $bdt = $statement->fetch();
    if (!$bdt) {
        tc_json(['success' => false, 'message' => 'Heute ist noch kein 1.8.9-BdT vorhanden.']);
    }

    if ((string) $bdt['item_id'] === 'minecraft:fence' && strcasecmp((string) $bdt['item_name'], 'Fence') === 0) {
        $bdt['item_name'] = 'Eichenholzzaun';
    }

    tc_json([
        'success' => true,
        'bdt_id' => (int) $bdt['bdt_id'],
        'item_id' => (string) $bdt['item_id'],
        'item_name' => (string) $bdt['item_name'],
        'gewinn' => (string) $bdt['gewinn'],
        'schnell_votes' => (int) $bdt['schnell_votes'],
        'langsam_votes' => (int) $bdt['langsam_votes'],
        'has_voted' => (bool) $bdt['has_voted'],
        'edition' => '1.8.9',
    ]);
} catch (Throwable $error) {
    tc_log('get_bdt', $error->getMessage());
    tc_error(500, 'Der 1.8.9-BdT konnte nicht geladen werden.');
}
