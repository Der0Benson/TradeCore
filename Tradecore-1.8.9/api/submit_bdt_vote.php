<?php

declare(strict_types=1);

require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/discord.php';

tc_require_method('POST');
tc_require_signature();
$input = tc_input();

$bdtId = filter_var($input['bdtId'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
$playerUuid = strtolower(trim((string) ($input['playerUuid'] ?? '')));
$voteType = trim((string) ($input['voteType'] ?? ''));
if ($bdtId === false || !tc_valid_uuid($playerUuid) || !in_array($voteType, ['schnell', 'langsam'], true)) {
    tc_error(400, 'Ungültige Abstimmungsdaten.');
}

try {
    $pdo = tc_db();
    $bdtStatement = $pdo->prepare('SELECT * FROM ' . TC_BDT_TABLE . ' WHERE id = :id AND bdt_date = CURDATE() LIMIT 1');
    $bdtStatement->execute([':id' => $bdtId]);
    $bdt = $bdtStatement->fetch();
    if (!$bdt) {
        tc_error(404, 'Der heutige 1.8.9-BdT wurde nicht gefunden.');
    }

    $insert = $pdo->prepare(
        'INSERT INTO ' . TC_BDT_VOTES_TABLE . ' (bdt_id, player_uuid, vote_type)
         VALUES (:bdt_id, :player_uuid, :vote_type)'
    );
    try {
        $insert->execute([
            ':bdt_id' => $bdtId,
            ':player_uuid' => $playerUuid,
            ':vote_type' => $voteType,
        ]);
    } catch (PDOException $error) {
        if ($error->getCode() === '23000') {
            tc_error(409, 'Du hast für diesen 1.8.9-BdT bereits abgestimmt.');
        }
        throw $error;
    }

    $counts = $pdo->prepare(
        'SELECT
            SUM(CASE WHEN vote_type = \'schnell\' THEN 1 ELSE 0 END) AS schnell,
            SUM(CASE WHEN vote_type = \'langsam\' THEN 1 ELSE 0 END) AS langsam
         FROM ' . TC_BDT_VOTES_TABLE . ' WHERE bdt_id = :bdt_id'
    );
    $counts->execute([':bdt_id' => $bdtId]);
    $votes = $counts->fetch() ?: ['schnell' => 0, 'langsam' => 0];

    try {
        tc_discord_update_bdt($bdt, (int) $votes['schnell'], (int) $votes['langsam']);
    } catch (Throwable $discordError) {
        tc_log('vote_bdt_discord', $discordError->getMessage());
    }

    tc_json([
        'success' => true,
        'message' => 'Deine Stimme für den 1.8.9-BdT wurde gespeichert.',
        'schnell_votes' => (int) $votes['schnell'],
        'langsam_votes' => (int) $votes['langsam'],
        'edition' => '1.8.9',
    ]);
} catch (Throwable $error) {
    tc_log('submit_bdt_vote', $error->getMessage());
    tc_error(500, 'Die Abstimmung konnte nicht gespeichert werden.');
}
